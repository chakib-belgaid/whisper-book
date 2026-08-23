package com.whisperbook.app.ui.screens

import com.whisperbook.app.domain.model.BookFormat
import com.whisperbook.app.domain.model.PreparationStage
import com.whisperbook.app.domain.model.PreparationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParsingScreenStateTest {
    @Test
    fun pdfProgressReportsRealPageUnits() {
        val display = parsingProgressDisplay(
            format = BookFormat.PDF,
            preparation = PreparationState(
                stage = PreparationStage.READING_CHAPTERS,
                completedUnits = 4,
                totalUnits = 10,
            ),
        )

        assertEquals("Reading page 4 of 10", display.phase)
        assertEquals("4 of 10 pages processed", display.detail)
        assertEquals("Parsing progress, 4 of 10 pages processed", display.semanticDescription)
        assertEquals(0.4f, display.fraction ?: -1f, 0.001f)
    }

    @Test
    fun epubProgressUsesSectionsAndHonorsWorkerPhaseCopy() {
        val display = parsingProgressDisplay(
            format = BookFormat.EPUB,
            preparation = PreparationState(
                stage = PreparationStage.READING_CHAPTERS,
                completedUnits = 7,
                totalUnits = 12,
                message = "Saving chapter structure",
            ),
        )

        assertEquals("Saving chapter structure", display.phase)
        assertEquals("7 of 12 sections processed", display.detail)
        assertEquals(7f / 12f, display.fraction ?: -1f, 0.001f)
    }

    @Test
    fun missingTotalStaysIndeterminateRatherThanInventingAPercentage() {
        val display = parsingProgressDisplay(
            format = BookFormat.PDF,
            preparation = PreparationState(
                stage = PreparationStage.READING_CHAPTERS,
                completedUnits = 9,
                totalUnits = 0,
            ),
        )

        assertEquals("Reading pages", display.phase)
        assertEquals("Progress will appear when the book reports a total.", display.detail)
        assertEquals("Parsing progress, total amount unknown", display.semanticDescription)
        assertNull(display.fraction)
    }

    @Test
    fun progressIsClampedWhenPersistedCompletedUnitsExceedTotal() {
        val display = parsingProgressDisplay(
            format = BookFormat.PDF,
            preparation = PreparationState(
                stage = PreparationStage.READING_CHAPTERS,
                completedUnits = 15,
                totalUnits = 10,
            ),
        )

        assertEquals("Reading page 10 of 10", display.phase)
        assertEquals("10 of 10 pages processed", display.detail)
        assertEquals(1f, display.fraction ?: -1f, 0.001f)
    }

    @Test
    fun chapterCountCopyHandlesEmptySingularAndPlural() {
        assertEquals("Looking for chapters", chapterCountLabel(0))
        assertEquals("1 chapter found", chapterCountLabel(1))
        assertEquals("2 chapters found", chapterCountLabel(2))
    }
}
