package com.whisperbook.app.playback

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.whisperbook.app.domain.model.AudioSegment
import com.whisperbook.app.domain.model.AudioSegmentState
import com.whisperbook.app.engine.audio.Pcm16WavWriter
import java.io.File
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChapterContinuationAndroidTest {
    @Test
    fun finishingAChapterAutomaticallyStartsTheNextChapter() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val testDirectory = File(context.cacheDir, "chapter-continuation-test").apply { mkdirs() }
        val chapterOne = queue(testDirectory, "chapter-1", 220)
        val chapterTwo = queue(testDirectory, "chapter-2", 900)
        var nextChapterRequests = 0
        val source = object : PlaybackQueueSource {
            override suspend fun load(bookId: String, chapterId: String?) = Result.success(chapterOne)

            override suspend fun loadNext(
                bookId: String,
                chapterId: String,
            ): Result<PlaybackChapterQueue?> {
                nextChapterRequests += 1
                return Result.success(if (chapterId == chapterOne.chapterId) chapterTwo else null)
            }
        }
        PlaybackRuntime.installCheckpointSink(null)
        val gateway = ControllerBackedPlaybackGateway(context, source)

        try {
            gateway.playBook(chapterOne.bookId, chapterOne.chapterId)
            val continuedCursor = withTimeout(8_000L) {
                gateway.cursor.filterNotNull().first { it.chapterId == chapterTwo.chapterId }
            }

            assertEquals(chapterTwo.chapterId, continuedCursor.chapterId)
            assertTrue(nextChapterRequests >= 1)
        } finally {
            runCatching { gateway.pause() }
            gateway.close()
            testDirectory.deleteRecursively()
        }
    }

    private fun queue(directory: File, chapterId: String, durationMs: Int): PlaybackChapterQueue {
        val sampleRate = 24_000
        val samples = ShortArray(sampleRate * durationMs / 1_000)
        val file = File(directory, "$chapterId.wav")
        Pcm16WavWriter.writeAtomic(file, samples, sampleRate)
        val passageId = "$chapterId-passage"
        return PlaybackChapterQueue(
            bookId = "continuation-book",
            chapterId = chapterId,
            bookTitle = "Continuation Test",
            chapterTitle = chapterId,
            segments = listOf(
                PlayableSegment(
                    passageId = passageId,
                    passageOrdinal = 0,
                    speakerName = "Narrator",
                    audioSegment = AudioSegment(
                        id = "$chapterId-segment",
                        passageId = passageId,
                        cacheKey = "$chapterId-cache",
                        state = AudioSegmentState.READY,
                        path = file.absolutePath,
                        durationMs = durationMs.toLong(),
                        sampleRate = sampleRate,
                    ),
                ),
            ),
        )
    }
}
