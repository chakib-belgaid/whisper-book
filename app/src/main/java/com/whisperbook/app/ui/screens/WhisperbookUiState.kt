package com.whisperbook.app.ui.screens

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.whisperbook.app.domain.NarrationTextChunker
import com.whisperbook.app.domain.model.Book
import com.whisperbook.app.domain.model.Chapter
import com.whisperbook.app.domain.model.CharacterColorRole
import com.whisperbook.app.domain.model.CharacterVoiceAssignment
import com.whisperbook.app.domain.model.NarrationLanguage
import com.whisperbook.app.domain.model.PreparationStage
import com.whisperbook.app.domain.model.PreparationState
import com.whisperbook.app.domain.model.StoryCharacter
import com.whisperbook.app.domain.model.SpeakerCorrectionScope
import com.whisperbook.app.domain.model.VoiceDescriptor
import com.whisperbook.app.domain.model.VoiceRegenerationScope
import com.whisperbook.app.domain.model.speakerPhraseMatchKey
import com.whisperbook.app.integration.WhisperbookUiSnapshot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface WhisperbookUiActions {
    fun importBook(uri: Uri)
    fun retryPreparation()
    fun deleteSelectedBook()
    fun exportSelectedBook(destination: Uri)
    fun selectBook(bookId: String)
    fun selectChapter(chapterId: String)
    fun playPreviousChapter()
    fun playNextChapter()
    fun playSelectedChapter()
    fun playOrPause()
    fun seekByFraction(delta: Float)
    fun seekToFraction(fraction: Float)
    fun seekToPassage(passageId: String)
    fun correctPassageSpeaker(passageId: String, speakerId: String, scope: SpeakerCorrectionScope)
    fun cycleSpeed()
    fun cycleNarrationChunkSize()
    fun downloadLanguagePack(languageCode: String)
    fun selectNarrationLanguage(languageCode: String)
    fun cycleSleepTimer()
    fun cycleVoice(characterId: String)
    fun assignVoice(characterId: String, voiceId: String, regenerationScope: VoiceRegenerationScope)
    fun revertVoiceChange()
    fun previewCharacter(characterId: String)
    fun previewVoice(voiceId: String, characterName: String)
    fun setAutoScroll(enabled: Boolean)
    fun setKeepScreenAwake(enabled: Boolean)
    fun setLargerText(enabled: Boolean)
    fun completeOnboarding()
}

@Immutable
data class LibraryBookUi(
    val id: String,
    val title: String,
    val author: String,
    val chapter: Int,
    val totalChapters: Int,
    val progress: Float,
    val preparation: PreparationState = PreparationState.Ready,
) {
    val canListen: Boolean
        get() = totalChapters > 0 && preparation.stage.isPlaybackSafeStage()
}

@Immutable
data class ChapterUi(
    val number: Int,
    val title: String,
    val selected: Boolean = false,
    val id: String = number.toString(),
    val isLoading: Boolean = false,
    val isAvailable: Boolean = true,
)

@Immutable
data class CastMemberUi(
    val id: String,
    val character: String,
    val voice: String,
    val confidence: Int,
    val lines: Int,
    val portraitRes: Int,
    val role: SpeakerRole,
    val voiceId: String = "",
)

@Immutable
data class VoiceOptionUi(
    val id: String,
    val displayName: String,
    val portraitRes: Int,
)

enum class SpeakerRole { Narrator, Elara, Fox }

@Immutable
data class PassageUi(
    val id: String,
    val speaker: SpeakerRole,
    val text: String,
    val sourcePassageId: String = id,
    val speakerId: String = "",
    val speakerName: String = speaker.name,
)

private data class CharacterPassageUi(
    val name: String,
    val role: SpeakerRole,
)

/**
 * UI-facing integration seam. Production repositories and the Media3 gateway can drive this
 * holder without coupling screens to storage, parsing, synthesis, or playback implementations.
 */
