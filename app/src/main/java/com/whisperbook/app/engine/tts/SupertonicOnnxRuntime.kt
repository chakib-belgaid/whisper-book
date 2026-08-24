package com.whisperbook.app.engine.tts

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.providers.NNAPIFlags
import android.content.Context
import android.os.ParcelFileDescriptor
import com.whisperbook.app.engine.compute.NeuralExecutionBackend
import com.whisperbook.app.engine.compute.throwIfFatalOrCancelled
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.nio.channels.FileChannel
import java.text.Normalizer
import java.util.EnumSet
import java.util.Random
import kotlin.math.ceil
import kotlin.math.min
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal interface SupertonicRuntime : AutoCloseable {
    val backend: NeuralExecutionBackend
    val sampleRate: Int
    val speakerCount: Int

    fun synthesize(text: String, languageCode: String, speakerIndex: Int, speed: Float): FloatArray
}

internal fun interface SupertonicRuntimeFactory {
    fun create(backend: NeuralExecutionBackend): SupertonicRuntime
}

/**
 * Direct Supertonic 3 execution through ONNX Runtime.
 *
 * The inference flow follows the MIT-licensed Supertonic reference pipeline and the Apache-2.0
 * sherpa-onnx binary-asset format. Direct sessions let Whisperbook request accelerator-only NNAPI
 * partitions and retain ONNX Runtime CPU kernels as the per-operation fallback.
 */
