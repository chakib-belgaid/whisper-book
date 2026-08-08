package com.whisperbook.app.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.whisperbook.app.domain.model.BookFormat
import com.whisperbook.app.domain.model.PlaybackCursor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookPlaybackStateAndroidTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun checkpointsAndCurrentChaptersRemainIndependentAcrossBooks() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, WhisperBookDatabase::class.java).build()

        try {
            insertBook(database, "book-a", chapterCount = 2)
            insertBook(database, "book-b", chapterCount = 3)

            persistLocation(database, cursor("book-a", "book-a-chapter-2", 2_000L), 10L)
            persistLocation(database, cursor("book-b", "book-b-chapter-3", 7_000L), 20L)

            assertEquals("book-a-chapter-2", database.bookDao().getById("book-a")?.currentChapterId)
            assertEquals("book-b-chapter-3", database.bookDao().getById("book-b")?.currentChapterId)
            assertEquals(2_000L, database.playbackCheckpointDao().getForBook("book-a")?.chapterPositionMs)
            assertEquals(7_000L, database.playbackCheckpointDao().getForBook("book-b")?.chapterPositionMs)

            persistLocation(database, cursor("book-a", "book-a-chapter-1", 500L), 30L)

            assertEquals("book-a-chapter-1", database.bookDao().getById("book-a")?.currentChapterId)
            assertEquals("book-b-chapter-3", database.bookDao().getById("book-b")?.currentChapterId)
            assertEquals(7_000L, database.playbackCheckpointDao().getForBook("book-b")?.chapterPositionMs)
        } finally {
            database.close()
        }
    }

    private suspend fun insertBook(
        database: WhisperBookDatabase,
        bookId: String,
        chapterCount: Int,
    ) {
        database.bookDao().insert(
            BookEntity(
                id = bookId,
                title = bookId,
                author = "Tester",
                format = BookFormat.EPUB.name,
                sourceUri = null,
                privateSourcePath = null,
                sourceSha256 = bookId,
                coverPath = null,
                currentChapterId = null,
                currentPassageId = null,
                progressFraction = 0f,
                lastOpenedAtEpochMs = 0L,
            ),
        )
        database.chapterDao().insertAll(
            (1..chapterCount).map { number ->
                ChapterEntity(
                    id = "$bookId-chapter-$number",
                    bookId = bookId,
                    ordinal = number - 1,
                    title = "Chapter $number",
                )
            },
        )
    }

    private suspend fun persistLocation(
        database: WhisperBookDatabase,
        cursor: PlaybackCursor,
        timestamp: Long,
    ) {
        database.playbackCheckpointDao().upsert(cursor.toEntity(timestamp))
        database.bookDao().updatePlaybackLocation(
            bookId = cursor.bookId,
            chapterId = cursor.chapterId,
            passageId = cursor.passageId,
            openedAtEpochMs = timestamp,
        )
    }

    private fun cursor(bookId: String, chapterId: String, positionMs: Long) = PlaybackCursor(
        bookId = bookId,
        chapterId = chapterId,
        passageId = "$chapterId-passage",
        segmentId = "$chapterId-segment",
        segmentPositionMs = positionMs,
        chapterPositionMs = positionMs,
        chapterDurationMs = 10_000L,
        isPlaying = false,
        speed = 1f,
    )
}
