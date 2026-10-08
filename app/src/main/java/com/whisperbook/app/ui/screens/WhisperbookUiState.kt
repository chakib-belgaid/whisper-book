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
import com.whisperbook.app.domain.model.ChapterPlanEntry
import com.whisperbook.app.domain.model.CharacterColorRole
import com.whisperbook.app.domain.model.CharacterVoiceAssignment
import com.whisperbook.app.domain.model.NarrationLanguage
import com.whisperbook.app.domain.model.PreparationStage
import com.whisperbook.app.domain.model.PreparationRunState
import com.whisperbook.app.domain.model.PreparationState
import com.whisperbook.app.domain.model.StoryCharacter
import com.whisperbook.app.domain.model.SpeakerCorrectionScope
import com.whisperbook.app.domain.model.VoiceDescriptor
import com.whisperbook.app.domain.model.VoiceRegenerationScope
import com.whisperbook.app.domain.model.speakerPhraseMatchKey
import com.whisperbook.app.integration.WhisperbookUiSnapshot
import com.whisperbook.app.integration.flux.WhisperbookAction
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface WhisperbookUiActions {
    fun dispatch(action: WhisperbookAction)
    fun importBook(uri: Uri) = dispatch(WhisperbookAction.ImportBook(uri))
    fun setChapterSelected(chapterId: String, selected: Boolean) =
        dispatch(WhisperbookAction.SetChapterSelected(chapterId, selected))
    fun moveChapter(chapterId: String, targetSelectedPosition: Int) =
        dispatch(WhisperbookAction.MoveChapter(chapterId, targetSelectedPosition))
    fun selectAllChapters() = dispatch(WhisperbookAction.SelectAllChapters)
    fun deselectAllChapters() = dispatch(WhisperbookAction.DeselectAllChapters)
    fun restoreOriginalChapterOrder() = dispatch(WhisperbookAction.RestoreOriginalChapterOrder)
    fun resetChapterPlan() = dispatch(WhisperbookAction.ResetChapterPlan)
    fun confirmChapterPlan() = dispatch(WhisperbookAction.ConfirmChapterPlan)
    fun confirmNarrationSetup(languageCode: String, narratorVoiceId: String) =
        dispatch(WhisperbookAction.ConfirmNarrationSetup(languageCode, narratorVoiceId))
    fun confirmStoryReview() = dispatch(WhisperbookAction.ConfirmStoryReview)
    fun retryPreparation() {
        dispatch(WhisperbookAction.ClearMessage)
        dispatch(WhisperbookAction.RetryPreparation)
    }
    fun pausePreparation() = dispatch(WhisperbookAction.PausePreparation)
    fun resumePreparation() = dispatch(WhisperbookAction.ResumePreparation)
    fun cancelPreparation() = dispatch(WhisperbookAction.CancelPreparation)
    fun deleteSelectedBook() = dispatch(WhisperbookAction.DeleteSelectedBook)
    fun exportSelectedBook(destination: Uri) = dispatch(WhisperbookAction.ExportSelectedBook(destination))
    fun selectBook(bookId: String) = dispatch(WhisperbookAction.SelectBook(bookId))
    fun selectChapter(chapterId: String) = dispatch(WhisperbookAction.SelectChapter(chapterId))
    fun playPreviousChapter() = dispatch(WhisperbookAction.PlayPreviousChapter)
    fun playNextChapter() = dispatch(WhisperbookAction.PlayNextChapter)
    fun playSelectedChapter() = dispatch(WhisperbookAction.PlaySelectedChapter)
    fun playOrPause() = dispatch(WhisperbookAction.PlayOrPause)
    fun seekByFraction(delta: Float) = dispatch(
        WhisperbookAction.SeekBy(if (delta < 0f) -15_000L else 15_000L),
    )
    fun seekToFraction(fraction: Float) = dispatch(WhisperbookAction.SeekToFraction(fraction))
    fun seekToPassage(passageId: String) = dispatch(WhisperbookAction.SeekToPassage(passageId))
    fun correctPassageSpeaker(passageId: String, speakerId: String, scope: SpeakerCorrectionScope) =
        dispatch(WhisperbookAction.CorrectPassageSpeaker(passageId, speakerId, scope))
    fun correctPassageSpeakers(
        passageIds: List<String>,
        speakerId: String,
        scope: SpeakerCorrectionScope,
    ) = dispatch(WhisperbookAction.CorrectPassageSpeakers(passageIds.distinct(), speakerId, scope))
    fun cycleSpeed() = dispatch(WhisperbookAction.CycleSpeed)
    fun cycleNarrationChunkSize() = dispatch(WhisperbookAction.CycleNarrationChunkSize)
    fun downloadLanguagePack(languageCode: String) =
        dispatch(WhisperbookAction.DownloadLanguagePack(languageCode))
    fun selectNarrationLanguage(languageCode: String) =
        dispatch(WhisperbookAction.SelectNarrationLanguage(languageCode))
    fun cycleSleepTimer() = dispatch(WhisperbookAction.CycleSleepTimer)
    fun cycleVoice(characterId: String) = dispatch(WhisperbookAction.CycleVoice(characterId))
    fun assignVoice(characterId: String, voiceId: String, regenerationScope: VoiceRegenerationScope) =
        dispatch(WhisperbookAction.AssignVoice(characterId, voiceId, regenerationScope))
    fun revertVoiceChange() = dispatch(WhisperbookAction.RevertVoiceChange)
    fun previewCharacter(characterId: String) = dispatch(WhisperbookAction.PreviewCharacter(characterId))
    fun previewVoice(voiceId: String, characterName: String) =
        dispatch(WhisperbookAction.PreviewVoice(voiceId, characterName))
    fun previewNarrationSetupVoice(voiceId: String, languageCode: String) =
        dispatch(WhisperbookAction.PreviewNarrationSetupVoice(voiceId, languageCode))
    fun setAutoScroll(enabled: Boolean) = dispatch(WhisperbookAction.SetAutoScroll(enabled))
    fun setKeepScreenAwake(enabled: Boolean) = dispatch(WhisperbookAction.SetKeepScreenAwake(enabled))
    fun setLargerText(enabled: Boolean) = dispatch(WhisperbookAction.SetLargerText(enabled))
    fun completeOnboarding() = dispatch(WhisperbookAction.CompleteOnboarding)
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
    val narrationSetupConfirmed: Boolean = true,
    val chapterPlanConfirmed: Boolean = true,
    val storyReviewConfirmed: Boolean = true,
) {
    val needsChapterReview: Boolean
        get() = preparation.stage == PreparationStage.AWAITING_CHAPTER_SELECTION

    val needsNarrationSetup: Boolean
        get() = chapterPlanConfirmed && !narrationSetupConfirmed

    /** True only once every selected chapter is attributed and waits for the listener. */
    val needsStoryReview: Boolean
        get() = chapterPlanConfirmed && narrationSetupConfirmed && !storyReviewConfirmed &&
            preparation.stage == PreparationStage.AWAITING_STORY_REVIEW

    val canListen: Boolean
        get() = chapterPlanConfirmed && narrationSetupConfirmed && storyReviewConfirmed &&
            totalChapters > 0 &&
            preparation.stage.isPlaybackSafeStage()
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
    val supportedLanguageCodes: Set<String> = NarrationLanguage.supportedCodes,
) {
    fun supportsLanguage(languageCode: String): Boolean {
        val requestedLanguage = languageCode.substringBefore('-').substringBefore('_').lowercase()
        return supportedLanguageCodes.any { supported ->
            supported.substringBefore('-').substringBefore('_').lowercase() == requestedLanguage
        }
    }
}

