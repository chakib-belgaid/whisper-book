package com.whisperbook.app.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.whisperbook.app.domain.model.BuiltInCharacters
import com.whisperbook.app.domain.model.Chapter
import com.whisperbook.app.domain.model.Passage
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
}
