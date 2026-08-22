package com.whisperbook.app.integration

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.whisperbook.app.domain.NarrationTextChunker
import com.whisperbook.app.domain.model.AppSettings
import com.whisperbook.app.domain.model.BookMp3ExportProgress
import com.whisperbook.app.domain.model.BookMp3ExportStage
import com.whisperbook.app.domain.model.NarrationLanguage
import com.whisperbook.app.domain.model.Book
import com.whisperbook.app.domain.model.Chapter
import com.whisperbook.app.domain.model.CharacterVoiceAssignment
import com.whisperbook.app.domain.model.PlaybackCursor
import com.whisperbook.app.domain.model.PlaybackPreparationProgress
import com.whisperbook.app.domain.model.PreparationState
import com.whisperbook.app.domain.model.RevertibleVoiceChange
import com.whisperbook.app.domain.model.StoryCharacter
import com.whisperbook.app.domain.model.SpeakerCorrectionScope
import com.whisperbook.app.domain.model.VoiceRegenerationRequest
import com.whisperbook.app.domain.model.VoiceRegenerationScope
import com.whisperbook.app.diagnostics.BetaDiagnostics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class WhisperbookViewModel(
    private val services: WhisperbookServices,
) : ViewModel() {
    private val selectedBookId = MutableStateFlow<String?>(null)
    private val selectedChapterId = MutableStateFlow<String?>(null)
    private val loadingChapterId = MutableStateFlow<String?>(null)
    private val operation = MutableStateFlow(OperationState())
    private val storageRefreshVersion = MutableStateFlow(0L)
    private val voiceRetentionRefreshVersion = MutableStateFlow(0L)
    private var voicePreviewJob: Job? = null
    private var bookExportJob: Job? = null
    private var chapterSelectionJob: Job? = null
    private var chapterSelectionRequest = 0L
    private val selectedChapterIdsByBook = mutableMapOf<String, String>()

    private val books = services.libraryRepository.observeBooks().stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList(),
    )
    private val selectedBook: Flow<Book?> = combine(books, selectedBookId) { allBooks, bookId ->
        bookId?.let { id -> allBooks.firstOrNull { it.id == id } }
    }.distinctUntilChanged()
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
    private val voiceAssignments: Flow<Map<String, CharacterVoiceAssignment>> = combine(
        selectedBookId,
        selectedChapterId,
    ) { bookId, chapterId -> bookId to chapterId }
        .flatMapLatest { (bookId, chapterId) ->
            if (bookId == null || chapterId == null) {
                flowOf(emptyMap())
            } else {
                services.observeChapterVoiceAssignments(bookId, chapterId)
            }
        }
    private val revertibleVoiceChange: Flow<RevertibleVoiceChange?> = combine(
        selectedBookId,
        characters,
        voiceRetentionRefreshVersion,
    ) { bookId, cast, revision ->
        VoiceRetentionLookup(bookId, cast.map(StoryCharacter::id), revision)
    }
        .mapLatest { lookup ->
            val bookId = lookup.bookId ?: return@mapLatest null
            services.retainedVoiceChanges(bookId, lookup.characterIds)
                .maxByOrNull(RevertibleVoiceChange::expiresAtEpochMs)
        }
        .distinctUntilChanged()
    private val storageBytes: Flow<Long> = combine(books, storageRefreshVersion) { allBooks, revision ->
        StorageRefreshRequest(
            books = allBooks.map { book ->
                BookStorageState(book.id, book.privateSourcePath, book.preparation.stage)
            },
            revision = revision,
        )
    }
        .distinctUntilChanged()
        .mapLatest { runCatching { services.localStorageBytes() }.getOrDefault(0L) }
        .distinctUntilChanged()

    private val libraryState = combine(
        books,
        selectedBook,
        chapters,
        selectedChapter,
        characters,
    ) { allBooks, book, allChapters, chapter, cast ->
        LibraryState(allBooks, book, allChapters, chapter, cast)
    }
    private val operationState = combine(
        operation,
        loadingChapterId,
        services.playbackGateway.preparationProgress,
    ) { operationState, chapterId, audioProgress ->
        PendingOperationState(operationState, chapterId, audioProgress)
    }
    private val voiceState = combine(voiceAssignments, revertibleVoiceChange) { assignments, change ->
        VoiceState(assignments, change)
    }
    private val sessionState = combine(
        preparation,
        services.settingsRepository.settings,
        services.playbackGateway.cursor,
        voiceState,
        operationState,
    ) { prep, settings, playback, voice, pendingOperation ->
        SessionState(prep, settings, playback, voice.assignments, voice.revertibleChange, pendingOperation)
    }

    val uiState: StateFlow<WhisperbookUiSnapshot> = combine(libraryState, sessionState, storageBytes) { library, session, bytes ->
        val selectedBookId = library.selectedBook?.id
        val selectedPlayback = session.playback?.takeIf { it.bookId == selectedBookId }
        val selectedAudioProgress = session.pendingOperation.audioProgress
            ?.takeIf { it.bookId == selectedBookId }
        val foregroundOperation = session.pendingOperation.operation
        val selectedBookExport = foregroundOperation.takeIf {
            it.kind == OperationKind.BOOK_MP3_EXPORT && it.targetBookId == selectedBookId
        }
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
            // The Media3 service has one active queue, while every book has its own persisted
            // checkpoint. Never project another book's live cursor onto the selected book's UI.
            playback = selectedPlayback,
            loadingChapterId = session.pendingOperation.chapterId,
            isBusy = foregroundOperation.isBusy ||
                selectedAudioProgress != null,
            statusMessage = selectedAudioProgress?.let { progress ->
                progress.statusMessage(library.chapters, selectedPlayback)
            } ?: foregroundOperation.statusMessage,
            backgroundProgressFraction = selectedAudioProgress?.progressFraction
                ?: foregroundOperation.progressFraction,
            errorMessage = foregroundOperation.errorMessage,
            isExportingBook = selectedBookExport?.isBusy == true,
            bookExportMessage = selectedBookExport?.let { it.errorMessage ?: it.statusMessage },
            localStorageBytes = bytes,
            canRevertVoiceChange = session.revertibleVoiceChange != null,
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
                    selectedChapterId.value = next?.let(::rememberedChapterId)
                }
            }
        }
        viewModelScope.launch {
            chapters.collect { currentChapters ->
                val selected = selectedChapterId.value
                if (selected == null || currentChapters.none { it.id == selected }) {
                    selectedChapterId.value = currentChapters.firstOrNull()?.id.also { chapterId ->
                        val bookId = selectedBookId.value
                        if (bookId != null && chapterId != null) {
                            selectedChapterIdsByBook[bookId] = chapterId
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            services.playbackGateway.cursor.collect { cursor ->
                cursor ?: return@collect
                // Playback may keep publishing while the user browses another book. It must not
                // pull navigation back to the active queue or replace that book's chapter choice.
                if (cursor.bookId != selectedBookId.value) return@collect
                loadingChapterId.value?.let { pendingChapterId ->
                    if (cursor.chapterId != pendingChapterId) {
                        return@collect
                    }
                    loadingChapterId.value = null
                }
                selectedChapterIdsByBook[cursor.bookId] = cursor.chapterId
                if (selectedChapterId.value != cursor.chapterId) {
                    selectedChapterId.value = cursor.chapterId
                }
            }
        }
    }

    fun selectBook(bookId: String) {
        if (bookId.isBlank() || selectedBookId.value == bookId) return
        cancelChapterSelection()
        selectedBookId.value = bookId
        selectedChapterId.value = books.value
            .firstOrNull { it.id == bookId }
            ?.let(::rememberedChapterId)
    }

    fun selectChapter(chapterId: String) {
        if (chapterId.isBlank() || chapterId == selectedChapterId.value) return
        val bookId = selectedBookId.value ?: return
        if (uiState.value.chapters.none { it.id == chapterId }) return
        selectedChapterIdsByBook[bookId] = chapterId
        selectedChapterId.value = chapterId
        openChapter(bookId, chapterId)
    }

    fun playPreviousChapter() = playAdjacentChapter(-1)

    fun playNextChapter() = playAdjacentChapter(1)

    fun importBook(uri: Uri) = launchOperation("Importing your book", "book_import") {
        services.libraryRepository.importBook(
            uri,
            NarrationLanguage.ENGLISH.code,
        ).getOrThrow().also { bookId ->
            selectedBookId.value = bookId
            selectedChapterIdsByBook.remove(bookId)
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

    fun deleteSelectedBook() = launchOperation("Removing book", "book_remove") {
        val bookId = selectedBookId.value ?: return@launchOperation null
        if (uiState.value.playback?.bookId == bookId) {
            services.playbackGateway.pause()
        }
        services.preparationCoordinator.cancel(bookId)
        services.libraryRepository.deleteBook(bookId)
        selectedChapterIdsByBook.remove(bookId)
        selectedBookId.value = null
        selectedChapterId.value = null
        "Book removed from this device."
    }

    fun exportSelectedBook(destination: Uri): Job {
        bookExportJob?.takeIf(Job::isActive)?.let { return it }
        val book = uiState.value.selectedBook
            ?: return launchOperation { error("Choose a book to export") }
        return viewModelScope.launch {
            val startedAtMs = monotonicNowMs()
            BetaDiagnostics.info("book_mp3_export_started")
            operation.value = OperationState(
                isBusy = true,
                statusMessage = "Preparing ${book.title} for MP3 export",
                progressFraction = 0f,
                kind = OperationKind.BOOK_MP3_EXPORT,
                targetBookId = book.id,
            )
            try {
                val exported = services.bookMp3Exporter.export(book.id, destination) { progress ->
                    operation.value = OperationState(
                        isBusy = true,
                        statusMessage = progress.exportStatus(book.title),
                        progressFraction = progress.progressFraction,
                        kind = OperationKind.BOOK_MP3_EXPORT,
                        targetBookId = book.id,
                    )
                }
                operation.value = OperationState(
                    statusMessage = "Exported ${book.title} as MP3 (${exported.bytesWritten.toFileSize()}).",
                    progressFraction = 1f,
                    kind = OperationKind.BOOK_MP3_EXPORT,
                    targetBookId = book.id,
                )
                BetaDiagnostics.performance(
                    "book_mp3_export_completed",
                    mapOf(
                        "elapsed_ms" to (monotonicNowMs() - startedAtMs),
                        "bytes_written" to exported.bytesWritten,
                    ),
                )
            } catch (cancellation: CancellationException) {
                operation.value = OperationState()
                throw cancellation
            } catch (failure: Throwable) {
                BetaDiagnostics.error(
                    "book_mp3_export_failed",
                    failure,
                    mapOf("elapsed_ms" to (monotonicNowMs() - startedAtMs)),
                )
                operation.value = OperationState(
                    errorMessage = userFacingOperationError(
                        failure,
                        fallback = "The book could not be exported as MP3. Please try again.",
                    ),
                    kind = OperationKind.BOOK_MP3_EXPORT,
                    targetBookId = book.id,
                )
            }
        }.also { bookExportJob = it }
    }

    fun playOrPause(): Job {
        val cursor = uiState.value.playback
        val selectedBook = uiState.value.selectedBook
            ?: return launchOperation { error("Choose a book to listen to") }
        return if (cursor?.bookId != selectedBook.id) {
            val chapterId = uiState.value.selectedChapter?.id
                ?: return launchOperation { error("Choose a chapter to listen to") }
            openChapter(selectedBook.id, chapterId)
        } else {
            launchOperation {
                if (cursor.isPlaying) services.playbackGateway.pause() else services.playbackGateway.play()
                null
            }
        }
    }

    fun playSelectedChapter() {
        val book = uiState.value.selectedBook
        val chapter = uiState.value.selectedChapter
        if (book == null || chapter == null) {
            launchOperation { error("Choose a book to listen to") }
            return
        }
        openChapter(book.id, chapter.id)
    }

    fun previewCharacter(characterId: String): Job {
        stopVoicePreview()
        return launchOperation("Preparing voice preview", "voice_preview") {
            val snapshot = uiState.value
            val character = snapshot.characters.firstOrNull { it.id == characterId }
                ?: error("That character is no longer available")
            val voices = services.availableVoices
            val assignedVoiceId = snapshot.voiceAssignments[characterId]?.voiceId
            val fallbackIndex = snapshot.characters.indexOf(character).coerceAtLeast(0)
            val voice = voices.firstOrNull { it.id == assignedVoiceId }
                ?: voices.getOrNull(fallbackIndex % voices.size.coerceAtLeast(1))
                ?: error("No embedded voices are available")
            if (snapshot.playback?.isPlaying == true) services.playbackGateway.pause()
            services.voicePreviewPlayer.play(
                text = voicePreviewText(
                    character.displayName,
                    snapshot.selectedBook?.narrationLanguageCode
                        ?: NarrationLanguage.ENGLISH.code,
                ),
                voice = voice,
                speed = snapshot.settings.speakingSpeed,
                languageCode = snapshot.selectedBook?.narrationLanguageCode
                    ?: NarrationLanguage.ENGLISH.code,
            ).getOrThrow()
            "Played ${voice.displayName} for ${character.displayName}."
        }.also { voicePreviewJob = it }
    }

    fun previewVoice(voiceId: String, characterName: String): Job {
        stopVoicePreview()
        return launchOperation("Preparing voice preview", "voice_preview") {
            val snapshot = uiState.value
            val voice = services.availableVoices.firstOrNull { it.id == voiceId }
                ?: error("That embedded voice is no longer available")
            if (snapshot.playback?.isPlaying == true) services.playbackGateway.pause()
            services.voicePreviewPlayer.play(
                text = voicePreviewText(
                    characterName,
                    snapshot.selectedBook?.narrationLanguageCode
                        ?: NarrationLanguage.ENGLISH.code,
                ),
                voice = voice,
                speed = snapshot.settings.speakingSpeed,
                languageCode = snapshot.selectedBook?.narrationLanguageCode
                    ?: NarrationLanguage.ENGLISH.code,
            ).getOrThrow()
            "Played ${voice.displayName} preview."
        }.also { voicePreviewJob = it }
    }

    fun seekBy(deltaMs: Long) = launchOperation {
        if (uiState.value.playback == null) return@launchOperation null
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
        if (uiState.value.playback == null) return@launchOperation null
        services.playbackGateway.seekToPassage(passageId)
        null
    }

    fun correctPassageSpeaker(
        passageId: String,
        speakerId: String,
        scope: SpeakerCorrectionScope,
    ): Job = launchOperation("Correcting the attributed voice", "speaker_correction") {
        val snapshot = uiState.value
        val book = snapshot.selectedBook ?: error("Choose a book before correcting a voice")
        val passage = snapshot.chapters.asSequence()
            .flatMap { it.passages.asSequence() }
            .firstOrNull { it.id == passageId }
            ?: error("That phrase is no longer available")
        val speaker = snapshot.characters.firstOrNull { it.id == speakerId }
            ?: error("That voice is no longer available in this book")
        if (passage.speakerId == speaker.id) return@launchOperation null
        val correctedCount = services.applySpeakerCorrection(book.id, passage.id, speaker.id, scope)
        refreshStorageUsage()
        when (correctedCount) {
            0 -> null
            1 -> "This phrase will now be read by ${speaker.displayName}."
            else -> "$correctedCount matching phrases will now be read by ${speaker.displayName}."
        }
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

    fun cycleNarrationChunkSize() {
        val current = uiState.value.settings.narrationChunkChars
        val sizes = NarrationTextChunker.CONFIGURABLE_SIZES
        setNarrationChunkSize(sizes.firstOrNull { it > current } ?: sizes.first())
    }

    fun setNarrationChunkSize(maxChars: Int) = launchOperation(
        "Updating narration chunk size",
        "narration_chunk_update",
    ) {
        val normalized = NarrationTextChunker.normalizeMaxChars(maxChars)
        val snapshot = uiState.value
        services.settingsRepository.update { it.copy(narrationChunkChars = normalized) }
        snapshot.selectedBook?.let { book ->
            services.playbackGateway.pause()
            services.playbackGateway.invalidateQueuedChapters(
                bookId = book.id,
                chapterIds = snapshot.chapters.mapTo(linkedSetOf()) { it.id },
            )
            services.preparationCoordinator.regenerateAudio(book.id, 0)
        }
        "$normalized-character narration chunks selected. Only the opening chunk is prepared ahead."
    }

    fun downloadLanguagePack(languageCode: String): Job {
        val language = NarrationLanguage.fromCode(languageCode) ?: return launchOperation {
            error("That language pack is unavailable")
        }
        return changeNarrationLanguage(language, install = true)
    }

    fun selectNarrationLanguage(languageCode: String): Job {
        val language = NarrationLanguage.fromCode(languageCode) ?: return launchOperation {
            error("That language pack is unavailable")
        }
        if (language.code !in uiState.value.settings.installedLanguagePackCodes) {
            return launchOperation { error("Download the ${language.displayName} language pack first") }
        }
        return changeNarrationLanguage(language, install = false)
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

    fun assignVoice(
        characterId: String,
        voiceId: String,
        regenerationScope: VoiceRegenerationScope = VoiceRegenerationScope.WHOLE_BOOK,
    ): Job {
        stopVoicePreview()
        return launchOperation("Updating the cast", "voice_assignment") {
            val snapshot = uiState.value
            val voice = services.availableVoices.firstOrNull { it.id == voiceId }
                ?: error("That embedded voice is unavailable")
            val previous = snapshot.voiceAssignments[characterId]
                ?: error("The current voice assignment is unavailable")
            if (previous.voiceId == voice.id) return@launchOperation null
            val book = snapshot.selectedBook ?: error("Choose a book before changing its cast")
            val currentChapter = snapshot.selectedChapter ?: snapshot.chapters.firstOrNull()
                ?: error("This book has no prepared chapters")
            val requestChapter = when (regenerationScope) {
                VoiceRegenerationScope.WHOLE_BOOK -> snapshot.chapters.first()
                VoiceRegenerationScope.THIS_CHAPTER,
                VoiceRegenerationScope.FROM_THIS_CHAPTER,
                -> currentChapter
            }
            val fromChapterOrdinal = requestChapter.ordinal
            if (fromChapterOrdinal !in snapshot.chapters.indices) {
                error("There is no chapter at that voice-change boundary")
            }
            val speed = snapshot.settings.speakingSpeed
            services.applyVoiceRegeneration(
                VoiceRegenerationRequest(
                    bookId = book.id,
                    characterId = characterId,
                    assignment = CharacterVoiceAssignment(
                        characterId = characterId,
                        voiceId = voice.id,
                        modelVersion = services.ttsModelVersion,
                        speed = speed,
                    ),
                    scope = regenerationScope,
                    selectedChapterId = requestChapter.id,
                    fromChapterOrdinal = fromChapterOrdinal,
                ),
            )
            voiceRetentionRefreshVersion.value += 1L
            refreshStorageUsage()
            val boundary = when (regenerationScope) {
                VoiceRegenerationScope.WHOLE_BOOK -> "the whole book"
                VoiceRegenerationScope.THIS_CHAPTER -> "Chapter ${fromChapterOrdinal + 1} only"
                VoiceRegenerationScope.FROM_THIS_CHAPTER -> "Chapter ${fromChapterOrdinal + 1} onward"
            }
            "${voice.displayName} will narrate from $boundary. The previous audio is kept for 24 hours."
        }
    }

    fun revertVoiceChange(): Job = launchOperation("Restoring the previous voice", "voice_assignment_revert") {
        val snapshot = uiState.value
        val book = snapshot.selectedBook ?: error("Choose a book before reverting its cast")
        val change = services.retainedVoiceChanges(book.id, snapshot.characters.map(StoryCharacter::id))
            .maxByOrNull(RevertibleVoiceChange::expiresAtEpochMs)
            ?: error("The previous voice is no longer available")
        check(services.revertVoiceChange(change)) { "The previous voice is no longer available" }
        voiceRetentionRefreshVersion.value += 1L
        refreshStorageUsage()
        "Previous narration restored. Missing chapters will finish in the background."
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

    fun setAudioCacheLimit(bytes: Long) = launchOperation("Optimizing local audio storage", "audio_cache_update") {
        services.settingsRepository.update { it.copy(audioCacheLimitBytes = bytes) }
        services.audioSegmentStore.trimTo(bytes)
        refreshStorageUsage()
        null
    }

    fun clearMessage() {
        operation.value = OperationState()
    }

    override fun onCleared() {
        cancelChapterSelection()
        stopVoicePreview()
        super.onCleared()
    }

    private fun openChapter(bookId: String, chapterId: String): Job {
        selectedChapterIdsByBook[bookId] = chapterId
        val chapterNumber = uiState.value.chapters.indexOfFirst { it.id == chapterId }
            .takeIf { it >= 0 }
            ?.plus(1)
        val status = chapterNumber
            ?.let { "Preparing Chapter $it audio on this device. You can keep using Whisperbook." }
            ?: "Preparing chapter audio on this device. You can keep using Whisperbook."
        val request = ++chapterSelectionRequest
        chapterSelectionJob?.cancel()
        loadingChapterId.value = chapterId
        return viewModelScope.launch {
            operation.value = OperationState(isBusy = true, statusMessage = status)
            try {
                services.playbackGateway.playBook(bookId, chapterId)
                if (request == chapterSelectionRequest) {
                    refreshStorageUsage()
                    operation.value = OperationState()
                }
            } catch (cancellation: CancellationException) {
                if (request == chapterSelectionRequest) {
                    loadingChapterId.value = null
                    operation.value = OperationState()
                }
                throw cancellation
            } catch (failure: Throwable) {
                if (request == chapterSelectionRequest) {
                    loadingChapterId.value = null
                    operation.value = OperationState(
                        errorMessage = userFacingOperationError(
                            failure,
                            fallback = "This chapter could not be prepared.",
                        ),
                    )
                }
            }
        }.also { chapterSelectionJob = it }
    }

    private fun playAdjacentChapter(offset: Int) {
        val snapshot = uiState.value
        val currentChapterId = selectedChapterId.value ?: snapshot.selectedChapter?.id ?: return
        val currentIndex = snapshot.chapters.indexOfFirst { it.id == currentChapterId }
        val targetChapter = snapshot.chapters.getOrNull(currentIndex + offset) ?: return
        selectChapter(targetChapter.id)
    }

    private fun cancelChapterSelection() {
        chapterSelectionRequest += 1
        chapterSelectionJob?.cancel()
        chapterSelectionJob = null
        loadingChapterId.value = null
        operation.value = OperationState()
    }

    private fun rememberedChapterId(book: Book): String? =
        selectedChapterIdsByBook[book.id] ?: book.currentChapterId

    private fun stopVoicePreview() {
        voicePreviewJob?.cancel()
        voicePreviewJob = null
        services.voicePreviewPlayer.stop()
    }

    private fun refreshStorageUsage() {
        storageRefreshVersion.value += 1L
    }

    private fun changeNarrationLanguage(
        language: NarrationLanguage,
        install: Boolean,
    ) = launchOperation(
        if (install) "Adding ${language.displayName} for this book" else "Changing this book's language",
    ) {
        val snapshot = uiState.value
        val book = snapshot.selectedBook ?: error("Choose a book before changing its language")
        if (install && language.code !in snapshot.settings.installedLanguagePackCodes) {
            services.settingsRepository.update { current ->
                current.copy(installedLanguagePackCodes = current.installedLanguagePackCodes + language.code)
            }
        }
        if (book.narrationLanguageCode == language.code) {
            return@launchOperation if (install) {
                "${language.displayName} is ready for ${book.title}."
            } else {
                null
            }
        }
        services.applyNarrationLanguageToBook(book.id, language.code)
        refreshStorageUsage()
        "${book.title} now uses ${language.displayName}. Other books are unchanged."
    }

    private fun updateSettings(transform: (AppSettings) -> AppSettings) = launchOperation {
        services.settingsRepository.update(transform)
        null
    }

    private fun launchOperation(
        status: String? = null,
        diagnosticEvent: String? = null,
        block: suspend () -> String?,
    ) = viewModelScope.launch {
        val startedAtMs = monotonicNowMs()
        val isUserVisibleWork = status != null
        diagnosticEvent?.let { BetaDiagnostics.info("${it}_started") }
        if (isUserVisibleWork) {
            operation.value = OperationState(isBusy = true, statusMessage = status)
        }
        try {
            val resultMessage = block()
            if (isUserVisibleWork || resultMessage != null) {
                operation.value = OperationState(statusMessage = resultMessage)
            }
            diagnosticEvent?.let { event ->
                BetaDiagnostics.performance(
                    "${event}_completed",
                    mapOf("elapsed_ms" to (monotonicNowMs() - startedAtMs)),
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            BetaDiagnostics.error(
                event = diagnosticEvent?.let { "${it}_failed" } ?: "ui_operation_failed",
                failure = failure,
                details = mapOf("elapsed_ms" to (monotonicNowMs() - startedAtMs)),
            )
            operation.value = OperationState(
                errorMessage = userFacingOperationError(failure),
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

private fun monotonicNowMs(): Long = System.nanoTime() / 1_000_000L

internal fun voicePreviewText(characterName: String, languageCode: String = "en"): String {
    val name = characterName.trim().take(48).ifBlank { "this character" }
    val narrator = name.equals("Narrator", ignoreCase = true)
    return when (languageCode) {
        "fr" -> if (narrator) {
            "Il était une fois, chaque histoire commençait par une voix."
        } else {
            "Bonjour, je suis $name. Voici ma voix dans votre histoire."
        }
        "ar" -> if (narrator) {
            "كان يا ما كان، كل حكاية تبدأ بصوت."
        } else {
            "مرحبًا، أنا $name. هكذا سيكون صوتي في حكايتك."
        }
        else -> if (narrator) {
            "Once upon a time, every story began with a voice."
        } else {
            "Hello, I am $name. This is how I will sound in your story."
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
    val revertibleVoiceChange: RevertibleVoiceChange?,
    val pendingOperation: PendingOperationState,
)

private data class VoiceState(
    val assignments: Map<String, CharacterVoiceAssignment>,
    val revertibleChange: RevertibleVoiceChange?,
)

private data class VoiceRetentionLookup(
    val bookId: String?,
    val characterIds: List<String>,
    val revision: Long,
)

private data class PendingOperationState(
    val operation: OperationState,
    val chapterId: String?,
    val audioProgress: PlaybackPreparationProgress?,
)

private data class OperationState(
    val isBusy: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val progressFraction: Float? = null,
    val kind: OperationKind? = null,
    val targetBookId: String? = null,
)

private enum class OperationKind { BOOK_MP3_EXPORT }

private fun BookMp3ExportProgress.exportStatus(bookTitle: String): String = when (stage) {
    BookMp3ExportStage.PREPARING_AUDIO ->
        "Preparing chapter ${chapterNumber.coerceAtLeast(1)} of $totalChapters for $bookTitle"
    BookMp3ExportStage.ENCODING_MP3 -> "Encoding $bookTitle as MP3"
    BookMp3ExportStage.SAVING -> "Saving $bookTitle.mp3"
}

private fun Long.toFileSize(): String = when {
    this >= 1024L * 1024L -> "%.1f MB".format(this / (1024.0 * 1024.0))
    this >= 1024L -> "%.1f KB".format(this / 1024.0)
    else -> "$this bytes"
}

internal fun userFacingOperationError(
    failure: Throwable,
    fallback: String = "The local operation could not finish. Please try again.",
): String {
    val detail = failure.message?.trim()?.takeIf(String::isNotBlank)
    val exposesDatabaseDetails = generateSequence(failure) { it.cause }
        .any { it.javaClass.name.startsWith("android.database.sqlite.") } ||
        detail?.let { message ->
            message.contains("SQLITE_", ignoreCase = true) ||
                message.contains("while compiling:", ignoreCase = true) ||
                message.contains("too many SQL variables", ignoreCase = true) ||
                message.contains("database is locked", ignoreCase = true)
        } == true
    return detail?.takeIf { message ->
        !exposesDatabaseDetails && message.length <= MAX_USER_FACING_ERROR_LENGTH
    } ?: fallback
}

private const val MAX_USER_FACING_ERROR_LENGTH = 240

private fun PlaybackPreparationProgress.statusMessage(
    chapters: List<Chapter>,
    playback: PlaybackCursor?,
): String {
    val chapterNumber = chapters.indexOfFirst { it.id == chapterId }
        .takeIf { it >= 0 }
        ?.plus(1)
    val chapterLabel = chapterNumber?.let { "Chapter $it" } ?: "this chapter"
    val nextSegment = (completedSegments + 1).coerceAtMost(totalSegments)
    return if (
        playback?.isPlaying == true &&
        playback.bookId == bookId &&
        playback.chapterId == chapterId
    ) {
        "Playing $chapterLabel now. Preparing passage $nextSegment of $totalSegments in the background."
    } else {
        "Preparing passage $nextSegment of $totalSegments for $chapterLabel on this device."
    }
}

private data class StorageRefreshRequest(
    val books: List<BookStorageState>,
    val revision: Long,
)

private data class BookStorageState(
    val id: String,
    val privateSourcePath: String?,
    val preparationStage: com.whisperbook.app.domain.model.PreparationStage,
)