enum class SpeakerRole { Narrator, Elara, Fox }

@Immutable
data class PassageUi(
    val id: String,
    val speaker: SpeakerRole,
    val text: String,
    val sourcePassageId: String = id,
    val speakerId: String = "",
    val speakerName: String = speaker.name,
    val playbackPassageIds: List<String> = listOf(id),
    val sourcePassageIds: List<String> = listOf(sourcePassageId),
)

internal fun groupReaderPassages(
    passages: List<PassageUi>,
    maxGroupChars: Int = MAX_SAFE_READER_GROUP_CHARS,
): List<PassageUi> {
    require(maxGroupChars > 0) { "maxGroupChars must be positive" }
    val grouped = mutableListOf<PassageUi>()
    passages.forEach { passage ->
        val previous = grouped.lastOrNull()
        val separator = if (previous?.sourcePassageIds?.lastOrNull() == passage.sourcePassageId) {
            " "
        } else {
            "\n\n"
        }
        val canJoin = previous != null &&
            previous.speakerId == passage.speakerId &&
            previous.text.length + separator.length + passage.text.length <= maxGroupChars
        if (!canJoin) {
            grouped += passage
        } else {
            grouped[grouped.lastIndex] = previous.copy(
                text = previous.text + separator + passage.text,
                playbackPassageIds = previous.playbackPassageIds + passage.playbackPassageIds,
                sourcePassageIds = (previous.sourcePassageIds + passage.sourcePassageIds).distinct(),
            )
        }
    }
    return grouped
}

