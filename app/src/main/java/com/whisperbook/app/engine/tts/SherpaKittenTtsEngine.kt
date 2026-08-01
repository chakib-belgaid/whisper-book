package com.whisperbook.app.engine.tts

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKittenModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.whisperbook.app.domain.LocalTtsEngine
import com.whisperbook.app.domain.SynthesisRequest
import com.whisperbook.app.domain.SynthesisResult
import com.whisperbook.app.domain.model.VoiceDescriptor
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SherpaKittenTtsEngine(
    context: Context,
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : LocalTtsEngine {
    private val appContext = context.applicationContext
    private val nativeLock = ReentrantLock()

    @Volatile
    private var closed = false
    private var runtime: OfflineTts? = null
    private var warmed = false

    override suspend fun warmUp(): Result<Unit> = resultOf {
        withContext(workerDispatcher) {
            nativeLock.withLock {
                val tts = requireRuntime()
                if (!warmed) {
                    val warmup = try {
                        tts.generate(WARMUP_TEXT, DEFAULT_SPEAKER_ID, 1f)
                    } catch (failure: Throwable) {
                        throw TtsEngineException("The embedded TTS model failed during warm-up", failure)
                    }
                    if (warmup.samples.isEmpty()) {
                        throw TtsEngineException("The embedded TTS model produced no audio during warm-up")
                    }
                    if (warmup.sampleRate != EXPECTED_SAMPLE_RATE) {
                        throw TtsEngineException(
                            "The embedded TTS warm-up returned ${warmup.sampleRate} Hz; " +
                                "$EXPECTED_SAMPLE_RATE Hz is required",
                        )
                    }
                    warmed = true
                }
            }
        }
    }

    override suspend fun voices(): List<VoiceDescriptor> = KITTEN_VOICES

    override suspend fun synthesize(request: SynthesisRequest): Result<SynthesisResult> = resultOf {
        validateRequest(request)
        withContext(workerDispatcher) {
            nativeLock.withLock {
                val tts = requireRuntime()
                val speaker = KITTEN_VOICES.firstOrNull { it.id == request.voice.id }
                    ?: throw TtsEngineException(
                        "Voice '${request.voice.id}' is not available in the embedded Kitten model",
                    )
                val audio = try {
                    tts.generate(request.text.trim(), speaker.speakerIndex, request.speed)
                } catch (failure: Throwable) {
                    throw TtsEngineException(
                        "The embedded TTS model could not synthesize voice ${speaker.displayName}",
                        failure,
                    )
                }
                if (audio.sampleRate != EXPECTED_SAMPLE_RATE) {
                    throw TtsEngineException(
                        "The embedded TTS model returned ${audio.sampleRate} Hz; " +
                            "$EXPECTED_SAMPLE_RATE Hz is required",
                    )
                }
                if (audio.samples.isEmpty()) {
                    throw TtsEngineException("The embedded TTS model returned empty audio")
                }
                SynthesisResult(
                    pcm16 = floatsToPcm16(audio.samples),
                    sampleRate = audio.sampleRate,
                    durationMs = pcmDurationMs(audio.samples.size, audio.sampleRate),
                )
            }
        }
    }

    override fun close() {
        closed = true
        nativeLock.withLock {
            val current = runtime
            runtime = null
            warmed = false
            if (current != null) runCatching { current.release() }
        }
    }

    private fun requireRuntime(): OfflineTts {
        if (closed) throw TtsEngineException("The embedded TTS engine has been closed")
        runtime?.let { return it }

        val dataDir = try {
            EspeakAssetExtractor(appContext.assets, appContext.noBackupFilesDir).prepare()
        } catch (failure: Throwable) {
            throw TtsEngineException("The embedded pronunciation data could not be prepared", failure)
        }
        val kitten = OfflineTtsKittenModelConfig(
            model = KittenAssets.modelAssetPath,
            voices = KittenAssets.voicesAssetPath,
            tokens = KittenAssets.tokensAssetPath,
            dataDir = dataDir.absolutePath,
            lengthScale = 1f,
        )
        val model = OfflineTtsModelConfig(
            kitten = kitten,
            numThreads = THREAD_COUNT,
            debug = false,
            provider = "cpu",
        )
        val created = try {
            OfflineTts(appContext.assets, OfflineTtsConfig(model = model))
        } catch (failure: Throwable) {
            throw TtsEngineException("The embedded Kitten TTS model could not be loaded", failure)
        }
        val speakerCount = try {
            created.numSpeakers()
        } catch (failure: Throwable) {
            runCatching { created.release() }
            throw TtsEngineException("The embedded Kitten model could not report its speakers", failure)
        }
        if (speakerCount < KITTEN_VOICES.size) {
            runCatching { created.release() }
            throw TtsEngineException(
                "The embedded Kitten model exposes $speakerCount speakers; " +
                    "${KITTEN_VOICES.size} are required",
            )
        }
        val sampleRate = try {
            created.sampleRate()
        } catch (failure: Throwable) {
            runCatching { created.release() }
            throw TtsEngineException("The embedded Kitten model could not report its sample rate", failure)
        }
        if (sampleRate != EXPECTED_SAMPLE_RATE) {
            runCatching { created.release() }
            throw TtsEngineException(
                "The embedded Kitten model reports $sampleRate Hz; $EXPECTED_SAMPLE_RATE Hz is required",
            )
        }
        runtime = created
        return created
    }

    private fun validateRequest(request: SynthesisRequest) {
        if (request.text.isBlank()) throw TtsEngineException("Text to synthesize must not be blank")
        if (!request.speed.isFinite() || request.speed !in MIN_SPEED..MAX_SPEED) {
            throw TtsEngineException("Speaking speed must be between $MIN_SPEED and $MAX_SPEED")
        }
    }

    companion object {
        /** Include both artifacts so persisted audio is invalidated when model inference changes. */
        const val MODEL_VERSION = "kitten-nano-en-v0_8-int8+sherpa-onnx-1.13.4"
        const val EXPECTED_SAMPLE_RATE = 24_000
        const val MIN_SPEED = 0.5f
        const val MAX_SPEED = 2f
        private const val THREAD_COUNT = 2
        private const val WARMUP_TEXT = "Once."
        private const val DEFAULT_SPEAKER_ID = 0

        val KITTEN_VOICES: List<VoiceDescriptor> = listOf(
            VoiceDescriptor(id = "bella", displayName = "Bella", speakerIndex = 0),
            VoiceDescriptor(id = "jasper", displayName = "Jasper", speakerIndex = 1),
            VoiceDescriptor(id = "luna", displayName = "Luna", speakerIndex = 2),
            VoiceDescriptor(id = "bruno", displayName = "Bruno", speakerIndex = 3),
            VoiceDescriptor(id = "rosie", displayName = "Rosie", speakerIndex = 4),
            VoiceDescriptor(id = "hugo", displayName = "Hugo", speakerIndex = 5),
            VoiceDescriptor(id = "kiki", displayName = "Kiki", speakerIndex = 6),
            VoiceDescriptor(id = "leo", displayName = "Leo", speakerIndex = 7),
        )
    }
}

class TtsEngineException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

private suspend inline fun <T> resultOf(crossinline block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (failure: Throwable) {
    Result.failure(failure)
}
