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
    fun manualSpeakerCorrectionBatchesLargeBooksAndStaysBookScoped() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, WhisperBookDatabase::class.java).build()
        val passageCount = 1_005

        try {
            database.bookDao().insert(testBook())
            database.bookDao().insert(testBook("other-book"))
            database.chapterDao().insertAll(
                listOf(
                    testChapter("book-chapter", 0),
                    testChapter("other-chapter", 0, "other-book"),
                ),
            )
            database.passageDao().insertAll(
                (0 until passageCount).map { ordinal ->
                    testPassage("passage-$ordinal", "book-chapter", ordinal, "Wait!")
                } + testPassage("other-passage", "other-chapter", 0, "Wait!"),
            )

            val bookPassages = database.passageDao().getForBook("book")
            val updated = database.passageDao().updateSpeakerAttributionBatched(
                passageIds = bookPassages.map { it.id },
                speakerId = "book-character-elara",
                attributionRule = "manual-speaker:matching-phrases",
            )

            assertEquals(passageCount, updated)
            assertTrue(database.passageDao().getForBook("book").all { passage ->
                passage.speakerId == "book-character-elara" &&
                    passage.confidence == 1f &&
                    passage.attributionRule == "manual-speaker:matching-phrases"
            })
            assertEquals(
                "book-character-narrator",
                database.passageDao().getById("other-passage")?.speakerId,
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun narratorInvalidationAcross2450ChaptersStaysBelowTheSqlVariableLimit() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, WhisperBookDatabase::class.java).build()
        val chapterCount = 2_450
        val narratorId = "book-character-narrator"
        val sentinelOrdinals = listOf(0, 996, 997, 998, 999, chapterCount - 1)
        val otherBookId = "other-book"

        try {
            database.bookDao().insert(testBook())
            database.bookDao().insert(testBook(otherBookId))
            database.chapterDao().insertAll(
                (0 until chapterCount).map { ordinal ->
                    testChapter(id = "book-chapter-$ordinal", ordinal = ordinal)
                } + testChapter(
                    id = "$otherBookId-chapter-0",
                    ordinal = 0,
                    bookId = otherBookId,
                ),
            )
            database.passageDao().insertAll(
                (0 until chapterCount).map { ordinal ->
                    testPassage(
                        id = "book-passage-$ordinal",
                        chapterId = "book-chapter-$ordinal",
                        ordinal = 0,
                        text = "Narration $ordinal",
                        speakerId = narratorId,
                    )
                } + listOf(
                    testPassage(
                        id = "other-speaker-passage",
                        chapterId = "book-chapter-0",
                        ordinal = 1,
                        text = "Keep this speaker",
                        speakerId = "book-character-other",
                    ),
                    testPassage(
                        id = "other-book-passage",
                        chapterId = "$otherBookId-chapter-0",
                        ordinal = 0,
                        text = "Keep this book",
                        speakerId = narratorId,
                    ),
                ),
            )
            sentinelOrdinals.forEach { ordinal ->
                database.audioSegmentDao().upsert(
                    testAudioSegment(
                        id = "book-audio-$ordinal",
                        passageId = "book-passage-$ordinal",
                        cacheKey = "book-cache-$ordinal",
                    ),
                )
            }
            database.audioSegmentDao().upsert(
                testAudioSegment(
                    id = "other-speaker-audio",
                    passageId = "other-speaker-passage",
                    cacheKey = "other-speaker-cache",
                ),
            )
            database.audioSegmentDao().upsert(
                testAudioSegment(
                    id = "other-book-audio",
                    passageId = "other-book-passage",
                    cacheKey = "other-book-cache",
                ),
            )

            val passageIds = database.audioSegmentDao()
                .getPassageIdsForCharacterFromChapterOrdinal(narratorId, "book", 0)

            assertEquals(chapterCount, passageIds.size)
            assertEquals(chapterCount, passageIds.toSet().size)
            assertTrue(sentinelOrdinals.all { "book-passage-$it" in passageIds })
            assertEquals(
                setOf("book-passage-999"),
                database.audioSegmentDao().getPassageIdsForCharacterInChapter(
                    narratorId,
                    "book",
                    "book-chapter-999",
                ).toSet(),
            )

            database.audioSegmentDao().deleteForPassageIdsBatched(passageIds)

            sentinelOrdinals.forEach { ordinal ->
                assertNull(database.audioSegmentDao().findByCacheKey("book-cache-$ordinal"))
            }
            assertEquals(
                "other-speaker-audio",
                database.audioSegmentDao().findByCacheKey("other-speaker-cache")?.id,
            )
            assertEquals(
                "other-book-audio",
                database.audioSegmentDao().findByCacheKey("other-book-cache")?.id,
            )
        } finally {
            database.close()
        }
    }

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

    private fun testBook(id: String = "book") = BookEntity(
        id = id,
        title = "Large book $id",
        author = "Tester",
        format = BookFormat.EPUB.name,
        sourceUri = null,
        privateSourcePath = "/private/$id.epub",
        sourceSha256 = "$id-hash",
        coverPath = null,
        currentChapterId = null,
        currentPassageId = null,
        progressFraction = 0f,
        lastOpenedAtEpochMs = 1L,
    )

    private fun testChapter(id: String, ordinal: Int, bookId: String = "book") = ChapterEntity(
        id = id,
        bookId = bookId,
        ordinal = ordinal,
        title = "Chapter ${ordinal + 1}",
    )

    private fun testPassage(
        id: String,
        chapterId: String,
        ordinal: Int,
        text: String,
        speakerId: String = "book-character-narrator",
    ) = PassageEntity(
        id = id,
        chapterId = chapterId,
        ordinal = ordinal,
        text = text,
        speakerId = speakerId,
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