@Stable
class WhisperbookAppState(private val productionActions: WhisperbookUiActions? = null) {
    private val demoMode = productionActions == null
    private var synchronizedVoices: List<VoiceDescriptor>? = null
    private var synchronizedBooks: List<Book>? = null
    private var synchronizedBookChapters: List<Chapter>? = null
    private var synchronizedChapters: List<Chapter>? = null
    private var synchronizedChapterSelectionId: String? = null
    private var synchronizedLoadingChapterId: String? = null
    private var synchronizedChapterAssignments: Map<String, CharacterVoiceAssignment>? = null
    private var chaptersSynchronized = false
    private var synchronizedCharacters: List<StoryCharacter>? = null
    private var synchronizedAssignments: Map<String, CharacterVoiceAssignment>? = null
    private var synchronizedCastVoices: List<VoiceDescriptor>? = null
    private var synchronizedPassageChapter: Chapter? = null
    private var synchronizedPassageCharacters: List<StoryCharacter>? = null
    private var synchronizedPassageChunkChars: Int? = null
    private var passagesSynchronized = false

    val books = mutableStateListOf<LibraryBookUi>().apply {
        if (demoMode) addAll(
            listOf(
                LibraryBookUi("moonlit", "The Moonlit Wood", "E. Wren", 7, 18, .58f),
                LibraryBookUi("alice", "Alice's Adventures", "Lewis Carroll", 1, 12, .08f),
                LibraryBookUi("garden", "The Secret Garden", "Frances Hodgson Burnett", 1, 27, 0f),
            ),
        )
    }
    val chapters = mutableStateListOf<ChapterUi>().apply {
        if (demoMode) addAll(
            listOf(
                ChapterUi(7, "The Hidden Glade", true),
                ChapterUi(8, "A Lantern in the Rain"),
                ChapterUi(9, "The Fox's Promise"),
                ChapterUi(10, "Under the Rowan Tree"),
            ),
        )
    }
    val cast = mutableStateListOf<CastMemberUi>().apply {
        if (demoMode) addAll(
            listOf(
                CastMemberUi("narrator", "Narrator", "Bella", 98, 426, voiceAvatarRes("bella"), SpeakerRole.Narrator),
                CastMemberUi("elara", "Elara", "Luna", 91, 84, voiceAvatarRes("luna"), SpeakerRole.Elara),
                CastMemberUi("fox", "Fox", "Leo", 87, 39, voiceAvatarRes("leo"), SpeakerRole.Fox),
            ),
        )
    }
    val voiceOptions = mutableStateListOf<VoiceOptionUi>().apply {
        if (demoMode) addAll(demoVoiceOptions)
    }
    val passages = mutableStateListOf<PassageUi>().apply {
        if (demoMode) addAll(
            listOf(
                PassageUi("p1", SpeakerRole.Narrator, "The trees leaned close as the path narrowed beneath the moon.", speakerId = "narrator"),
                PassageUi("p2", SpeakerRole.Elara, "We should turn back before the lantern fades.", speakerId = "elara"),
                PassageUi("p3", SpeakerRole.Fox, "The woods remember every traveler.", speakerId = "fox"),
                PassageUi("p4", SpeakerRole.Narrator, "Beyond the trees, a glimmer of silver called to them, soft as a secret.", speakerId = "narrator"),
            ),
        )
    }

