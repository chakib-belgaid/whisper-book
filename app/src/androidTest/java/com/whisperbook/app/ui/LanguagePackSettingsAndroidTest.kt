package com.whisperbook.app.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.whisperbook.app.ui.screens.SettingsScreen
import com.whisperbook.app.ui.screens.VoiceCastScreen
import com.whisperbook.app.ui.screens.WhisperbookAppState
import com.whisperbook.app.ui.theme.WhisperbookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LanguagePackSettingsAndroidTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun frenchAndArabicAreBookScopedAndFrenchCanBeAddedForTheCurrentBook() {
        val appState = WhisperbookAppState()
        composeRule.setContent {
            WhisperbookTheme {
                VoiceCastScreen(
                    contentPadding = PaddingValues(),
                    appState = appState,
                    onBack = {},
                    onApply = {},
                )
            }
        }

        composeRule.onNodeWithTag("book-language-settings").assertIsDisplayed()
        composeRule.onNodeWithText("This choice applies only to The Moonlit Wood.").assertIsDisplayed()
        composeRule.onNodeWithText("English · English").assertIsDisplayed()
        composeRule.onNodeWithText("French · Français").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Arabic · العربية").assertIsDisplayed()

        composeRule.runOnIdle {
            assertEquals("fr", appState.narrationLanguageCode)
            assertTrue("fr" in appState.installedLanguagePackCodes)
            assertTrue("ar" !in appState.installedLanguagePackCodes)
        }
    }

    @Test
    fun globalSettingsDoNotOfferNarratorOrLanguageDefaults() {
        val appState = WhisperbookAppState()
        composeRule.setContent {
            WhisperbookTheme {
                SettingsScreen(
                    contentPadding = PaddingValues(),
                    appState = appState,
                )
            }
        }

        composeRule.onNodeWithText("Playback & preparation").assertExists()
        composeRule.onNodeWithText("Narration chunk size").assertExists().performClick()
        composeRule.onNodeWithText("Default narrator").assertDoesNotExist()
        composeRule.onNodeWithText("Language packs").assertDoesNotExist()
        composeRule.onNodeWithText("French · Français").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(240, appState.narrationChunkChars) }
    }
}
