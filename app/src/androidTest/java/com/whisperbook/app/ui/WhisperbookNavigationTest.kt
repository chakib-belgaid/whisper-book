package com.whisperbook.app.ui

import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.whisperbook.app.ui.navigation.WhisperbookDestination
import com.whisperbook.app.ui.screens.WhisperbookAppState
import org.junit.Rule
import org.junit.Test

class WhisperbookNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()
    private lateinit var navController: NavHostController

    @Test
    fun welcomeToLibraryToImport_followsPrimaryGraph() {
        setApp(WhisperbookDestination.Welcome.route)

        composeRule.onNodeWithText("Whisperbook").assertIsDisplayed()
        composeRule.onNodeWithText("Explore the app").performClick()
        composeRule.onNodeWithText("Your Library").assertIsDisplayed()
        composeRule.onNodeWithText("Add a book").performClick()
        composeRule.onAllNodesWithText("Import a book")[0].assertExists()
        composeRule.onNodeWithText("Processed privately on this device").assertExists()
    }

    @Test
    fun readAlong_hasSpeakerLabelsActiveStateAndPlayback() {
        setApp()
        composeRule.runOnIdle { navController.navigate(WhisperbookDestination.CurrentChapter.route()) }

        composeRule.onNodeWithText("Chapter 7").assertIsDisplayed()
        composeRule.onAllNodesWithText("NARRATOR", substring = false)[0].assertExists()
        composeRule.onAllNodesWithText("ELARA", substring = false)[0].assertExists()
        composeRule.onNodeWithText("Now speaking").assertExists()
        composeRule.onNodeWithContentDescription("Play").performClick()
        composeRule.onNodeWithContentDescription("Pause").assertExists()
    }

    @Test
    fun voiceCast_omitsPersistentBottomNavigation() {
        setApp()
        composeRule.runOnIdle { navController.navigate(WhisperbookDestination.VoiceCast.route()) }

        composeRule.onNodeWithText("Voice cast").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Library").assertDoesNotExist()
        composeRule.onNodeWithText("Automatically matched. You can adjust any voice.").assertExists()
    }

    @Test
    fun chapterPicker_opensLaterChapterFromNowPlaying() {
        setApp(WhisperbookDestination.NowPlaying.route)

        composeRule.onNodeWithContentDescription("Choose chapter, currently 7 of 18").performClick()
        composeRule.onNodeWithText("Choose a chapter").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Chapter 8, A Lantern in the Rain").performClick()

        composeRule.onNodeWithText("Chapter 8").assertIsDisplayed()
        composeRule.onNodeWithText("A Lantern in the Rain").assertIsDisplayed()
    }

    private fun setApp(startDestination: String = WhisperbookDestination.Welcome.route) {
        composeRule.setContent {
            navController = rememberNavController()
            WhisperbookApp(
                appState = remember { WhisperbookAppState() },
                navController = navController,
                startDestination = startDestination,
            )
        }
    }
}
