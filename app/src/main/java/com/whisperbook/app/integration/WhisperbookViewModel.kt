package com.whisperbook.app.integration

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.whisperbook.app.domain.model.AppSettings
import com.whisperbook.app.domain.model.Book
import com.whisperbook.app.domain.model.Chapter
import com.whisperbook.app.domain.model.CharacterVoiceAssignment
import com.whisperbook.app.domain.model.PlaybackCursor
import com.whisperbook.app.domain.model.PreparationState
import com.whisperbook.app.domain.model.StoryCharacter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class WhisperbookViewModel(
    private val services: WhisperbookServices,
) : ViewModel() {
    private val selectedBookId = MutableStateFlow<String?>(null)
    private val selectedChapterId = MutableStateFlow<String?>(null)
    private val operation = MutableStateFlow(OperationState())

    private val books = services.libraryRepository.observeBooks()
    private val selectedBook: Flow<Book?> = selectedBookId.flatMapLatest { bookId ->
        if (bookId == null) flowOf(null) else services.libraryRepository.observeBook(bookId)
    }
    private val chapters: Flow<List<Chapter>> = selectedBookId.flatMapLatest { bookId ->
        if (bookId == null) flowOf(emptyList()) else services.libraryRepository.observeChapters(bookId)
    }
    private val selectedChapter: Flow<Chapter?> = combine(chapters, selectedChapterId) { all, chapterId ->
        chapterId?.let { id -> all.firstOrNull { it.id == id } } ?: all.firstOrNull()
    }
    private val characters: Flow<List<StoryCharacter>> = selectedBookId.flatMapLatest { bookId ->
        if (bookId == null) flowOf(emptyList()) else services.libraryRepository.observeCharacters(bookId)
    }
    private val preparation: Flow<PreparationState?> = selectedBookId.flatMapLatest { bookId ->
        if (bookId == null) flowOf(null) else services.preparationCoordinator.observe(bookId)
    }
    private val voiceAssignments: Flow<Map<String, CharacterVoiceAssignment>> = characters
        .flatMapLatest { cast -> services.observeVoiceAssignments(cast.map(StoryCharacter::id)) }
    private val storageBytes: Flow<Long> = combine(books, preparation) { _, _ ->
        runCatching { services.localStorageBytes() }.getOrDefault(0L)
    }.distinctUntilChanged()

    private val libraryState = combine(
        books,
        selectedBook,
        chapters,
        selectedChapter,
        characters,
    ) { allBooks, book, allChapters, chapter, cast ->
        LibraryState(allBooks, book, allChapters, chapter, cast)
    }
    private val sessionState = combine(
        preparation,
        services.settingsRepository.settings,
        services.playbackGateway.cursor,
        voiceAssignments,
        operation,
    ) { prep, settings, playback, assignments, operationState ->
        SessionState(prep, settings, playback, assignments, operationState)
    }

    val uiState: StateFlow<WhisperbookUiSnapshot> = combine(libraryState, sessionState, storageBytes) { library, session, bytes ->
        WhisperbookUiSnapshot(
            books = library.books,
            selectedBook = library.selectedBook,
            chapters = library.chapters,
            selectedChapter = library.selectedChapter,
            characters = library.characters,
            voiceAssignments = session.voiceAssignments,
            voices = services.availableVoices,
            preparation = session.preparation,
            settings = session.settings,
            playback = session.playback,
            isBusy = session.operation.isBusy,
            statusMessage = session.operation.statusMessage,
            errorMessage = session.operation.errorMessage,
            localStorageBytes = bytes,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = WhisperbookUiSnapshot(voices = services.availableVoices),
    )

    init {
        viewModelScope.launch {
            books.collect { currentBooks ->
                val selected = selectedBookId.value
                if (selected == null || currentBooks.none { it.id == selected }) {
                    val next = currentBooks.firstOrNull()
                    selectedBookId.value = next?.id
                    selectedChapterId.value = next?.currentChapterId
                }
            }
        }
        viewModelScope.launch {
            chapters.collect { currentChapters ->
                val selected = selectedChapterId.value
                if (selected == null || currentChapters.none { it.id == selected }) {
                    selectedChapterId.value = currentChapters.firstOrNull()?.id
                }
            }
        }
        viewModelScope.launch {
            services.playbackGateway.cursor.collect { cursor ->
                cursor ?: return@collect
                if (selectedBookId.value != cursor.bookId) selectedBookId.value = cursor.bookId
                if (selectedChapterId.value != cursor.chapterId) selectedChapterId.value = cursor.chapterId
            }
        }
    }

    fun selectBook(bookId: String) {
        if (bookId.isBlank() || selectedBookId.value == bookId) return
        selectedBookId.value = bookId
        selectedChapterId.value = null
    }

    fun selectChapter(chapterId: String) {
        if (chapterId.isBlank() || chapterId == selectedChapterId.value) return
        val bookId = selectedBookId.value ?: return
        if (uiState.value.chapters.none { it.id == chapterId }) return
        selectedChapterId.value = chapterId
        launchOperation("Opening chapter") {
            services.playbackGateway.playBook(bookId, chapterId)
            null
        }
    }

    fun importBook(uri: Uri) = launchOperation("Importing your book") {
        services.libraryRepository.importBook(uri).getOrThrow().also { bookId ->
            selectedBookId.value = bookId
            selectedChapterId.value = null
            services.preparationCoordinator.enqueue(bookId)
        }
        "Book imported. Its voices are being prepared on this device."
    }

    fun retryPreparation() {
        selectedBookId.value?.let(services.preparationCoordinator::enqueue)
    }

    fun cancelPreparation() {
        selectedBookId.value?.let(services.preparationCoordinator::cancel)
    }

    fun deleteSelectedBook() = launchOperation("Removing book") {
        val bookId = selectedBookId.value ?: return@launchOperation null
        services.preparationCoordinator.cancel(bookId)
        services.libraryRepository.deleteBook(bookId)
        selectedBookId.value = null
        selectedChapterId.value = null
        "Book removed from this device."
    }

    fun playOrPause() = launchOperation {
        val cursor = uiState.value.playback
        val selectedBook = uiState.value.selectedBook ?: error("Choose a book to listen to")
        when {
            cursor?.bookId != selectedBook.id -> services.playbackGateway.playBook(
                selectedBook.id,
                uiState.value.selectedChapter?.id,
            )
            cursor.isPlaying -> services.playbackGateway.pause()
            else -> services.playbackGateway.play()
        }
        null
    }

    fun playSelectedChapter() = launchOperation("Preparing this chapter") {
        val book = uiState.value.selectedBook ?: error("Choose a book to listen to")
        services.playbackGateway.playBook(book.id, uiState.value.selectedChapter?.id)
        null
    }

    fun previewCharacter(characterId: String) = launchOperation("Preparing voice preview") {
        val book = uiState.value.selectedBook ?: error("Choose a book first")
        val chapter = uiState.value.selectedChapter ?: error("Choose a chapter first")
        val passage = chapter.passages.firstOrNull { it.speakerId == characterId }
            ?: error("This character has no passage in the selected chapter")
        services.playbackGateway.playBook(book.id, chapter.id)
        services.playbackGateway.seekToPassage(passage.id)
        "Playing ${uiState.value.characters.firstOrNull { it.id == characterId }?.displayName ?: "voice"}"
    }

    fun seekBy(deltaMs: Long) = launchOperation {
        services.playbackGateway.seekBy(deltaMs)
        null
    }

    fun seekToFraction(fraction: Float) = launchOperation {
        val playback = uiState.value.playback ?: return@launchOperation null
        val target = (playback.chapterDurationMs * fraction.coerceIn(0f, 1f)).toLong()
        services.playbackGateway.seekBy(target - playback.chapterPositionMs)
        null
    }

    fun seekToPassage(passageId: String) = launchOperation {
        services.playbackGateway.seekToPassage(passageId)
        null
    }

    fun cycleSpeed() {
        val current = uiState.value.settings.speakingSpeed
        val speeds = listOf(0.8f, 1f, 1.2f, 1.5f, 2f)
        val index = speeds.indexOfFirst { kotlin.math.abs(it - current) < 0.01f }.coerceAtLeast(0)
        setSpeakingSpeed(speeds[(index + 1) % speeds.size])
    }

    fun setSpeakingSpeed(speed: Float) = launchOperation {
        val normalized = speed.coerceIn(0.5f, 2f)
        services.settingsRepository.update { it.copy(speakingSpeed = normalized) }
        services.playbackGateway.setSpeed(normalized)
        null
    }

    fun cycleDefaultNarratorVoice() {
        val voices = services.availableVoices
        if (voices.isEmpty()) return
        val currentId = uiState.value.settings.defaultNarratorVoiceId
        val index = voices.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
        val next = voices[(index + 1) % voices.size]
        updateSettings { it.copy(defaultNarratorVoiceId = next.id) }
    }

    fun cycleSleepTimer() {
        val current = uiState.value.settings.sleepTimerMinutes
        val timers = listOf(15, 30, 45, 60, 0)
        val index = timers.indexOf(current).coerceAtLeast(0)
        setSleepTimer(timers[(index + 1) % timers.size])
    }

    fun setSleepTimer(minutes: Int) = launchOperation {
        val normalized = minutes.coerceIn(0, 24 * 60)
        services.settingsRepository.update { it.copy(sleepTimerMinutes = normalized) }
        services.playbackGateway.setSleepTimer(normalized.takeIf { it > 0 })
        null
    }

    fun assignVoice(characterId: String, voiceId: String) = launchOperation("Updating the cast") {
        val voice = services.availableVoices.firstOrNull { it.id == voiceId }
            ?: error("That embedded voice is unavailable")
        val speed = uiState.value.settings.speakingSpeed
        services.audioSegmentStore.invalidateForCharacter(characterId)
        services.deletePersistedAudioForCharacter(characterId)
        services.libraryRepository.updateVoiceAssignment(
            CharacterVoiceAssignment(characterId, voice.id, services.ttsModelVersion, speed),
        )
        "${voice.displayName} is ready for the next passage."
    }

    fun cycleVoice(characterId: String) {
        val voices = services.availableVoices
        if (voices.isEmpty()) return
        val currentId = uiState.value.voiceAssignments[characterId]?.voiceId
        val index = voices.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
        assignVoice(characterId, voices[(index + 1) % voices.size].id)
    }

    fun completeOnboarding() = updateSettings { it.copy(onboardingComplete = true) }
    fun setAutoScroll(enabled: Boolean) = updateSettings { it.copy(autoScroll = enabled) }
    fun setKeepScreenAwake(enabled: Boolean) = updateSettings { it.copy(keepScreenAwake = enabled) }
    fun setLargerText(enabled: Boolean) = updateSettings { it.copy(largerText = enabled) }

    fun setAudioCacheLimit(bytes: Long) = launchOperation {
        services.settingsRepository.update { it.copy(audioCacheLimitBytes = bytes) }
        services.audioSegmentStore.trimTo(bytes)
        null
    }

    fun clearMessage() {
        operation.value = OperationState()
    }

    private fun updateSettings(transform: (AppSettings) -> AppSettings) = launchOperation {
        services.settingsRepository.update(transform)
        null
    }

    private fun launchOperation(
        status: String? = null,
        block: suspend () -> String?,
    ) = viewModelScope.launch {
        operation.value = OperationState(isBusy = true, statusMessage = status)
        try {
            operation.value = OperationState(statusMessage = block())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            operation.value = OperationState(
                errorMessage = failure.message?.trim()?.takeIf(String::isNotBlank)
                    ?: "The local operation could not finish.",
            )
        }
    }

    class Factory(private val services: WhisperbookServices) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(WhisperbookViewModel::class.java))
            return WhisperbookViewModel(services) as T
        }
    }
}

private data class LibraryState(
    val books: List<Book>,
    val selectedBook: Book?,
    val chapters: List<Chapter>,
    val selectedChapter: Chapter?,
    val characters: List<StoryCharacter>,
)

private data class SessionState(
    val preparation: PreparationState?,
    val settings: AppSettings,
    val playback: PlaybackCursor?,
    val voiceAssignments: Map<String, CharacterVoiceAssignment>,
    val operation: OperationState,
)

private data class OperationState(
    val isBusy: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
)