internal class OrtSupertonicRuntime(
    context: Context,
    override val backend: NeuralExecutionBackend,
) : SupertonicRuntime {
    private val appContext = context.applicationContext
    private val environment = OrtEnvironment.getEnvironment()
    private val config = loadConfig(appContext)
    private val unicodeIndexer = loadInt32Asset(appContext, SupertonicAssets.unicodeIndexerAssetPath)
    private val styles = loadVoiceStyles(appContext, SupertonicAssets.voiceStyleAssetPath)
    private val textProcessor = SupertonicTextProcessor(unicodeIndexer)

    private val durationPredictor: OrtSession
    private val textEncoder: OrtSession
    private val vectorEstimator: OrtSession
    private val vocoder: OrtSession

    override val sampleRate: Int = config.sampleRate
    override val speakerCount: Int = styles.speakerCount

    init {
        val options = createSessionOptions(backend)
        var duration: OrtSession? = null
        var encoder: OrtSession? = null
        var estimator: OrtSession? = null
        var decoder: OrtSession? = null
        try {
            duration = createAssetSession(SupertonicAssets.durationPredictorAssetPath, options)
            encoder = createAssetSession(SupertonicAssets.textEncoderAssetPath, options)
            estimator = createAssetSession(SupertonicAssets.vectorEstimatorAssetPath, options)
            decoder = createAssetSession(SupertonicAssets.vocoderAssetPath, options)
        } catch (failure: Throwable) {
            failure.throwIfFatalOrCancelled()
            runCatching { decoder?.close() }
            runCatching { estimator?.close() }
            runCatching { encoder?.close() }
            runCatching { duration?.close() }
            throw failure
        } finally {
            options.close()
        }
        durationPredictor = requireNotNull(duration)
        textEncoder = requireNotNull(encoder)
        vectorEstimator = requireNotNull(estimator)
        vocoder = requireNotNull(decoder)
    }

    override fun synthesize(
        text: String,
        languageCode: String,
        speakerIndex: Int,
        speed: Float,
    ): FloatArray {
        require(speakerIndex in 0 until speakerCount) { "Speaker index is out of bounds" }
        require(speed.isFinite() && speed > 0f) { "Speech speed must be positive" }
        val textIds = textProcessor.encode(text, languageCode)
        require(textIds.isNotEmpty()) { "Text preprocessing returned no model tokens" }
        val textMask = FloatArray(textIds.size) { 1f }
        val textIdsShape = longArrayOf(1, textIds.size.toLong())
        val textMaskShape = longArrayOf(1, 1, textIds.size.toLong())
        val style = styles.slice(speakerIndex)

        OnnxTensor.createTensor(environment, LongBuffer.wrap(textIds), textIdsShape).use { idsTensor ->
            OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(textMask),
                textMaskShape,
            ).use { textMaskTensor ->
                OnnxTensor.createTensor(
                    environment,
                    FloatBuffer.wrap(style.dp),
                    style.dpShape,
                ).use { styleDpTensor ->
                    OnnxTensor.createTensor(
                        environment,
                        FloatBuffer.wrap(style.ttl),
                        style.ttlShape,
                    ).use { styleTtlTensor ->
                        return synthesizeWithTensors(
                            idsTensor = idsTensor,
                            textMaskTensor = textMaskTensor,
                            styleDpTensor = styleDpTensor,
                            styleTtlTensor = styleTtlTensor,
                            speed = speed,
                        )
                    }
                }
            }
        }
    }

    private fun synthesizeWithTensors(
        idsTensor: OnnxTensor,
        textMaskTensor: OnnxTensor,
        styleDpTensor: OnnxTensor,
        styleTtlTensor: OnnxTensor,
        speed: Float,
    ): FloatArray {
        val durationSeconds = durationPredictor.run(
            mapOf(
                "text_ids" to idsTensor,
                "style_dp" to styleDpTensor,
                "text_mask" to textMaskTensor,
            ),
        ).use { result ->
            val values = result.firstTensorFloats()
            check(values.size == 1) { "Duration predictor returned ${values.size} values instead of one" }
            (values.single() / speed).coerceAtLeast(MIN_DURATION_SECONDS).also { duration ->
                check(duration.isFinite()) { "Duration predictor returned a non-finite duration" }
            }
        }

        return textEncoder.run(
            mapOf(
                "text_ids" to idsTensor,
                "style_ttl" to styleTtlTensor,
                "text_mask" to textMaskTensor,
            ),
        ).use { textResult ->
            val textEmbedding = textResult[0] as? OnnxTensor
                ?: error("Text encoder did not return a tensor")
            denoiseAndDecode(
                durationSeconds = durationSeconds,
                textEmbedding = textEmbedding,
                textMaskTensor = textMaskTensor,
                styleTtlTensor = styleTtlTensor,
            )
        }
    }

    private fun denoiseAndDecode(
        durationSeconds: Float,
        textEmbedding: OnnxTensor,
        textMaskTensor: OnnxTensor,
        styleTtlTensor: OnnxTensor,
    ): FloatArray {
        val wavLength = (durationSeconds * sampleRate).toLong().coerceAtLeast(1L)
        val chunkSize = config.baseChunkSize * config.chunkCompressFactor
        val latentLength = ceil(wavLength.toDouble() / chunkSize.toDouble())
            .toInt()
            .coerceIn(1, MAX_LATENT_LENGTH)
        val latentDimension = config.latentDimension * config.chunkCompressFactor
        val latentSize = Math.multiplyExact(latentDimension, latentLength)
        var latent = FloatArray(latentSize)
        val random = Random()
        latent.indices.forEach { index -> latent[index] = random.nextGaussian().toFloat() }
        val latentShape = longArrayOf(1, latentDimension.toLong(), latentLength.toLong())
        val latentMask = FloatArray(latentLength) { 1f }
        val latentMaskShape = longArrayOf(1, 1, latentLength.toLong())

        OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(latentMask),
            latentMaskShape,
        ).use { latentMaskTensor ->
            OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(floatArrayOf(DENOISING_STEPS.toFloat())),
                longArrayOf(1),
            ).use { totalStepTensor ->
                repeat(DENOISING_STEPS) { step ->
                    OnnxTensor.createTensor(
                        environment,
                        FloatBuffer.wrap(latent),
                        latentShape,
                    ).use { noisyLatentTensor ->
                        OnnxTensor.createTensor(
                            environment,
                            FloatBuffer.wrap(floatArrayOf(step.toFloat())),
                            longArrayOf(1),
                        ).use { currentStepTensor ->
                            latent = vectorEstimator.run(
                                mapOf(
                                    "noisy_latent" to noisyLatentTensor,
                                    "text_emb" to textEmbedding,
                                    "style_ttl" to styleTtlTensor,
                                    "latent_mask" to latentMaskTensor,
                                    "text_mask" to textMaskTensor,
                                    "current_step" to currentStepTensor,
                                    "total_step" to totalStepTensor,
                                ),
                            ).use { result ->
                                result.firstTensorFloats().also { denoised ->
                                    check(denoised.size == latentSize) {
                                        "Vector estimator returned ${denoised.size} values; $latentSize were expected"
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(latent),
            latentShape,
        ).use { latentTensor ->
            vocoder.run(mapOf("latent" to latentTensor)).use { result ->
                val output = result.firstTensorFloats()
                check(output.isNotEmpty()) { "Vocoder returned empty audio" }
                check(output.all(Float::isFinite)) { "Vocoder returned non-finite audio" }
                output.copyOf(min(output.size.toLong(), wavLength).toInt())
            }
        }
    }

    override fun close() {
        runCatching { vocoder.close() }
        runCatching { vectorEstimator.close() }
        runCatching { textEncoder.close() }
        runCatching { durationPredictor.close() }
    }

    private fun createAssetSession(path: String, options: OrtSession.SessionOptions): OrtSession =
        environment.createSession(mapUncompressedAsset(appContext, path), options)

    private companion object {
        // Match the Supertonic/sherpa default so CPU fallback does not add unnecessary work.
        const val DENOISING_STEPS = 5
        const val MIN_DURATION_SECONDS = 0.1f
        const val MAX_LATENT_LENGTH = 10_000
    }
}

internal data class SupertonicModelConfig(
    val sampleRate: Int,
    val baseChunkSize: Int,
    val chunkCompressFactor: Int,
    val latentDimension: Int,
)

private data class SupertonicStyleSlice(
    val ttl: FloatArray,
    val ttlShape: LongArray,
    val dp: FloatArray,
    val dpShape: LongArray,
)

private data class SupertonicVoiceStyles(
    val ttl: FloatArray,
    val ttlShape: LongArray,
    val dp: FloatArray,
    val dpShape: LongArray,
) {
    val speakerCount: Int = ttlShape.first().toInt()

    init {
        require(ttlShape.size == 3 && dpShape.size == 3) { "Voice style tensors must be rank three" }
        require(speakerCount > 0 && dpShape.first() == ttlShape.first()) {
            "Voice style tensors must contain the same positive speaker count"
        }
    }

    fun slice(speakerIndex: Int): SupertonicStyleSlice {
        require(speakerIndex in 0 until speakerCount)
        val ttlSliceSize = Math.multiplyExact(ttlShape[1], ttlShape[2]).toInt()
        val dpSliceSize = Math.multiplyExact(dpShape[1], dpShape[2]).toInt()
        return SupertonicStyleSlice(
            ttl = ttl.copyOfRange(speakerIndex * ttlSliceSize, (speakerIndex + 1) * ttlSliceSize),
            ttlShape = longArrayOf(1, ttlShape[1], ttlShape[2]),
            dp = dp.copyOfRange(speakerIndex * dpSliceSize, (speakerIndex + 1) * dpSliceSize),
            dpShape = longArrayOf(1, dpShape[1], dpShape[2]),
        )
    }
}

internal class SupertonicTextProcessor(private val indexer: IntArray) {
    fun encode(text: String, languageCode: String): LongArray {
        require(languageCode in SUPPORTED_LANGUAGES) { "Unsupported Supertonic language: $languageCode" }
        val processed = preprocess(text, languageCode)
        return processed.codePoints()
            .mapToLong { codePoint ->
                if (codePoint in indexer.indices) indexer[codePoint].toLong() else UNKNOWN_TOKEN_ID
            }
            .toArray()
    }

    internal fun preprocess(text: String, languageCode: String): String {
        var normalized = Normalizer.normalize(text.trim(), Normalizer.Form.NFKD)
        REPLACEMENTS.forEach { (source, target) -> normalized = normalized.replace(source, target) }
        normalized = buildString(normalized.length) {
            normalized.codePoints().forEach { codePoint ->
                if (!isEmojiOrSymbol(codePoint)) appendCodePoint(codePoint)
            }
        }
        normalized = SPACE_BEFORE_PUNCTUATION.replace(normalized, "$1")
        normalized = normalized
            .replace("\"\"", "\"")
            .replace("''", "'")
            .replace("`", "")
        normalized = WHITESPACE.replace(normalized, " ").trim()
        if (normalized.isNotEmpty() && normalized.lastCodePoint() !in ENDING_PUNCTUATION) {
            normalized += "."
        }
        return "<$languageCode>$normalized</$languageCode>"
    }

    private fun String.lastCodePoint(): Int = codePointBefore(length)

    private fun isEmojiOrSymbol(codePoint: Int): Boolean =
        codePoint in 0x1F000..0x1FAFF || codePoint in 0x2600..0x27BF

    private companion object {
        const val UNKNOWN_TOKEN_ID = 0L
        val SUPPORTED_LANGUAGES = setOf(
            "en", "ko", "ja", "ar", "bg", "cs", "da", "de", "el", "es", "et",
            "fi", "fr", "hi", "hr", "hu", "id", "it", "lt", "lv", "nl", "pl",
            "pt", "ro", "ru", "sk", "sl", "sv", "tr", "uk", "vi",
        )
        val REPLACEMENTS = linkedMapOf(
            "–" to "-", "‑" to "-", "—" to "-", "_" to " ",
            "“" to "\"", "”" to "\"", "‘" to "'", "’" to "'", "´" to "'", "`" to "'",
            "[" to " ", "]" to " ", "|" to " ", "/" to " ", "#" to " ",
            "→" to " ", "←" to " ", "♥" to "", "☆" to "", "♡" to "", "©" to "",
            "\\" to "", "@" to " at ", "e.g.," to "for example, ", "i.e.," to "that is, ",
        )
        val SPACE_BEFORE_PUNCTUATION = Regex("\\s+([,.!?;:'])")
        val WHITESPACE = Regex("\\s+")
        val ENDING_PUNCTUATION = setOf(
            '.'.code, '!'.code, '?'.code, ';'.code, ':'.code, ','.code, '\''.code, '"'.code,
            ')'.code, ']'.code, '}'.code, '>'.code, '…'.code, '。'.code, '」'.code, '』'.code,
            '】'.code, '〉'.code, '》'.code, '›'.code, '»'.code,
        )
    }
}

private fun createSessionOptions(backend: NeuralExecutionBackend): OrtSession.SessionOptions =
    OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(1)
        setInterOpNumThreads(1)
        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        if (backend == NeuralExecutionBackend.NNAPI) {
            // CPU_DISABLED prevents the slow NNAPI reference CPU from being selected. Unsupported
            // partitions remain on ONNX Runtime's optimized CPU execution provider.
            // Supertonic convolution tensors are already channel-first. API 29+ drivers may
            // accept NCHW directly, avoiding NNAPI-inserted NHWC transpose work.
            addNnapi(EnumSet.of(NNAPIFlags.CPU_DISABLED, NNAPIFlags.USE_NCHW))
        }
    }

private fun OrtSession.Result.firstTensorFloats(): FloatArray {
    val tensor = this[0] as? OnnxTensor ?: error("ONNX session did not return a tensor")
    val buffer = requireNotNull(tensor.floatBuffer) { "ONNX tensor is not float data" }.duplicate()
    buffer.rewind()
    val values = FloatArray(buffer.remaining())
    buffer.get(values)
    return values
}

private fun loadConfig(context: Context): SupertonicModelConfig {
    val root = context.assets.open(SupertonicAssets.ttsJsonAssetPath).bufferedReader().use { reader ->
        Json.parseToJsonElement(reader.readText()).jsonObject
    }
    val ae = root.getValue("ae").jsonObject
    val ttl = root.getValue("ttl").jsonObject
    return SupertonicModelConfig(
        sampleRate = ae.getValue("sample_rate").jsonPrimitive.content.toInt(),
        baseChunkSize = ae.getValue("base_chunk_size").jsonPrimitive.content.toInt(),
        chunkCompressFactor = ttl.getValue("chunk_compress_factor").jsonPrimitive.content.toInt(),
        latentDimension = ttl.getValue("latent_dim").jsonPrimitive.content.toInt(),
    ).also { config ->
        require(config.sampleRate > 0 && config.baseChunkSize > 0) { "Invalid Supertonic audio config" }
        require(config.chunkCompressFactor > 0 && config.latentDimension > 0) {
            "Invalid Supertonic latent config"
        }
    }
}

private fun loadInt32Asset(context: Context, path: String): IntArray {
    val bytes = context.assets.open(path).use { it.readBytes() }
    require(bytes.isNotEmpty() && bytes.size % Int.SIZE_BYTES == 0) {
        "Invalid Supertonic unicode indexer"
    }
    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer()
    return IntArray(buffer.remaining()).also(buffer::get)
}

private fun loadVoiceStyles(context: Context, path: String): SupertonicVoiceStyles {
    val bytes = context.assets.open(path).use { it.readBytes() }
    val headerBytes = 6 * Long.SIZE_BYTES
    require(bytes.size >= headerBytes) { "Invalid Supertonic voice-style header" }
    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    val dimensions = LongArray(6) { buffer.long }
    require(dimensions.all { it > 0 }) { "Invalid Supertonic voice-style dimensions" }
    val ttlSize = checkedTensorSize(dimensions[0], dimensions[1], dimensions[2])
    val dpSize = checkedTensorSize(dimensions[3], dimensions[4], dimensions[5])
    val expectedBytes = Math.addExact(
        headerBytes.toLong(),
        Math.multiplyExact(Math.addExact(ttlSize, dpSize).toLong(), Float.SIZE_BYTES.toLong()),
    )
    require(expectedBytes <= MAX_STYLE_ASSET_BYTES && expectedBytes == bytes.size.toLong()) {
        "Invalid Supertonic voice-style payload"
    }
    val ttl = FloatArray(ttlSize) { buffer.float }
    val dp = FloatArray(dpSize) { buffer.float }
    return SupertonicVoiceStyles(
        ttl = ttl,
        ttlShape = dimensions.copyOfRange(0, 3),
        dp = dp,
        dpShape = dimensions.copyOfRange(3, 6),
    )
}

private fun checkedTensorSize(first: Long, second: Long, third: Long): Int =
    Math.multiplyExact(Math.multiplyExact(first, second), third)
        .also { require(it <= Int.MAX_VALUE) { "Supertonic tensor is too large" } }
        .toInt()

private fun mapUncompressedAsset(context: Context, path: String): ByteBuffer =
    context.assets.openFd(path).use { asset ->
        require(asset.length > 0L) { "Model asset is empty: $path" }
        ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.dup(asset.fileDescriptor)).use { input ->
            input.channel.map(
                FileChannel.MapMode.READ_ONLY,
                asset.startOffset,
                asset.length,
            )
        }
    }

private const val MAX_STYLE_ASSET_BYTES = 64L * 1024L * 1024L
