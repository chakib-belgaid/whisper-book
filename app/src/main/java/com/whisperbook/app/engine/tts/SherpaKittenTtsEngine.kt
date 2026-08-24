package com.whisperbook.app.engine.tts

import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import com.whisperbook.app.engine.compute.NeuralExecutionBackend
import com.whisperbook.app.engine.compute.OnDeviceAccelerationPolicy
import com.whisperbook.app.engine.compute.loadFirstAvailableBackend
import com.whisperbook.app.engine.compute.throwIfFatalOrCancelled
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

class SherpaKittenTtsEngine internal constructor(
    workerDispatcher: CoroutineDispatcher?,
    private val androidSdk: Int,
    private val runtimeFactory: SupertonicRuntimeFactory,
) : LocalTtsEngine {
    constructor(
        context: Context,
        workerDispatcher: CoroutineDispatcher? = null,
    ) : this(
        workerDispatcher = workerDispatcher,
        androidSdk = Build.VERSION.SDK_INT,
        runtimeFactory = SupertonicRuntimeFactory { backend -> OrtSupertonicRuntime(context, backend) },
    )

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
    private var runtime: SupertonicRuntime? = null
    private var activeBackend: NeuralExecutionBackend? = null
    private var acceleratorDisabledForProcess = false
    private var warmed = false

    override suspend fun warmUp(): Result<Unit> = resultOf {
        withContext(workerDispatcher) {
            nativeLock.withLock {
                val tts = requireRuntime()
                if (!warmed) {
                    // Constructing and validating the native runtime is the cold-start work we
                    // need here. A dummy utterance delayed the real opening line with a second
                    // complete inference and provided no additional validation over synthesize().
                    tts.sampleRate
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
                val generated = try {
                    val synthesisStartedAtMs = monotonicNowMs()
                    synthesizeWithCpuFallback(tts, request, speaker).also { samples ->
                        val elapsedMs = monotonicNowMs() - synthesisStartedAtMs
                        val durationMs = pcmDurationMs(samples.size, EXPECTED_SAMPLE_RATE)
                        val realTimeFactorMilli = if (durationMs > 0L) {
                            elapsedMs * 1_000L / durationMs
                        } else {
                            0L
                        }
                        logRuntimeInfo(
                            "synthesis_ready chars=${request.text.length} elapsedMs=$elapsedMs " +
                                "audioMs=$durationMs rtfMilli=$realTimeFactorMilli " +
                                "backend=${activeBackend?.diagnosticName ?: "unknown"}",
                        )
                        BetaDiagnostics.recordSynthesis(
                            chars = request.text.length,
                            elapsedMs = elapsedMs,
                            audioMs = durationMs,
                            realTimeFactorMilli = realTimeFactorMilli,
                            backend = activeBackend?.diagnosticName ?: "unknown",
                        )
                        if (
                            activeBackend == NeuralExecutionBackend.NNAPI &&
                            shouldFallbackFromSlowAccelerator(durationMs, realTimeFactorMilli)
                        ) {
                            BetaDiagnostics.performance(
                                "tts_accelerator_performance_fallback",
                                mapOf(
                                    "backend" to NeuralExecutionBackend.NNAPI.diagnosticName,
                                    "audio_ms" to durationMs,
                                    "rtf_milli" to realTimeFactorMilli,
                                    "next_backend" to NeuralExecutionBackend.CPU.diagnosticName,
                                ),
                            )
                            logRuntimeInfo(
                                "accelerator_too_slow audioMs=$durationMs " +
                                    "rtfMilli=$realTimeFactorMilli nextBackend=cpu",
                            )
                            acceleratorDisabledForProcess = true
                            runtime = null
                            activeBackend = null
                            warmed = false
                            runCatching { tts.close() }
                        }
                    }
                } catch (failure: Throwable) {
                    failure.throwIfFatalOrCancelled()
                    throw TtsEngineException(
                        "The embedded TTS model could not synthesize voice ${speaker.displayName}",
                        failure,
                    )
                }
                if (generated.isEmpty()) {
                    throw TtsEngineException("The embedded TTS model returned empty audio")
                }
                SynthesisResult(
                    pcm16 = floatsToPcm16(generated),
                    sampleRate = EXPECTED_SAMPLE_RATE,
                    durationMs = pcmDurationMs(generated.size, EXPECTED_SAMPLE_RATE),
                )
            }
        }
    }

    override fun close() {
        closed = true
        nativeLock.withLock {
            val current = runtime
            runtime = null
            activeBackend = null
            warmed = false
            if (current != null) runCatching { current.close() }
        }
        ownedWorkerDispatcher?.close()
    }

    private fun requireRuntime(
        candidates: List<NeuralExecutionBackend>? = null,
    ): SupertonicRuntime {
        if (closed) throw TtsEngineException("The embedded TTS engine has been closed")
        runtime?.let { return it }

        val selectedCandidates = candidates ?: if (acceleratorDisabledForProcess) {
            listOf(NeuralExecutionBackend.CPU)
        } else {
            OnDeviceAccelerationPolicy.ttsCandidates(androidSdk)
        }

        val runtimeStartedAtMs = monotonicNowMs()
        val loaded = try {
            loadFirstAvailableBackend(selectedCandidates) { backend ->
                runtimeFactory.create(backend).also(::validateRuntime)
            }
        } catch (failure: Throwable) {
            failure.throwIfFatalOrCancelled()
            throw TtsEngineException("The embedded Supertonic TTS model could not be loaded", failure)
        }
        loaded.earlierFailures.forEach { failed ->
            BetaDiagnostics.error(
                event = "tts_backend_initialization_failed",
                failure = failed.cause,
                details = mapOf("backend" to failed.backend.diagnosticName),
            )
        }
        val created = loaded.value
        val sampleRate = created.sampleRate
        val speakerCount = created.speakerCount
        activeBackend = loaded.backend
        logRuntimeInfo(
            "runtime_ready elapsedMs=${monotonicNowMs() - runtimeStartedAtMs} " +
                "sampleRate=$sampleRate speakers=$speakerCount backend=${loaded.backend.diagnosticName} " +
                "cpuFallback=true",
        )
        BetaDiagnostics.performance(
            "tts_runtime_ready",
            mapOf(
                "elapsed_ms" to (monotonicNowMs() - runtimeStartedAtMs),
                "sample_rate" to sampleRate,
                "speakers" to speakerCount,
                "threads" to THREAD_COUNT,
                "backend" to loaded.backend.diagnosticName,
                "cpu_fallback" to true,
            ),
        )
        runtime = created
        return created
    }

    private fun validateRuntime(candidate: SupertonicRuntime) {
        val requiredSpeakerCount = KITTEN_VOICES.maxOf(VoiceDescriptor::speakerIndex) + 1
        if (candidate.speakerCount < requiredSpeakerCount) {
            runCatching { candidate.close() }
            throw TtsEngineException(
                "The embedded Supertonic model exposes ${candidate.speakerCount} speakers; " +
                    "$requiredSpeakerCount are required by the voice catalog",
            )
        }
        if (candidate.sampleRate != EXPECTED_SAMPLE_RATE) {
            runCatching { candidate.close() }
            throw TtsEngineException(
                "The embedded Supertonic model reports ${candidate.sampleRate} Hz; " +
                    "$EXPECTED_SAMPLE_RATE Hz is required",
            )
        }
    }

    private fun synthesizeWithCpuFallback(
        initialRuntime: SupertonicRuntime,
        request: SynthesisRequest,
        speaker: VoiceDescriptor,
    ): FloatArray = try {
        initialRuntime.synthesize(
            text = request.text.trim(),
            languageCode = request.languageCode,
            speakerIndex = speaker.speakerIndex,
            speed = request.speed,
        )
    } catch (acceleratedFailure: Throwable) {
        acceleratedFailure.throwIfFatalOrCancelled()
        if (activeBackend != NeuralExecutionBackend.NNAPI) throw acceleratedFailure
        BetaDiagnostics.error(
            event = "tts_accelerator_runtime_failed",
            failure = acceleratedFailure,
            details = mapOf("backend" to NeuralExecutionBackend.NNAPI.diagnosticName),
        )
        runCatching { initialRuntime.close() }
        runtime = null
        activeBackend = null
        warmed = false
        acceleratorDisabledForProcess = true
        val cpuRuntime = requireRuntime(listOf(NeuralExecutionBackend.CPU))
        cpuRuntime.synthesize(
            text = request.text.trim(),
            languageCode = request.languageCode,
            speakerIndex = speaker.speakerIndex,
            speed = request.speed,
        )
    }

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
        const val MODEL_VERSION = "supertonic-3-int8-2026-05-11+onnxruntime-1.28.0-nnapi"
        const val EXPECTED_SAMPLE_RATE = 44_100
        const val MIN_SPEED = 0.5f
        const val MAX_SPEED = 2f
        // A single low-priority lane leaves Media3 and Compose responsive when unsupported NNAPI
        // graph partitions fall back to CPU.
        private const val THREAD_COUNT = 1
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

/**
 * Partial offload can cost more in CPU/accelerator synchronization than it saves. After one
 * representative NNAPI utterance, keep the rest of the process on CPU if offload is clearly below
 * acceptable interactive throughput. Short previews are excluded because fixed startup dominates.
 */
internal fun shouldFallbackFromSlowAccelerator(audioMs: Long, realTimeFactorMilli: Long): Boolean =
    audioMs >= MIN_ACCELERATOR_SAMPLE_AUDIO_MS && realTimeFactorMilli > MAX_ACCELERATOR_RTF_MILLI

private const val MIN_ACCELERATOR_SAMPLE_AUDIO_MS = 1_500L
private const val MAX_ACCELERATOR_RTF_MILLI = 2_200L

class TtsEngineException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

private suspend inline fun <T> resultOf(crossinline block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (failure: Throwable) {
    Result.failure(failure)
}

private fun monotonicNowMs(): Long = System.nanoTime() / 1_000_000L

/** Android's uninstrumented JVM stub throws from Log; diagnostics remain the durable record. */
private fun logRuntimeInfo(message: String) {
    runCatching { Log.i("WhisperbookTts", message) }
}
