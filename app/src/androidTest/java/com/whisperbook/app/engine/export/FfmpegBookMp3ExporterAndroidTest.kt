package com.whisperbook.app.engine.export

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.whisperbook.app.data.local.db.BookEntity
import com.whisperbook.app.data.local.db.ChapterEntity
import com.whisperbook.app.data.local.db.WhisperBookDatabase
import com.whisperbook.app.domain.model.AudioSegment
import com.whisperbook.app.domain.model.AudioSegmentState
import com.whisperbook.app.domain.model.BookFormat
import com.whisperbook.app.playback.PlayableSegment
import com.whisperbook.app.playback.PlaybackChapterQueue
import com.whisperbook.app.playback.PlaybackQueueSource
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FfmpegBookMp3ExporterAndroidTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun exportUsesOnlySelectedChaptersInCustomOrder() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, WhisperBookDatabase::class.java).build()
        val audio = File(context.cacheDir, "export-source-${System.nanoTime()}.wav")
        val destination = File(context.cacheDir, "export-destination-${System.nanoTime()}.mp3")
        val loadedChapterIds = mutableListOf<String>()
        try {
            database.bookDao().insert(
                BookEntity(
                    id = BOOK_ID,
                    title = "Custom order",
                    author = "Tester",
                    format = BookFormat.EPUB.name,
                    sourceUri = null,
                    privateSourcePath = null,
                    sourceSha256 = null,
                    coverPath = null,
                    currentChapterId = null,
                    currentPassageId = null,
                    progressFraction = 0f,
                    lastOpenedAtEpochMs = 1L,
                ),
            )
            database.chapterDao().insertAll(
                (1..5).map { number ->
                    ChapterEntity(chapterId(number), BOOK_ID, number - 1, "Chapter $number")
                },
            )
            insertChapterPlan(database)
            audio.writeBytes(ByteArray(64) { 1 })
            val queueSource = PlaybackQueueSource { bookId, requestedChapterId ->
                val chapterId = requireNotNull(requestedChapterId)
                loadedChapterIds += chapterId
                Result.success(
                    PlaybackChapterQueue(
                        bookId = bookId,
                        chapterId = chapterId,
                        bookTitle = "Custom order",
                        chapterTitle = chapterId,
                        segments = listOf(
                            PlayableSegment(
                                passageId = "$chapterId-passage",
                                passageOrdinal = 0,
                                speakerName = "Narrator",
                                audioSegment = AudioSegment(
                                    id = "$chapterId-segment",
                                    passageId = "$chapterId-passage",
                                    cacheKey = "$chapterId-cache",
                                    state = AudioSegmentState.READY,
                                    path = audio.absolutePath,
                                    durationMs = 100L,
                                ),
                            ),
                        ),
                    ),
                )
            }
            val exporter = FfmpegBookMp3Exporter(
                context = context,
                database = database,
                queueSource = queueSource,
                encoder = FakeEncoder(),
            )

            val result = exporter.export(BOOK_ID, Uri.fromFile(destination)) { }

            assertEquals(listOf(chapterId(5), chapterId(1), chapterId(3)), loadedChapterIds)
            assertEquals(3, result.chapterCount)
            assertEquals(300L, result.durationMs)
            assertTrue(destination.length() > 0L)
        } finally {
            database.close()
            audio.delete()
            destination.delete()
        }
    }

    private fun insertChapterPlan(database: WhisperBookDatabase) {
        listOf(
            PlanRow(5, selected = true, position = 0),
            PlanRow(2, selected = false, position = 1),
            PlanRow(1, selected = true, position = 2),
            PlanRow(3, selected = true, position = 3),
            PlanRow(4, selected = false, position = 4),
        ).forEach { row ->
            database.openHelper.writableDatabase.execSQL(
                """
                INSERT INTO chapter_plan_entries (
                    book_id, chapter_id, is_selected, custom_position, updated_at
                ) VALUES (?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    BOOK_ID,
                    chapterId(row.number),
                    if (row.selected) 1 else 0,
                    row.position,
                    1L,
                ),
            )
        }
    }

    private fun chapterId(number: Int) = "$BOOK_ID-chapter-$number"

    private data class PlanRow(val number: Int, val selected: Boolean, val position: Int)

    private class FakeEncoder : FfmpegMp3Encoder() {
        override fun encode(
            manifest: File,
            destination: File,
            title: String,
            artist: String?,
        ) {
            check(manifest.isFile)
            destination.writeBytes(byteArrayOf(1, 2, 3, 4))
        }
    }

    private companion object {
        const val BOOK_ID = "export-custom-order-book"
    }
}
