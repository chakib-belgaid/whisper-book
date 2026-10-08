package com.whisperbook.app.domain

import android.net.Uri
import com.whisperbook.app.domain.model.AppSettings
import com.whisperbook.app.domain.model.AudioSegment
import com.whisperbook.app.domain.model.Book
import com.whisperbook.app.domain.model.BookFormat
import com.whisperbook.app.domain.model.Chapter
import com.whisperbook.app.domain.model.ChapterPlanEntry
import com.whisperbook.app.domain.model.CharacterVoiceAssignment
import com.whisperbook.app.domain.model.ChapterVoiceAssignmentSnapshot
import com.whisperbook.app.domain.model.PlaybackCursor
import com.whisperbook.app.domain.model.PlaybackPreparationProgress
import com.whisperbook.app.domain.model.PlaybackNarrationReload
import com.whisperbook.app.domain.model.PreparationState
import com.whisperbook.app.domain.model.StoryCharacter
import com.whisperbook.app.domain.model.VoiceDescriptor
import com.whisperbook.app.domain.model.VoiceRegenerationScope
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

data class ImportedBook(
    val title: String,
    val author: String?,
    val format: BookFormat,
    val privateFile: File,
    val sha256: String,
)

data class ExtractedPublication(
    val title: String,
    val author: String?,
    val chapters: List<ExtractedChapter>,
    /** Canonical, cleaned document produced before passages are persisted. */
    val markdown: String = "",
)

data class ExtractedChapter(val title: String, val paragraphs: List<String>)

data class AttributedPublication(
    val chapters: List<Chapter>,
    val characters: List<StoryCharacter>,
)

data class SynthesisRequest(
    val text: String,
    val voice: VoiceDescriptor,
    val speed: Float,
    val cacheKey: String,
    val languageCode: String = "en",
)

data class SynthesisResult(
    val pcm16: ShortArray,
    val sampleRate: Int,
    val durationMs: Long,
)

/**
 * A previous local narration generation which remains available for a bounded undo window.
 *
 * [previousAssignment] is persisted with the retained audio when supplied, allowing callers to
 * restore both the files and the voice choice after a process restart. An empty [passageIds] set
 * means the retention covered every cached passage owned by the character.
 */
data class AudioRetentionGeneration(
    val id: String,
    val characterId: String,
    val createdAtEpochMs: Long,
    val expiresAtEpochMs: Long,
    val segmentCount: Int,
    val passageIds: Set<String>,
    val previousAssignment: CharacterVoiceAssignment? = null,
    val bookId: String? = null,
    val previousChapterAssignments: List<ChapterVoiceAssignmentSnapshot> = emptyList(),
    val voiceRegenerationScope: VoiceRegenerationScope = VoiceRegenerationScope.WHOLE_BOOK,
    val fromChapterOrdinal: Int = 0,
) {
    init {
        require(id.isNotBlank())
        require(characterId.isNotBlank())
        require(expiresAtEpochMs > createdAtEpochMs)
        require(segmentCount >= 0)
        require(passageIds.none(String::isBlank))
        require(previousAssignment == null || previousAssignment.characterId == characterId)
        require(bookId == null || bookId.isNotBlank())
        require(previousChapterAssignments.all { it.assignment.characterId == characterId })
        require(fromChapterOrdinal >= 0)
    }

    val isScoped: Boolean get() = passageIds.isNotEmpty()

    companion object {
        const val DEFAULT_GRACE_PERIOD_MS = 24L * 60L * 60L * 1_000L
    }
}

interface BookImporter {
    suspend fun import(uri: Uri): Result<ImportedBook>
}

interface PublicationExtractor {
    suspend fun extract(book: ImportedBook): Result<ExtractedPublication>

    suspend fun extract(
        book: ImportedBook,
        onProgress: suspend (completedUnits: Int, totalUnits: Int) -> Unit,
    ): Result<ExtractedPublication> {
        val result = extract(book)
        if (result.isSuccess) onProgress(1, 1)
        return result
    }
}

interface SpeakerAttributor {
    suspend fun attribute(bookId: String, publication: ExtractedPublication): AttributedPublication

    /**
     * Attributes one already-persisted chapter without deriving its identity from list position.
     *
     * [chapterId] and [chapterOrdinal] are authoritative database values. [knownCharacters] is the
     * cumulative, book-scoped catalog discovered before this chapter, so implementations can keep
     * character IDs, aliases, colors, and profile evidence stable across incremental calls.
     */
    suspend fun attributeChapter(
        bookId: String,
        chapterId: String,
        chapterOrdinal: Int,
        chapter: ExtractedChapter,
        knownCharacters: List<StoryCharacter> = emptyList(),
    ): AttributedPublication
}

interface LocalTtsEngine : AutoCloseable {
    suspend fun warmUp(): Result<Unit>
    suspend fun voices(): List<VoiceDescriptor>
    suspend fun synthesize(request: SynthesisRequest): Result<SynthesisResult>
    override fun close()
}

interface VoicePreviewPlayer : AutoCloseable {
    suspend fun play(
        text: String,
        voice: VoiceDescriptor,
        speed: Float,
        languageCode: String = "en",
    ): Result<Unit>
    fun stop()
    override fun close()
}

interface AudioSegmentStore {
    suspend fun find(cacheKey: String): AudioSegment?
    suspend fun write(request: SynthesisRequest, result: SynthesisResult): AudioSegment
    suspend fun invalidateForCharacter(characterId: String)