    var importedUri by mutableStateOf<Uri?>(null)
        private set
    var importError by mutableStateOf<String?>(null)
        private set
    var preparationProgress by mutableFloatStateOf(if (demoMode) .62f else 0f)
        private set
    var preparationStage by mutableIntStateOf(if (demoMode) 1 else 0)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var chapterProgress by mutableFloatStateOf(if (demoMode) .44f else 0f)
        private set
    var speed by mutableFloatStateOf(1f)
        private set
    var sleepMinutes by mutableIntStateOf(if (demoMode) 30 else 0)
        private set
    var activePassageId by mutableStateOf(if (demoMode) "p2" else "")
        private set
    var autoScroll by mutableStateOf(true)
        private set
    var keepScreenAwake by mutableStateOf(false)
        private set
    var largerText by mutableStateOf(false)
        private set
    var isBusy by mutableStateOf(false)
        private set
    var statusMessage by mutableStateOf<String?>(null)
        private set
    var backgroundProgressFraction by mutableStateOf<Float?>(null)
        private set
    var isExportingBook by mutableStateOf(false)
        private set
    var bookExportMessage by mutableStateOf<String?>(null)
        private set
    var preparationFailed by mutableStateOf(false)
        private set
    var currentBookTitle by mutableStateOf(if (demoMode) "The Moonlit Wood" else "")
        private set
    var currentBookId by mutableStateOf(if (demoMode) "demo-book" else "")
        private set
    var currentAuthor by mutableStateOf(if (demoMode) "E. Wren" else "")
        private set
    var currentChapterTitle by mutableStateOf(if (demoMode) "The Hidden Glade" else "")
        private set
    var currentChapterNumber by mutableIntStateOf(if (demoMode) 7 else 0)
        private set
    var totalChapters by mutableIntStateOf(if (demoMode) 18 else 0)
        private set
    var chapterPositionMs by mutableLongStateOf(if (demoMode) 18L * 60_000L + 42_000L else 0L)
        private set
    var chapterDurationMs by mutableLongStateOf(if (demoMode) 42L * 60_000L + 58_000L else 0L)
        private set
    var activePassagePositionMs by mutableLongStateOf(if (demoMode) 6_400L else 0L)
        private set
    var activePassageDurationMs by mutableLongStateOf(if (demoMode) 10_000L else 0L)
        private set
    var localStorageBytes by mutableLongStateOf(if (demoMode) 1_800_000_000L else 0L)
        private set
    var storageLimitBytes by mutableLongStateOf(2L * 1024L * 1024L * 1024L)
        private set
    var narrationLanguageCode by mutableStateOf(NarrationLanguage.ENGLISH.code)
        private set
    var narrationChunkChars by mutableIntStateOf(NarrationTextChunker.MAX_CHARS)
        private set
    var installedLanguagePackCodes by mutableStateOf(setOf(NarrationLanguage.ENGLISH.code))
        private set
    var canRevertVoiceChange by mutableStateOf(false)
        private set
    var preparationStatus by mutableStateOf<PreparationState?>(null)
        private set

    val isProductionBacked: Boolean get() = productionActions != null
    val isChapterLoading: Boolean get() = chapters.any(ChapterUi::isLoading)
    val currentPassage: PassageUi?
        get() = passages.firstOrNull { it.id == activePassageId } ?: passages.firstOrNull()
    val isBookPreparing: Boolean
        get() = preparationStatus?.stage?.let { it != PreparationStage.READY && it != PreparationStage.FAILED } == true
    val canListen: Boolean
        get() = totalChapters > 0 &&
            (preparationStatus?.stage?.isPlaybackSafeStage() ?: demoMode)
    val hasPreviousChapter: Boolean
        get() = chapters.getOrNull(selectedChapterIndex() - 1)?.isAvailable == true
    val hasNextChapter: Boolean
        get() = chapters.getOrNull(selectedChapterIndex() + 1)?.isAvailable == true

    suspend fun synchronizeAsync(
        snapshot: WhisperbookUiSnapshot,
        projectionDispatcher: CoroutineDispatcher = Dispatchers.Default,
    ) {
        val projectedPassages = if (shouldSynchronizePassages(snapshot)) {
            withContext(projectionDispatcher) { projectPassages(snapshot) }
        } else {
            null
        }
        synchronize(snapshot, projectedPassages)
    }

