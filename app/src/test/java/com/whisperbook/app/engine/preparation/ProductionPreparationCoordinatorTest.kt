package com.whisperbook.app.engine.preparation

import androidx.work.ExistingWorkPolicy
import app.cash.turbine.test
import com.whisperbook.app.data.local.db.PreparationJobDao
import com.whisperbook.app.data.local.db.PreparationJobEntity
import com.whisperbook.app.domain.model.PreparationStage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    }

    @Test
    fun `enqueue and cancel always target the same book scoped unique work`() {
        val scheduler = RecordingScheduler()
        val coordinator = ProductionPreparationCoordinator(scheduler, FakePreparationJobDao())

        coordinator.enqueue("book-42")
        coordinator.enqueue("book-42")
        coordinator.cancel("book-42")

        assertEquals(listOf("prepare-book-book-42", "prepare-book-book-42"), scheduler.enqueuedNames)
        assertEquals(listOf("book-42", "book-42"), scheduler.enqueuedBookIds)
        assertEquals(listOf("prepare-book-book-42"), scheduler.cancelledNames)
    }

    @Test
    fun `audio regeneration replaces preparation with an audio only restart point`() {
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
}

private class RecordingScheduler : PreparationWorkScheduler {
    val enqueuedNames = mutableListOf<String>()
    val enqueuedBookIds = mutableListOf<String>()
    val cancelledNames = mutableListOf<String>()
    val audioRestarts = mutableListOf<AudioRestart>()

    override fun enqueueUniqueChain(uniqueName: String, bookId: String) {
        enqueuedNames += uniqueName
        enqueuedBookIds += bookId
    }

    override fun cancelUnique(uniqueName: String) {
        cancelledNames += uniqueName
    }

    override fun replaceWithAudioGeneration(
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
}
