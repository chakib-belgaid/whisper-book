package com.whisperbook.app.ui.screens

import com.whisperbook.app.domain.model.Chapter
import com.whisperbook.app.domain.model.ChapterPlanEntry
import com.whisperbook.app.domain.model.PreparationStage
import com.whisperbook.app.ui.components.ProcessingChapterState
import org.junit.Assert.assertEquals
import org.junit.Test

class ProcessingScreenLayoutTest {
    @Test
    fun regularPhoneWidthsKeepNativeScale() {
        assertEquals(1f, processingContentScale(320f), 0.001f)
        assertEquals(1f, processingContentScale(400f), 0.001f)
    }

    @Test
    fun wideLogicalViewportsScaleUpAndRemainCapped() {
        assertEquals(1.5f, processingContentScale(600f), 0.001f)
        assertEquals(1.8f, processingContentScale(752.8f), 0.001f)
        assertEquals(1.8f, processingContentScale(1_200f), 0.001f)
    }

    @Test
    fun selectedPlanUsesCustomOrderAndCompactListeningPositions() {
        val plan = listOf(
            planEntry(chapterNumber = 1, selected = true, customPosition = 4),
            planEntry(chapterNumber = 2, selected = false, customPosition = 0),
            planEntry(chapterNumber = 3, selected = true, customPosition = 2),
            planEntry(chapterNumber = 5, selected = true, customPosition = 1),
        )

        val queue = buildProcessingQueue(
            selectedChapterPlan = plan,
            fallbackChapters = emptyList(),
            explicitStates = mapOf(
                "chapter-5" to ProcessingChapterState.Ready,
                "chapter-3" to ProcessingChapterState.Failed,
            ),
            preparationStage = PreparationStage.PREPARING_AUDIO,
            activeChapterId = "chapter-1",
        )

        assertEquals(listOf("chapter-5", "chapter-3", "chapter-1"), queue.map { it.id })
        assertEquals(listOf(1, 2, 3), queue.map { it.listeningPosition })
        assertEquals(listOf(5, 3, 1), queue.map { it.originalChapterNumber })
        assertEquals(
            listOf(
                ProcessingChapterState.Ready,
                ProcessingChapterState.Failed,
                ProcessingChapterState.Preparing,
            ),
            queue.map { it.state },
        )
    }

    @Test
    fun readyStageOverridesStaleChapterStates() {
        val queue = buildProcessingQueue(
            selectedChapterPlan = listOf(planEntry(1, selected = true, customPosition = 0)),
            fallbackChapters = emptyList(),
            explicitStates = mapOf("chapter-1" to ProcessingChapterState.Waiting),
            preparationStage = PreparationStage.READY,
            activeChapterId = null,
        )

        assertEquals(ProcessingChapterState.Ready, queue.single().state)
        assertEquals("1 of 1 chapter ready", chapterReadinessSummary(1, 1))
        assertEquals("Preparing selected chapters", chapterReadinessSummary(0, 0))
        assertEquals(
            "Preparing audio. Current chapter: Chapter 1",
            preparationActivityLabel(
                stage = PreparationStage.PREPARING_AUDIO,
                message = null,
                activeChapterTitle = "Chapter 1",
            ),
        )
    }

    @Test
    fun longQueuesShowABoundedPreview() {
        val queue = buildProcessingQueue(
            selectedChapterPlan = (1..2_450).map { chapterNumber ->
                planEntry(
                    chapterNumber = chapterNumber,
                    selected = true,
                    customPosition = chapterNumber - 1,
                )
            },
            fallbackChapters = emptyList(),
            explicitStates = emptyMap(),
            preparationStage = PreparationStage.FINDING_CHARACTERS,
            activeChapterId = null,
        )

        assertEquals(6, visibleProcessingQueue(queue).size)
        assertEquals(ProcessingChapterState.Preparing, queue.first().state)
        assertEquals(ProcessingChapterState.Waiting, queue[1].state)
    }

    private fun planEntry(
        chapterNumber: Int,
        selected: Boolean,
        customPosition: Int,
    ) = ChapterPlanEntry(
        chapter = Chapter(
            id = "chapter-$chapterNumber",
            bookId = "book",
            ordinal = chapterNumber - 1,
            title = "Chapter $chapterNumber",
        ),
        isSelected = selected,
        customPosition = customPosition,
    )
}
