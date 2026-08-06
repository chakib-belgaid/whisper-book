package com.whisperbook.app.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.whisperbook.app.data.local.db.WhisperBookDatabase
import com.whisperbook.app.data.local.db.BookEntity
import com.whisperbook.app.data.local.db.ChapterEntity
import com.whisperbook.app.data.local.db.PreparationJobEntity
import com.whisperbook.app.domain.BookImporter
import com.whisperbook.app.domain.ImportedBook
import com.whisperbook.app.domain.model.BookFormat
import com.whisperbook.app.domain.model.PreparationStage
import com.whisperbook.app.engine.metadata.AppPrivateCharacterMetadataCatalog
import com.whisperbook.app.engine.metadata.ChapterCharacterMetadata
import com.whisperbook.app.engine.metadata.CharacterMetadataChapterUpdate
import com.whisperbook.app.engine.metadata.CharacterMetadataFingerprint
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomLibraryRepositoryAndroidTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun eachLibraryBookReportsItsOwnPersistedChapterCountDuringBackgroundPreparation() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, WhisperBookDatabase::class.java).build()
        val importer = object : BookImporter {
            override suspend fun import(uri: Uri): Result<ImportedBook> =
                Result.failure(UnsupportedOperationException())
        }
        val repository = RoomLibraryRepository(database, importer)

        try {
            listOf("large-a" to 37, "large-b" to 12).forEach { (bookId, chapterCount) ->
                database.bookDao().insert(
                    BookEntity(
                        id = bookId,
                        title = bookId,
                        author = "Tester",
                        format = BookFormat.EPUB.name,
                        sourceUri = null,
                        privateSourcePath = "/private/$bookId.epub",
                        sourceSha256 = bookId,
                        coverPath = null,
                        currentChapterId = "$bookId-chapter-1",
                        currentPassageId = null,
                        progressFraction = 0f,
                        lastOpenedAtEpochMs = chapterCount.toLong(),
                    ),
                )
                database.chapterDao().insertAll(
                    (0 until chapterCount).map { ordinal ->
                        ChapterEntity(
                            id = "$bookId-chapter-${ordinal + 1}",
                            bookId = bookId,
                            ordinal = ordinal,
                            title = "Chapter ${ordinal + 1}",
                        )
                    },
                )
                database.preparationJobDao().upsert(
                    PreparationJobEntity(
                        bookId = bookId,
                        stage = PreparationStage.PREPARING_AUDIO.name,
                        completedUnits = 2,
                        totalUnits = chapterCount,
                        progressFraction = 2f / chapterCount,
                        message = "Recording chapters",
                        retryable = false,
                        attemptCount = 0,
                        updatedAtEpochMs = 1L,
                    ),
                )
            }

            val books = repository.observeBooks().first().associateBy { it.id }

            assertEquals(37, books.getValue("large-a").chapterCount)
            assertEquals(12, books.getValue("large-b").chapterCount)
            assertEquals(0, books.getValue("large-a").currentChapterOrdinal)
        } finally {
            database.close()
        }
    }

    @Test
    fun importingTheSamePrivateBookTwiceReusesItsExistingRecord() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, WhisperBookDatabase::class.java).build()
        var generatedIds = 0
        val privateFile = File(context.cacheDir, "same-book.pdf")
        val importer = object : BookImporter {
            override suspend fun import(uri: Uri): Result<ImportedBook> = Result.success(
                ImportedBook(
                    title = "Same book",
                    author = "Tester",
                    format = BookFormat.PDF,
                    privateFile = privateFile,
                    sha256 = "stable-book-hash",
                ),
            )
        }
        val repository = RoomLibraryRepository(
            database = database,
            bookImporter = importer,
            idGenerator = { "book-${++generatedIds}" },
        )

        try {
            val first = repository.importBook(Uri.parse("content://books/first")).getOrThrow()
            val second = repository.importBook(Uri.parse("content://books/second")).getOrThrow()

            assertEquals(first, second)
            assertEquals(1, database.bookDao().count())
            assertEquals(1, generatedIds)
        } finally {
            database.close()
        }
    }

    @Test
    fun removingBookDeletesPrivateCopyButLeavesOriginalFileAlone() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, WhisperBookDatabase::class.java).build()
        val originalFile = File(context.cacheDir, "original-story.pdf").apply { writeText("original") }
        val privateFile = File(context.cacheDir, "private-story.pdf").apply { writeText("private") }
        val metadataRoot = File(context.cacheDir, "character-metadata-${System.nanoTime()}")
        val metadataCatalog = AppPrivateCharacterMetadataCatalog(metadataRoot)
        val importer = object : BookImporter {
            override suspend fun import(uri: Uri): Result<ImportedBook> = Result.success(
                ImportedBook(
                    title = "Removable book",
                    author = null,
                    format = BookFormat.PDF,
                    privateFile = privateFile,
                    sha256 = "removable-book-hash",
                ),
            )
        }
        val repository = RoomLibraryRepository(
            database,
            importer,
            idGenerator = { "removable-book" },
            characterMetadataCatalog = metadataCatalog,
        )

        try {
            val bookId = repository.importBook(Uri.fromFile(originalFile)).getOrThrow()
            metadataCatalog.recordChapter(
                CharacterMetadataChapterUpdate(
                    bookId = bookId,
                    sourceSha256 = "removable-book-hash",
                    analysisVersion = "test-analysis",
                    chapter = ChapterCharacterMetadata(
                        chapterId = "$bookId-chapter-1",
                        ordinal = 0,
                        textSha256 = CharacterMetadataFingerprint.sha256Utf8("chapter"),
                        contributions = emptyList(),
                    ),
                    characters = emptyList(),
                    complete = true,
                ),
            )
            val metadataFile = metadataCatalog.metadataFile(bookId)
            assertTrue(metadataFile.isFile)
            repository.deleteBook(bookId)

            assertEquals(0, database.bookDao().count())
            assertFalse(privateFile.exists())
            assertTrue(originalFile.exists())
            assertFalse(metadataFile.exists())
        } finally {
            originalFile.delete()
            privateFile.delete()
            metadataRoot.deleteRecursively()
            database.close()
        }
    }
}
