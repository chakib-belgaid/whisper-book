package com.whisperbook.app.engine.audio

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.whisperbook.app.domain.LocalTtsEngine
import com.whisperbook.app.domain.SynthesisRequest
import com.whisperbook.app.domain.model.VoiceDescriptor
import com.whisperbook.app.engine.tts.SherpaKittenTtsEngine
import com.whisperbook.app.engine.preparation.PreparationRuntime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

internal object VoicePreviewBootstrap {
    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<VoicePreviewBootstrapWorker>()
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, MINIMUM_BACKOFF_SECONDS, TimeUnit.SECONDS)
            .addTag(WORK_TAG)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private const val MINIMUM_BACKOFF_SECONDS = 10L
    private const val WORK_TAG = "voice-preview-bootstrap"
    private const val UNIQUE_WORK_NAME = "voice-preview-bootstrap"
}

internal class VoicePreviewBootstrapWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val engine = PreparationRuntime.resolve(applicationContext).ttsEngineFactory.create()
        val cache = AppPrivateVoicePreviewCache(
            context = applicationContext,
            modelVersion = SherpaKittenTtsEngine.MODEL_VERSION,
            expectedSampleRate = SherpaKittenTtsEngine.EXPECTED_SAMPLE_RATE,
        )
        return try {
            VoicePreviewBootstrapper(
                ttsEngine = engine,
                voices = SherpaKittenTtsEngine.KITTEN_VOICES,
                cache = cache,
                expectedSampleRate = SherpaKittenTtsEngine.EXPECTED_SAMPLE_RATE,
            ).generateMissing()
            Result.success()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            if (runAttemptCount < MAX_RETRY_COUNT) {
                Result.retry()
            } else {
                Result.failure(workDataOf(ERROR_MESSAGE_KEY to (failure.message ?: "Voice preview generation failed")))
            }
        } finally {
            engine.close()
        }
    }

    private companion object {
        const val MAX_RETRY_COUNT = 2
        const val ERROR_MESSAGE_KEY = "voice_preview_error"
    }
}

internal class VoicePreviewBootstrapper(
    private val ttsEngine: LocalTtsEngine,
    private val voices: List<VoiceDescriptor>,
    private val cache: VoicePreviewClipCache,
    private val expectedSampleRate: Int,
) {
    suspend fun generateMissing() {
        val missingVoices = voices.filter { cache.read(it, PREVIEW_SPEED) == null }
        if (missingVoices.isEmpty()) return

        LocalAudioGenerationCoordinator.runBackground {
            ttsEngine.warmUp().getOrThrow()
        }

        var firstFailure: Throwable? = null
        missingVoices.forEach { voice ->
            try {
                // Release the shared synthesis lane after every voice so an imported chapter can
                // start while the remaining installation previews continue in the background.
                val synthesis = LocalAudioGenerationCoordinator.runBackground {
                    ttsEngine.synthesize(
                        SynthesisRequest(
                            text = PREVIEW_TEXT,
                            voice = voice,
                            speed = PREVIEW_SPEED,
                            cacheKey = "voice-preview-bootstrap-${voice.id}",
                        ),
                    ).getOrThrow()
                }
                check(synthesis.sampleRate == expectedSampleRate) {
                    "Voice preview for ${voice.displayName} used ${synthesis.sampleRate} Hz; " +
                        "$expectedSampleRate Hz is required"
                }
                cache.write(
                    voice = voice,
                    speed = PREVIEW_SPEED,
                    clip = VoicePreviewClip(synthesis.pcm16, synthesis.sampleRate),
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                if (firstFailure == null) firstFailure = failure
            }
        }
        firstFailure?.let { throw VoicePreviewBootstrapException(it) }
    }

    private companion object {
        const val PREVIEW_TEXT = "Once upon a time, every story began with a voice."
        const val PREVIEW_SPEED = 1f
    }
}

private class VoicePreviewBootstrapException(cause: Throwable) :
    IllegalStateException("One or more voice previews could not be generated", cause)