    fun synchronize(
        snapshot: WhisperbookUiSnapshot,
        projectedPassages: List<PassageUi>? = null,
    ) {
        if (snapshot.voices !== synchronizedVoices) {
            voiceOptions.clear()
            voiceOptions.addAll(
                snapshot.voices.map { voice ->
                    VoiceOptionUi(voice.id, voice.displayName, voiceAvatarRes(voice.id))
                },
            )
            synchronizedVoices = snapshot.voices
        }
        if (snapshot.selectedBook == null) {
            currentBookId = ""
            currentBookTitle = ""
            currentAuthor = ""
            narrationLanguageCode = NarrationLanguage.ENGLISH.code
        }
        snapshot.selectedBook?.let { selectedBook ->
            currentBookId = selectedBook.id
            currentBookTitle = selectedBook.title
            currentAuthor = selectedBook.author ?: "Unknown author"
            narrationLanguageCode = selectedBook.narrationLanguageCode
        }
        if (snapshot.selectedChapter == null) {
            currentChapterTitle = ""
            currentChapterNumber = 0
        }
        snapshot.selectedChapter?.let { selectedChapter ->
            currentChapterTitle = selectedChapter.title
            currentChapterNumber = selectedChapter.ordinal + 1
        }
        totalChapters = maxOf(snapshot.chapters.size, snapshot.selectedBook?.chapterCount ?: 0)
        if (snapshot.books !== synchronizedBooks || snapshot.chapters !== synchronizedBookChapters) {
            books.clear()
            books.addAll(snapshot.books.map { book ->
                LibraryBookUi(
                    id = book.id,
                    title = book.title,
                    author = book.author ?: "Unknown author",
                    chapter = (book.currentChapterOrdinal?.plus(1) ?: 1),
                    totalChapters = book.chapterCount,
                    progress = book.progressFraction,
                    preparation = book.preparation,
                )
            })
            synchronizedBooks = snapshot.books
            synchronizedBookChapters = snapshot.chapters
        }
        val selectedChapterId = snapshot.selectedChapter?.id
        if (
            !chaptersSynchronized ||
            snapshot.chapters !== synchronizedChapters ||
            selectedChapterId != synchronizedChapterSelectionId ||
            snapshot.loadingChapterId != synchronizedLoadingChapterId ||
            snapshot.voiceAssignments !== synchronizedChapterAssignments
        ) {
            chapters.clear()
            chapters.addAll(snapshot.chapters.map { chapter ->
                ChapterUi(
                    number = chapter.ordinal + 1,
                    title = chapter.title,
                    selected = chapter.id == selectedChapterId,
                    id = chapter.id,
                    isLoading = chapter.id == snapshot.loadingChapterId,
                    isAvailable = chapter.passages.isNotEmpty() &&
                        chapter.passages.all { it.attributionRule != UNATTRIBUTED_RULE } &&
                        (
                            chapter.id != selectedChapterId ||
                                chapter.passages.all { it.speakerId in snapshot.voiceAssignments }
                        ),
                )
            })
            synchronizedChapters = snapshot.chapters
            synchronizedChapterSelectionId = selectedChapterId
            synchronizedLoadingChapterId = snapshot.loadingChapterId
            synchronizedChapterAssignments = snapshot.voiceAssignments
            chaptersSynchronized = true
        }
        if (
            snapshot.characters !== synchronizedCharacters ||
            snapshot.voiceAssignments !== synchronizedAssignments ||
            snapshot.voices !== synchronizedCastVoices
        ) {
            cast.clear()
            cast.addAll(snapshot.characters.filter { character ->
                character.id in snapshot.voiceAssignments
            }.map { character ->
                val role = character.colorRole.toSpeakerRole()
                val assignment = snapshot.voiceAssignments[character.id]
                val voice = snapshot.voices.firstOrNull { it.id == assignment?.voiceId }
                CastMemberUi(
                    id = character.id,
                    character = character.displayName,
                    voice = voice?.displayName ?: "Automatic",
                    confidence = 90,
                    lines = character.dialogueLineCount,
                    portraitRes = voice?.let { voiceAvatarRes(it.id) } ?: when (character.colorRole) {
                        CharacterColorRole.NARRATOR, CharacterColorRole.BLUE -> com.whisperbook.app.R.drawable.portrait_narrator
                        CharacterColorRole.ELARA_BURGUNDY, CharacterColorRole.BURGUNDY -> com.whisperbook.app.R.drawable.portrait_elara
                        CharacterColorRole.FOX_ORANGE, CharacterColorRole.ORANGE -> com.whisperbook.app.R.drawable.portrait_fox
                    },
                    role = role,
                    voiceId = assignment?.voiceId.orEmpty(),
                )
            }.sortedWith(compareBy<CastMemberUi>({ it.role != SpeakerRole.Narrator }, { -it.lines }, { it.character })))
            synchronizedCharacters = snapshot.characters
            synchronizedAssignments = snapshot.voiceAssignments
            synchronizedCastVoices = snapshot.voices
        }
        if (shouldSynchronizePassages(snapshot)) {
            passages.clear()
            passages.addAll(projectedPassages ?: projectPassages(snapshot))
            synchronizedPassageChapter = snapshot.selectedChapter
            synchronizedPassageCharacters = snapshot.characters
            synchronizedPassageChunkChars = snapshot.settings.narrationChunkChars
            passagesSynchronized = true
        }
        snapshot.preparation?.let { preparation ->
            preparationStatus = preparation
            preparationProgress = preparation.overallProgress()
            preparationStage = when (preparation.stage) {
                PreparationStage.COPY_AND_VALIDATE, PreparationStage.READING_CHAPTERS -> 0
                PreparationStage.FINDING_CHARACTERS -> 1
                PreparationStage.ASSIGNING_VOICES -> 2
                // Progressive playback only needs the cast and the first short audio segment;
                // it no longer waits for an entire chapter to finish recording.
                PreparationStage.PREPARING_AUDIO -> 3
                PreparationStage.READY -> 4
                PreparationStage.FAILED -> 0
            }
            preparationFailed = preparation.stage == PreparationStage.FAILED
        } ?: run {
            preparationStatus = null
            preparationProgress = 0f
            preparationStage = 0
            preparationFailed = false
        }
        chapterProgress = snapshot.chapterProgress
        val selectedPlayback = snapshot.playback
            ?.takeIf { playback -> snapshot.selectedBook == null || playback.bookId == snapshot.selectedBook.id }
        selectedPlayback?.let { playback ->
            isPlaying = playback.isPlaying
            speed = playback.speed
            activePassageId = playback.passageId
            chapterPositionMs = playback.chapterPositionMs
            chapterDurationMs = playback.chapterDurationMs
            activePassagePositionMs = playback.segmentPositionMs
            activePassageDurationMs = playback.segmentDurationMs
        } ?: run {
            isPlaying = false
            chapterPositionMs = 0L
            chapterDurationMs = 0L
            activePassagePositionMs = 0L
            activePassageDurationMs = 0L
        }
        autoScroll = snapshot.settings.autoScroll
        keepScreenAwake = snapshot.settings.keepScreenAwake
        largerText = snapshot.settings.largerText
        speed = snapshot.settings.speakingSpeed
        sleepMinutes = snapshot.settings.sleepTimerMinutes
        localStorageBytes = snapshot.localStorageBytes.coerceAtLeast(0L)
        storageLimitBytes = snapshot.settings.audioCacheLimitBytes.coerceAtLeast(1L)
        narrationChunkChars = snapshot.settings.narrationChunkChars
        installedLanguagePackCodes = snapshot.settings.installedLanguagePackCodes
        importError = snapshot.errorMessage
        isBusy = snapshot.isBusy
        statusMessage = snapshot.statusMessage
        backgroundProgressFraction = snapshot.backgroundProgressFraction
        isExportingBook = snapshot.isExportingBook
        bookExportMessage = snapshot.bookExportMessage
        canRevertVoiceChange = snapshot.canRevertVoiceChange
    }

