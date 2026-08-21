package com.whisperbook.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.whisperbook.app.domain.model.VoiceRegenerationScope
import com.whisperbook.app.ui.screens.VoiceRegenerationDialog
import com.whisperbook.app.ui.theme.WhisperbookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VoiceRegenerationDialogTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun userCanChooseThisChapterAndLater() {
        var selected: VoiceRegenerationScope? = null
        composeRule.setContent {
            WhisperbookTheme {
                VoiceRegenerationDialog(
                    characterName = "Narrator",
                    voiceName = "Jasper",
                    canApplyFromThisChapter = true,
                    onConfirm = { selected = it },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag("voice-regeneration-dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("regenerate-from-this-chapter").performClick()

        assertEquals(VoiceRegenerationScope.FROM_THIS_CHAPTER, selected)
    }

    @Test
    fun userCanChooseThisChapterOnly() {
        var selected: VoiceRegenerationScope? = null
        composeRule.setContent {
            WhisperbookTheme {
                VoiceRegenerationDialog(
                    characterName = "Narrator",
                    voiceName = "Jasper",
                    canApplyFromThisChapter = true,
                    onConfirm = { selected = it },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag("regenerate-this-chapter").performClick()
        assertEquals(VoiceRegenerationScope.THIS_CHAPTER, selected)
    }

    @Test
    fun userCanChooseWholeBook() {
        var selected: VoiceRegenerationScope? = null
        composeRule.setContent {
            WhisperbookTheme {
                VoiceRegenerationDialog(
                    characterName = "Narrator",
                    voiceName = "Jasper",
                    canApplyFromThisChapter = true,
                    onConfirm = { selected = it },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag("regenerate-whole-book").performClick()
        assertEquals(VoiceRegenerationScope.WHOLE_BOOK, selected)
    }

    @Test
    fun chapterAndLaterChoiceIsDisabledAtTheEndOfTheBook() {
        composeRule.setContent {
            WhisperbookTheme {
                VoiceRegenerationDialog(
                    characterName = "Narrator",
                    voiceName = "Jasper",
                    canApplyFromThisChapter = false,
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag("regenerate-whole-book").assertIsDisplayed()
        composeRule.onNodeWithTag("regenerate-from-this-chapter").assertIsNotEnabled()
    }
}
