package com.whisperbook.app.integration

import android.net.Uri
import app.cash.turbine.test
import com.whisperbook.app.domain.AudioSegmentStore
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
import com.whisperbook.app.domain.model.Chapter
import com.whisperbook.app.domain.model.CharacterColorRole
import com.whisperbook.app.domain.model.CharacterVoiceAssignment
import com.whisperbook.app.domain.model.PlaybackCursor
import com.whisperbook.app.domain.model.PlaybackPreparationProgress
import com.whisperbook.app.domain.model.PreparationStage
import com.whisperbook.app.domain.model.PreparationState
import com.whisperbook.app.domain.model.RevertibleVoiceChange
import com.whisperbook.app.domain.model.StoryCharacter
import com.whisperbook.app.domain.model.VoiceDescriptor
import com.whisperbook.app.domain.model.VoiceRegenerationRequest
import com.whisperbook.app.domain.model.VoiceRegenerationScope
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
    fun changingVoiceFromNextChapterUsesTheChapterAfterTheCurrentSelection() = runTest(dispatcher) {
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
            viewModel.assignVoice("narrator", "jasper", VoiceRegenerationScope.FROM_NEXT_CHAPTER)
            advanceUntilIdle()

            assertEquals(
                "voice-regeneration:book-a:narrator:jasper:FROM_NEXT_CHAPTER:2",
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
    fun defaultNarratorCyclesThroughEmbeddedVoicesWithoutRequiringABookCharacter() = runTest(dispatcher) {
        val services = FakeServices()
        val viewModel = WhisperbookViewModel(services)

        viewModel.cycleDefaultNarratorVoice()
        advanceUntilIdle()

        assertEquals("jasper", services.settings.value.defaultNarratorVoiceId)
    }

    @Test
    fun defaultNarratorCanBeSelectedDirectlyFromTheVoiceList() = runTest(dispatcher) {
        val services = FakeServices()
        val viewModel = WhisperbookViewModel(services)

        viewModel.chooseDefaultNarratorVoice("jasper")
        advanceUntilIdle()

        assertEquals("jasper", services.settings.value.defaultNarratorVoiceId)
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

    private fun cursor(isPlaying: Boolean, chapterId: String = "chapter-a") = PlaybackCursor(
        bookId = "book-a",
        chapterId = chapterId,
        passageId = "passage-a",
        segmentId = "segment-a",
        segmentPositionMs = 100,
        chapterPositionMs = 100,
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

    override val voicePreviewPlayer = object : VoicePreviewPlayer {
        override suspend fun play(text: String, voice: VoiceDescriptor, speed: Float): Result<Unit> {
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
        override suspend fun importBook(uri: Uri): Result<String> = Result.failure(UnsupportedOperationException())
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

    override fun observeVoiceAssignments(
        characterIds: List<String>,
    ): Flow<Map<String, CharacterVoiceAssignment>> = assignments.map { all -> all.filterKeys(characterIds::contains) }

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

private fun book(id: String) = Book(
    id = id,
    title = "The Moonlit Wood",
    author = "E. Wren",
    format = BookFormat.EPUB,
    sourceUri = null,
    privateSourcePath = null,
    coverPath = null,
    preparation = PreparationState.Ready,
    currentChapterId = "chapter-a",
    currentPassageId = null,
    progressFraction = 0f,
    lastOpenedAtEpochMs = 1L,
)
