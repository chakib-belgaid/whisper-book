package com.whisperbook.app.ui

import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.whisperbook.app.ui.navigation.WhisperbookDestination
import com.whisperbook.app.domain.model.PreparationStage
import com.whisperbook.app.domain.model.PreparationState
import com.whisperbook.app.integration.WhisperbookUiSnapshot
import com.whisperbook.app.ui.screens.WhisperbookAppState
import org.junit.Assert.assertFalse
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
    fun libraryTab_withoutLibraryAnchor_buildsCleanRoot() {
        setApp(WhisperbookDestination.Settings.route)

        composeRule.onNodeWithContentDescription("Library").performClick()
        composeRule.onNodeWithText("Your Library").assertIsDisplayed()

        composeRule.runOnIdle {
            assertFalse("Library should be the root after top-level navigation", navController.popBackStack())
        }
    }

    @Test
    fun importFromWelcome_placesLibraryBehindImportInsteadOfWelcome() {
        setApp(WhisperbookDestination.Welcome.route)

        composeRule.onNodeWithText("Import a book").performClick()
        composeRule.onNodeWithText("Processed privately on this device").assertIsDisplayed()
        composeRule.runOnIdle { navController.popBackStack() }

        composeRule.onNodeWithText("Your Library").assertIsDisplayed()
        composeRule.onNodeWithText("Whisperbook").assertDoesNotExist()
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
    fun backgroundPreparationShowsRecordedChaptersAndListenReturnsToProgressWhenEmpty() {
        val appState = WhisperbookAppState().apply {
            synchronize(
                WhisperbookUiSnapshot(
                    preparation = PreparationState(
                        stage = PreparationStage.PREPARING_AUDIO,
                        completedUnits = 2,
                        totalUnits = 19,
                        progressFraction = 2f / 19f,
                        message = "Recording chapter 3",
                    ),
                ),
            )
        }
        setApp(WhisperbookDestination.Library.route, appState)

        composeRule.onNodeWithText("Recorded 2 of 19 chapters").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Listen").performClick()
        composeRule.onNodeWithText("Preparing your audiobook").assertIsDisplayed()
    }

    @Test
    fun voiceCast_changeVoiceOpensListAndUpdatesAssignment() {
        setApp()
        composeRule.runOnIdle { navController.navigate(WhisperbookDestination.VoiceCast.route()) }

        composeRule.onNodeWithContentDescription("Change voice for Narrator").performClick()
        composeRule.onNodeWithText("Choose a voice for Narrator").assertIsDisplayed()
        composeRule.onNodeWithTag("voice-preview-jasper").performClick()
        composeRule.onNodeWithTag("voice-picker").assertIsDisplayed()
        composeRule.onNodeWithTag("voice-option-jasper").performClick()
        composeRule.onNodeWithTag("voice-regeneration-dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("regenerate-whole-book").performClick()

        composeRule.onNodeWithText("Jasper").assertIsDisplayed()
        composeRule.onNodeWithTag("voice-picker").assertDoesNotExist()
    }

    @Test
    fun settings_defaultNarratorOpensAListThatCanBeTestedBeforeChoosing() {
        setApp(WhisperbookDestination.Settings.route)

        composeRule.onNodeWithText("Default narrator").performClick()
        composeRule.onNodeWithText("Choose a voice for Narrator").assertIsDisplayed()
        composeRule.onNodeWithTag("voice-preview-jasper").performClick()
        composeRule.onNodeWithTag("voice-picker").assertIsDisplayed()
        composeRule.onNodeWithTag("voice-option-jasper").performClick()

        composeRule.onNodeWithText("Jasper").assertIsDisplayed()
        composeRule.onNodeWithTag("voice-picker").assertDoesNotExist()
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

    @Test
    fun bookDetails_removeFromLibraryRequiresConfirmation() {
        setApp(WhisperbookDestination.BookDetails.route())

        composeRule.onNodeWithContentDescription("Remove The Moonlit Wood from library").performClick()
        composeRule.onNodeWithText("Remove this book?").assertIsDisplayed()
        composeRule.onNodeWithText("Keep book").performClick()
        composeRule.onNodeWithTag("remove-book-dialog").assertDoesNotExist()

        composeRule.onNodeWithContentDescription("Remove The Moonlit Wood from library").performClick()
        composeRule.onNodeWithText("Remove book").performClick()
        composeRule.onNodeWithText("Your Library").assertIsDisplayed()
    }

    @Test
    fun library_removeButtonAlsoRequiresConfirmation() {
        setApp(WhisperbookDestination.Library.route)

        composeRule.onNodeWithContentDescription("Remove The Moonlit Wood from library").performClick()
        composeRule.onNodeWithText("Remove this book?").assertIsDisplayed()
        composeRule.onNodeWithText("Keep book").performClick()

        composeRule.onNodeWithText("Your Library").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Remove The Moonlit Wood from library").assertExists()
    }

    private fun setApp(
        startDestination: String = WhisperbookDestination.Welcome.route,
        appState: WhisperbookAppState = WhisperbookAppState(),
    ) {
        composeRule.setContent {
            navController = rememberNavController()
            WhisperbookApp(
                appState = remember { appState },
                navController = navController,
                startDestination = startDestination,
            )
        }
    }
}