    private fun shouldSynchronizePassages(snapshot: WhisperbookUiSnapshot): Boolean =
        !passagesSynchronized ||
            snapshot.selectedChapter !== synchronizedPassageChapter ||
            snapshot.characters !== synchronizedPassageCharacters ||
            snapshot.settings.narrationChunkChars != synchronizedPassageChunkChars

    private fun projectPassages(snapshot: WhisperbookUiSnapshot): List<PassageUi> {
        val charactersById = snapshot.characters.associate { character ->
            character.id to CharacterPassageUi(
                name = character.displayName,
                role = character.colorRole.toSpeakerRole(),
            )
        }
        return snapshot.selectedChapter?.passages.orEmpty().flatMap { passage ->
            val character = charactersById[passage.speakerId]
            NarrationTextChunker.chunks(
                passageId = passage.id,
                text = passage.text,
                maxChars = snapshot.settings.narrationChunkChars,
            ).map { chunk ->
                PassageUi(
                    id = chunk.id,
                    speaker = character?.role ?: SpeakerRole.Narrator,
                    text = chunk.text,
                    sourcePassageId = passage.id,
                    speakerId = passage.speakerId,
                    speakerName = character?.name ?: "Narrator",
                )
            }
        }
    }

    private fun com.whisperbook.app.domain.model.PreparationState.overallProgress(): Float {
        val local = progressFraction.coerceIn(0f, 1f)
        return when (stage) {
            PreparationStage.COPY_AND_VALIDATE -> 0.08f * local
            PreparationStage.READING_CHAPTERS -> 0.08f + 0.37f * local
            PreparationStage.FINDING_CHARACTERS -> 0.45f + 0.25f * local
            PreparationStage.ASSIGNING_VOICES -> 0.70f + 0.12f * local
            PreparationStage.PREPARING_AUDIO -> 0.82f + 0.18f * local
            PreparationStage.READY -> 1f
            PreparationStage.FAILED -> local
        }.coerceIn(0f, 1f)
    }

