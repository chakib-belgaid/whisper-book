package com.whisperbook.app.data.repository

import android.net.Uri
import androidx.room.withTransaction
import com.whisperbook.app.data.local.db.BookEntity
import com.whisperbook.app.data.local.db.PreparationJobEntity
import com.whisperbook.app.data.local.db.WhisperBookDatabase
import com.whisperbook.app.data.local.db.toDomain
import com.whisperbook.app.data.local.db.toEntity
import com.whisperbook.app.data.local.db.toHeaderDomain
import com.whisperbook.app.domain.BookImporter
import com.whisperbook.app.domain.LibraryRepository
import com.whisperbook.app.domain.model.Book
import com.whisperbook.app.domain.model.Chapter
import com.whisperbook.app.domain.model.ChapterPlanEntry
import com.whisperbook.app.domain.model.CharacterVoiceAssignment
import com.whisperbook.app.domain.model.NarrationLanguage
import com.whisperbook.app.domain.model.PreparationStage
import com.whisperbook.app.domain.model.StoryCharacter
import com.whisperbook.app.engine.document.PublicationMarkdownFiles
import com.whisperbook.app.engine.metadata.CharacterMetadataCatalog
import java.io.File
import java.util.UUID
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class RoomLibraryRepository(
    private val database: WhisperBookDatabase,
    private val bookImporter: BookImporter,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    private val clockEpochMs: () -> Long = System::currentTimeMillis,
    private val characterMetadataCatalog: CharacterMetadataCatalog? = null,
) : LibraryRepository {
    override fun observeBooks(): Flow<List<Book>> = database.bookDao()
        .observeAll()
        .map { books -> books.map { it.toDomain() } }

    override fun observeBook(bookId: String): Flow<Book?> = database.bookDao()
        .observeById(bookId)
        .map { it?.toDomain() }

    override fun observeChapters(bookId: String): Flow<List<Chapter>> = database.chapterDao()
        .observeForBook(bookId)
        .map { chapters -> chapters.map { it.toDomain() } }

    override fun observeChapterHeaders(bookId: String): Flow<List<Chapter>> = database.chapterDao()
        .observeHeadersForBook(bookId, PREPARATION_UNATTRIBUTED_RULE)
        .map { chapters -> chapters.map { it.toDomain() } }
        .distinctUntilChanged()

    override fun observeChapter(bookId: String, chapterId: String): Flow<Chapter?> = database.chapterDao()
        .observeById(bookId, chapterId)
        .map { chapter -> chapter?.toDomain() }
        .distinctUntilChanged()

    override fun observeChapterPlan(bookId: String): Flow<List<ChapterPlanEntry>> =
        database.chapterPlanDao()
            .observeForBook(bookId, PREPARATION_UNATTRIBUTED_RULE)
            .map { entries -> entries.map { it.toDomain() } }
            .distinctUntilChanged()

    override fun observeCharacters(bookId: String): Flow<List<StoryCharacter>> =
        database.storyCharacterDao()
            .observeForBook(bookId)
            .map { characters -> characters.map { it.toDomain() } }

    override suspend fun importBook(uri: Uri, narrationLanguageCode: String): Result<String> {
        val initialLanguage = narrationLanguageCode
            .takeIf { it in NarrationLanguage.supportedCodes }
            ?: NarrationLanguage.ENGLISH.code
        val imported = bookImporter.import(uri).getOrElse { error ->
            if (error is CancellationException) throw error
            return Result.failure(error)
        }
        return try {
            val now = clockEpochMs()
            val bookId = database.withTransaction {
                imported.sha256.takeIf(String::isNotBlank)
                    ?.let { database.bookDao().findIdBySourceSha256(it) }
                    ?.let { return@withTransaction it }
                val newBookId = idGenerator()
                database.bookDao().insert(
                    BookEntity(
                        id = newBookId,
                        title = imported.title.ifBlank { "Untitled book" },
                        author = imported.author,
                        format = imported.format.name,
                        sourceUri = uri.toString(),
                        privateSourcePath = imported.privateFile.absolutePath,
                        sourceSha256 = imported.sha256,
                        coverPath = null,
                        currentChapterId = null,
                        currentPassageId = null,
                        progressFraction = 0f,
                        lastOpenedAtEpochMs = now,
                        narrationLanguageCode = initialLanguage,
                        narrationProfileRevision = 0L,
                        narrationProfileSeeded = true,
                        preferredNarratorVoiceId = DEFAULT_NARRATOR_VOICE_ID,
                        narrationSetupConfirmed = false,
                        storyReviewConfirmed = false,
                    ),
                )
                database.preparationJobDao().upsert(
                    PreparationJobEntity(
                        bookId = newBookId,
                        stage = PreparationStage.COPY_AND_VALIDATE.name,
                        completedUnits = 1,
                        totalUnits = 1,
                        progressFraction = 1f,
                        message = "Copied securely to this device",
                        retryable = false,
                        chapterPlanConfirmed = false,
                        planRevision = 0L,
                        activeChapterId = null,
                        attemptCount = 0,
                        updatedAtEpochMs = now,
                    ),
                )
                newBookId
            }
            Result.success(bookId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    override suspend fun confirmNarrationSetup(
        bookId: String,
        languageCode: String,
        narratorVoiceId: String,
    ) {
        require(bookId.isNotBlank()) { "Book id must not be blank" }
        require(languageCode in NarrationLanguage.supportedCodes) { "Unsupported narration language" }
        require(narratorVoiceId.isNotBlank()) { "Narrator voice id must not be blank" }
        check(database.bookDao().confirmNarrationSetup(bookId, languageCode, narratorVoiceId) == 1) {
            "This book's narration setup is no longer awaiting confirmation"
        }
    }

    override suspend fun confirmStoryReview(bookId: String) {
        require(bookId.isNotBlank()) { "Book id must not be blank" }
        check(database.bookDao().confirmStoryReview(bookId) == 1) {
            "This book's story review is no longer awaiting confirmation"
        }
    }

    override suspend fun initializeChapterPlan(bookId: String) {
        require(bookId.isNotBlank()) { "Book id must not be blank" }
        val now = clockEpochMs()
        database.withTransaction {
            check(database.bookDao().getById(bookId) != null) { "Unknown book: $bookId" }
            database.chapterPlanDao().initializeAllSelected(bookId, now)
            val chapterCount = database.chapterDao().countForBook(bookId)
            check(chapterCount > 0) { "A chapter plan requires at least one parsed chapter" }
            check(database.chapterPlanDao().countForBook(bookId) == chapterCount) {
                "Chapter plan does not cover every parsed chapter"
            }
            check(
                database.preparationJobDao().awaitChapterSelection(
                    bookId = bookId,
                    stage = PreparationStage.AWAITING_CHAPTER_SELECTION.name,
                    message = "Choose the chapters you want to hear",
                    updatedAtEpochMs = now,
                ) == 1,
            ) { "Missing preparation job for book: $bookId" }
        }
    }

    override suspend fun setChapterSelected(bookId: String, chapterId: String, selected: Boolean) {
        require(bookId.isNotBlank()) { "Book id must not be blank" }
        require(chapterId.isNotBlank()) { "Chapter id must not be blank" }
        val now = clockEpochMs()
        database.withTransaction {
            val changed = database.chapterPlanDao().setSelected(bookId, chapterId, selected, now)
            if (changed == 0) {
                check(database.chapterPlanDao().getCustomPosition(bookId, chapterId) != null) {
                    "Chapter is not part of this book's plan"
                }
                return@withTransaction
            }
            incrementPlanRevisionOrThrow(bookId, now)
        }
    }

    override suspend fun moveChapter(
        bookId: String,
        chapterId: String,
        targetSelectedPosition: Int,
    ) {
        require(bookId.isNotBlank()) { "Book id must not be blank" }
        require(chapterId.isNotBlank()) { "Chapter id must not be blank" }
        require(targetSelectedPosition >= 0) { "Target position must not be negative" }
        val now = clockEpochMs()
        database.withTransaction {
            val selected = database.chapterPlanDao().getEntriesForBook(bookId).filter { it.isSelected }
            val sourcePosition = selected.indexOfFirst { it.chapterId == chapterId }
            check(sourcePosition >= 0) { "Only selected chapters can be reordered" }
            require(targetSelectedPosition in selected.indices) { "Target position is outside the selected plan" }
            if (sourcePosition == targetSelectedPosition) return@withTransaction

            val reordered = selected.toMutableList().apply {
                add(targetSelectedPosition, removeAt(sourcePosition))
            }
            val availablePositions = selected.map { it.customPosition }
            val changed = reordered.mapIndexedNotNull { index, entry ->
                val targetPosition = availablePositions[index]
                if (entry.customPosition == targetPosition) null else entry to targetPosition
            }
            // Vacate the affected unique slots first; observers only see the committed result.
            changed.forEachIndexed { index, (entry, _) ->
                check(
                    database.chapterPlanDao().setCustomPosition(
                        bookId,
                        entry.chapterId,
                        -index - 1,
                        now,
                    ) == 1,
                )
            }
            changed.forEach { (entry, targetPosition) ->
                check(
                    database.chapterPlanDao().setCustomPosition(
                        bookId,
                        entry.chapterId,
                        targetPosition,
                        now,
                    ) == 1,
                )
            }
            incrementPlanRevisionOrThrow(bookId, now)
        }
    }

    override suspend fun selectAllChapters(bookId: String) {
        setAllChaptersSelected(bookId, selected = true)
    }

    override suspend fun deselectAllChapters(bookId: String) {
        setAllChaptersSelected(bookId, selected = false)
    }

    override suspend fun restoreOriginalChapterOrder(bookId: String) {
        require(bookId.isNotBlank()) { "Book id must not be blank" }
        val now = clockEpochMs()
        database.withTransaction {
            if (!restoreOriginalOrderIfNeeded(bookId, now)) return@withTransaction
            incrementPlanRevisionOrThrow(bookId, now)
        }
    }

    override suspend fun resetChapterPlan(bookId: String) {
        require(bookId.isNotBlank()) { "Book id must not be blank" }
        val now = clockEpochMs()
        database.withTransaction {
            val selectionChanged = database.chapterPlanDao().setAllSelected(bookId, true, now) > 0
            val orderChanged = restoreOriginalOrderIfNeeded(bookId, now)
            if (!selectionChanged && !orderChanged) return@withTransaction
            incrementPlanRevisionOrThrow(bookId, now)
        }
    }

    override suspend fun confirmChapterPlan(bookId: String) {
        require(bookId.isNotBlank()) { "Book id must not be blank" }
        val now = clockEpochMs()
        database.withTransaction {
            val chapterCount = database.chapterDao().countForBook(bookId)
            check(chapterCount > 0 && database.chapterPlanDao().countForBook(bookId) == chapterCount) {
                "Chapter plan does not cover every parsed chapter"
            }
            check(database.chapterPlanDao().countSelectedForBook(bookId) > 0) {
                "Select at least one chapter before continuing"
            }
            check(
                database.preparationJobDao().confirmChapterPlan(
                    bookId = bookId,
                    nextStage = PreparationStage.AWAITING_NARRATION_SETUP.name,
                    updatedAtEpochMs = now,
                ) == 1,
            ) { "Missing preparation job for book: $bookId" }
        }
    }

    override suspend fun getSelectedChapterHeaders(bookId: String): List<Chapter> =
        database.chapterPlanDao().getSelectedChapterHeaders(bookId).map { it.toHeaderDomain() }

    override suspend fun updateVoiceAssignment(assignment: CharacterVoiceAssignment) {
        database.voiceAssignmentDao().upsert(assignment.toEntity())
    }

    override suspend fun deleteBook(bookId: String) {
        val artifacts = database.withTransaction {
            val book = database.bookDao().getById(bookId) ?: return@withTransaction null
            val audioPaths = database.audioSegmentDao().getPathsForBook(bookId).distinct()
            database.bookDao().deleteById(bookId)
            val privateSourcePath = book.privateSourcePath
                ?.takeIf { database.bookDao().countByPrivateSourcePath(it) == 0 }
            val unreferencedAudioPaths = audioPaths.filter { database.audioSegmentDao().countByPath(it) == 0 }
            DeletedBookArtifacts(privateSourcePath, unreferencedAudioPaths)
        } ?: return
        withContext(Dispatchers.IO) {
            artifacts.privateSourcePath?.let(::File)?.let { privateSource ->
                PublicationMarkdownFiles.forSource(privateSource).delete()
                privateSource.delete()
            }
            artifacts.audioPaths.map(::File).forEach(File::delete)
            characterMetadataCatalog?.delete(bookId)
        }
    }

    private suspend fun setAllChaptersSelected(bookId: String, selected: Boolean) {
        require(bookId.isNotBlank()) { "Book id must not be blank" }
        val now = clockEpochMs()
        database.withTransaction {
            if (database.chapterPlanDao().setAllSelected(bookId, selected, now) == 0) {
                check(database.chapterPlanDao().countForBook(bookId) > 0) {
                    "This book has no initialized chapter plan"
                }
                return@withTransaction
            }
            incrementPlanRevisionOrThrow(bookId, now)
        }
    }

    private suspend fun restoreOriginalOrderIfNeeded(bookId: String, updatedAtEpochMs: Long): Boolean {
        val entries = database.chapterPlanDao().getEntriesForBook(bookId)
        check(entries.isNotEmpty()) { "This book has no initialized chapter plan" }
        val sourceOrdinals = database.chapterDao().getHeadersForBook(bookId).associate { it.id to it.ordinal }
        val changed = entries.any { entry -> sourceOrdinals[entry.chapterId] != entry.customPosition }
        if (!changed) return false
        val maximumPosition = database.chapterPlanDao().getMaxCustomPosition(bookId)
        val offset = Math.addExact(Math.addExact(maximumPosition, entries.size), 1)
        database.chapterPlanDao().offsetAllPositions(bookId, offset, updatedAtEpochMs)
        database.chapterPlanDao().restoreOriginalPositions(bookId, updatedAtEpochMs)
        return true
    }

    private suspend fun incrementPlanRevisionOrThrow(bookId: String, updatedAtEpochMs: Long) {
        check(database.preparationJobDao().incrementPlanRevision(bookId, updatedAtEpochMs) == 1) {
            "Missing preparation job for book: $bookId"
        }
    }
}

private data class DeletedBookArtifacts(
    val privateSourcePath: String?,
    val audioPaths: List<String>,
)

private const val DEFAULT_NARRATOR_VOICE_ID = "bella"
private const val PREPARATION_UNATTRIBUTED_RULE = "preparation-unattributed"