    /**
     * Retains the current generation in-place so an active player can keep reading its WAV paths.
     * [passageIds] scopes retention to selected chapters when their passage IDs are known.
     */
    suspend fun retainForCharacter(
        characterId: String,
        previousAssignment: CharacterVoiceAssignment? = null,
        passageIds: Set<String> = emptySet(),
        gracePeriodMs: Long = AudioRetentionGeneration.DEFAULT_GRACE_PERIOD_MS,
        bookId: String? = null,
        previousChapterAssignments: List<ChapterVoiceAssignmentSnapshot> = emptyList(),
        scope: VoiceRegenerationScope = VoiceRegenerationScope.WHOLE_BOOK,
        fromChapterOrdinal: Int = 0,
    ): AudioRetentionGeneration? {
        invalidateForCharacter(characterId)
        return null
    }

    /** Restores retained files and returns the persisted generation/assignment, if still valid. */
    suspend fun restoreRetainedGeneration(generationId: String): AudioRetentionGeneration? = null

    /** Lists undoable generations, including after a process restart. */
    suspend fun retainedAudioGenerations(
        characterId: String? = null,
    ): List<AudioRetentionGeneration> = emptyList()

    /** Convenience for a visible "Revert voice" action. */
    suspend fun latestRetainedVoiceChange(characterId: String): AudioRetentionGeneration? =
        retainedAudioGenerations(characterId).maxByOrNull { it.createdAtEpochMs }

    /** Deletes only retained generations whose grace period has elapsed. */
    suspend fun cleanupExpiredRetainedAudio(): Int = 0

    suspend fun trimTo(limitBytes: Long)
}

interface PreparationCoordinator {
    /** Returns only after the durable work request has been accepted by the scheduler. */
    suspend fun enqueue(bookId: String)
    /** Pauses durable work after WorkManager has stopped the active worker. */
    suspend fun pause(bookId: String)
    /** Resumes a paused job from its persisted preparation checkpoints. */
    suspend fun resume(bookId: String)
    suspend fun regenerateAudio(bookId: String, fromChapterOrdinal: Int)
    /** Stops future work while retaining already completed local preparation. */
    suspend fun cancel(bookId: String)
    fun observe(bookId: String): Flow<PreparationState>
}

interface LibraryRepository {
    fun observeBooks(): Flow<List<Book>>
    fun observeBook(bookId: String): Flow<Book?>
    fun observeChapters(bookId: String): Flow<List<Chapter>>
    fun observeChapterHeaders(bookId: String): Flow<List<Chapter>> = observeChapters(bookId).map { chapters ->
        chapters.map { chapter -> chapter.copy(passages = emptyList()) }
    }
    fun observeChapter(bookId: String, chapterId: String): Flow<Chapter?> = observeChapters(bookId).map { chapters ->
        chapters.firstOrNull { chapter -> chapter.id == chapterId && chapter.bookId == bookId }
    }
    fun observeChapterPlan(bookId: String): Flow<List<ChapterPlanEntry>> =
        observeChapterHeaders(bookId).map { chapters ->
            chapters.mapIndexed { position, chapter ->
                ChapterPlanEntry(chapter = chapter, isSelected = true, customPosition = position)
            }
        }
    fun observeCharacters(bookId: String): Flow<List<StoryCharacter>>
    suspend fun importBook(
        uri: Uri,
        narrationLanguageCode: String = "en",
    ): Result<String>
    suspend fun confirmNarrationSetup(bookId: String, languageCode: String, narratorVoiceId: String)
    /** Records that the listener reviewed the attributed story, opening voice generation. */
    suspend fun confirmStoryReview(bookId: String)
    /** Creates an all-selected plan after the parser has persisted the stable chapter list. */
    suspend fun initializeChapterPlan(bookId: String) = Unit
    suspend fun setChapterSelected(bookId: String, chapterId: String, selected: Boolean) = Unit
    /** [targetSelectedPosition] is zero-based within the selected-chapter list. */
    suspend fun moveChapter(bookId: String, chapterId: String, targetSelectedPosition: Int) = Unit
    suspend fun selectAllChapters(bookId: String) = Unit
    suspend fun deselectAllChapters(bookId: String) = Unit
    suspend fun restoreOriginalChapterOrder(bookId: String) = Unit
    suspend fun resetChapterPlan(bookId: String) = Unit
    suspend fun confirmChapterPlan(bookId: String) = Unit
    suspend fun getSelectedChapterHeaders(bookId: String): List<Chapter> = emptyList()
    suspend fun updateVoiceAssignment(assignment: CharacterVoiceAssignment)
    suspend fun deleteBook(bookId: String)
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun update(transform: (AppSettings) -> AppSettings)
}

interface PlaybackGateway {
    val cursor: Flow<PlaybackCursor?>
    val preparationProgress: Flow<PlaybackPreparationProgress?> get() = flowOf(null)
    suspend fun playBook(bookId: String, chapterId: String? = null)
    suspend fun play()
    suspend fun pause()
    suspend fun seekBy(deltaMs: Long)
    suspend fun seekToPassage(passageId: String)
    suspend fun setSpeed(speed: Float)
    suspend fun setSleepTimer(minutes: Int?)

    /**
     * Drops already-prepared future chapters whose audio is no longer authoritative while leaving
     * the currently playing chapter untouched. Implementations without a prefetched queue may
     * safely keep the default no-op.
     */
    suspend fun invalidateQueuedChapters(bookId: String, chapterIds: Set<String>) = Unit

    /** Cancels stale progressive work and clears the current queue when its chapter is affected. */
    suspend fun invalidateNarrationProfile(
        bookId: String,
        chapterIds: Set<String>,
    ): PlaybackNarrationReload? {
        invalidateQueuedChapters(bookId, chapterIds)
        return null
    }

    /** Rebuilds a queue after a narration profile change and restores source-passage position. */
    suspend fun reloadNarrationProfile(reload: PlaybackNarrationReload) = Unit
}