    fun imported(uri: Uri) {
        importedUri = uri
        importError = null
        preparationProgress = .12f
        preparationStage = 0
        productionActions?.importBook(uri)
    }

    fun importFailed(message: String) {
        importError = message
    }

    fun retryPreparation() {
        importError = null
        preparationFailed = false
        productionActions?.retryPreparation()
    }

    fun deleteSelectedBook() {
        productionActions?.deleteSelectedBook()
    }

    fun exportSelectedBook(destination: Uri) {
        productionActions?.exportSelectedBook(destination)
    }

    fun deleteBook(bookId: String) {
        if (productionActions == null) {
            books.removeAll { it.id == bookId }
            return
        }
        productionActions.selectBook(bookId)
        productionActions.deleteSelectedBook()
    }

    fun advancePreparation() {
        if (productionActions != null) return
        preparationStage = (preparationStage + 1).coerceAtMost(4)
        preparationProgress = when (preparationStage) {
            0 -> .22f
            1 -> .62f
            2 -> .82f
            else -> 1f
        }
    }

    fun togglePlayback() {
        if (productionActions == null) isPlaying = !isPlaying
        productionActions?.playOrPause()
    }

    fun startPlayback() {
        if (productionActions == null) isPlaying = true
        productionActions?.playSelectedChapter()
    }

    fun playPreviousChapter() {
        val target = chapters.getOrNull(selectedChapterIndex() - 1) ?: return
        if (productionActions == null) selectChapter(target.id)
        productionActions?.playPreviousChapter()
    }

    fun playNextChapter() {
        val target = chapters.getOrNull(selectedChapterIndex() + 1) ?: return
        if (productionActions == null) selectChapter(target.id)
        productionActions?.playNextChapter()
    }

    fun seekBy(delta: Float) {
        chapterProgress = (chapterProgress + delta).coerceIn(0f, 1f)
        if (passages.isNotEmpty()) {
            val passageIndex = (chapterProgress * passages.size).toInt().coerceIn(0, passages.lastIndex)
            activePassageId = passages[passageIndex].id
        }
        productionActions?.seekByFraction(delta)
    }

