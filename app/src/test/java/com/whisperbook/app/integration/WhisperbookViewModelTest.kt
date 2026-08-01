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
import com.whisperbook.app.domain.model.AppSettings
import com.whisperbook.app.domain.model.AudioSegment
import com.whisperbook.app.domain.model.Book
import com.whisperbook.app.domain.model.BookFormat
import com.whisperbook.app.domain.model.Chapter
import com.whisperbook.app.domain.model.CharacterColorRole
import com.whisperbook.app.domain.model.CharacterVoiceAssignment
import com.whisperbook.app.domain.model.PlaybackCursor
import com.whisperbook.app.domain.model.PreparationState
import com.whisperbook.app.domain.model.StoryCharacter
import com.whisperbook.app.domain.model.VoiceDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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
    fun changingVoiceInvalidatesCharacterAudioBeforePersistingAssignment() = runTest(dispatcher) {
        val services = FakeServices()
        val viewModel = WhisperbookViewModel(services)
        viewModel.uiState.test {
            awaitItem()
            advanceUntilIdle()
            viewModel.assignVoice("narrator", "jasper")
            advanceUntilIdle()

            assertEquals(
                listOf("invalidate:narrator", "delete-audio:narrator", "assign:narrator:jasper"),
                services.events,
            )
            assertEquals("jasper", services.assignments.value["narrator"]?.voiceId)
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

    private fun cursor(isPlaying: Boolean) = PlaybackCursor(
        bookId = "book-a",
        chapterId = "chapter-a",
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
    val assignments = MutableStateFlow<Map<String, CharacterVoiceAssignment>>(emptyMap())
    val settings = MutableStateFlow(AppSettings())
    val playback = MutableStateFlow<PlaybackCursor?>(null)

    override val availableVoices = listOf(
        VoiceDescriptor("bella", "Bella", 0),
        VoiceDescriptor("jasper", "Jasper", 1),
    )
    override val ttsModelVersion = "test-model"

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
        override fun observe(bookId: String): Flow<PreparationState> = MutableStateFlow(PreparationState.Ready)
    }

    override val playbackGateway = object : PlaybackGateway {
        override val cursor: Flow<PlaybackCursor?> = playback
        override suspend fun playBook(bookId: String, chapterId: String?) {
            events += "playBook:$bookId:$chapterId"
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

    override suspend fun deletePersistedAudioForCharacter(characterId: String) {
        events += "delete-audio:$characterId"
    }

    override suspend fun localStorageBytes(): Long = 0L
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
