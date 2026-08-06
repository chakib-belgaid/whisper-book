package com.whisperbook.app.playback

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.whisperbook.app.domain.model.AudioSegment
import com.whisperbook.app.domain.model.AudioSegmentState
import com.whisperbook.app.engine.audio.Pcm16WavWriter
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
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
    fun playbackStartsFromFirstReadySegmentWhileTheChapterContinuesPreparing() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val testDirectory = File(context.cacheDir, "progressive-chapter-test").apply { mkdirs() }
        val fullQueue = queue(testDirectory, "chapter-progressive", 900, segmentCount = 2)
        val openingQueue = fullQueue.copy(segments = fullQueue.segments.take(1))
        val releaseRemainingAudio = CompletableDeferred<Unit>()
        val source = object : PlaybackQueueSource {
            override suspend fun load(bookId: String, chapterId: String?) = Result.success(fullQueue)

            override suspend fun loadProgressively(
                bookId: String,
                chapterId: String?,
                onProgress: suspend (PlaybackChapterQueue?, Int, Int) -> Unit,
            ): Result<PlaybackChapterQueue> {
                onProgress(null, 0, 2)
                onProgress(openingQueue, 1, 2)
                releaseRemainingAudio.await()
                onProgress(fullQueue, 2, 2)
                return Result.success(fullQueue)
            }
        }
        PlaybackRuntime.installCheckpointSink(null)
        val gateway = ControllerBackedPlaybackGateway(context, source)

        try {
            gateway.playBook(fullQueue.bookId, fullQueue.chapterId)
            val openingCursor = withTimeout(8_000L) {
                gateway.cursor.filterNotNull().first { it.passageId.endsWith("passage-1") }
            }

            assertEquals(fullQueue.chapterId, openingCursor.chapterId)
            assertEquals(1, gateway.preparationProgress.value?.completedSegments)

            releaseRemainingAudio.complete(Unit)
            val continuedCursor = withTimeout(8_000L) {
                gateway.cursor.filterNotNull().first { it.passageId.endsWith("passage-2") }
            }
            assertEquals(fullQueue.chapterId, continuedCursor.chapterId)
            withTimeout(8_000L) { gateway.preparationProgress.first { it == null } }
        } finally {
            releaseRemainingAudio.complete(Unit)
            runCatching { gateway.pause() }
            gateway.close()
            testDirectory.deleteRecursively()
        }
        Unit
    }

    @Test
    fun aLateQueueCannotReplaceANewerChapterSelection() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val testDirectory = File(context.cacheDir, "chapter-selection-race-test").apply { mkdirs() }
        val chapterOne = queue(testDirectory, "chapter-1", 5_000)
        val chapterTwo = queue(testDirectory, "chapter-2", 5_000)
        val firstRequestStarted = CompletableDeferred<Unit>()
        val releaseFirstRequest = CompletableDeferred<Unit>()
        val source = object : PlaybackQueueSource {
            override suspend fun load(bookId: String, chapterId: String?): Result<PlaybackChapterQueue> {
                if (chapterId == chapterOne.chapterId) {
                    firstRequestStarted.complete(Unit)
                    releaseFirstRequest.await()
                }
                return Result.success(if (chapterId == chapterOne.chapterId) chapterOne else chapterTwo)
            }

            override suspend fun loadNext(
                bookId: String,
                chapterId: String,
            ): Result<PlaybackChapterQueue?> = Result.success(null)
        }
        PlaybackRuntime.installCheckpointSink(null)
        val gateway = ControllerBackedPlaybackGateway(context, source)

        try {
            val firstRequest = async { gateway.playBook(chapterOne.bookId, chapterOne.chapterId) }
            firstRequestStarted.await()
            gateway.playBook(chapterTwo.bookId, chapterTwo.chapterId)
            withTimeout(8_000L) {
                gateway.cursor.filterNotNull().first { it.chapterId == chapterTwo.chapterId }
            }

            releaseFirstRequest.complete(Unit)
            firstRequest.await()
            delay(250L)

            assertEquals(chapterTwo.chapterId, gateway.cursor.value?.chapterId)
        } finally {
            releaseFirstRequest.complete(Unit)
            runCatching { gateway.pause() }
            gateway.close()
            testDirectory.deleteRecursively()
        }
        Unit
    }

    @Test
    fun finishingAChapterAutomaticallyStartsTheNextChapter() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val testDirectory = File(context.cacheDir, "chapter-continuation-test").apply { mkdirs() }
        val chapterOne = queue(testDirectory, "chapter-1", 220)
        val chapterTwo = queue(testDirectory, "chapter-2", 900)
        val nextChapterRequestStarted = CompletableDeferred<Unit>()
        val releaseNextChapter = CompletableDeferred<Unit>()
        var nextChapterRequests = 0
        val source = object : PlaybackQueueSource {
            override suspend fun load(bookId: String, chapterId: String?) = Result.success(chapterOne)

            override suspend fun loadNext(
                bookId: String,
                chapterId: String,
            ): Result<PlaybackChapterQueue?> {
                nextChapterRequests += 1
                if (chapterId != chapterOne.chapterId) return Result.success(null)
                nextChapterRequestStarted.complete(Unit)
                releaseNextChapter.await()
                return Result.success(chapterTwo)
            }
        }
        PlaybackRuntime.installCheckpointSink(null)
        val gateway = ControllerBackedPlaybackGateway(context, source)

        try {
            gateway.playBook(chapterOne.bookId, chapterOne.chapterId)
            withTimeout(8_000L) { nextChapterRequestStarted.await() }
            val endedCursor = withTimeout(8_000L) {
                gateway.cursor.filterNotNull().first { cursor ->
                    cursor.chapterId == chapterOne.chapterId &&
                        !cursor.isPlaying &&
                        cursor.chapterPositionMs >= chapterOne.durationMs
                }
            }
            assertEquals(chapterOne.chapterId, endedCursor.chapterId)

            releaseNextChapter.complete(Unit)
            val continuedCursor = withTimeout(8_000L) {
                gateway.cursor.filterNotNull().first { it.chapterId == chapterTwo.chapterId }
            }

            assertEquals(chapterTwo.chapterId, continuedCursor.chapterId)
            assertTrue(nextChapterRequests >= 1)
        } finally {
            releaseNextChapter.complete(Unit)
            runCatching { gateway.pause() }
            gateway.close()
            testDirectory.deleteRecursively()
        }
        Unit
    }

    @Test
    fun unavailableNextChapterIsRetriedAfterAPlaybackCallback() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val testDirectory = File(context.cacheDir, "chapter-retry-prefetch-test").apply { mkdirs() }
        val chapterOne = queue(testDirectory, "retry-chapter-1", 1_200)
        val chapterTwo = queue(testDirectory, "retry-chapter-2", 900)
        val firstUnavailableRequest = CompletableDeferred<Unit>()
        val nextChapterAvailable = CompletableDeferred<Unit>()
        var nextChapterRequests = 0
        val source = object : PlaybackQueueSource {
            override suspend fun load(bookId: String, chapterId: String?) = Result.success(chapterOne)

            override suspend fun loadNext(
                bookId: String,
                chapterId: String,
            ): Result<PlaybackChapterQueue?> {
                if (chapterId != chapterOne.chapterId) return Result.success(null)
                nextChapterRequests += 1
                if (nextChapterRequests == 1) {
                    firstUnavailableRequest.complete(Unit)
                    return Result.failure(
                        IllegalStateException("The next chapter is still being attributed"),
                    )
                }
                return if (nextChapterAvailable.isCompleted) {
                    Result.success(chapterTwo)
                } else {
                    Result.success(null)
                }
            }
        }
        PlaybackRuntime.installCheckpointSink(null)
        val gateway = ControllerBackedPlaybackGateway(context, source)

        try {
            gateway.playBook(chapterOne.bookId, chapterOne.chapterId)
            withTimeout(8_000L) { firstUnavailableRequest.await() }
            nextChapterAvailable.complete(Unit)

            withTimeout(8_000L) {
                while (nextChapterRequests < 2) delay(20L)
            }

            val continuedCursor = withTimeout(8_000L) {
                gateway.cursor.filterNotNull().first { it.chapterId == chapterTwo.chapterId }
            }

            assertEquals(chapterTwo.chapterId, continuedCursor.chapterId)
            assertTrue(nextChapterRequests >= 2)
        } finally {
            nextChapterAvailable.complete(Unit)
            runCatching { gateway.pause() }
            gateway.close()
            testDirectory.deleteRecursively()
        }
        Unit
    }

    @Test
    fun narratorChangeDropsAPrefetchedNextChapterWithoutInterruptingTheCurrentChapter() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val testDirectory = File(context.cacheDir, "chapter-voice-change-prefetch-test").apply { mkdirs() }
        val chapterOne = queue(testDirectory, "chapter-1", 900)
        val chapterTwo = queue(testDirectory, "chapter-2", 900)
        val firstPrefetchFinished = CompletableDeferred<Unit>()
        var nextChapterRequests = 0
        val source = object : PlaybackQueueSource {
            override suspend fun load(bookId: String, chapterId: String?) = Result.success(chapterOne)

            override suspend fun loadNext(
                bookId: String,
                chapterId: String,
            ): Result<PlaybackChapterQueue?> {
                if (chapterId != chapterOne.chapterId) return Result.success(null)
                nextChapterRequests += 1
                if (nextChapterRequests == 1) firstPrefetchFinished.complete(Unit)
                return Result.success(chapterTwo)
            }
        }
        PlaybackRuntime.installCheckpointSink(null)
        val gateway = ControllerBackedPlaybackGateway(context, source)

        try {
            gateway.playBook(chapterOne.bookId, chapterOne.chapterId)
            withTimeout(8_000L) { firstPrefetchFinished.await() }
            delay(250L)

            gateway.invalidateQueuedChapters(chapterOne.bookId, setOf(chapterTwo.chapterId))
            assertEquals(chapterOne.chapterId, gateway.cursor.value?.chapterId)

            val continuedCursor = withTimeout(8_000L) {
                gateway.cursor.filterNotNull().first { it.chapterId == chapterTwo.chapterId }
            }
            assertEquals(chapterTwo.chapterId, continuedCursor.chapterId)
            assertTrue(nextChapterRequests >= 2)
        } finally {
            runCatching { gateway.pause() }
            gateway.close()
            testDirectory.deleteRecursively()
        }
        Unit
    }

    private fun queue(
        directory: File,
        chapterId: String,
        durationMs: Int,
        segmentCount: Int = 1,
    ): PlaybackChapterQueue {
        val sampleRate = 24_000
        val samples = ShortArray(sampleRate * durationMs / 1_000)
        return PlaybackChapterQueue(
            bookId = "continuation-book",
            chapterId = chapterId,
            bookTitle = "Continuation Test",
            chapterTitle = chapterId,
            segments = (1..segmentCount).map { index ->
                val file = File(directory, "$chapterId-$index.wav")
                Pcm16WavWriter.writeAtomic(file, samples, sampleRate)
                val passageId = "$chapterId-passage-$index"
                PlayableSegment(
                    passageId = passageId,
                    passageOrdinal = index - 1,
                    speakerName = "Narrator",
                    audioSegment = AudioSegment(
                        id = "$chapterId-segment-$index",
                        passageId = passageId,
                        cacheKey = "$chapterId-cache-$index",
                        state = AudioSegmentState.READY,
                        path = file.absolutePath,
                        durationMs = durationMs.toLong(),
                        sampleRate = sampleRate,
                    ),
                )
            },
        )
    }
}
