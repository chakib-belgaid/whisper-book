package com.whisperbook.app.data.repository

import android.net.Uri
import androidx.room.withTransaction
import com.whisperbook.app.data.local.db.BookEntity
import com.whisperbook.app.data.local.db.PreparationJobEntity
import com.whisperbook.app.data.local.db.WhisperBookDatabase
import com.whisperbook.app.data.local.db.toDomain
import com.whisperbook.app.data.local.db.toEntity
import com.whisperbook.app.domain.BookImporter
import com.whisperbook.app.domain.LibraryRepository
import com.whisperbook.app.domain.model.Book
import com.whisperbook.app.domain.model.Chapter
import com.whisperbook.app.domain.model.CharacterVoiceAssignment
import com.whisperbook.app.domain.model.PreparationStage
import com.whisperbook.app.domain.model.StoryCharacter
import java.util.UUID
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomLibraryRepository(
    private val database: WhisperBookDatabase,
    private val bookImporter: BookImporter,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    private val clockEpochMs: () -> Long = System::currentTimeMillis,
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

    override fun observeCharacters(bookId: String): Flow<List<StoryCharacter>> =
        database.storyCharacterDao()
            .observeForBook(bookId)
            .map { characters -> characters.map { it.toDomain() } }

    override suspend fun importBook(uri: Uri): Result<String> {
        val imported = bookImporter.import(uri).getOrElse { error ->
            if (error is CancellationException) throw error
            return Result.failure(error)
        }
        return try {
            val bookId = idGenerator()
            val now = clockEpochMs()
            database.withTransaction {
                database.bookDao().insert(
                    BookEntity(
                        id = bookId,
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
                    ),
                )
                database.preparationJobDao().upsert(
                    PreparationJobEntity(
                        bookId = bookId,
                        stage = PreparationStage.COPY_AND_VALIDATE.name,
                        completedUnits = 1,
                        totalUnits = 1,
                        progressFraction = 1f,
                        message = "Copied securely to this device",
                        retryable = false,
                        attemptCount = 0,
                        updatedAtEpochMs = now,
                    ),
                )
            }
            Result.success(bookId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    override suspend fun updateVoiceAssignment(assignment: CharacterVoiceAssignment) {
        database.voiceAssignmentDao().upsert(assignment.toEntity())
    }

    override suspend fun deleteBook(bookId: String) {
        database.bookDao().deleteById(bookId)
    }
}
