package com.whisperbook.app.engine.tts

import android.content.Context
import android.os.Process
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsSupertonicModelConfig
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.whisperbook.app.domain.LocalTtsEngine
import com.whisperbook.app.domain.SynthesisRequest
import com.whisperbook.app.domain.SynthesisResult
import com.whisperbook.app.domain.model.VoiceDescriptor
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.Executors
import kotlin.concurrent.withLock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

class SherpaKittenTtsEngine(
    context: Context,
    workerDispatcher: CoroutineDispatcher? = null,
) : LocalTtsEngine {
    private val appContext = context.applicationContext
    private val nativeLock = ReentrantLock()
    private val ownedWorkerDispatcher: ExecutorCoroutineDispatcher? = if (workerDispatcher == null) {
        Executors.newSingleThreadExecutor { task ->
            Thread(
                {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_LOWEST)
                    task.run()
                },
                "WhisperbookTts",
            )
        }.asCoroutineDispatcher()
    } else {
        null
    }
    private val workerDispatcher: CoroutineDispatcher = workerDispatcher
        ?: requireNotNull(ownedWorkerDispatcher)

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
                        tts.generateWithConfig(
                            WARMUP_TEXT,
                            generationConfig(DEFAULT_SPEAKER_ID, 1f),
                        )
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
                        "Voice '${request.voice.id}' is not available in the embedded Supertonic model",
                    )
                val audio = try {
                    tts.generateWithConfig(
                        request.text.trim(),
                        generationConfig(speaker.speakerIndex, request.speed),
                    )
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
        ownedWorkerDispatcher?.close()
    }

    private fun requireRuntime(): OfflineTts {
        if (closed) throw TtsEngineException("The embedded TTS engine has been closed")
        runtime?.let { return it }

        val supertonic = OfflineTtsSupertonicModelConfig(
            durationPredictor = SupertonicAssets.durationPredictorAssetPath,
            textEncoder = SupertonicAssets.textEncoderAssetPath,
            vectorEstimator = SupertonicAssets.vectorEstimatorAssetPath,
            vocoder = SupertonicAssets.vocoderAssetPath,
            ttsJson = SupertonicAssets.ttsJsonAssetPath,
            unicodeIndexer = SupertonicAssets.unicodeIndexerAssetPath,
            voiceStyle = SupertonicAssets.voiceStyleAssetPath,
        )
        val model = OfflineTtsModelConfig(
            supertonic = supertonic,
            numThreads = THREAD_COUNT,
            debug = false,
            provider = "cpu",
        )
        val created = try {
            OfflineTts(appContext.assets, OfflineTtsConfig(model = model))
        } catch (failure: Throwable) {
            throw TtsEngineException("The embedded Supertonic TTS model could not be loaded", failure)
        }
        val speakerCount = try {
            created.numSpeakers()
        } catch (failure: Throwable) {
            runCatching { created.release() }
            throw TtsEngineException("The embedded Supertonic model could not report its speakers", failure)
        }
        val requiredSpeakerCount = KITTEN_VOICES.maxOf(VoiceDescriptor::speakerIndex) + 1
        if (speakerCount < requiredSpeakerCount) {
            runCatching { created.release() }
            throw TtsEngineException(
                "The embedded Supertonic model exposes $speakerCount speakers; " +
                    "$requiredSpeakerCount are required by the voice catalog",
            )
        }
        val sampleRate = try {
            created.sampleRate()
        } catch (failure: Throwable) {
            runCatching { created.release() }
            throw TtsEngineException("The embedded Supertonic model could not report its sample rate", failure)
        }
        if (sampleRate != EXPECTED_SAMPLE_RATE) {
            runCatching { created.release() }
            throw TtsEngineException(
                "The embedded Supertonic model reports $sampleRate Hz; $EXPECTED_SAMPLE_RATE Hz is required",
            )
        }
        runtime = created
        return created
    }

    private fun generationConfig(speakerIndex: Int, speed: Float) = GenerationConfig(
        sid = speakerIndex,
        speed = speed,
        extra = mapOf("lang" to DEFAULT_LANGUAGE),
    )

    private fun validateRequest(request: SynthesisRequest) {
        if (request.text.isBlank()) throw TtsEngineException("Text to synthesize must not be blank")
        if (!request.speed.isFinite() || request.speed !in MIN_SPEED..MAX_SPEED) {
            throw TtsEngineException("Speaking speed must be between $MIN_SPEED and $MAX_SPEED")
        }
    }

    companion object {
        /** Include both artifacts so persisted audio is invalidated when model inference changes. */
        const val MODEL_VERSION = "supertonic-3-int8-2026-05-11+sherpa-onnx-1.13.4"
        const val EXPECTED_SAMPLE_RATE = 44_100
        const val MIN_SPEED = 0.5f
        const val MAX_SPEED = 2f
        // A single low-priority inference lane leaves Media3 and Compose responsive on older
        // mobile CPUs. More ONNX threads improve synthesis throughput but can starve audio I/O.
        private const val THREAD_COUNT = 1
        private const val WARMUP_TEXT = "Once."
        private const val DEFAULT_SPEAKER_ID = 0
        private const val DEFAULT_LANGUAGE = "en"

        /**
         * The bundled voice.bin is ordered F1-F5, then M1-M5. Keep the friendly identities tied
         * to compatible vocal personas so the name, portrait, and generated voice tell the same
         * story. The selected presets progress from mature/steady to youthful/energetic where the
         * corresponding portrait does the same.
         */
        val KITTEN_VOICES: List<VoiceDescriptor> = listOf(
            VoiceDescriptor(id = "bella", displayName = "Bella", speakerIndex = 4), // F5: mature, calm
            VoiceDescriptor(id = "jasper", displayName = "Jasper", speakerIndex = 9), // M5: older, measured
            VoiceDescriptor(id = "luna", displayName = "Luna", speakerIndex = 1), // F2: young, lively
            VoiceDescriptor(id = "bruno", displayName = "Bruno", speakerIndex = 8), // M4: warm, mature
            VoiceDescriptor(id = "rosie", displayName = "Rosie", speakerIndex = 2), // F3: older, measured
            VoiceDescriptor(id = "hugo", displayName = "Hugo", speakerIndex = 6), // M2: grounded adult
            VoiceDescriptor(id = "kiki", displayName = "Kiki", speakerIndex = 3), // F4: youthful, energetic
            VoiceDescriptor(id = "leo", displayName = "Leo", speakerIndex = 7), // M3: youthful, energetic
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