    fun seekTo(fraction: Float) {
        chapterProgress = fraction.coerceIn(0f, 1f)
        if (passages.isNotEmpty()) {
            val passageIndex = (chapterProgress * passages.size).toInt().coerceIn(0, passages.lastIndex)
            activePassageId = passages[passageIndex].id
        }
        productionActions?.seekToFraction(chapterProgress)
    }

    fun cycleSpeed() {
        val speeds = listOf(0.8f, 1f, 1.2f, 1.5f, 2f)
        speed = speeds[(speeds.indexOf(speed).takeIf { it >= 0 } ?: 0).plus(1) % speeds.size]
        productionActions?.cycleSpeed()
    }

    fun cycleNarrationChunkSize() {
        val sizes = NarrationTextChunker.CONFIGURABLE_SIZES
        val next = sizes.firstOrNull { it > narrationChunkChars } ?: sizes.first()
        narrationChunkChars = next
        passagesSynchronized = false
        productionActions?.cycleNarrationChunkSize()
    }

    fun downloadLanguagePack(languageCode: String) {
        val language = NarrationLanguage.fromCode(languageCode) ?: return
        if (productionActions == null) {
            installedLanguagePackCodes = installedLanguagePackCodes + language.code
            narrationLanguageCode = language.code
        }
        productionActions?.downloadLanguagePack(language.code)
    }

    fun selectNarrationLanguage(languageCode: String) {
        if (languageCode !in installedLanguagePackCodes) return
        if (productionActions == null) narrationLanguageCode = languageCode
        productionActions?.selectNarrationLanguage(languageCode)
    }

    fun cycleSleepTimer() {
        val timers = listOf(15, 30, 45, 60, 0)
        sleepMinutes = timers[(timers.indexOf(sleepMinutes).takeIf { it >= 0 } ?: 0).plus(1) % timers.size]
        productionActions?.cycleSleepTimer()
    }

    fun selectPassage(id: String) {
        activePassageId = id
        val index = passages.indexOfFirst { it.id == id }
        if (index >= 0) chapterProgress = index / passages.size.toFloat()
        productionActions?.seekToPassage(id)
    }

    fun correctPassageSpeaker(
        passageId: String,
        speakerId: String,
        scope: SpeakerCorrectionScope,
    ) {
        if (productionActions == null) {
            val target = cast.firstOrNull { it.id == speakerId } ?: return
            val source = passages.firstOrNull { it.sourcePassageId == passageId } ?: return
            val sourceSpeakerId = source.speakerId
            val sourceKey = speakerPhraseMatchKey(source.text)
            passages.indices.forEach { index ->
                val candidate = passages[index]
                val matches = when (scope) {
                    SpeakerCorrectionScope.THIS_PASSAGE -> candidate.sourcePassageId == passageId
                    SpeakerCorrectionScope.MATCHING_PHRASES ->
                        candidate.speakerId == sourceSpeakerId &&
                            sourceKey.isNotBlank() &&
                            speakerPhraseMatchKey(candidate.text) == sourceKey
                }
                if (matches) {
                    passages[index] = candidate.copy(
                        speaker = target.role,
                        speakerId = target.id,
                        speakerName = target.character,
                    )
                }
            }
        }
        productionActions?.correctPassageSpeaker(passageId, speakerId, scope)
    }

    fun updateAutoScroll(value: Boolean) {
        autoScroll = value
        productionActions?.setAutoScroll(value)
    }

    fun updateKeepScreenAwake(value: Boolean) {
        keepScreenAwake = value
        productionActions?.setKeepScreenAwake(value)
    }

    fun updateLargerText(value: Boolean) {
        largerText = value
        productionActions?.setLargerText(value)
    }

