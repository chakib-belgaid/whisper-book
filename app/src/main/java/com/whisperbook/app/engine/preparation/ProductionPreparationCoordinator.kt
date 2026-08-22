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
import com.whisperbook.app.data.local.db.toDomain
import com.whisperbook.app.diagnostics.BetaDiagnostics
import com.whisperbook.app.domain.PreparationCoordinator
import com.whisperbook.app.domain.model.PreparationStage
import com.whisperbook.app.domain.model.PreparationState
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class ProductionPreparationCoordinator internal constructor(
    private val scheduler: PreparationWorkScheduler,
    private val preparationJobs: PreparationJobDao,
) : PreparationCoordinator {
    constructor(
        context: Context,
        dependencies: PreparationDependencies = PreparationRuntime.resolve(context),
        workManager: WorkManager = WorkManager.getInstance(context.applicationContext),
    ) : this(
        scheduler = WorkManagerPreparationScheduler(workManager),
        preparationJobs = dependencies.database.preparationJobDao(),
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
        scheduler.enqueueUniqueChain(PreparationWorkPlan.uniqueName(bookId), bookId)
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
        scheduler.replaceWithAudioGeneration(
            uniqueName = PreparationWorkPlan.uniqueName(bookId),
            bookId = bookId,
            fromChapterOrdinal = fromChapterOrdinal,
        )
    }

    override suspend fun cancel(bookId: String) {
        require(bookId.isNotBlank()) { "bookId must not be blank" }
        BetaDiagnostics.info(
            "preparation_cancel_requested",
            mapOf("book_key" to preparationCorrelationKey(bookId)),
        )
        scheduler.cancelUnique(PreparationWorkPlan.uniqueName(bookId))
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
}

internal interface PreparationWorkScheduler {
    suspend fun enqueueUniqueChain(uniqueName: String, bookId: String)
    suspend fun replaceWithAudioGeneration(uniqueName: String, bookId: String, fromChapterOrdinal: Int)
    suspend fun cancelUnique(uniqueName: String)
}

internal class WorkManagerPreparationScheduler(
    private val workManager: WorkManager,
) : PreparationWorkScheduler {
    override suspend fun enqueueUniqueChain(uniqueName: String, bookId: String) {
        val requests = PreparationWorkPlan.stages.map { stage -> request(bookId, stage) }
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

    val stages = listOf(
        PreparationStage.COPY_AND_VALIDATE,
        PreparationStage.READING_CHAPTERS,
        PreparationStage.FINDING_CHARACTERS,
        PreparationStage.ASSIGNING_VOICES,
        PreparationStage.PREPARING_AUDIO,
    )
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
