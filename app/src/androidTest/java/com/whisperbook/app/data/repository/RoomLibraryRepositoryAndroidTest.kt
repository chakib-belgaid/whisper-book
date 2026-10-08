package com.whisperbook.app.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.whisperbook.app.data.local.db.WhisperBookDatabase
import com.whisperbook.app.data.local.db.BookEntity
import com.whisperbook.app.data.local.db.ChapterEntity
import com.whisperbook.app.data.local.db.PassageEntity
import com.whisperbook.app.data.local.db.PreparationJobEntity
import com.whisperbook.app.domain.BookImporter
import com.whisperbook.app.domain.ImportedBook
import com.whisperbook.app.domain.model.BookFormat
import com.whisperbook.app.domain.model.PreparationStage
import com.whisperbook.app.engine.metadata.AppPrivateCharacterMetadataCatalog
import com.whisperbook.app.engine.document.PublicationMarkdownFiles
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
    fun chapterHeadersExposeReadinessWithoutLoadingTextWhileSelectedChapterKeepsPassages() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, WhisperBookDatabase::class.java).build()
        val importer = object : BookImporter {
            override suspend fun import(uri: Uri): Result<ImportedBook> =
                Result.failure(UnsupportedOperationException())
        }
        val repository = RoomLibraryRepository(database, importer)
        val longText = "A".repeat(32_000)

        try {
            database.bookDao().insert(
                BookEntity(
                    id = "large-book",
                    title = "Large book",
                    author = "Tester",
                    format = BookFormat.EPUB.name,
                    sourceUri = null,
                    privateSourcePath = "/private/large-book.epub",
                    sourceSha256 = "large-book-hash",
                    coverPath = null,
                    currentChapterId = "chapter-1",
                    currentPassageId = "passage-1",
                    progressFraction = 0f,
                    lastOpenedAtEpochMs = 1L,
                ),
            )
            database.chapterDao().insertAll(
                listOf(
                    ChapterEntity("chapter-1", "large-book", 0, "Opening"),
                    ChapterEntity("chapter-2", "large-book", 1, "Next"),
                ),
            )
            database.passageDao().insertAll(
                listOf(
                    PassageEntity(
                        id = "passage-1",
                        chapterId = "chapter-1",
                        ordinal = 0,
                        text = longText,
                        speakerId = "large-book-character-narrator",
                        confidence = 0f,
                        attributionRule = "preparation-unattributed",
                    ),
                    PassageEntity(
                        id = "passage-2",
                        chapterId = "chapter-2",
                        ordinal = 0,
                        text = "Already attributed",
                        speakerId = "large-book-character-narrator",
                        confidence = 1f,
                        attributionRule = "narration-outside-dialogue",
                    ),
                ),
            )

            val initialHeaders = repository.observeChapterHeaders("large-book").first()

            assertTrue(initialHeaders.all { it.passages.isEmpty() })
            assertEquals(listOf(1, 1), initialHeaders.map { it.passageCount })
            assertEquals(listOf(1, 0), initialHeaders.map { it.unattributedPassageCount })
            assertFalse(initialHeaders.first().isAttributed)
            assertTrue(initialHeaders.last().isAttributed)

            val selected = repository.observeChapter("large-book", "chapter-1").first()!!
            assertEquals(longText, selected.passages.single().text)

            database.passageDao().updateSpeakerAttribution(
                passageIds = listOf("passage-1"),
                speakerId = "large-book-character-narrator",
                attributionRule = "narration-outside-dialogue",
            )
            val updatedHeaders = repository.observeChapterHeaders("large-book").first()
            val updatedSelected = repository.observeChapter("large-book", "chapter-1").first()!!

            assertTrue(updatedHeaders.first().passages.isEmpty())
            assertEquals(0, updatedHeaders.first().unattributedPassageCount)
            assertTrue(updatedHeaders.first().isAttributed)
            assertEquals("narration-outside-dialogue", updatedSelected.passages.single().attributionRule)
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
            val awaitingSetup = database.bookDao().getById(first)!!
            assertFalse(awaitingSetup.narrationSetupConfirmed)
            assertEquals("bella", awaitingSetup.preferredNarratorVoiceId)

            repository.confirmNarrationSetup(first, "fr", "jasper")

            val confirmed = database.bookDao().getById(first)!!
            assertTrue(confirmed.narrationSetupConfirmed)
            assertEquals("fr", confirmed.narrationLanguageCode)
            assertEquals("jasper", confirmed.preferredNarratorVoiceId)
        } finally {
            database.close()
        }
    }

    @Test
    fun chapterPlanSelectionReorderRestoreAndResetAreDurableAndRevisionedOncePerAction() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, WhisperBookDatabase::class.java).build()
        val importer = object : BookImporter {
            override suspend fun import(uri: Uri): Result<ImportedBook> =
                Result.failure(UnsupportedOperationException())
        }
        var now = 10L
        val repository = RoomLibraryRepository(database, importer, clockEpochMs = { ++now })

        try {
            database.bookDao().insert(
                BookEntity(
                    id = "plan-book",
                    title = "Plan book",
                    author = "Tester",
                    format = BookFormat.EPUB.name,
                    sourceUri = null,
                    privateSourcePath = "/private/plan-book.epub",
                    sourceSha256 = "plan-book-hash",
                    coverPath = null,
                    currentChapterId = null,
                    currentPassageId = null,
                    progressFraction = 0f,
                    lastOpenedAtEpochMs = 1L,
                    narrationSetupConfirmed = false,
                ),
            )
            database.chapterDao().insertAll(
                (1..5).map { number ->
                    ChapterEntity(
                        id = "chapter-$number",
                        bookId = "plan-book",
                        ordinal = number - 1,
                        title = "Chapter $number",
                    )
                },
            )
            database.preparationJobDao().upsert(
                PreparationJobEntity(
                    bookId = "plan-book",
                    stage = PreparationStage.READING_CHAPTERS.name,
                    completedUnits = 5,
                    totalUnits = 5,
                    progressFraction = 1f,
                    message = "Parsed",
                    retryable = false,
                    chapterPlanConfirmed = false,
                    attemptCount = 0,
                    updatedAtEpochMs = 1L,
                ),
            )

            repository.initializeChapterPlan("plan-book")

            val initial = repository.observeChapterPlan("plan-book").first()
            assertEquals(listOf(1, 2, 3, 4, 5), initial.map { it.chapter.ordinal + 1 })
            assertTrue(initial.all { it.isSelected })
            assertEquals(listOf(0, 1, 2, 3, 4), initial.map { it.customPosition })
            assertFalse(database.preparationJobDao().getForBook("plan-book")!!.chapterPlanConfirmed)

            repository.setChapterSelected("plan-book", "chapter-2", false)
            repository.setChapterSelected("plan-book", "chapter-4", false)
            repository.moveChapter("plan-book", "chapter-5", targetSelectedPosition = 0)

            assertEquals(
                listOf("chapter-5", "chapter-1", "chapter-3"),
                database.chapterPlanDao().getSelectedChapterHeaders("plan-book").map { it.id },
            )
            val afterMove = database.chapterPlanDao().getEntriesForBook("plan-book")
            assertEquals(1, afterMove.single { it.chapterId == "chapter-2" }.customPosition)
            assertEquals(3, afterMove.single { it.chapterId == "chapter-4" }.customPosition)
            assertEquals(3L, database.preparationJobDao().getForBook("plan-book")!!.planRevision)

            repository.restoreOriginalChapterOrder("plan-book")
            assertEquals(
                listOf("chapter-1", "chapter-3", "chapter-5"),
                database.chapterPlanDao().getSelectedChapterHeaders("plan-book").map { it.id },
            )
            assertEquals(4L, database.preparationJobDao().getForBook("plan-book")!!.planRevision)

            repository.moveChapter("plan-book", "chapter-5", targetSelectedPosition = 0)
            repository.resetChapterPlan("plan-book")
            val reset = repository.observeChapterPlan("plan-book").first()
            assertEquals(listOf("chapter-1", "chapter-2", "chapter-3", "chapter-4", "chapter-5"), reset.map { it.chapter.id })
            assertTrue(reset.all { it.isSelected })
            assertEquals(listOf(0, 1, 2, 3, 4), reset.map { it.customPosition })
            assertEquals(6L, database.preparationJobDao().getForBook("plan-book")!!.planRevision)

            repository.confirmChapterPlan("plan-book")
            val confirmed = database.preparationJobDao().getForBook("plan-book")!!
            assertTrue(confirmed.chapterPlanConfirmed)
            assertEquals(PreparationStage.AWAITING_NARRATION_SETUP.name, confirmed.stage)
            assertEquals(7L, confirmed.planRevision)
        } finally {
            database.close()
        }
    }

    @Test
    fun removingBookDeletesPrivateCopyButLeavesOriginalFileAlone() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, WhisperBookDatabase::class.java).build()
        val originalFile = File(context.cacheDir, "original-story.pdf").apply { writeText("original") }
        val privateFile = File(context.cacheDir, "private-story.pdf").apply { writeText("private") }
        val markdownFile = PublicationMarkdownFiles.write(privateFile, "# Removable book\n")
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
            assertFalse(markdownFile.exists())
            assertTrue(originalFile.exists())
            assertFalse(metadataFile.exists())
        } finally {
            originalFile.delete()
            privateFile.delete()
            markdownFile.delete()
            metadataRoot.deleteRecursively()
            database.close()
        }
    }
}
