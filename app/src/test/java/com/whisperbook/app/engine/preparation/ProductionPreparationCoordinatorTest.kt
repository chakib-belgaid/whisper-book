package com.whisperbook.app.engine.preparation

import androidx.work.ExistingWorkPolicy
import app.cash.turbine.test
import com.whisperbook.app.data.local.db.PreparationJobDao
import com.whisperbook.app.data.local.db.PreparationJobEntity
import com.whisperbook.app.domain.model.PreparationRunState
import com.whisperbook.app.domain.model.PreparationStage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionPreparationCoordinatorTest {
    @Test
    fun `work plan is one keep chain with every executable stage`() {
        assertEquals("prepare-book-book-42", PreparationWorkPlan.uniqueName("book-42"))
        assertEquals(ExistingWorkPolicy.KEEP, PreparationWorkPlan.existingWorkPolicy)
        assertEquals(
            listOf(
                PreparationStage.COPY_AND_VALIDATE,
                PreparationStage.READING_CHAPTERS,
                PreparationStage.FINDING_CHARACTERS,
                PreparationStage.ASSIGNING_VOICES,
                PreparationStage.PREPARING_AUDIO,
            ),
            PreparationWorkPlan.stages,
        )
        assertFalse(PreparationStage.READY in PreparationWorkPlan.stages)
        assertFalse(PreparationStage.FAILED in PreparationWorkPlan.stages)
        assertFalse(PreparationStage.AWAITING_CHAPTER_SELECTION in PreparationWorkPlan.stages)
        assertFalse(PreparationStage.AWAITING_NARRATION_SETUP in PreparationWorkPlan.stages)
        assertFalse(PreparationStage.AWAITING_STORY_REVIEW in PreparationWorkPlan.stages)
    }

    @Test
    fun `enqueue and cancel always target the same book scoped unique work`() = runTest {
        val scheduler = RecordingScheduler()
        val coordinator = ProductionPreparationCoordinator(scheduler, FakePreparationJobDao())

        coordinator.enqueue("book-42")
        coordinator.enqueue("book-42")
        coordinator.cancel("book-42")

        assertEquals(listOf("prepare-book-book-42", "prepare-book-book-42"), scheduler.enqueuedNames)
        assertEquals(listOf("book-42", "book-42"), scheduler.enqueuedBookIds)
        assertEquals(
            listOf(PreparationWorkPlan.parsingStages, PreparationWorkPlan.parsingStages),
            scheduler.enqueuedStages,
        )
        assertEquals(listOf("prepare-book-book-42"), scheduler.cancelledNames)
    }

    @Test
    fun `awaiting chapter review is a durable stop with no scheduled worker`() = runTest {
        val scheduler = RecordingScheduler()
        val jobs = FakePreparationJobDao()
        jobs.upsert(
            preparationJob().copy(
                stage = PreparationStage.AWAITING_CHAPTER_SELECTION.name,
                chapterPlanConfirmed = false,
            ),
        )
        val coordinator = ProductionPreparationCoordinator(scheduler, jobs)

        coordinator.enqueue("book-42")

        assertTrue(scheduler.enqueuedStages.isEmpty())
        assertEquals(
            PreparationStage.AWAITING_CHAPTER_SELECTION.name,
            jobs.getForBook("book-42")?.stage,
        )
    }

    @Test
    fun `confirmed chapter plan waits for narration then schedules only narration stages`() = runTest {
        val scheduler = RecordingScheduler()
        val jobs = FakePreparationJobDao()
        jobs.upsert(
            preparationJob().copy(
                stage = PreparationStage.AWAITING_NARRATION_SETUP.name,
                chapterPlanConfirmed = true,
            ),
        )
        var narrationConfirmed = false
        val coordinator = ProductionPreparationCoordinator(
            scheduler = scheduler,
            preparationJobs = jobs,
            narrationSetupConfirmed = { narrationConfirmed },
        )

        coordinator.enqueue("book-42")
        assertTrue(scheduler.enqueuedStages.isEmpty())

        narrationConfirmed = true
        coordinator.enqueue("book-42")
        assertEquals(listOf(PreparationWorkPlan.narrationStages), scheduler.enqueuedStages)
    }

    @Test
    fun `story review gate waits for confirmation then schedules only voices and audio`() = runTest {
        val scheduler = RecordingScheduler()
        val jobs = FakePreparationJobDao()
        jobs.upsert(
            preparationJob().copy(
                stage = PreparationStage.AWAITING_STORY_REVIEW.name,
                chapterPlanConfirmed = true,
            ),
        )
        var reviewConfirmed = false
        val coordinator = ProductionPreparationCoordinator(
            scheduler = scheduler,
            preparationJobs = jobs,
            narrationSetupConfirmed = { true },
            storyReviewConfirmed = { reviewConfirmed },
        )

        coordinator.enqueue("book-42")
        assertTrue(scheduler.enqueuedStages.isEmpty())

        reviewConfirmed = true
        coordinator.enqueue("book-42")
        assertEquals(
            listOf(listOf(PreparationStage.ASSIGNING_VOICES, PreparationStage.PREPARING_AUDIO)),
            scheduler.enqueuedStages,
        )
    }

    @Test
    fun `failure after story review restarts narration without reopening the review`() = runTest {
        val scheduler = RecordingScheduler()
        val jobs = FakePreparationJobDao()
        jobs.upsert(
            preparationJob().copy(
                stage = PreparationStage.FAILED.name,
                chapterPlanConfirmed = true,
            ),
        )
        val coordinator = ProductionPreparationCoordinator(
            scheduler = scheduler,
            preparationJobs = jobs,
            narrationSetupConfirmed = { true },
            storyReviewConfirmed = { true },
        )

        coordinator.enqueue("book-42")

        // Attribution is idempotent: the restarted FINDING_CHARACTERS worker skips attributed
        // chapters and, because the review is confirmed, hands straight over to voice casting.
        assertEquals(listOf(PreparationWorkPlan.narrationStages), scheduler.enqueuedStages)
    }

    @Test
    fun `audio regeneration replaces preparation with an audio only restart point`() = runTest {
        val scheduler = RecordingScheduler()
        val coordinator = ProductionPreparationCoordinator(scheduler, FakePreparationJobDao())

        coordinator.regenerateAudio("book-42", fromChapterOrdinal = 3)

        assertEquals(
            listOf(RecordingScheduler.AudioRestart("prepare-book-book-42", "book-42", 3)),
            scheduler.audioRestarts,
        )
        assertEquals(ExistingWorkPolicy.REPLACE, PreparationWorkPlan.regenerationWorkPolicy)
        assertEquals(
            3,
            PreparationWorkPlan.input("book-42", PreparationStage.PREPARING_AUDIO, 3)
                .getInt(PreparationWorkPlan.KEY_FROM_CHAPTER_ORDINAL, -1),
        )
    }

    @Test
    fun `pause resume and cancel persist control state around unique work`() = runTest {
        val scheduler = RecordingScheduler()
        val jobs = FakePreparationJobDao()
        jobs.upsert(preparationJob())
        val coordinator = ProductionPreparationCoordinator(scheduler, jobs)

        coordinator.pause("book-42")
        assertEquals(PreparationRunState.PAUSED.name, jobs.getForBook("book-42")?.runState)

        coordinator.resume("book-42")
        assertEquals(PreparationRunState.RUNNING.name, jobs.getForBook("book-42")?.runState)

        coordinator.cancel("book-42")
        assertEquals(PreparationRunState.CANCELLED.name, jobs.getForBook("book-42")?.runState)
        assertEquals(
            listOf("prepare-book-book-42", "prepare-book-book-42"),
            scheduler.cancelledNames,
        )
        assertEquals(listOf("prepare-book-book-42"), scheduler.enqueuedNames)
    }

    @Test
    fun `room job is mapped into observable domain progress`() = runTest {
        val jobs = FakePreparationJobDao()
        val coordinator = ProductionPreparationCoordinator(RecordingScheduler(), jobs)

        coordinator.observe("book-42").test {
            assertEquals(PreparationStage.COPY_AND_VALIDATE, awaitItem().stage)
            jobs.upsert(
                PreparationJobEntity(
                    bookId = "book-42",
                    stage = PreparationStage.PREPARING_AUDIO.name,
                    completedUnits = 2,
                    totalUnits = 8,
                    progressFraction = 0.25f,
                    message = "Preparing the opening passages",
                    retryable = false,
                    attemptCount = 1,
                    updatedAtEpochMs = 10L,
                ),
            )
            val progress = awaitItem()
            assertEquals(PreparationStage.PREPARING_AUDIO, progress.stage)
            assertEquals(2, progress.completedUnits)
            assertEquals(8, progress.totalUnits)
            assertEquals(0.25f, progress.progressFraction)
            assertEquals("Preparing the opening passages", progress.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `error mapping distinguishes retryable storage from permanent publication errors`() {
        val temporary = PreparationErrorMapper.map(java.io.IOException("busy"))
        val invalid = PreparationErrorMapper.map(IllegalArgumentException("bad book"))
        val explicit = PreparationErrorMapper.map(
            PreparationPipelineException("missing", "not here", retryable = false),
        )

        assertTrue(temporary.retryable)
        assertEquals("temporary-storage-error", temporary.code)
        assertFalse(invalid.retryable)
        assertEquals("invalid-publication", invalid.code)
        assertEquals("missing", explicit.code)
        assertEquals("not here", explicit.message)
    }

    @Test
    fun `automatic retry remains active and preserves the latest progress`() {
        val retry = automaticRetryState(
            workerStage = PreparationStage.READING_CHAPTERS,
            previous = com.whisperbook.app.domain.model.PreparationState(
                stage = PreparationStage.READING_CHAPTERS,
                completedUnits = 640,
                totalUnits = 2_400,
                progressFraction = 640f / 2_400f,
                message = "Reading page 640 of 2400",
            ),
            failure = MappedPreparationError(
                code = "temporary-storage-error",
                message = "Preparation was interrupted",
                retryable = true,
            ),
        )

        assertEquals(PreparationStage.READING_CHAPTERS, retry.stage)
        assertEquals(640, retry.completedUnits)
        assertEquals(2_400, retry.totalUnits)
        assertEquals(640f / 2_400f, retry.progressFraction)
        assertEquals("Preparation was interrupted — retrying automatically", retry.message)
        assertTrue(retry.retryable)
    }

    @Test
    fun `only retryable failures below the attempt limit retry automatically`() {
        val retryable = MappedPreparationError("temporary", "Temporary interruption", retryable = true)
        val terminal = MappedPreparationError("invalid", "Invalid publication", retryable = false)

        assertTrue(shouldRetryAutomatically(retryable, runAttemptCount = 0))
        assertTrue(shouldRetryAutomatically(retryable, runAttemptCount = 1))
        assertFalse(shouldRetryAutomatically(retryable, runAttemptCount = 2))
        assertFalse(shouldRetryAutomatically(terminal, runAttemptCount = 0))
    }

    @Test
    fun `extraction progress checkpoints advance by percent and emit terminal once`() {
        val throttle = PreparationProgressThrottle()

        assertFalse(throttle.shouldCheckpoint(completedUnits = 0, totalUnits = 1_000))
        assertFalse(throttle.shouldCheckpoint(completedUnits = 9, totalUnits = 1_000))
        assertTrue(throttle.shouldCheckpoint(completedUnits = 10, totalUnits = 1_000))
        assertFalse(throttle.shouldCheckpoint(completedUnits = 19, totalUnits = 1_000))
        assertTrue(throttle.shouldCheckpoint(completedUnits = 20, totalUnits = 1_000))
        assertFalse(throttle.shouldCheckpoint(completedUnits = 10, totalUnits = 1_000))
        assertTrue(throttle.shouldCheckpoint(completedUnits = 1_000, totalUnits = 1_000))
        assertFalse(throttle.shouldCheckpoint(completedUnits = 1_000, totalUnits = 1_000))
    }

    @Test
    fun `diagnostic book correlation is stable without exposing the book id`() {
        val bookId = "book-42-sensitive-local-id"

        val key = preparationCorrelationKey(bookId)

        assertEquals(key, preparationCorrelationKey(bookId))
        assertEquals(12, key.length)
        assertFalse(key.contains(bookId))
        assertNotEquals(key, preparationCorrelationKey("book-43-sensitive-local-id"))
    }
}

private fun preparationJob() = PreparationJobEntity(
    bookId = "book-42",
    stage = PreparationStage.READING_CHAPTERS.name,
    completedUnits = 4,
    totalUnits = 10,
    progressFraction = 0.4f,
    message = "Reading chapter 4 of 10",
    retryable = false,
    attemptCount = 0,
    updatedAtEpochMs = 1L,
)

private class RecordingScheduler : PreparationWorkScheduler {
    val enqueuedNames = mutableListOf<String>()
    val enqueuedBookIds = mutableListOf<String>()
    val cancelledNames = mutableListOf<String>()
    val enqueuedStages = mutableListOf<List<PreparationStage>>()
    val audioRestarts = mutableListOf<AudioRestart>()

    override suspend fun enqueueUniqueChain(
        uniqueName: String,
        bookId: String,
        stages: List<PreparationStage>,
    ) {
        enqueuedNames += uniqueName
        enqueuedBookIds += bookId
        enqueuedStages += stages
    }

    override suspend fun cancelUnique(uniqueName: String) {
        cancelledNames += uniqueName
    }

    override suspend fun replaceWithAudioGeneration(
        uniqueName: String,
        bookId: String,
        fromChapterOrdinal: Int,
    ) {
        audioRestarts += AudioRestart(uniqueName, bookId, fromChapterOrdinal)
    }

    data class AudioRestart(
        val uniqueName: String,
        val bookId: String,
        val fromChapterOrdinal: Int,
    )
}

private class FakePreparationJobDao : PreparationJobDao {
    private val jobs = mutableMapOf<String, PreparationJobEntity>()
    private val observed = mutableMapOf<String, MutableStateFlow<PreparationJobEntity?>>()

    override fun observeForBook(bookId: String): Flow<PreparationJobEntity?> =
        observed.getOrPut(bookId) { MutableStateFlow(jobs[bookId]) }

    override suspend fun getForBook(bookId: String): PreparationJobEntity? = jobs[bookId]

    override suspend fun upsert(job: PreparationJobEntity) {
        jobs[job.bookId] = job
        observed.getOrPut(job.bookId) { MutableStateFlow(null) }.value = job
    }

    override suspend fun incrementPlanRevision(bookId: String, updatedAtEpochMs: Long): Int =
        update(bookId) { current ->
            current.copy(
                planRevision = current.planRevision + 1,
                updatedAtEpochMs = updatedAtEpochMs,
            )
        }

    override suspend fun confirmChapterPlan(
        bookId: String,
        nextStage: String,
        updatedAtEpochMs: Long,
    ): Int = update(bookId, predicate = { !it.chapterPlanConfirmed }) { current ->
        current.copy(
            chapterPlanConfirmed = true,
            planRevision = current.planRevision + 1,
            stage = nextStage,
            activeChapterId = null,
            updatedAtEpochMs = updatedAtEpochMs,
        )
    }

    override suspend fun awaitChapterSelection(
        bookId: String,
        stage: String,
        message: String,
        updatedAtEpochMs: Long,
    ): Int = update(bookId) { current ->
        current.copy(
            stage = stage,
            completedUnits = 0,
            totalUnits = 0,
            progressFraction = 0f,
            message = message,
            retryable = false,
            runState = PreparationRunState.RUNNING.name,
            chapterPlanConfirmed = false,
            activeChapterId = null,
            updatedAtEpochMs = updatedAtEpochMs,
        )
    }

    override suspend fun setActiveChapter(
        bookId: String,
        chapterId: String?,
        updatedAtEpochMs: Long,
    ): Int = update(bookId) { current ->
        current.copy(activeChapterId = chapterId, updatedAtEpochMs = updatedAtEpochMs)
    }

    private suspend fun update(
        bookId: String,
        predicate: (PreparationJobEntity) -> Boolean = { true },
        transform: (PreparationJobEntity) -> PreparationJobEntity,
    ): Int {
        val current = jobs[bookId]?.takeIf(predicate) ?: return 0
        upsert(transform(current))
        return 1
    }
}
