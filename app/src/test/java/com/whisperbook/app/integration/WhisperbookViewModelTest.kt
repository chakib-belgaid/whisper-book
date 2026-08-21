package com.whisperbook.app.integration

import android.net.Uri
import app.cash.turbine.test
import com.whisperbook.app.domain.AudioSegmentStore
import com.whisperbook.app.domain.BookMp3Exporter
import com.whisperbook.app.domain.LibraryRepository
import com.whisperbook.app.domain.PlaybackGateway
import com.whisperbook.app.domain.PreparationCoordinator
import com.whisperbook.app.domain.SettingsRepository
import com.whisperbook.app.domain.SynthesisRequest
import com.whisperbook.app.domain.SynthesisResult
import com.whisperbook.app.domain.VoicePreviewPlayer
import com.whisperbook.app.domain.model.AppSettings
import com.whisperbook.app.domain.model.AudioSegment
import com.whisperbook.app.domain.model.Book
import com.whisperbook.app.domain.model.BookFormat
import com.whisperbook.app.domain.model.BookMp3ExportProgress
import com.whisperbook.app.domain.model.BookMp3ExportResult
import com.whisperbook.app.domain.model.BookMp3ExportStage
import com.whisperbook.app.domain.model.Chapter
import com.whisperbook.app.domain.model.CharacterColorRole
import com.whisperbook.app.domain.model.CharacterVoiceAssignment
import com.whisperbook.app.domain.model.PlaybackCursor
import com.whisperbook.app.domain.model.PlaybackPreparationProgress
import com.whisperbook.app.domain.model.Passage
import com.whisperbook.app.domain.model.PreparationStage
import com.whisperbook.app.domain.model.PreparationState
import com.whisperbook.app.domain.model.RevertibleVoiceChange
import com.whisperbook.app.domain.model.StoryCharacter
import com.whisperbook.app.domain.model.SpeakerCorrectionScope
import com.whisperbook.app.domain.model.VoiceDescriptor
import com.whisperbook.app.domain.model.VoiceRegenerationRequest
import com.whisperbook.app.domain.model.VoiceRegenerationScope
import com.whisperbook.app.domain.model.speakerPhraseMatchKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WhisperbookViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun databaseDetailsAreNotExposedAsOperationCopy() {
        val databaseFailure = IllegalStateException(
            "too many SQL variables (code 1 SQLITE_ERROR), while compiling: SELECT passages.id " +
                "FROM passages WHERE chapter_id IN (?, ?, ?)",
        )

        assertEquals(
            "The local operation could not finish. Please try again.",
            userFacingOperationError(databaseFailure),
        )
        assertEquals(
            "The local operation could not finish. Please try again.",
            userFacingOperationError(IllegalStateException("too many SQL variables")),
        )
        assertEquals(
            "Choose a book to listen to",
            userFacingOperationError(IllegalStateException("Choose a book to listen to")),
        )
    }

    @Test
    fun productionFlowsSelectNewestBookAndItsChapter() = runTest(dispatcher) {
        val services = FakeServices()
        val viewModel = WhisperbookViewModel(services)

        viewModel.uiState.test {
            awaitItem()
            advanceUntilIdle()
            val snapshot = expectMostRecentItem()
            assertEquals("book-a", snapshot.selectedBook?.id)
            assertEquals("chapter-a", snapshot.selectedChapter?.id)
            assertEquals("Narrator", snapshot.characters.single().displayName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun changingVoiceRegeneratesWholeBookWithoutDeletingRetainedAudio() = runTest(dispatcher) {
        val services = FakeServices()
        val viewModel = WhisperbookViewModel(services)
        viewModel.uiState.test {
            awaitItem()
            advanceUntilIdle()
            viewModel.assignVoice("narrator", "jasper", VoiceRegenerationScope.WHOLE_BOOK)
            advanceUntilIdle()

            assertEquals(
                "voice-regeneration:book-a:narrator:jasper:WHOLE_BOOK:0",
                services.events.last(),
            )
            assertEquals("jasper", services.assignments.value["narrator"]?.voiceId)
            assertTrue(viewModel.uiState.value.canRevertVoiceChange)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun changingVoiceFromThisChapterUsesTheCurrentSelection() = runTest(dispatcher) {
        val services = FakeServices().apply {
            chapters.value = mapOf(
                "book-a" to listOf(
                    Chapter("chapter-a", "book-a", 0, "The Beginning"),
                    Chapter("chapter-b", "book-a", 1, "The Middle"),
                    Chapter("chapter-c", "book-a", 2, "The End"),
                ),
            )
        }
        val viewModel = WhisperbookViewModel(services)
        viewModel.uiState.test {
            awaitItem()
            advanceUntilIdle()

            viewModel.selectChapter("chapter-b")
            advanceUntilIdle()
            viewModel.assignVoice("narrator", "jasper", VoiceRegenerationScope.FROM_THIS_CHAPTER)
            advanceUntilIdle()

            assertEquals(
                "voice-regeneration:book-a:narrator:jasper:FROM_THIS_CHAPTER:1",
                services.events.last(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun revertingVoiceChangeRestoresThePreviousAssignment() = runTest(dispatcher) {
        val services = FakeServices()
        val viewModel = WhisperbookViewModel(services)
        viewModel.uiState.test {
            awaitItem()
            advanceUntilIdle()

            viewModel.assignVoice("narrator", "jasper", VoiceRegenerationScope.WHOLE_BOOK)
            advanceUntilIdle()
            viewModel.revertVoiceChange()
            advanceUntilIdle()

            assertEquals("bella", services.assignments.value["narrator"]?.voiceId)
            assertEquals("revert-voice:narrator:bella", services.events.last())
            assertFalse(viewModel.uiState.value.canRevertVoiceChange)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun previewCharacterPausesBookAndPlaysAssignedVoiceDemo() = runTest(dispatcher) {
        val services = FakeServices().apply {
            assignments.value = mapOf(
                "narrator" to CharacterVoiceAssignment("narrator", "jasper", ttsModelVersion, 1f),
            )
            playback.value = cursor(isPlaying = true)
        }
        val viewModel = WhisperbookViewModel(services)
        viewModel.uiState.test {
            awaitItem()
            advanceUntilIdle()

            viewModel.previewCharacter("narrator")
            advanceUntilIdle()

            assertEquals(
                listOf(
                    "pause",
                    "preview:jasper:Once upon a time, every story began with a voice.:1.0",
                ),
                services.events.takeLast(2),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun previewVoiceAuditionsRequestedNarratorWithoutChangingAssignment() = runTest(dispatcher) {
        val services = FakeServices()
        val viewModel = WhisperbookViewModel(services)
        viewModel.uiState.test {
            awaitItem()
            advanceUntilIdle()

            viewModel.previewVoice("jasper", "Narrator")
            advanceUntilIdle()

            assertEquals(
                "preview:jasper:Once upon a time, every story began with a voice.:1.0",
                services.events.last(),
            )
            assertEquals("bella", services.assignments.value["narrator"]?.voiceId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun downloadingFrenchPackInstallsItAndAppliesItToTheBrowsedBook() = runTest(dispatcher) {
        val services = FakeServices()
        val viewModel = WhisperbookViewModel(services)
        viewModel.uiState.test {
            awaitItem()
            advanceUntilIdle()

            viewModel.downloadLanguagePack("fr")
            advanceUntilIdle()

            val settings = services.settings.value
            assertTrue("fr" in settings.installedLanguagePackCodes)
            assertEquals("fr", services.books.value.single().narrationLanguageCode)
            assertEquals("book-language:book-a:fr", services.events.last())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun changingLanguageUpdatesOnlyTheBrowsedBook() = runTest(dispatcher) {
        val services = FakeServices().apply {
            books.value = listOf(book("book-a"), book("book-b"))
            settings.value = AppSettings(installedLanguagePackCodes = setOf("en", "fr"))
        }
        val viewModel = WhisperbookViewModel(services)
        viewModel.uiState.test {
            awaitItem()
            advanceUntilIdle()

            viewModel.selectBook("book-b")
            advanceUntilIdle()
            viewModel.selectNarrationLanguage("fr")
            advanceUntilIdle()

            assertEquals("en", services.books.value.first { it.id == "book-a" }.narrationLanguageCode)
            assertEquals("fr", services.books.value.first { it.id == "book-b" }.narrationLanguageCode)
            assertEquals("book-language:book-b:fr", services.events.last())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun correctingMatchingPhrasesChangesOnlyEquivalentTextWithTheSameOldAttribution() = runTest(dispatcher) {
        val repeated = Passage("passage-a", "chapter-a", 0, "Wait!", "narrator", .4f, "automatic")
        val services = FakeServices().apply {
            chapters.value = mapOf(
                "book-a" to listOf(
                    Chapter(
                        "chapter-a",
                        "book-a",
                        0,
                        "Opening",
                        passages = listOf(
                            repeated,
                            repeated.copy(id = "passage-b", ordinal = 1, text = " wait "),
                            repeated.copy(id = "passage-c", ordinal = 2, speakerId = "fox"),
                        ),
                    ),
                ),
            )
            characters.value = mapOf(
                "book-a" to listOf(
                    StoryCharacter("narrator", "book-a", "Narrator", emptySet(), CharacterColorRole.NARRATOR, 2),
                    StoryCharacter("elara", "book-a", "Elara", emptySet(), CharacterColorRole.ELARA_BURGUNDY, 1),
                ),
            )
        }
        val viewModel = WhisperbookViewModel(services)
        viewModel.uiState.test {
            awaitItem()
            advanceUntilIdle()

            viewModel.correctPassageSpeaker(
                "passage-a",
                "elara",
                SpeakerCorrectionScope.MATCHING_PHRASES,
            )
            advanceUntilIdle()

            val passages = services.chapters.value.getValue("book-a").single().passages
            assertEquals(listOf("elara", "elara", "fox"), passages.map(Passage::speakerId))
            assertTrue(services.events.last().endsWith(":MATCHING_PHRASES:2"))
            assertEquals(
                "2 matching phrases will now be read by Elara.",
                viewModel.uiState.value.statusMessage,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun changingChunkSizePersistsItAndRegeneratesOnlyTheOpeningAudio() = runTest(dispatcher) {
        val services = FakeServices()
        val viewModel = WhisperbookViewModel(services)
        viewModel.uiState.test {
            awaitItem()
            advanceUntilIdle()

            viewModel.cycleNarrationChunkSize()
            advanceUntilIdle()

            assertEquals(240, services.settings.value.narrationChunkChars)
            assertEquals(
                listOf("pause", "invalidate-queue:book-a:chapter-a", "regenerate:book-a:0"),
                services.events.takeLast(3),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun deletingSelectedBookPausesPlaybackCancelsPreparationAndRemovesIt() = runTest(dispatcher) {
        val services = FakeServices().apply {
            playback.value = cursor(isPlaying = true)
        }
        val viewModel = WhisperbookViewModel(services)
        viewModel.uiState.test {
            awaitItem()
            advanceUntilIdle()

            viewModel.deleteSelectedBook()
            advanceUntilIdle()

            assertEquals(emptyList<Book>(), services.books.value)
            assertEquals(listOf("pause", "cancel:book-a", "delete:book-a"), services.events.takeLast(3))
            assertEquals(null, expectMostRecentItem().selectedBook)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun playOrPauseStartsSelectedLocalChapterThenPausesActivePlayback() = runTest(dispatcher) {
        val services = FakeServices()
        val viewModel = WhisperbookViewModel(services)
        viewModel.uiState.test {
            awaitItem()
            advanceUntilIdle()
            viewModel.playOrPause()
            advanceUntilIdle()
            assertEquals("playBook:book-a:chapter-a", services.events.last())

            services.playback.value = cursor(isPlaying = true)
            advanceUntilIdle()
            viewModel.playOrPause()
            advanceUntilIdle()
            assertEquals("pause", services.events.last())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun accessibilitySettingsArePersistedWithoutDemoState() = runTest(dispatcher) {
        val services = FakeServices()
        val viewModel = WhisperbookViewModel(services)
        viewModel.setLargerText(true)
        viewModel.setAutoScroll(false)
        advanceUntilIdle()

        assertTrue(services.settings.value.largerText)
        assertFalse(services.settings.value.autoScroll)
    }

    @Test
    fun selectingTheBooksExistingLanguageHasNoSideEffects() = runTest(dispatcher) {
        val services = FakeServices()
        val viewModel = WhisperbookViewModel(services)
        advanceUntilIdle()

        viewModel.selectNarrationLanguage("en")
        advanceUntilIdle()

        assertTrue(services.events.none { it.startsWith("book-language:") })
    }

    @Test
    fun languageCannotBeChangedWithoutABrowsedBook() = runTest(dispatcher) {
        val services = FakeServices().apply { books.value = emptyList() }
        val viewModel = WhisperbookViewModel(services)
        viewModel.uiState.test {
            awaitItem()
            advanceUntilIdle()

            viewModel.downloadLanguagePack("fr")
            advanceUntilIdle()

            assertTrue("fr" !in services.settings.value.installedLanguagePackCodes)
            assertTrue(services.events.none { it.startsWith("book-language:") })
            assertEquals("Choose a book before changing its language", viewModel.uiState.value.errorMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun selectingAnotherChapterReplacesThePlaybackQueue() = runTest(dispatcher) {
        val services = FakeServices().apply {
            chapters.value = mapOf(
                "book-a" to listOf(
                    Chapter("chapter-a", "book-a", 0, "The Beginning"),
                    Chapter("chapter-b", "book-a", 1, "The Next Path"),
                ),
            )
        }
        val viewModel = WhisperbookViewModel(services)
        viewModel.uiState.test {
            awaitItem()
            advanceUntilIdle()

            viewModel.selectChapter("chapter-b")
            advanceUntilIdle()

            assertEquals("chapter-b", expectMostRecentItem().selectedChapter?.id)
            assertEquals("playBook:book-a:chapter-b", services.events.last())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun chapterTransportMovesToAdjacentChaptersAndStopsAtBookEdges() = runTest(dispatcher) {
        val services = FakeServices().apply {
            chapters.value = mapOf(
                "book-a" to listOf(
                    Chapter("chapter-a", "book-a", 0, "The Beginning"),
                    Chapter("chapter-b", "book-a", 1, "The Next Path"),
                    Chapter("chapter-c", "book-a", 2, "The Last Path"),
                ),
            )
        }
        val viewModel = WhisperbookViewModel(services)
        viewModel.uiState.test {
            awaitItem()
            advanceUntilIdle()

            viewModel.playPreviousChapter()
            advanceUntilIdle()
            assertTrue(services.events.none { it.startsWith("playBook:") })

            viewModel.playNextChapter()
            advanceUntilIdle()
            assertEquals("playBook:book-a:chapter-b", services.events.last())

            viewModel.playPreviousChapter()
            advanceUntilIdle()
            assertEquals("playBook:book-a:chapter-a", services.events.last())

            viewModel.selectChapter("chapter-c")
            advanceUntilIdle()
            val requestCountAtLastChapter = services.events.count { it.startsWith("playBook:") }
            viewModel.playNextChapter()
            advanceUntilIdle()

            assertEquals(
                requestCountAtLastChapter,
                services.events.count { it.startsWith("playBook:") },
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun newestChapterSelectionWinsWhileThePreviousChapterIsStillGenerating() = runTest(dispatcher) {
        val chapterBStarted = CompletableDeferred<Unit>()
        val chapterBCancelled = CompletableDeferred<Unit>()
        val services = FakeServices().apply {
            chapters.value = mapOf(
                "book-a" to listOf(
                    Chapter("chapter-a", "book-a", 0, "The Beginning"),
                    Chapter("chapter-b", "book-a", 1, "The Long Road"),
                    Chapter("chapter-c", "book-a", 2, "The New Path"),
                ),
            )
            playBookHandler = { _, chapterId ->
                if (chapterId == "chapter-b") {
                    chapterBStarted.complete(Unit)
                    try {
                        awaitCancellation()
                    } finally {
                        chapterBCancelled.complete(Unit)
                    }
                }
            }
        }
        val viewModel = WhisperbookViewModel(services)
        viewModel.uiState.test {
            awaitItem()
            advanceUntilIdle()

            viewModel.selectChapter("chapter-b")
            runCurrent()
            chapterBStarted.await()
            services.playback.value = cursor(isPlaying = true)
            runCurrent()
            assertEquals("chapter-b", expectMostRecentItem().selectedChapter?.id)

            viewModel.selectChapter("chapter-c")
            runCurrent()

            assertTrue(chapterBCancelled.isCompleted)
            assertEquals("chapter-c", expectMostRecentItem().selectedChapter?.id)
            assertEquals("chapter-c", viewModel.uiState.value.loadingChapterId)
            assertEquals(
                listOf("playBook:book-a:chapter-b", "playBook:book-a:chapter-c"),
                services.events.takeLast(2),
            )

            services.playback.value = cursor(isPlaying = true, chapterId = "chapter-c")
            runCurrent()
            assertEquals(null, expectMostRecentItem().loadingChapterId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun playbackCursorMovesSelectionWhenTheNextChapterStarts() = runTest(dispatcher) {
        val services = FakeServices().apply {
            chapters.value = mapOf(
                "book-a" to listOf(
                    Chapter("chapter-a", "book-a", 0, "The Beginning"),
                    Chapter("chapter-b", "book-a", 1, "The Next Path"),
                ),
            )
        }
        val viewModel = WhisperbookViewModel(services)
        viewModel.uiState.test {
            awaitItem()
            advanceUntilIdle()

            services.playback.value = cursor(isPlaying = true).copy(chapterId = "chapter-b")
            advanceUntilIdle()

            assertEquals("chapter-b", expectMostRecentItem().selectedChapter?.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun switchingBooksRestoresEachBooksChapterAndIgnoresForeignPlaybackTicks() = runTest(dispatcher) {
        val services = FakeServices().apply {
            books.value = listOf(
                book("book-a", currentChapterId = "a-2"),
                book("book-b", currentChapterId = "b-3"),
            )
            chapters.value = mapOf(
                "book-a" to listOf(
                    Chapter("a-1", "book-a", 0, "A Beginning"),
                    Chapter("a-2", "book-a", 1, "A Return"),
                ),
                "book-b" to listOf(
                    Chapter("b-1", "book-b", 0, "B Beginning"),
                    Chapter("b-2", "book-b", 1, "B Middle"),
                    Chapter("b-3", "book-b", 2, "B Return"),
                ),
            )
            characters.value = mapOf("book-a" to emptyList(), "book-b" to emptyList())
        }
        val viewModel = WhisperbookViewModel(services)
        viewModel.uiState.test {
            awaitItem()
            advanceUntilIdle()
            assertEquals("book-a", expectMostRecentItem().selectedBook?.id)
            assertEquals("a-2", viewModel.uiState.value.selectedChapter?.id)

            services.playback.value = cursor(
                isPlaying = true,
                bookId = "book-a",
                chapterId = "a-2",
                chapterPositionMs = 2_000L,
            )
            advanceUntilIdle()

            viewModel.selectBook("book-b")
            advanceUntilIdle()
            assertEquals("book-b", expectMostRecentItem().selectedBook?.id)
            assertEquals("b-3", viewModel.uiState.value.selectedChapter?.id)
            assertEquals(null, viewModel.uiState.value.playback)

            // The old book keeps ticking while its audio is active. That must not navigate back.
            services.playback.value = cursor(
                isPlaying = true,
                bookId = "book-a",
                chapterId = "a-2",
                chapterPositionMs = 2_250L,
            )
            advanceUntilIdle()
            assertEquals("book-b", viewModel.uiState.value.selectedBook?.id)
            assertEquals("b-3", viewModel.uiState.value.selectedChapter?.id)

            viewModel.playOrPause()
            advanceUntilIdle()
            assertEquals("playBook:book-b:b-3", services.events.last())

            services.playback.value = cursor(
                isPlaying = true,
                bookId = "book-b",
                chapterId = "b-3",
                chapterPositionMs = 9_000L,
            )
            advanceUntilIdle()
            viewModel.selectBook("book-a")
            advanceUntilIdle()
            assertEquals("a-2", viewModel.uiState.value.selectedChapter?.id)

            viewModel.playOrPause()
            advanceUntilIdle()
            assertEquals("playBook:book-a:a-2", services.events.last())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun unsavedChapterChoiceRemainsScopedToItsBookAcrossNavigation() = runTest(dispatcher) {
        val services = FakeServices().apply {
            books.value = listOf(
                book("book-a", currentChapterId = "a-1"),
                book("book-b", currentChapterId = "b-1"),
            )
            chapters.value = mapOf(
                "book-a" to listOf(
                    Chapter("a-1", "book-a", 0, "A Beginning"),
                    Chapter("a-2", "book-a", 1, "A End"),
                ),
                "book-b" to listOf(
                    Chapter("b-1", "book-b", 0, "B Beginning"),
                    Chapter("b-2", "book-b", 1, "B Middle"),
                    Chapter("b-3", "book-b", 2, "B End"),
                ),
            )
            characters.value = mapOf("book-a" to emptyList(), "book-b" to emptyList())
        }
        val viewModel = WhisperbookViewModel(services)
        viewModel.uiState.test {
            awaitItem()
            advanceUntilIdle()

            viewModel.selectBook("book-b")
            advanceUntilIdle()
            viewModel.selectChapter("b-2")
            advanceUntilIdle()
            assertEquals("playBook:book-b:b-2", services.events.last())

            viewModel.selectBook("book-a")
            advanceUntilIdle()
            viewModel.selectChapter("a-2")
            advanceUntilIdle()
            assertEquals("playBook:book-a:a-2", services.events.last())

            viewModel.selectBook("book-b")
            advanceUntilIdle()
            assertEquals("b-2", viewModel.uiState.value.selectedChapter?.id)

            viewModel.selectBook("book-a")
            advanceUntilIdle()
            assertEquals("a-2", viewModel.uiState.value.selectedChapter?.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun foreignPlaybackCannotSeekOrDriveTheSelectedBooksProgress() = runTest(dispatcher) {
        val services = FakeServices().apply {
            books.value = listOf(
                book("book-a", currentChapterId = "a-1").copy(progressFraction = 0.25f),
                book("book-b", currentChapterId = "b-1").copy(progressFraction = 0.75f),
            )
            chapters.value = mapOf(
                "book-a" to listOf(Chapter("a-1", "book-a", 0, "A Beginning")),
                "book-b" to listOf(Chapter("b-1", "book-b", 0, "B Beginning")),
            )
            characters.value = mapOf("book-a" to emptyList(), "book-b" to emptyList())
            playback.value = cursor(
                isPlaying = true,
                bookId = "book-a",
                chapterId = "a-1",
                chapterPositionMs = 900L,
            )
        }
        val viewModel = WhisperbookViewModel(services)
        viewModel.uiState.test {
            awaitItem()
            advanceUntilIdle()

            viewModel.selectBook("book-b")
            advanceUntilIdle()
            val selectedBook = expectMostRecentItem()
            assertEquals(null, selectedBook.playback)
            assertEquals(0.75f, selectedBook.chapterProgress)

            val eventCount = services.events.size
            viewModel.seekBy(15_000L)
            viewModel.seekToFraction(0.5f)
            viewModel.seekToPassage("b-passage")
            advanceUntilIdle()
            assertEquals(eventCount, services.events.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun storageUsageIsNotRescannedForEveryProgressCheckpoint() = runTest(dispatcher) {
        val services = FakeServices()
        val viewModel = WhisperbookViewModel(services)
        viewModel.uiState.test {
            awaitItem()
            advanceUntilIdle()
            val initialScans = services.storageScans

            services.books.value = listOf(
                book("book-a").copy(
                    preparation = PreparationState(
                        stage = PreparationStage.READING_CHAPTERS,
                        progressFraction = 0.1f,
                    ),
                ),
            )
            advanceUntilIdle()
            val scansAfterStageChange = services.storageScans

            services.books.value = listOf(
                book("book-a").copy(
                    preparation = PreparationState(
                        stage = PreparationStage.READING_CHAPTERS,
                        progressFraction = 0.8f,
                    ),
                ),
            )
            advanceUntilIdle()

            assertEquals(initialScans + 1, scansAfterStageChange)
            assertEquals(scansAfterStageChange, services.storageScans)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun progressiveAudioPreparationReportsPassageProgressWithoutBlockingPlayback() = runTest(dispatcher) {
        val services = FakeServices()
        val viewModel = WhisperbookViewModel(services)
        viewModel.uiState.test {
            awaitItem()
            advanceUntilIdle()

            services.audioProgress.value = PlaybackPreparationProgress("book-a", "chapter-a", 0, 12)
            advanceUntilIdle()
            val preparing = expectMostRecentItem()
            assertTrue(preparing.isBusy)
            assertTrue(preparing.statusMessage.orEmpty().contains("passage 1 of 12"))

            services.playback.value = cursor(isPlaying = true)
            services.audioProgress.value = PlaybackPreparationProgress("book-a", "chapter-a", 1, 12)
            advanceUntilIdle()
            val playing = expectMostRecentItem()
            assertTrue(playing.statusMessage.orEmpty().contains("Playing Chapter 1 now"))
            assertTrue(playing.statusMessage.orEmpty().contains("passage 2 of 12"))

            services.audioProgress.value = null
            advanceUntilIdle()
            assertFalse(expectMostRecentItem().isBusy)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun cursor(
        isPlaying: Boolean,
        bookId: String = "book-a",
        chapterId: String = "chapter-a",
        chapterPositionMs: Long = 100L,
    ) = PlaybackCursor(
        bookId = bookId,
        chapterId = chapterId,
        passageId = "passage-a",
        segmentId = "segment-a",
        segmentPositionMs = 100,
        chapterPositionMs = chapterPositionMs,
        chapterDurationMs = 1_000,
        isPlaying = isPlaying,
        speed = 1f,
    )
}

private class FakeServices : WhisperbookServices {
    val events = mutableListOf<String>()
    val books = MutableStateFlow(listOf(book("book-a")))
    val chapters = MutableStateFlow(
        mapOf("book-a" to listOf(Chapter("chapter-a", "book-a", 0, "The Beginning"))),
    )
    val characters = MutableStateFlow(
        mapOf(
            "book-a" to listOf(
                StoryCharacter(
                    "narrator",
                    "book-a",
                    "Narrator",
                    emptySet(),
                    CharacterColorRole.NARRATOR,
                    1,
                ),
            ),
        ),
    )
    val assignments = MutableStateFlow<Map<String, CharacterVoiceAssignment>>(
        mapOf("narrator" to CharacterVoiceAssignment("narrator", "bella", "test-model", 1f)),
    )
    val retainedChanges = mutableListOf<RevertibleVoiceChange>()
    val settings = MutableStateFlow(AppSettings())
    val playback = MutableStateFlow<PlaybackCursor?>(null)
    val audioProgress = MutableStateFlow<PlaybackPreparationProgress?>(null)
    var playBookHandler: suspend (bookId: String, chapterId: String?) -> Unit = { _, _ -> }
    var storageScans = 0

    override val availableVoices = listOf(
        VoiceDescriptor("bella", "Bella", 0),
        VoiceDescriptor("jasper", "Jasper", 1),
    )
    override val ttsModelVersion = "test-model"

    override val bookMp3Exporter = object : BookMp3Exporter {
        override suspend fun export(
            bookId: String,
            destination: Uri,
            onProgress: (BookMp3ExportProgress) -> Unit,
        ): BookMp3ExportResult {
            onProgress(
                BookMp3ExportProgress(
                    stage = BookMp3ExportStage.PREPARING_AUDIO,
                    progressFraction = 0.4f,
                    chapterNumber = 1,
                    totalChapters = 1,
                ),
            )
            onProgress(
                BookMp3ExportProgress(
                    stage = BookMp3ExportStage.SAVING,
                    progressFraction = 1f,
                    chapterNumber = 1,
                    totalChapters = 1,
                ),
            )
            events += "export:$bookId"
            return BookMp3ExportResult(chapterCount = 1, durationMs = 1_000L, bytesWritten = 4_096L)
        }
    }

    override val voicePreviewPlayer = object : VoicePreviewPlayer {
        override suspend fun play(
            text: String,
            voice: VoiceDescriptor,
            speed: Float,
            languageCode: String,
        ): Result<Unit> {
            events += "preview:${voice.id}:$text:$speed"
            return Result.success(Unit)
        }
        override fun stop() = Unit
        override fun close() = Unit
    }

    override val libraryRepository = object : LibraryRepository {
        override fun observeBooks(): Flow<List<Book>> = books
        override fun observeBook(bookId: String): Flow<Book?> = books.map { all -> all.firstOrNull { it.id == bookId } }
        override fun observeChapters(bookId: String): Flow<List<Chapter>> = chapters.map { it[bookId].orEmpty() }
        override fun observeCharacters(bookId: String): Flow<List<StoryCharacter>> = characters.map { it[bookId].orEmpty() }
        override suspend fun importBook(
            uri: Uri,
            narrationLanguageCode: String,
        ): Result<String> = Result.failure(UnsupportedOperationException())
        override suspend fun updateVoiceAssignment(assignment: CharacterVoiceAssignment) {
            events += "assign:${assignment.characterId}:${assignment.voiceId}"
            assignments.value = assignments.value + (assignment.characterId to assignment)
        }
        override suspend fun deleteBook(bookId: String) {
            events += "delete:$bookId"
            books.value = books.value.filterNot { it.id == bookId }
        }
    }

    override val settingsRepository = object : SettingsRepository {
        override val settings: Flow<AppSettings> = this@FakeServices.settings
        override suspend fun update(transform: (AppSettings) -> AppSettings) {
            this@FakeServices.settings.value = transform(this@FakeServices.settings.value)
        }
    }

    override val preparationCoordinator = object : PreparationCoordinator {
        override fun enqueue(bookId: String) { events += "enqueue:$bookId" }
        override fun cancel(bookId: String) { events += "cancel:$bookId" }
        override fun regenerateAudio(bookId: String, fromChapterOrdinal: Int) {
            events += "regenerate:$bookId:$fromChapterOrdinal"
        }
        override fun observe(bookId: String): Flow<PreparationState> = MutableStateFlow(PreparationState.Ready)
    }

    override val playbackGateway = object : PlaybackGateway {
        override val cursor: Flow<PlaybackCursor?> = playback
        override val preparationProgress: Flow<PlaybackPreparationProgress?> = audioProgress
        override suspend fun playBook(bookId: String, chapterId: String?) {
            events += "playBook:$bookId:$chapterId"
            playBookHandler(bookId, chapterId)
        }
        override suspend fun play() { events += "play" }
        override suspend fun pause() { events += "pause" }
        override suspend fun seekBy(deltaMs: Long) { events += "seek:$deltaMs" }
        override suspend fun seekToPassage(passageId: String) { events += "passage:$passageId" }
        override suspend fun setSpeed(speed: Float) { events += "speed:$speed" }
        override suspend fun setSleepTimer(minutes: Int?) { events += "sleep:$minutes" }
        override suspend fun invalidateQueuedChapters(bookId: String, chapterIds: Set<String>) {
            events += "invalidate-queue:$bookId:${chapterIds.sorted().joinToString(",")}"
        }
    }

    override val audioSegmentStore = object : AudioSegmentStore {
        override suspend fun find(cacheKey: String): AudioSegment? = null
        override suspend fun write(request: SynthesisRequest, result: SynthesisResult): AudioSegment =
            error("not needed")
        override suspend fun invalidateForCharacter(characterId: String) {
            events += "invalidate:$characterId"
        }
        override suspend fun trimTo(limitBytes: Long) { events += "trim:$limitBytes" }
    }

    override fun observeChapterVoiceAssignments(
        bookId: String,
        chapterId: String,
    ): Flow<Map<String, CharacterVoiceAssignment>> = assignments

    override suspend fun applyNarrationLanguageToBook(bookId: String, languageCode: String) {
        books.value = books.value.map { book ->
            if (book.id == bookId) book.copy(
                narrationLanguageCode = languageCode,
                narrationProfileRevision = book.narrationProfileRevision + 1,
            ) else book
        }
        events += "book-language:$bookId:$languageCode"
    }

    override suspend fun applySpeakerCorrection(
        bookId: String,
        passageId: String,
        speakerId: String,
        scope: SpeakerCorrectionScope,
    ): Int {
        val bookChapters = chapters.value[bookId].orEmpty()
        val source = bookChapters.asSequence().flatMap { it.passages.asSequence() }
            .first { it.id == passageId }
        val sourceKey = speakerPhraseMatchKey(source.text)
        var corrected = 0
        chapters.value = chapters.value + (
            bookId to bookChapters.map { chapter ->
                chapter.copy(
                    passages = chapter.passages.map { passage ->
                        val matches = when (scope) {
                            SpeakerCorrectionScope.THIS_PASSAGE -> passage.id == passageId
                            SpeakerCorrectionScope.MATCHING_PHRASES ->
                                passage.speakerId == source.speakerId &&
                                    sourceKey.isNotBlank() &&
                                    speakerPhraseMatchKey(passage.text) == sourceKey
                        }
                        if (matches) {
                            corrected += 1
                            passage.copy(speakerId = speakerId, confidence = 1f, attributionRule = "manual")
                        } else {
                            passage
                        }
                    },
                )
            }
        )
        events += "speaker-correction:$bookId:$passageId:$speakerId:$scope:$corrected"
        return corrected
    }

    override suspend fun applyVoiceRegeneration(request: VoiceRegenerationRequest): RevertibleVoiceChange {
        val previous = assignments.value.getValue(request.characterId)
        assignments.value = assignments.value + (request.characterId to request.assignment)
        val change = RevertibleVoiceChange(
            generationId = "retained-${retainedChanges.size + 1}",
            bookId = request.bookId,
            characterId = request.characterId,
            previousAssignment = previous,
            replacementVoiceId = request.assignment.voiceId,
            expiresAtEpochMs = 86_400_000L,
        )
        retainedChanges += change
        events += "voice-regeneration:${request.bookId}:${request.characterId}:${request.assignment.voiceId}:" +
            "${request.scope}:${request.fromChapterOrdinal}"
        return change
    }

    override suspend fun retainedVoiceChanges(
        bookId: String,
        characterIds: List<String>,
    ): List<RevertibleVoiceChange> = retainedChanges.filter {
        it.bookId == bookId && it.characterId in characterIds
    }

    override suspend fun revertVoiceChange(change: RevertibleVoiceChange): Boolean {
        if (!retainedChanges.remove(change)) return false
        assignments.value = assignments.value + (change.characterId to change.previousAssignment)
        events += "revert-voice:${change.characterId}:${change.previousAssignment.voiceId}"
        return true
    }

    override suspend fun deletePersistedAudioForCharacter(characterId: String) {
        events += "delete-audio:$characterId"
    }

    override suspend fun localStorageBytes(): Long {
        storageScans += 1
        return 0L
    }
}

private fun book(id: String, currentChapterId: String = "chapter-a") = Book(
    id = id,
    title = "The Moonlit Wood",
    author = "E. Wren",
    format = BookFormat.EPUB,
    sourceUri = null,
    privateSourcePath = null,
    coverPath = null,
    preparation = PreparationState.Ready,
    currentChapterId = currentChapterId,
    currentPassageId = null,
    progressFraction = 0f,
    lastOpenedAtEpochMs = 1L,
)
