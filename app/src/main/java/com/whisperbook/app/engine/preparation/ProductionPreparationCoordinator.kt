package com.whisperbook.app.engine.preparation

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import androidx.work.workDataOf
import com.whisperbook.app.data.local.db.PreparationJobDao
import com.whisperbook.app.data.local.db.PreparationJobEntity
import com.whisperbook.app.data.local.db.toDomain
import com.whisperbook.app.data.local.db.toEntity
import com.whisperbook.app.diagnostics.BetaDiagnostics
import com.whisperbook.app.domain.PreparationCoordinator
import com.whisperbook.app.domain.model.PreparationRunState
import com.whisperbook.app.domain.model.PreparationStage
import com.whisperbook.app.domain.model.PreparationState
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class ProductionPreparationCoordinator internal constructor(
    private val scheduler: PreparationWorkScheduler,
    private val preparationJobs: PreparationJobDao,
    private val narrationSetupConfirmed: suspend (String) -> Boolean = { true },
    private val storyReviewConfirmed: suspend (String) -> Boolean = { true },
) : PreparationCoordinator {
    constructor(
        context: Context,
        dependencies: PreparationDependencies = PreparationRuntime.resolve(context),
        workManager: WorkManager = WorkManager.getInstance(context.applicationContext),
    ) : this(
        scheduler = WorkManagerPreparationScheduler(workManager),
        preparationJobs = dependencies.database.preparationJobDao(),
        narrationSetupConfirmed = { bookId ->
            dependencies.database.bookDao().getById(bookId)?.narrationSetupConfirmed == true
        },
        storyReviewConfirmed = { bookId ->
            dependencies.database.bookDao().getById(bookId)?.storyReviewConfirmed == true
        },
    ) {
        PreparationRuntime.install(dependencies)
    }

    override suspend fun enqueue(bookId: String) {
        require(bookId.isNotBlank()) { "bookId must not be blank" }
        BetaDiagnostics.info(
            "preparation_enqueue_requested",
            mapOf(
                "book_key" to preparationCorrelationKey(bookId),
                "mode" to "full_chain",
                "policy" to PreparationWorkPlan.existingWorkPolicy.name,
            ),
        )
        scheduleAsRunning(bookId) { current ->
            val stages = stagesToSchedule(bookId, current)
            if (stages.isNotEmpty()) {
                scheduler.enqueueUniqueChain(PreparationWorkPlan.uniqueName(bookId), bookId, stages)
            }
        }
    }

    override suspend fun pause(bookId: String) {
        require(bookId.isNotBlank()) { "bookId must not be blank" }
        BetaDiagnostics.info(
            "preparation_pause_requested",
            mapOf("book_key" to preparationCorrelationKey(bookId)),
        )
        scheduler.cancelUnique(PreparationWorkPlan.uniqueName(bookId))
        updateRunState(bookId, PreparationRunState.PAUSED)
    }

    override suspend fun resume(bookId: String) {
        require(bookId.isNotBlank()) { "bookId must not be blank" }
        val current = preparationJobs.getForBook(bookId)?.toDomain()
        if (current?.runState != PreparationRunState.PAUSED) return
        BetaDiagnostics.info(
            "preparation_resume_requested",
            mapOf("book_key" to preparationCorrelationKey(bookId)),
        )
        scheduleAsRunning(bookId) { persisted ->
            val stages = stagesToSchedule(bookId, persisted)
            if (stages.isNotEmpty()) {
                scheduler.enqueueUniqueChain(PreparationWorkPlan.uniqueName(bookId), bookId, stages)
            }
        }
    }

    override suspend fun regenerateAudio(bookId: String, fromChapterOrdinal: Int) {
        require(bookId.isNotBlank()) { "bookId must not be blank" }
        require(fromChapterOrdinal >= 0) { "fromChapterOrdinal must not be negative" }
        BetaDiagnostics.info(
            "preparation_enqueue_requested",
            mapOf(
                "book_key" to preparationCorrelationKey(bookId),
                "mode" to "audio_regeneration",
                "policy" to PreparationWorkPlan.regenerationWorkPolicy.name,
                "from_chapter_ordinal" to fromChapterOrdinal,
            ),
        )
        scheduleAsRunning(bookId) {
            scheduler.replaceWithAudioGeneration(
                uniqueName = PreparationWorkPlan.uniqueName(bookId),
                bookId = bookId,
                fromChapterOrdinal = fromChapterOrdinal,
            )
        }
    }

    override suspend fun cancel(bookId: String) {
        require(bookId.isNotBlank()) { "bookId must not be blank" }
        BetaDiagnostics.info(
            "preparation_cancel_requested",
            mapOf("book_key" to preparationCorrelationKey(bookId)),
        )
        scheduler.cancelUnique(PreparationWorkPlan.uniqueName(bookId))
        updateRunState(bookId, PreparationRunState.CANCELLED)
    }

    override fun observe(bookId: String): Flow<PreparationState> {
        require(bookId.isNotBlank()) { "bookId must not be blank" }
        return preparationJobs.observeForBook(bookId)
            .map { job ->
                job?.toDomain() ?: PreparationState(
                    stage = PreparationStage.COPY_AND_VALIDATE,
                    message = "Waiting to prepare",
                )
            }
            .distinctUntilChanged()
    }

    private suspend fun scheduleAsRunning(
        bookId: String,
        schedule: suspend (PreparationJobEntity?) -> Unit,
    ) {
        val previous = preparationJobs.getForBook(bookId)
        val runStateChanged = previous != null &&
            previous.runState != PreparationRunState.RUNNING.name
        val scheduledState = if (previous != null && runStateChanged) {
            previous.toDomain()
                .copy(runState = PreparationRunState.RUNNING)
                .toEntity(bookId, previous.attemptCount, System.currentTimeMillis())
                .also { preparationJobs.upsert(it) }
        } else {
            previous
        }
        try {
            schedule(scheduledState)
        } catch (failure: Throwable) {
            if (runStateChanged) previous?.let { preparationJobs.upsert(it) }
            throw failure
        }
    }

    private suspend fun stagesToSchedule(
        bookId: String,
        current: PreparationJobEntity?,
    ): List<PreparationStage> {
        val state = current?.toDomain()
        if (state == null) return PreparationWorkPlan.parsingStages
        return when (state.stage) {
            PreparationStage.COPY_AND_VALIDATE,
            PreparationStage.READING_CHAPTERS,
            -> PreparationWorkPlan.parsingStages

            PreparationStage.AWAITING_CHAPTER_SELECTION -> emptyList()
            PreparationStage.AWAITING_NARRATION_SETUP -> {
                if (state.chapterPlanConfirmed && narrationSetupConfirmed(bookId)) {
                    PreparationWorkPlan.narrationStages
                } else {
                    emptyList()
                }
            }

            PreparationStage.FINDING_CHARACTERS -> PreparationWorkPlan.narrationStages
            PreparationStage.AWAITING_STORY_REVIEW -> {
                if (storyReviewConfirmed(bookId)) PreparationWorkPlan.synthesisStages else emptyList()
            }
            PreparationStage.ASSIGNING_VOICES -> PreparationWorkPlan.narrationStages.drop(1)
            PreparationStage.PREPARING_AUDIO -> PreparationWorkPlan.narrationStages.takeLast(1)
            // Attribution is idempotent. After a confirmed review the restarted FINDING_CHARACTERS
            // worker hands straight over to casting; before it, the chain stops at the review.
            PreparationStage.FAILED -> when {
                !state.chapterPlanConfirmed -> PreparationWorkPlan.parsingStages
                !narrationSetupConfirmed(bookId) -> emptyList()
                else -> PreparationWorkPlan.narrationStages
            }
            PreparationStage.READY -> emptyList()
        }
    }

    private suspend fun updateRunState(bookId: String, runState: PreparationRunState) {
        val current = preparationJobs.getForBook(bookId) ?: return
        val state = current.toDomain()
        if (state.stage == PreparationStage.READY || state.stage == PreparationStage.FAILED) return
        preparationJobs.upsert(
            state.copy(runState = runState).toEntity(
                bookId = bookId,
                attemptCount = current.attemptCount,
                updatedAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }
}

internal interface PreparationWorkScheduler {
    suspend fun enqueueUniqueChain(
        uniqueName: String,
        bookId: String,
        stages: List<PreparationStage>,
    )
    suspend fun replaceWithAudioGeneration(uniqueName: String, bookId: String, fromChapterOrdinal: Int)
    suspend fun cancelUnique(uniqueName: String)
}

internal class WorkManagerPreparationScheduler(
    private val workManager: WorkManager,
) : PreparationWorkScheduler {
    override suspend fun enqueueUniqueChain(
        uniqueName: String,
        bookId: String,
        stages: List<PreparationStage>,
    ) {
        require(stages.isNotEmpty()) { "A preparation chain must contain at least one stage" }
        val requests = stages.map { stage -> request(bookId, stage) }
        var continuation = workManager.beginUniqueWork(
            uniqueName,
            PreparationWorkPlan.existingWorkPolicy,
            requests.first(),
        )
        requests.drop(1).forEach { next -> continuation = continuation.then(next) }
        val operation = continuation.enqueue()
        // WorkManager persists work asynchronously. Await that acknowledgement so the UI never
        // marks a book as scheduled when its chain failed to reach WorkManager's database.
        operation.await()
    }

    override suspend fun replaceWithAudioGeneration(
        uniqueName: String,
        bookId: String,
        fromChapterOrdinal: Int,
    ) {
        workManager.beginUniqueWork(
            uniqueName,
            PreparationWorkPlan.regenerationWorkPolicy,
            request(bookId, PreparationStage.PREPARING_AUDIO, fromChapterOrdinal),
        ).enqueue().await()
    }

    override suspend fun cancelUnique(uniqueName: String) {
        workManager.cancelUniqueWork(uniqueName).await()
    }

    private fun request(
        bookId: String,
        stage: PreparationStage,
        fromChapterOrdinal: Int = 0,
    ): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<PreparationWorker>()
            .setInputData(PreparationWorkPlan.input(bookId, stage, fromChapterOrdinal))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, MINIMUM_BACKOFF_SECONDS, TimeUnit.SECONDS)
            .addTag(PreparationWorkPlan.bookTag(bookId))
            .addTag("preparation-stage-${stage.name.lowercase()}")
            .build()

    private companion object {
        const val MINIMUM_BACKOFF_SECONDS = 10L
    }
}

internal object PreparationWorkPlan {
    const val KEY_BOOK_ID = "book_id"
    const val KEY_STAGE = "preparation_stage"
    const val KEY_ERROR_CODE = "error_code"
    const val KEY_ERROR_MESSAGE = "error_message"
    const val KEY_FROM_CHAPTER_ORDINAL = "from_chapter_ordinal"

    val parsingStages = listOf(
        PreparationStage.COPY_AND_VALIDATE,
        PreparationStage.READING_CHAPTERS,
    )
    val narrationStages = listOf(
        PreparationStage.FINDING_CHARACTERS,
        PreparationStage.ASSIGNING_VOICES,
        PreparationStage.PREPARING_AUDIO,
    )
    /** Narration stages that cast voices or synthesize audio; both wait for the story review. */
    val synthesisStages = narrationStages.drop(1)
    val stages = parsingStages + narrationStages
    val existingWorkPolicy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP
    val regenerationWorkPolicy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE

    fun uniqueName(bookId: String): String = "prepare-book-$bookId"

    fun bookTag(bookId: String): String = "prepare-book-tag-$bookId"

    fun input(
        bookId: String,
        stage: PreparationStage,
        fromChapterOrdinal: Int = 0,
    ): Data = workDataOf(
        KEY_BOOK_ID to bookId,
        KEY_STAGE to stage.name,
        KEY_FROM_CHAPTER_ORDINAL to fromChapterOrdinal,
    )
}
