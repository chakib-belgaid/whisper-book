package com.whisperbook.app.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.whisperbook.app.domain.model.Book
import com.whisperbook.app.domain.model.BookFormat
import com.whisperbook.app.domain.model.PreparationRunState
import com.whisperbook.app.domain.model.PreparationStage
import com.whisperbook.app.domain.model.PreparationState
import com.whisperbook.app.ui.screens.ParsingChapterHeader
import com.whisperbook.app.ui.screens.ParsingScreen
import com.whisperbook.app.ui.theme.WhisperbookTheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ParsingScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun runningStateShowsHonestProgressReadOnlyChaptersAndActions() {
        val backgroundClicks = AtomicInteger(0)
        val pauseClicks = AtomicInteger(0)

        setScreen(
            preparation = PreparationState(
                stage = PreparationStage.READING_CHAPTERS,
                completedUnits = 4,
                totalUnits = 10,
            ),
            chapters = sampleChapters(7),
            onContinueInBackground = backgroundClicks::incrementAndGet,
            onPause = pauseClicks::incrementAndGet,
        )

        composeRule.onNodeWithText("Reading page 4 of 10").assertExists()
        composeRule.onNodeWithContentDescription(
            "Parsing progress, 4 of 10 pages processed",
        ).assertExists()
        composeRule.onNodeWithText("7 chapters found").assertExists()
        composeRule.onNodeWithText("+ 2 more found").assertExists()
        composeRule.onNodeWithTag("parsing-chapter-chapter-0").assertHasNoClickAction()

        composeRule.onNodeWithTag("parsing-background-action").performScrollTo().performClick()
        composeRule.onNodeWithTag("parsing-pause-action").performScrollTo().performClick()

        assertEquals(1, backgroundClicks.get())
        assertEquals(1, pauseClicks.get())
    }

    @Test
    fun pausedStateRemainsUsableOnCompactScreenAtTwoHundredPercentText() {
        val resumeClicks = AtomicInteger(0)
        val cancelClicks = AtomicInteger(0)

        setScreen(
            preparation = PreparationState(
                stage = PreparationStage.READING_CHAPTERS,
                completedUnits = 8,
                totalUnits = 20,
                runState = PreparationRunState.PAUSED,
            ),
            chapters = sampleChapters(5),
            fontScale = 2f,
            onResume = resumeClicks::incrementAndGet,
            onCancel = cancelClicks::incrementAndGet,
        )

        composeRule.onNodeWithText("Parsing paused").assertExists()
        composeRule.onNodeWithTag("parsing-background-action").assertDoesNotExist()
        composeRule.onNodeWithTag("parsing-resume-action")
            .performScrollTo()
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.onNodeWithTag("parsing-cancel-action")
            .performScrollTo()
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        assertEquals(1, resumeClicks.get())
        assertEquals(1, cancelClicks.get())
    }

    @Test
    fun cancelledStateOffersRecoverableStartAgainAction() {
        val retryClicks = AtomicInteger(0)
        setScreen(
            preparation = PreparationState(
                stage = PreparationStage.READING_CHAPTERS,
                completedUnits = 3,
                totalUnits = 10,
                runState = PreparationRunState.CANCELLED,
            ),
            chapters = sampleChapters(2),
            onRetry = retryClicks::incrementAndGet,
        )

        composeRule.onNodeWithText("Parsing cancelled").assertExists()
        composeRule.onNodeWithTag("parsing-pause-action").assertDoesNotExist()
        composeRule.onNodeWithTag("parsing-retry-action").performScrollTo().performClick()

        assertEquals(1, retryClicks.get())
    }

    @Test
    fun retryableFailureShowsWorkerMessageAndRetryAction() {
        val retryClicks = AtomicInteger(0)
        setScreen(
            preparation = PreparationState(
                stage = PreparationStage.FAILED,
                completedUnits = 2,
                totalUnits = 10,
                message = "Page 3 could not be read.",
                retryable = true,
            ),
            chapters = sampleChapters(1),
            onRetry = retryClicks::incrementAndGet,
        )

        composeRule.onNodeWithText("Parsing stopped").assertExists()
        composeRule.onNodeWithText("Page 3 could not be read.").assertExists()
        composeRule.onNodeWithTag("parsing-retry-action").performScrollTo().performClick()

        assertEquals(1, retryClicks.get())
    }

    private fun setScreen(
        preparation: PreparationState,
        chapters: List<ParsingChapterHeader>,
        fontScale: Float = 1f,
        onContinueInBackground: () -> Unit = {},
        onPause: () -> Unit = {},
        onResume: () -> Unit = {},
        onCancel: () -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(baseDensity.density, fontScale),
            ) {
                WhisperbookTheme {
                    ParsingScreen(
                        contentPadding = PaddingValues.Zero,
                        book = testBook(preparation),
                        preparation = preparation,
                        discoveredChapters = chapters,
                        onBack = {},
                        onContinueInBackground = onContinueInBackground,
                        onPause = onPause,
                        onResume = onResume,
                        onCancel = onCancel,
                        onRetry = onRetry,
                        modifier = Modifier.width(360.dp).height(640.dp),
                    )
                }
            }
        }
    }

    private fun sampleChapters(count: Int): List<ParsingChapterHeader> = List(count) { index ->
        ParsingChapterHeader(
            id = "chapter-$index",
            ordinal = index,
            title = "Chapter title ${index + 1}",
        )
    }

    private fun testBook(preparation: PreparationState) = Book(
        id = "parsing-screen-book",
        title = "The Very Long Book Title That Still Needs To Remain Readable",
        author = null,
        format = BookFormat.PDF,
        sourceUri = null,
        privateSourcePath = null,
        coverPath = null,
        preparation = preparation,
        currentChapterId = null,
        currentPassageId = null,
        progressFraction = 0f,
        lastOpenedAtEpochMs = 0L,
    )
}
