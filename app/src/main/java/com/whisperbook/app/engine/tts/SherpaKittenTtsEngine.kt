package com.whisperbook.app.engine.tts

import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsSupertonicModelConfig
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.whisperbook.app.domain.LocalTtsEngine
import com.whisperbook.app.domain.SynthesisRequest
import com.whisperbook.app.domain.SynthesisResult
import com.whisperbook.app.domain.model.CharacterGender
import com.whisperbook.app.domain.model.NarrationLanguage
import com.whisperbook.app.domain.model.VocalAge
import com.whisperbook.app.domain.model.VoiceDescriptor
import com.whisperbook.app.diagnostics.BetaDiagnostics
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
                    // Constructing and validating the native runtime is the cold-start work we
                    // need here. A dummy utterance delayed the real opening line with a second
                    // complete inference and provided no additional validation over synthesize().
                    tts.sampleRate()
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
                    val synthesisStartedAtMs = SystemClock.elapsedRealtime()
                    tts.generateWithConfig(
                        request.text.trim(),
                        generationConfig(speaker.speakerIndex, request.speed, request.languageCode),
                    ).also { generated ->
                        val elapsedMs = SystemClock.elapsedRealtime() - synthesisStartedAtMs
                        val durationMs = pcmDurationMs(generated.samples.size, generated.sampleRate)
                        val realTimeFactorMilli = if (durationMs > 0L) {
                            elapsedMs * 1_000L / durationMs
                        } else {
                            0L
                        }
                        Log.i(
                            LOG_TAG,
                            "synthesis_ready chars=${request.text.length} elapsedMs=$elapsedMs " +
                                "audioMs=$durationMs rtfMilli=$realTimeFactorMilli",
                        )
                        BetaDiagnostics.recordSynthesis(
                            chars = request.text.length,
                            elapsedMs = elapsedMs,
                            audioMs = durationMs,
                            realTimeFactorMilli = realTimeFactorMilli,
                        )
                    }
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
        val runtimeStartedAtMs = SystemClock.elapsedRealtime()
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
        Log.i(
            LOG_TAG,
            "runtime_ready elapsedMs=${SystemClock.elapsedRealtime() - runtimeStartedAtMs} " +
                "sampleRate=$sampleRate speakers=$speakerCount threads=$THREAD_COUNT",
        )
        BetaDiagnostics.performance(
            "tts_runtime_ready",
            mapOf(
                "elapsed_ms" to (SystemClock.elapsedRealtime() - runtimeStartedAtMs),
                "sample_rate" to sampleRate,
                "speakers" to speakerCount,
                "threads" to THREAD_COUNT,
            ),
        )
        runtime = created
        return created
    }

    private fun generationConfig(speakerIndex: Int, speed: Float, languageCode: String) = GenerationConfig(
        sid = speakerIndex,
        speed = speed,
        extra = mapOf("lang" to languageCode),
    )

    private fun validateRequest(request: SynthesisRequest) {
        if (request.text.isBlank()) throw TtsEngineException("Text to synthesize must not be blank")
        if (!request.speed.isFinite() || request.speed !in MIN_SPEED..MAX_SPEED) {
            throw TtsEngineException("Speaking speed must be between $MIN_SPEED and $MAX_SPEED")
        }
        if (request.languageCode !in NarrationLanguage.supportedCodes) {
            throw TtsEngineException("Language '${request.languageCode}' is not installed in Whisperbook")
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
        private const val LOG_TAG = "WhisperbookTts"

        /**
         * The bundled voice.bin is ordered F1-F5, then M1-M5. Keep the friendly identities tied
         * to compatible vocal personas so the name, portrait, and generated voice tell the same
         * story. The selected presets progress from mature/steady to youthful/energetic where the
         * corresponding portrait does the same.
         */
        val KITTEN_VOICES: List<VoiceDescriptor> = listOf(
            VoiceDescriptor("bella", "Bella", 4, gender = CharacterGender.FEMALE, vocalAge = VocalAge.ADULT),
            VoiceDescriptor("jasper", "Jasper", 9, gender = CharacterGender.MALE, vocalAge = VocalAge.MATURE),
            VoiceDescriptor("luna", "Luna", 1, gender = CharacterGender.FEMALE, vocalAge = VocalAge.YOUTHFUL),
            VoiceDescriptor("bruno", "Bruno", 8, gender = CharacterGender.MALE, vocalAge = VocalAge.ADULT),
            VoiceDescriptor("rosie", "Rosie", 2, gender = CharacterGender.FEMALE, vocalAge = VocalAge.MATURE),
            VoiceDescriptor("hugo", "Hugo", 6, gender = CharacterGender.MALE, vocalAge = VocalAge.ADULT),
            VoiceDescriptor("kiki", "Kiki", 3, gender = CharacterGender.FEMALE, vocalAge = VocalAge.YOUTHFUL),
            VoiceDescriptor("leo", "Leo", 7, gender = CharacterGender.MALE, vocalAge = VocalAge.YOUTHFUL),
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
