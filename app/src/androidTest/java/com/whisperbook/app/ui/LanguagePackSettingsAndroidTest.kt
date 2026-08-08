package com.whisperbook.app.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.whisperbook.app.ui.screens.SettingsScreen
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
    fun frenchAndArabicAreOptionalAndFrenchCanBeAdded() {
        val appState = WhisperbookAppState()
        composeRule.setContent {
            WhisperbookTheme {
                SettingsScreen(
                    contentPadding = PaddingValues(),
                    appState = appState,
                    onManageVoices = {},
                )
            }
        }

        composeRule.onNodeWithText("English · English").assertExists()
        composeRule.onNodeWithText("French · Français").assertExists().performClick()
        composeRule.onNodeWithText("Arabic · العربية").assertExists()

        composeRule.runOnIdle {
            assertEquals("fr", appState.narrationLanguageCode)
            assertTrue("fr" in appState.installedLanguagePackCodes)
            assertTrue("ar" !in appState.installedLanguagePackCodes)
        }
    }
}
