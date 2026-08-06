package com.whisperbook.app.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.whisperbook.app.domain.model.BookFormat
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PassageDaoAndroidTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun deleteForChapterReplacesOnlyTheTargetChapterAndCascadesItsAudio() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, WhisperBookDatabase::class.java).build()

        try {
            database.bookDao().insert(testBook())
            database.chapterDao().insertAll(
                listOf(
                    testChapter(id = "book-chapter-1", ordinal = 0),
                    testChapter(id = "book-chapter-2", ordinal = 1),
                ),
            )
            database.passageDao().insertAll(
                listOf(
                    testPassage(
                        id = "chapter-1-passage-2",
                        chapterId = "book-chapter-1",
                        ordinal = 1,
                        text = "Second passage",
                    ),
                    testPassage(
                        id = "chapter-1-passage-1",
                        chapterId = "book-chapter-1",
                        ordinal = 0,
                        text = "First passage",
                    ),
                    testPassage(
                        id = "chapter-2-passage-1",
                        chapterId = "book-chapter-2",
                        ordinal = 0,
                        text = "Keep this passage",
                    ),
                ),
            )
            database.audioSegmentDao().upsert(
                testAudioSegment(
                    id = "chapter-1-audio",
                    passageId = "chapter-1-passage-1",
                    cacheKey = "chapter-1-cache",
                ),
            )
            database.audioSegmentDao().upsert(
                testAudioSegment(
                    id = "chapter-2-audio",
                    passageId = "chapter-2-passage-1",
                    cacheKey = "chapter-2-cache",
                ),
            )

            assertEquals(
                listOf("chapter-1-passage-1", "chapter-1-passage-2"),
                database.passageDao().getForChapter("book-chapter-1").map { it.id },
            )

            database.passageDao().deleteForChapter("book-chapter-1")
            database.passageDao().deleteForChapter("book-chapter-1")

            assertTrue(database.passageDao().getForChapter("book-chapter-1").isEmpty())
            assertEquals(
                listOf("chapter-2-passage-1"),
                database.passageDao().getForChapter("book-chapter-2").map { it.id },
            )
            assertNull(database.audioSegmentDao().findByCacheKey("chapter-1-cache"))
            assertEquals(
                "chapter-2-audio",
                database.audioSegmentDao().findByCacheKey("chapter-2-cache")?.id,
            )
        } finally {
            database.close()
        }
    }

    private fun testBook() = BookEntity(
        id = "book",
        title = "Large book",
        author = "Tester",
        format = BookFormat.EPUB.name,
        sourceUri = null,
        privateSourcePath = "/private/book.epub",
        sourceSha256 = "book-hash",
        coverPath = null,
        currentChapterId = null,
        currentPassageId = null,
        progressFraction = 0f,
        lastOpenedAtEpochMs = 1L,
    )

    private fun testChapter(id: String, ordinal: Int) = ChapterEntity(
        id = id,
        bookId = "book",
        ordinal = ordinal,
        title = "Chapter ${ordinal + 1}",
    )

    private fun testPassage(
        id: String,
        chapterId: String,
        ordinal: Int,
        text: String,
    ) = PassageEntity(
        id = id,
        chapterId = chapterId,
        ordinal = ordinal,
        text = text,
        speakerId = "book-character-narrator",
        confidence = 1f,
        attributionRule = "narration",
    )

    private fun testAudioSegment(
        id: String,
        passageId: String,
        cacheKey: String,
    ) = AudioSegmentEntity(
        id = id,
        passageId = passageId,
        cacheKey = cacheKey,
        state = "READY",
        path = "/private/$id.wav",
        durationMs = 1_000L,
        sampleRate = 24_000,
    )
}
