package com.whisperbook.app.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.whisperbook.app.domain.model.PreparationStage
import com.whisperbook.app.domain.model.PreparationState
import com.whisperbook.app.ui.theme.WhisperbookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LibraryScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun currentBookCardIsTheResumeAction() {
        val resumedBooks = mutableListOf<String>()
        val openedBooks = mutableListOf<String>()
        setLibrary(
            appState = WhisperbookAppState(),
            onBook = openedBooks::add,
            onResume = resumedBooks::add,
        )

        composeRule
            .onNode(
                hasContentDescription("Resume The Moonlit Wood") and
                    hasText("The Moonlit Wood"),
            )
            .assertHasClickAction()
            .performClick()

        assertEquals(listOf("moonlit"), resumedBooks)
        assertTrue(openedBooks.isEmpty())
    }

    @Test
    fun currentBookCardUsesOpenActionUntilItCanBeListenedTo() {
        val appState = WhisperbookAppState().apply {
            books.clear()
            books += LibraryBookUi(
                id = "preparing",
                title = "Preparing Story",
                author = "Test Author",
                chapter = 1,
                totalChapters = 0,
                progress = 0f,
                preparation = PreparationState(PreparationStage.READING_CHAPTERS),
            )
        }
        val openedBooks = mutableListOf<String>()
        val resumedBooks = mutableListOf<String>()
        setLibrary(
            appState = appState,
            onBook = openedBooks::add,
            onResume = resumedBooks::add,
        )

        composeRule
            .onNode(
                hasContentDescription("Open Preparing Story") and
                    hasText("Preparing Story"),
            )
            .assertHasClickAction()
            .performClick()

        assertEquals(listOf("preparing"), openedBooks)
        assertTrue(resumedBooks.isEmpty())
    }

    @Test
    fun currentBookDeleteRemainsAnIndependentControl() {
        val resumedBooks = mutableListOf<String>()
        setLibrary(
            appState = WhisperbookAppState(),
            onResume = resumedBooks::add,
        )

        composeRule
            .onNodeWithContentDescription("Remove The Moonlit Wood from library")
            .assertHasClickAction()
            .performClick()

        composeRule.onNodeWithText("Remove this book?").assertIsDisplayed()
        assertTrue(resumedBooks.isEmpty())
    }

    private fun setLibrary(
        appState: WhisperbookAppState,
        onBook: (String) -> Unit = {},
        onResume: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            WhisperbookTheme {
                LibraryScreen(
                    contentPadding = PaddingValues(),
                    appState = appState,
                    onImport = {},
                    onBook = onBook,
                    onResume = onResume,
                    onRemoveBook = {},
                )
            }
        }
    }
}