    fun cycleVoice(characterId: String) {
        val index = cast.indexOfFirst { it.id == characterId }
        if (index < 0 || voiceOptions.isEmpty()) return
        val member = cast[index]
        val currentIndex = voiceOptions.indexOfFirst { it.id == member.voiceId }.coerceAtLeast(0)
        val next = voiceOptions[(currentIndex + 1) % voiceOptions.size]
        assignVoice(characterId, next.id)
    }

    fun assignVoice(
        characterId: String,
        voiceId: String,
        regenerationScope: VoiceRegenerationScope = VoiceRegenerationScope.WHOLE_BOOK,
    ) {
        val voice = voiceOptions.firstOrNull { it.id == voiceId } ?: return
        val index = cast.indexOfFirst { it.id == characterId }
        if (index >= 0) {
            cast[index] = cast[index].copy(
                voice = voice.displayName,
                voiceId = voice.id,
                portraitRes = voice.portraitRes,
            )
        }
        productionActions?.assignVoice(characterId, voiceId, regenerationScope)
    }

    fun revertVoiceChange() {
        productionActions?.revertVoiceChange()
    }

    fun previewCharacter(characterId: String) {
        productionActions?.previewCharacter(characterId)
        if (productionActions == null) togglePlayback()
    }

    fun previewVoice(voiceId: String, characterName: String) {
        if (voiceOptions.none { it.id == voiceId }) return
        productionActions?.previewVoice(voiceId, characterName)
        if (productionActions == null) togglePlayback()
    }

    fun selectBook(bookId: String) {
        productionActions?.selectBook(bookId)
    }

    fun selectChapter(chapterId: String) {
        if (chapterId.isBlank()) return
        if (productionActions != null && chapters.firstOrNull { it.id == chapterId }?.isAvailable != true) return
        if (productionActions == null) {
            val chapterIndex = chapters.indexOfFirst { it.id == chapterId }
            if (chapterIndex >= 0) {
                chapters.indices.forEach { index ->
                    chapters[index] = chapters[index].copy(selected = index == chapterIndex)
                }
                val chapter = chapters[chapterIndex]
                currentChapterNumber = chapter.number
                currentChapterTitle = chapter.title
                chapterProgress = 0f
                activePassageId = passages.firstOrNull()?.id.orEmpty()
            }
        }
        productionActions?.selectChapter(chapterId)
    }

    fun completeOnboarding() {
        productionActions?.completeOnboarding()
    }

    private fun selectedChapterIndex(): Int = chapters.indexOfFirst(ChapterUi::selected)
}

private fun PreparationStage.isPlaybackSafeStage(): Boolean =
    this == PreparationStage.PREPARING_AUDIO || this == PreparationStage.READY

private const val UNATTRIBUTED_RULE = "preparation-unattributed"

private val demoVoiceOptions = listOf(
    VoiceOptionUi("bella", "Bella", voiceAvatarRes("bella")),
    VoiceOptionUi("jasper", "Jasper", voiceAvatarRes("jasper")),
    VoiceOptionUi("luna", "Luna", voiceAvatarRes("luna")),
    VoiceOptionUi("bruno", "Bruno", voiceAvatarRes("bruno")),
    VoiceOptionUi("rosie", "Rosie", voiceAvatarRes("rosie")),
    VoiceOptionUi("hugo", "Hugo", voiceAvatarRes("hugo")),
    VoiceOptionUi("kiki", "Kiki", voiceAvatarRes("kiki")),
    VoiceOptionUi("leo", "Leo", voiceAvatarRes("leo")),
)

private fun CharacterColorRole.toSpeakerRole(): SpeakerRole = when (this) {
    CharacterColorRole.NARRATOR, CharacterColorRole.BLUE -> SpeakerRole.Narrator
    CharacterColorRole.ELARA_BURGUNDY, CharacterColorRole.BURGUNDY -> SpeakerRole.Elara
    CharacterColorRole.FOX_ORANGE, CharacterColorRole.ORANGE -> SpeakerRole.Fox
}
