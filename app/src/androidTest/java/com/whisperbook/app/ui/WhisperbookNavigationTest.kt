package com.whisperbook.app.ui

import android.net.Uri
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.whisperbook.app.ui.navigation.WhisperbookDestination
import com.whisperbook.app.domain.model.Book
import com.whisperbook.app.domain.model.BookFormat
import com.whisperbook.app.domain.model.Chapter
import com.whisperbook.app.domain.model.PreparationStage
import com.whisperbook.app.domain.model.PreparationState
import com.whisperbook.app.domain.model.SpeakerCorrectionScope
import com.whisperbook.app.domain.model.VoiceRegenerationScope
import com.whisperbook.app.integration.WhisperbookUiSnapshot
import com.whisperbook.app.ui.screens.WhisperbookAppState
import com.whisperbook.app.ui.screens.WhisperbookUiActions
import org.junit.Assert.assertEquals
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
    fun readAlongLetsTheUserCorrectAnAttributedVoiceAndChooseTheScope() {
        val appState = WhisperbookAppState()
        setApp(appState = appState)
        composeRule.runOnIdle { navController.navigate(WhisperbookDestination.CurrentChapter.route()) }

        composeRule.onNodeWithContentDescription("Correct attributed voice for Elara").performClick()
        composeRule.onNodeWithTag("attributed-voice-picker").assertIsDisplayed()
        composeRule.onNodeWithTag("attributed-speaker-fox").performClick()
        composeRule.onNodeWithTag("speaker-correction-scope-dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("correct-this-phrase").assertIsDisplayed()
        composeRule.onNodeWithTag("correct-matching-phrases").performClick()

        composeRule.runOnIdle {
            assertEquals("fox", appState.passages.first { it.id == "p2" }.speakerId)
            assertEquals("Fox", appState.passages.first { it.id == "p2" }.speakerName)
        }
        composeRule.onAllNodesWithContentDescription("Correct attributed voice for Fox")
            .assertCountEquals(2)
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

        composeRule.onNodeWithText("Prepared 2 of 19 chapters").assertIsDisplayed()
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
    fun settingsKeepsBookVoiceAndLanguageChoicesOutOfGlobalDefaults() {
        setApp(WhisperbookDestination.Settings.route)

        composeRule.onNodeWithText("Playback & preparation").assertIsDisplayed()
        composeRule.onNodeWithText("Default narrator").assertDoesNotExist()
        composeRule.onNodeWithText("Language packs").assertDoesNotExist()
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
    fun nowPlaying_currentPassageOpensReadAlong() {
        setApp(WhisperbookDestination.NowPlaying.route)

        composeRule.onNodeWithTag("current-passage-excerpt").assertIsDisplayed()
        composeRule.onNodeWithText("We should turn back before the lantern fades.").assertExists()
        composeRule.onNodeWithTag("current-passage-excerpt").performClick()

        composeRule.onNodeWithTag("current-chapter-screen").assertIsDisplayed()
    }

    @Test
    fun bookDetails_removeFromLibraryRequiresConfirmation() {
        setApp(WhisperbookDestination.BookDetails.route())

        composeRule.onNodeWithText("Export MP3").assertIsDisplayed()

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

    @Test
    fun libraryNavigationSwitchesBooksAndReturnsToEachBooksOwnChapter() {
        val bookA = navigationBook("book-a", "Book A", currentChapter = 2, chapterCount = 2)
        val bookB = navigationBook("book-b", "Book B", currentChapter = 3, chapterCount = 3)
        val books = listOf(bookA, bookB)
        val chapters = mapOf(
            bookA.id to navigationChapters(bookA.id, 2),
            bookB.id to navigationChapters(bookB.id, 3),
        )
        val selectedBooks = mutableListOf<String>()
        lateinit var appState: WhisperbookAppState
        val actions = NavigationBookActions { bookId ->
            selectedBooks += bookId
            val book = books.first { it.id == bookId }
            val bookChapters = chapters.getValue(bookId)
            appState.synchronize(
                WhisperbookUiSnapshot(
                    books = books,
                    selectedBook = book,
                    chapters = bookChapters,
                    selectedChapter = bookChapters.first { it.id == book.currentChapterId },
                    preparation = PreparationState.Ready,
                ),
            )
        }
        appState = WhisperbookAppState(actions).apply {
            synchronize(
                WhisperbookUiSnapshot(
                    books = books,
                    selectedBook = bookA,
                    chapters = chapters.getValue(bookA.id),
                    selectedChapter = chapters.getValue(bookA.id)[1],
                    preparation = PreparationState.Ready,
                ),
            )
        }
        setApp(WhisperbookDestination.Library.route, appState)

        composeRule.onNodeWithContentDescription("Open Book B").performClick()
        composeRule.onNodeWithContentDescription("Papercraft cover illustration for Book B").assertIsDisplayed()
        composeRule.onNodeWithText("Chapter 3 of 3").assertIsDisplayed()

        composeRule.runOnIdle { navController.popBackStack() }
        composeRule.onNodeWithContentDescription("Resume Book A").performClick()
        composeRule.onNodeWithContentDescription("Book A, chapter 2. Open book details").assertIsDisplayed()

        assertEquals(listOf("book-b", "book-a"), selectedBooks)
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

private class NavigationBookActions(
    private val onSelectBook: (String) -> Unit,
) : WhisperbookUiActions {
    override fun importBook(uri: Uri) = Unit
    override fun retryPreparation() = Unit
    override fun deleteSelectedBook() = Unit
    override fun exportSelectedBook(destination: Uri) = Unit
    override fun selectBook(bookId: String) = onSelectBook(bookId)
    override fun selectChapter(chapterId: String) = Unit
    override fun playPreviousChapter() = Unit
    override fun playNextChapter() = Unit
    override fun playSelectedChapter() = Unit
    override fun playOrPause() = Unit
    override fun seekByFraction(delta: Float) = Unit
    override fun seekToFraction(fraction: Float) = Unit
    override fun seekToPassage(passageId: String) = Unit
    override fun correctPassageSpeaker(
        passageId: String,
        speakerId: String,
        scope: SpeakerCorrectionScope,
    ) = Unit
    override fun cycleSpeed() = Unit
    override fun cycleNarrationChunkSize() = Unit
    override fun downloadLanguagePack(languageCode: String) = Unit
    override fun selectNarrationLanguage(languageCode: String) = Unit
    override fun cycleSleepTimer() = Unit
    override fun cycleVoice(characterId: String) = Unit
    override fun assignVoice(
        characterId: String,
        voiceId: String,
        regenerationScope: VoiceRegenerationScope,
    ) = Unit
    override fun revertVoiceChange() = Unit
    override fun previewCharacter(characterId: String) = Unit
    override fun previewVoice(voiceId: String, characterName: String) = Unit
    override fun setAutoScroll(enabled: Boolean) = Unit
    override fun setKeepScreenAwake(enabled: Boolean) = Unit
    override fun setLargerText(enabled: Boolean) = Unit
    override fun completeOnboarding() = Unit
}

private fun navigationBook(
    id: String,
    title: String,
    currentChapter: Int,
    chapterCount: Int,
) = Book(
    id = id,
    title = title,
    author = "Test Author",
    format = BookFormat.EPUB,
    sourceUri = null,
    privateSourcePath = null,
    coverPath = null,
    preparation = PreparationState.Ready,
    currentChapterId = "$id-chapter-$currentChapter",
    currentPassageId = null,
    progressFraction = currentChapter.toFloat() / chapterCount,
    lastOpenedAtEpochMs = 0L,
    chapterCount = chapterCount,
    currentChapterOrdinal = currentChapter - 1,
)

private fun navigationChapters(bookId: String, chapterCount: Int): List<Chapter> =
    (1..chapterCount).map { number ->
        Chapter(
            id = "$bookId-chapter-$number",
            bookId = bookId,
            ordinal = number - 1,
            title = "Chapter $number",
        )
    }