private const val MAX_SAFE_READER_GROUP_CHARS = 24_000

private data class CharacterPassageUi(
    val name: String,
    val role: SpeakerRole,
)

private data class PendingImportCompletion(
    val existingBookIds: Set<String>,
    val onSuccess: () -> Unit,
    var observedBusy: Boolean = false,
)

private data class PendingNarrationSetupCompletion(
    val bookId: String,
    val onSuccess: () -> Unit,
)

private data class PendingChapterPlanCompletion(
    val bookId: String,
    val onSuccess: () -> Unit,
)

private data class PendingStoryReviewCompletion(
    val bookId: String,
    val onSuccess: () -> Unit,
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
    private var synchronizedChapterPlan: List<ChapterPlanEntry>? = null
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
    private var synchronizedNarrationSetupBookId: String? = null
    private var synchronizedStoryChapters: List<Chapter>? = null
    private var synchronizedStoryCharacters: List<StoryCharacter>? = null
    private var synchronizedStoryChunkChars: Int? = null
    private var pendingImportCompletion: PendingImportCompletion? = null
    private var pendingChapterPlanCompletion: PendingChapterPlanCompletion? = null
    private var pendingNarrationSetupCompletion: PendingNarrationSetupCompletion? = null
    private var pendingStoryReviewCompletion: PendingStoryReviewCompletion? = null

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
    /** Complete parsed plan; [chapters] remains the selected custom-order playback projection. */
    val chapterPlan = mutableStateListOf<ChapterPlanEntry>()
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
    val readerPassages = mutableStateListOf<PassageUi>().apply {
        addAll(groupReaderPassages(passages))
    }
    /** Character bible for the story review; empty unless the selected book awaits it. */
    val storyCharacters = mutableStateListOf<StoryCharacterUi>()
    /** Selected chapters with attributed reader passages for the story review. */
    val storyChapters = mutableStateListOf<StoryChapterUi>()
    /** Correction targets for the story review: every character, before any voice is cast. */
    val storyCast = mutableStateListOf<CastMemberUi>()

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
    var currentBook by mutableStateOf<Book?>(null)
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
    var narrationSetupLanguageCode by mutableStateOf(NarrationLanguage.ENGLISH.code)
        private set
    var narrationSetupNarratorVoiceId by mutableStateOf("bella")
        private set
    var narrationSetupRequired by mutableStateOf(false)
        private set
    var storyReviewRequired by mutableStateOf(false)
        private set
    var narrationChunkChars by mutableIntStateOf(NarrationTextChunker.TARGET_CHARS)
        private set
    var installedLanguagePackCodes by mutableStateOf(setOf(NarrationLanguage.ENGLISH.code))
        private set
    var canRevertVoiceChange by mutableStateOf(false)
        private set
    var preparationStatus by mutableStateOf<PreparationState?>(null)
        private set

    val bookVoiceOptions: List<VoiceOptionUi>
        get() = voiceOptions.filter { it.supportsLanguage(narrationLanguageCode) }
    val narrationSetupVoiceOptions: List<VoiceOptionUi>
        get() = voiceOptions.filter { it.supportsLanguage(narrationSetupLanguageCode) }

    val isProductionBacked: Boolean get() = productionActions != null
    val selectedChapterCount: Int get() = chapterPlan.count(ChapterPlanEntry::isSelected)
    val requiresChapterReview: Boolean
        get() = preparationStatus?.stage == PreparationStage.AWAITING_CHAPTER_SELECTION
    val isBookParsing: Boolean
        get() = preparationStatus?.let { preparation ->
            preparation.runState == PreparationRunState.RUNNING &&
                preparation.stage.isParsingStage()
        } == true
    val isChapterLoading: Boolean get() = chapters.any(ChapterUi::isLoading)
    val currentPassage: PassageUi?
        get() = passages.firstOrNull { it.id == activePassageId } ?: passages.firstOrNull()
    val isBookPreparing: Boolean
        get() = preparationStatus?.let { preparation ->
                preparation.runState == PreparationRunState.RUNNING &&
                    preparation.stage.isNarrationPreparationStage()
            } == true
    val isPreparationPaused: Boolean
        get() = preparationStatus?.runState == PreparationRunState.PAUSED
    val isPreparationCancelled: Boolean
        get() = preparationStatus?.runState == PreparationRunState.CANCELLED
    val canListen: Boolean
        get() = !narrationSetupRequired &&
            currentBook?.storyReviewConfirmed != false &&
            totalChapters > 0 &&
            (preparationStatus?.stage?.isPlaybackSafeStage() ?: demoMode)
    val hasPreviousChapter: Boolean
        get() = chapters.getOrNull(selectedChapterIndex() - 1)?.isAvailable == true
    val hasNextChapter: Boolean
        get() = chapters.getOrNull(selectedChapterIndex() + 1)?.isAvailable == true

    suspend fun synchronizeAsync(
        snapshot: WhisperbookUiSnapshot,
        projectionDispatcher: CoroutineDispatcher = Dispatchers.Default,
    ) {
        val projection = if (shouldSynchronizePassages(snapshot)) {
            withContext(projectionDispatcher) {
                projectPassages(snapshot).let { playbackPassages ->
                    playbackPassages to projectReaderPassages(snapshot, playbackPassages)
                }
            }
        } else {
            null
        }
        val storyProjection = if (shouldSynchronizeStory(snapshot)) {
            withContext(projectionDispatcher) { projectStory(snapshot) }
        } else {
            null
        }
        synchronize(
            snapshot = snapshot,
            projectedPassages = projection?.first,
            projectedReaderPassages = projection?.second,
            projectedStory = storyProjection,
        )
    }

    fun synchronize(
        snapshot: WhisperbookUiSnapshot,
        projectedPassages: List<PassageUi>? = null,
        projectedReaderPassages: List<PassageUi>? = null,
    ) = synchronize(snapshot, projectedPassages, projectedReaderPassages, projectedStory = null)

    private fun synchronize(
        snapshot: WhisperbookUiSnapshot,
        projectedPassages: List<PassageUi>?,
        projectedReaderPassages: List<PassageUi>?,
        projectedStory: StoryPreviewProjection?,
    ) {
        if (snapshot.voices !== synchronizedVoices) {
            voiceOptions.clear()
            voiceOptions.addAll(
                snapshot.voices.map { voice ->
                    VoiceOptionUi(
                        id = voice.id,
                        displayName = voice.displayName,
                        portraitRes = voiceAvatarRes(voice.id),
                        supportedLanguageCodes = voice.supportedLanguageCodes,
                    )
                },
            )
            synchronizedVoices = snapshot.voices
        }
        if (snapshot.selectedBook == null) {
            currentBook = null
            currentBookId = ""
            currentBookTitle = ""
            currentAuthor = ""
            narrationLanguageCode = NarrationLanguage.ENGLISH.code
            narrationSetupRequired = false
            storyReviewRequired = false
            synchronizedNarrationSetupBookId = null
        }
        snapshot.selectedBook?.let { selectedBook ->
            currentBook = selectedBook
            currentBookId = selectedBook.id
            currentBookTitle = selectedBook.title
            currentAuthor = selectedBook.author ?: "Unknown author"
            narrationLanguageCode = selectedBook.narrationLanguageCode
            narrationSetupRequired = selectedBook.preparation.chapterPlanConfirmed &&
                !selectedBook.narrationSetupConfirmed
            storyReviewRequired = selectedBook.narrationSetupConfirmed &&
                !selectedBook.storyReviewConfirmed &&
                selectedBook.preparation.stage == PreparationStage.AWAITING_STORY_REVIEW
            if (synchronizedNarrationSetupBookId != selectedBook.id) {
                narrationSetupLanguageCode = selectedBook.narrationLanguageCode
                narrationSetupNarratorVoiceId = selectedBook.preferredNarratorVoiceId
                    ?.takeIf { preferred -> narrationSetupVoiceOptions.any { it.id == preferred } }
                    ?: narrationSetupVoiceOptions.firstOrNull()?.id
                    ?: "bella"
                synchronizedNarrationSetupBookId = selectedBook.id
            }
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
        if (snapshot.chapterPlan !== synchronizedChapterPlan) {
            chapterPlan.clear()
            chapterPlan.addAll(snapshot.chapterPlan.sortedBy(ChapterPlanEntry::customPosition))
            synchronizedChapterPlan = snapshot.chapterPlan
        }
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
                    narrationSetupConfirmed = book.narrationSetupConfirmed,
                    chapterPlanConfirmed = book.preparation.chapterPlanConfirmed,
                    storyReviewConfirmed = book.storyReviewConfirmed,
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
                    isAvailable = chapter.isAttributed &&
                        (chapter.id != selectedChapterId ||
                            (chapter.passages.isNotEmpty() &&
                                chapter.passages.all { it.speakerId in snapshot.voiceAssignments })),
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
                    portraitRes = voice?.let { voiceAvatarRes(it.id) }
                        ?: characterPortraitRes(character.colorRole),
                    role = role,
                    voiceId = assignment?.voiceId.orEmpty(),
                )
            }.sortedWith(compareBy<CastMemberUi>({ it.role != SpeakerRole.Narrator }, { -it.lines }, { it.character })))
            synchronizedCharacters = snapshot.characters
            synchronizedAssignments = snapshot.voiceAssignments
            synchronizedCastVoices = snapshot.voices
        }
        if (
            snapshot.selectedBook?.preferredNarratorVoiceId == null &&
            snapshot.selectedBook?.narrationSetupConfirmed == true
        ) {
            cast.firstOrNull { it.role == SpeakerRole.Narrator && it.voiceId.isNotBlank() }
                ?.let { narrator -> narrationSetupNarratorVoiceId = narrator.voiceId }
        }
        if (shouldSynchronizePassages(snapshot)) {
            val nextPassages = projectedPassages ?: projectPassages(snapshot)
            passages.clear()
            passages.addAll(nextPassages)
            readerPassages.clear()
            readerPassages.addAll(
                projectedReaderPassages ?: projectReaderPassages(snapshot, nextPassages),
            )
            synchronizedPassageChapter = snapshot.selectedChapter
            synchronizedPassageCharacters = snapshot.characters
            synchronizedPassageChunkChars = snapshot.settings.narrationChunkChars
            passagesSynchronized = true
        }
        if (shouldSynchronizeStory(snapshot)) {
            val story = projectedStory ?: projectStory(snapshot)
            storyCharacters.clear()
            storyCharacters.addAll(story.characters)
            storyChapters.clear()
            storyChapters.addAll(story.chapters)
            storyCast.clear()
            storyCast.addAll(story.cast)
            synchronizedStoryChapters = snapshot.storyChapters
            synchronizedStoryCharacters = snapshot.characters
            synchronizedStoryChunkChars = snapshot.settings.narrationChunkChars
        }
        snapshot.preparation?.let { preparation ->
            preparationStatus = preparation
            preparationProgress = preparation.overallProgress()
            preparationStage = when (preparation.stage) {
                PreparationStage.COPY_AND_VALIDATE, PreparationStage.READING_CHAPTERS -> 0
                PreparationStage.AWAITING_CHAPTER_SELECTION,
                PreparationStage.AWAITING_NARRATION_SETUP,
                -> 0
                PreparationStage.FINDING_CHARACTERS,
                PreparationStage.AWAITING_STORY_REVIEW,
                -> 1
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
        resolvePendingCompletions(snapshot)
    }

    private fun resolvePendingCompletions(snapshot: WhisperbookUiSnapshot) {
        pendingImportCompletion?.let { pending ->
            when {
                snapshot.errorMessage != null -> pendingImportCompletion = null
                snapshot.isBusy -> pending.observedBusy = true
                !snapshot.isBusy &&
                    snapshot.selectedBook != null &&
                    (pending.observedBusy || snapshot.selectedBook.id !in pending.existingBookIds) -> {
                    pendingImportCompletion = null
                    pending.onSuccess()
                }
            }
        }
        pendingNarrationSetupCompletion?.let { pending ->
            when {
                snapshot.errorMessage != null -> pendingNarrationSetupCompletion = null
                !snapshot.isBusy &&
                    snapshot.selectedBook?.id == pending.bookId &&
                    snapshot.selectedBook?.narrationSetupConfirmed == true -> {
                    pendingNarrationSetupCompletion = null
                    pending.onSuccess()
                }
                snapshot.selectedBook?.id != pending.bookId -> pendingNarrationSetupCompletion = null
            }
        }
        pendingStoryReviewCompletion?.let { pending ->
            when {
                snapshot.errorMessage != null -> pendingStoryReviewCompletion = null
                !snapshot.isBusy &&
                    snapshot.selectedBook?.id == pending.bookId &&
                    snapshot.selectedBook?.storyReviewConfirmed == true -> {
                    pendingStoryReviewCompletion = null
                    pending.onSuccess()
                }
                snapshot.selectedBook?.id != pending.bookId -> pendingStoryReviewCompletion = null
            }
        }
        pendingChapterPlanCompletion?.let { pending ->
            when {
                snapshot.errorMessage != null -> pendingChapterPlanCompletion = null
                !snapshot.isBusy &&
                    snapshot.selectedBook?.id == pending.bookId &&
                    snapshot.preparation?.chapterPlanConfirmed == true -> {
                    pendingChapterPlanCompletion = null
                    pending.onSuccess()
                }
                snapshot.selectedBook?.id != pending.bookId -> pendingChapterPlanCompletion = null
            }
        }
    }

    private fun shouldSynchronizePassages(snapshot: WhisperbookUiSnapshot): Boolean =
        !passagesSynchronized ||
            snapshot.selectedChapter !== synchronizedPassageChapter ||
            snapshot.characters !== synchronizedPassageCharacters ||
            snapshot.settings.narrationChunkChars != synchronizedPassageChunkChars

    private fun shouldSynchronizeStory(snapshot: WhisperbookUiSnapshot): Boolean =
        snapshot.storyChapters !== synchronizedStoryChapters ||
            snapshot.characters !== synchronizedStoryCharacters ||
            snapshot.settings.narrationChunkChars != synchronizedStoryChunkChars

    private fun projectStory(snapshot: WhisperbookUiSnapshot): StoryPreviewProjection =
        projectStoryPreview(
            chapters = snapshot.storyChapters,
            characters = snapshot.characters,
            maxChars = snapshot.settings.narrationChunkChars,
        )

    private fun projectPassages(snapshot: WhisperbookUiSnapshot): List<PassageUi> =
        projectPlaybackPassages(
            chapter = snapshot.selectedChapter,
            characters = snapshot.characters,
            maxChars = snapshot.settings.narrationChunkChars,
        )

    private fun projectReaderPassages(
        snapshot: WhisperbookUiSnapshot,
        playbackPassages: List<PassageUi>,
    ): List<PassageUi> = projectReaderPassages(snapshot.selectedChapter, playbackPassages)

    private fun com.whisperbook.app.domain.model.PreparationState.overallProgress(): Float {
        val local = progressFraction.coerceIn(0f, 1f)
        return when (stage) {
            PreparationStage.COPY_AND_VALIDATE -> 0.08f * local
            PreparationStage.READING_CHAPTERS -> 0.08f + 0.37f * local
            PreparationStage.AWAITING_CHAPTER_SELECTION,
            PreparationStage.AWAITING_NARRATION_SETUP,
            -> 0.45f
            PreparationStage.FINDING_CHARACTERS -> 0.45f + 0.25f * local
            PreparationStage.AWAITING_STORY_REVIEW -> 0.70f
            PreparationStage.ASSIGNING_VOICES -> 0.70f + 0.12f * local
            PreparationStage.PREPARING_AUDIO -> 0.82f + 0.18f * local
            PreparationStage.READY -> 1f
            PreparationStage.FAILED -> local
        }.coerceIn(0f, 1f)
    }

    fun imported(uri: Uri, onSuccess: () -> Unit = {}) {
        importedUri = uri
        importError = null
        preparationProgress = .12f
        preparationStage = 0
        val actions = productionActions
        if (actions == null) {
            onSuccess()
            return
        }
        pendingImportCompletion = PendingImportCompletion(
            existingBookIds = books.mapTo(hashSetOf(), LibraryBookUi::id),
            onSuccess = onSuccess,
        )
        actions.importBook(uri)
    }

    fun chooseNarrationSetupLanguage(languageCode: String) {
        if (NarrationLanguage.fromCode(languageCode) == null) return
        narrationSetupLanguageCode = languageCode
        if (narrationSetupVoiceOptions.none { it.id == narrationSetupNarratorVoiceId }) {
            narrationSetupNarratorVoiceId = narrationSetupVoiceOptions.firstOrNull()?.id.orEmpty()
        }
    }

    fun setChapterSelected(chapterId: String, selected: Boolean) {
        if (chapterId.isBlank()) return
        if (productionActions == null) {
            val index = chapterPlan.indexOfFirst { it.chapter.id == chapterId }
            if (index >= 0) chapterPlan[index] = chapterPlan[index].copy(isSelected = selected)
        }
        productionActions?.setChapterSelected(chapterId, selected)
    }

    fun moveChapter(chapterId: String, targetSelectedPosition: Int) {
        if (chapterId.isBlank() || targetSelectedPosition < 0) return
        productionActions?.moveChapter(chapterId, targetSelectedPosition)
    }

    fun selectAllChapters() {
        if (productionActions == null) {
            chapterPlan.indices.forEach { index ->
                chapterPlan[index] = chapterPlan[index].copy(isSelected = true)
            }
        }
        productionActions?.selectAllChapters()
    }

    fun deselectAllChapters() {
        if (productionActions == null) {
            chapterPlan.indices.forEach { index ->
                chapterPlan[index] = chapterPlan[index].copy(isSelected = false)
            }
        }
        productionActions?.deselectAllChapters()
    }

    fun restoreOriginalChapterOrder() {
        productionActions?.restoreOriginalChapterOrder()
    }

    fun resetChapterPlan() {
        if (productionActions == null) {
            val reset = chapterPlan.sortedBy { it.chapter.ordinal }.mapIndexed { index, entry ->
                entry.copy(isSelected = true, customPosition = index)
            }
            chapterPlan.clear()
            chapterPlan.addAll(reset)
        }
        productionActions?.resetChapterPlan()
    }

    fun confirmChapterPlan(onSuccess: () -> Unit = {}) {
        if (selectedChapterCount <= 0 || pendingChapterPlanCompletion != null) return
        val actions = productionActions
        if (actions == null) {
            onSuccess()
            return
        }
        pendingChapterPlanCompletion = PendingChapterPlanCompletion(currentBookId, onSuccess)
        actions.confirmChapterPlan()
    }

    fun chooseNarrationSetupNarrator(voiceId: String) {
        if (narrationSetupVoiceOptions.none { it.id == voiceId }) return
        narrationSetupNarratorVoiceId = voiceId
    }

    fun confirmNarrationSetup(onSuccess: () -> Unit = {}) {
        if (!narrationSetupRequired) return
        val actions = productionActions
        if (actions == null) {
            narrationSetupRequired = false
            onSuccess()
            return
        }
        if (pendingNarrationSetupCompletion != null) return
        pendingNarrationSetupCompletion = PendingNarrationSetupCompletion(
            bookId = currentBookId,
            onSuccess = onSuccess,
        )
        actions.confirmNarrationSetup(
            narrationSetupLanguageCode,
            narrationSetupNarratorVoiceId,
        )
    }

    fun confirmStoryReview(onSuccess: () -> Unit = {}) {
        if (!storyReviewRequired) return
        val actions = productionActions
        if (actions == null) {
            storyReviewRequired = false
            onSuccess()
            return
        }
        if (pendingStoryReviewCompletion != null) return
        pendingStoryReviewCompletion = PendingStoryReviewCompletion(
            bookId = currentBookId,
            onSuccess = onSuccess,
        )
        actions.confirmStoryReview()
    }

    fun previewNarrationSetupVoice(voiceId: String = narrationSetupNarratorVoiceId) {
        if (narrationSetupVoiceOptions.none { it.id == voiceId }) return
        productionActions?.previewNarrationSetupVoice(voiceId, narrationSetupLanguageCode)
        if (productionActions == null) togglePlayback()
    }

    fun requiresNarrationSetup(bookId: String): Boolean =
        books.firstOrNull { it.id == bookId }?.needsNarrationSetup == true

    fun requiresChapterReview(bookId: String): Boolean =
        books.firstOrNull { it.id == bookId }?.needsChapterReview == true

    fun requiresStoryReview(bookId: String): Boolean =
        books.firstOrNull { it.id == bookId }?.needsStoryReview == true

    fun importFailed(message: String) {
        importError = message
    }

    fun retryPreparation() {
        importError = null
        preparationFailed = false
        productionActions?.retryPreparation()
    }

    fun pausePreparation() {
        productionActions?.pausePreparation()
    }

    fun resumePreparation() {
        productionActions?.resumePreparation()
    }

    fun cancelPreparation() {
        productionActions?.cancelPreparation()
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
    ) = correctPassageSpeakers(listOf(passageId), speakerId, scope)

    fun correctPassageSpeakers(
        passageIds: List<String>,
        speakerId: String,
        scope: SpeakerCorrectionScope,
    ) {
        val distinctPassageIds = passageIds.distinct()
        if (distinctPassageIds.isEmpty()) return
        if (productionActions == null) {
            val target = cast.firstOrNull { it.id == speakerId } ?: return
            val source = passages.firstOrNull { it.sourcePassageId in distinctPassageIds } ?: return
            val sourceSpeakerId = source.speakerId
            val sourceKeys = passages
                .filter { it.sourcePassageId in distinctPassageIds }
                .mapTo(linkedSetOf(), PassageUi::text)
                .mapTo(linkedSetOf(), ::speakerPhraseMatchKey)
                .filterTo(linkedSetOf(), String::isNotBlank)
            passages.indices.forEach { index ->
                val candidate = passages[index]
                val matches = when (scope) {
                    SpeakerCorrectionScope.THIS_PASSAGE -> candidate.sourcePassageId in distinctPassageIds
                    SpeakerCorrectionScope.MATCHING_PHRASES ->
                        candidate.speakerId == sourceSpeakerId &&
                            speakerPhraseMatchKey(candidate.text) in sourceKeys
                }
                if (matches) {
                    passages[index] = candidate.copy(
                        speaker = target.role,
                        speakerId = target.id,
                        speakerName = target.character,
                    )
                }
            }
            readerPassages.clear()
            readerPassages.addAll(groupReaderPassages(passages))
        }
        productionActions?.correctPassageSpeakers(distinctPassageIds, speakerId, scope)
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
        val compatibleVoices = bookVoiceOptions
        if (index < 0 || compatibleVoices.isEmpty()) return
        val member = cast[index]
        val currentIndex = compatibleVoices.indexOfFirst { it.id == member.voiceId }
        val nextIndex = if (currentIndex >= 0) (currentIndex + 1) % compatibleVoices.size else 0
        val next = compatibleVoices[nextIndex]
        assignVoice(characterId, next.id)
    }

    fun assignVoice(
        characterId: String,
        voiceId: String,
        regenerationScope: VoiceRegenerationScope = VoiceRegenerationScope.WHOLE_BOOK,
    ) {
        val voice = bookVoiceOptions.firstOrNull { it.id == voiceId } ?: return
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
        if (bookVoiceOptions.none { it.id == voiceId }) return
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

internal fun projectPlaybackPassages(
    chapter: Chapter?,
    characters: List<StoryCharacter>,
    maxChars: Int,
): List<PassageUi> {
    val charactersById = characters.associate { character ->
        character.id to CharacterPassageUi(
            name = character.displayName,
            role = character.colorRole.toSpeakerRole(),
        )
    }
    return chapter?.passages.orEmpty().flatMap { passage ->
        val character = charactersById[passage.speakerId]
        NarrationTextChunker.chunks(
            passageId = passage.id,
            text = passage.text,
            maxChars = maxChars,
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

internal fun projectReaderPassages(
    chapter: Chapter?,
    playbackPassages: List<PassageUi>,
): List<PassageUi> {
    val playbackBySourceId = playbackPassages.groupBy(PassageUi::sourcePassageId)
    val sourcePassages = chapter?.passages.orEmpty().flatMap { source ->
        val playback = playbackBySourceId[source.id].orEmpty()
        if (playback.isEmpty() || source.text.length > MAX_SAFE_READER_GROUP_CHARS) {
            groupReaderPassages(playback)
        } else {
            listOf(
                playback.first().copy(
                    text = source.text.trim(),
                    playbackPassageIds = playback.flatMap(PassageUi::playbackPassageIds),
                    sourcePassageIds = listOf(source.id),
                ),
            )
        }
    }
    return groupReaderPassages(sourcePassages)
}

internal fun characterPortraitRes(colorRole: CharacterColorRole): Int = when (colorRole) {
    CharacterColorRole.NARRATOR, CharacterColorRole.BLUE -> com.whisperbook.app.R.drawable.portrait_narrator
    CharacterColorRole.ELARA_BURGUNDY, CharacterColorRole.BURGUNDY -> com.whisperbook.app.R.drawable.portrait_elara
    CharacterColorRole.FOX_ORANGE, CharacterColorRole.ORANGE -> com.whisperbook.app.R.drawable.portrait_fox
}

private fun PreparationStage.isPlaybackSafeStage(): Boolean =
    this == PreparationStage.PREPARING_AUDIO || this == PreparationStage.READY

private fun PreparationStage.isParsingStage(): Boolean =
    this == PreparationStage.COPY_AND_VALIDATE || this == PreparationStage.READING_CHAPTERS

private fun PreparationStage.isNarrationPreparationStage(): Boolean = when (this) {
    PreparationStage.FINDING_CHARACTERS,
    PreparationStage.ASSIGNING_VOICES,
    PreparationStage.PREPARING_AUDIO,
    -> true
    else -> false
}

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

internal fun CharacterColorRole.toSpeakerRole(): SpeakerRole = when (this) {
    CharacterColorRole.NARRATOR, CharacterColorRole.BLUE -> SpeakerRole.Narrator
    CharacterColorRole.ELARA_BURGUNDY, CharacterColorRole.BURGUNDY -> SpeakerRole.Elara
    CharacterColorRole.FOX_ORANGE, CharacterColorRole.ORANGE -> SpeakerRole.Fox
}
