package com.whisperbook.app.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.whisperbook.app.domain.model.BuiltInCharacters
import com.whisperbook.app.domain.model.CharacterColorRole
import com.whisperbook.app.domain.model.Chapter
import com.whisperbook.app.domain.model.Passage
import com.whisperbook.app.domain.model.StoryCharacter
import com.whisperbook.app.integration.WhisperbookUiSnapshot
import com.whisperbook.app.ui.screens.CurrentChapterScreen
import com.whisperbook.app.ui.screens.WhisperbookAppState
import com.whisperbook.app.ui.theme.WhisperbookTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LargePassageReaderAndroidTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun legacyMillionCharacterPassageRendersAsBoundedLazyItems() {
        val chapter = Chapter(
            id = "chapter-1",
            bookId = "book-1",
            ordinal = 0,
            title = "Chapter 1",
            passages = listOf(
                Passage(
                    id = "legacy-passage",
                    chapterId = "chapter-1",
                    ordinal = 0,
                    text = "A".repeat(1_590_051),
                    speakerId = BuiltInCharacters.NARRATOR_ID,
                    confidence = 1f,
                    attributionRule = "narration",
                ),
            ),
        )
        val appState = WhisperbookAppState().apply {
            synchronize(
                WhisperbookUiSnapshot(
                    chapters = listOf(chapter),
                    selectedChapter = chapter,
                ),
            )
        }

        composeRule.setContent {
            WhisperbookTheme {
                CurrentChapterScreen(
                    contentPadding = PaddingValues(),
                    appState = appState,
                    onBack = {},
                    onVoiceCast = {},
                )
            }
        }

        composeRule.onNodeWithTag("current-chapter-screen").assertExists()
        composeRule.onNodeWithTag("passage-1").assertExists()
    }

    @Test
    fun consecutiveSectionsForTheSameVoiceRenderInOneReaderCard() {
        val chapter = Chapter(
            id = "chapter-1",
            bookId = "book-1",
            ordinal = 0,
            title = "Chapter 1",
            passages = listOf(
                Passage("p1", "chapter-1", 0, "First narrator paragraph.", "narrator", 1f, "narration"),
                Passage("p2", "chapter-1", 1, "Second narrator paragraph.", "narrator", 1f, "narration"),
                Passage("p3", "chapter-1", 2, "Elara answers.", "elara", 1f, "dialogue"),
            ),
        )
        val appState = WhisperbookAppState().apply {
            synchronize(
                WhisperbookUiSnapshot(
                    chapters = listOf(chapter),
                    selectedChapter = chapter,
                    characters = listOf(
                        StoryCharacter("narrator", "book-1", "Narrator", emptySet(), CharacterColorRole.NARRATOR, 0),
                        StoryCharacter("elara", "book-1", "Elara", emptySet(), CharacterColorRole.ELARA_BURGUNDY, 1),
                    ),
                ),
            )
        }

        composeRule.setContent {
            WhisperbookTheme {
                CurrentChapterScreen(
                    contentPadding = PaddingValues(),
                    appState = appState,
                    onBack = {},
                    onVoiceCast = {},
                )
            }
        }

        composeRule.onAllNodesWithText("NARRATOR", substring = false).assertCountEquals(1)
        composeRule.onNodeWithText("First narrator paragraph.", substring = true).assertExists()
        composeRule.onNodeWithText("Second narrator paragraph.", substring = true).assertExists()
        composeRule.onNodeWithTag("passage-2").assertExists()
        composeRule.onNodeWithTag("passage-3").assertDoesNotExist()
    }
}
