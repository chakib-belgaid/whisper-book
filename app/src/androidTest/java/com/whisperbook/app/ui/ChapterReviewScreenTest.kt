package com.whisperbook.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import com.whisperbook.app.domain.model.Chapter
import com.whisperbook.app.domain.model.ChapterPlanEntry
import com.whisperbook.app.ui.screens.ChapterReviewScreen
import com.whisperbook.app.ui.theme.WhisperbookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChapterReviewScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun allChaptersStartIncludedAndContinueUsesSimpleLabel() {
        showScreen(chapters = chapters(selected = setOf("chapter-1", "chapter-2", "chapter-3")))

        composeRule.onNodeWithTag("chapter-selection-count").assertIsDisplayed()
        composeRule.onNodeWithText("3 of 3 selected").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm-chapter-plan").assertIsEnabled()
        composeRule.onNodeWithText("Continue with all chapters").assertIsDisplayed()
        composeRule.onNodeWithText("Skipped for now").assertExists()
    }

    @Test
    fun zeroSelectionExplainsWhyContinueIsDisabled() {
        showScreen(chapters = chapters(selected = emptySet()))

        composeRule.onNodeWithTag("confirm-chapter-plan").assertIsNotEnabled()
        composeRule.onNodeWithTag("chapter-selection-required").assertIsDisplayed()
        composeRule.onNodeWithText("Select at least one chapter to continue.").assertIsDisplayed()
        composeRule.onNodeWithText("Continue with 0 chapters").assertIsDisplayed()
    }

    @Test
    fun userCanSkipReactivateAndMoveAChapter() {
        val toggles = mutableListOf<Pair<String, Boolean>>()
        val moves = mutableListOf<Pair<String, Int>>()
        showScreen(
            chapters = chapters(selected = setOf("chapter-1", "chapter-2")),
            onToggle = { id, selected -> toggles += id to selected },
            onMove = { id, position -> moves += id to position },
        )

        composeRule.onNodeWithTag("chapter-plan-chapter-1").performClick()
        composeRule.onNodeWithTag("chapter-plan-chapter-3").performClick()
        composeRule.onNodeWithTag("move-chapter-1-later").performClick()

        assertEquals(
            listOf("chapter-1" to false, "chapter-3" to true),
            toggles,
        )
        assertEquals(listOf("chapter-1" to 1), moves)
    }

    @Test
    fun searchKeepsSectionsVisibleAndDisablesReordering() {
        showScreen(chapters = chapters(selected = setOf("chapter-1", "chapter-2", "chapter-3")))

        composeRule.onNodeWithTag("chapter-search").performTextInput("Three")

        composeRule.onNodeWithText("Included").assertIsDisplayed()
        composeRule.onNodeWithText("Skipped for now").assertIsDisplayed()
        composeRule.onNodeWithText("Reordering is available after clearing search.").assertIsDisplayed()
        composeRule.onNodeWithTag("move-chapter-3-earlier").assertIsNotEnabled()
        composeRule.onNodeWithTag("move-chapter-3-later").assertIsNotEnabled()
    }

    @Test
    fun chapterActionsExposeBulkAndResetCallbacks() {
        var selectAll = false
        var restoreOrder = false
        showScreen(
            chapters = chapters(selected = setOf("chapter-1")),
            onSelectAll = { selectAll = true },
            onRestoreOriginalOrder = { restoreOrder = true },
        )

        composeRule.onNodeWithTag("chapter-actions").performClick()
        composeRule.onNodeWithTag("select-all-chapters").performClick()
        composeRule.onNodeWithTag("chapter-actions").performClick()
        composeRule.onNodeWithTag("restore-chapter-order").performClick()

        assertTrue(selectAll)
        assertTrue(restoreOrder)
    }

    @Test
    fun primaryActionRemainsVisibleAtTwoHundredPercentText() {
        val baseDensity = Density(composeRule.activity.resources.displayMetrics.density, 1f)
        composeRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(baseDensity.density, fontScale = 2f),
            ) {
                WhisperbookTheme {
                    ChapterReviewScreen(
                        contentPadding = PaddingValues(),
                        chapters = chapters(selected = setOf("chapter-1", "chapter-2", "chapter-3")),
                        onToggleChapter = { _, _ -> },
                        onMoveChapter = { _, _ -> },
                        onSelectAll = {},
                        onDeselectAll = {},
                        onRestoreOriginalOrder = {},
                        onReset = {},
                        onContinue = {},
                        onBack = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag("confirm-chapter-plan").assertIsDisplayed().assertIsEnabled()
    }

    private fun showScreen(
        chapters: List<ChapterPlanEntry>,
        onToggle: (String, Boolean) -> Unit = { _, _ -> },
        onMove: (String, Int) -> Unit = { _, _ -> },
        onSelectAll: () -> Unit = {},
        onRestoreOriginalOrder: () -> Unit = {},
    ) {
        composeRule.setContent {
            WhisperbookTheme {
                ChapterReviewScreen(
                    contentPadding = PaddingValues(),
                    chapters = chapters,
                    onToggleChapter = onToggle,
                    onMoveChapter = onMove,
                    onSelectAll = onSelectAll,
                    onDeselectAll = {},
                    onRestoreOriginalOrder = onRestoreOriginalOrder,
                    onReset = {},
                    onContinue = {},
                    onBack = {},
                )
            }
        }
    }

    private fun chapters(selected: Set<String>): List<ChapterPlanEntry> = listOf(
        "Chapter One",
        "Chapter Two",
        "Chapter Three",
    ).mapIndexed { index, title ->
        val chapterId = "chapter-${index + 1}"
        ChapterPlanEntry(
            chapter = Chapter(
                id = chapterId,
                bookId = "book-1",
                ordinal = index,
                title = title,
            ),
            isSelected = chapterId in selected,
            customPosition = index,
        )
    }
}
