package com.whisperbook.app.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.whisperbook.app.domain.PlaybackGateway
import com.whisperbook.app.domain.model.PlaybackCursor
import java.io.Closeable
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Compose-safe gateway which connects to [WhisperPlaybackService] through Media3's controller API.
 * Create one application-scoped instance and call [close] when the application container is torn
 * down; screen recreation does not interrupt service playback.
 */
class ControllerBackedPlaybackGateway(
    context: Context,
    private val queueSource: PlaybackQueueSource,
) : PlaybackGateway, Closeable {
    private val appContext = context.applicationContext
    private val continuationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var connectedController: MediaController? = null
    private var nextChapterJob: Job? = null
    private var prefetchedAfterChapterKey: String? = null
    private val controllerFuture = MediaController.Builder(
        appContext,
        SessionToken(appContext, ComponentName(appContext, WhisperPlaybackService::class.java)),
    )
        .setApplicationLooper(Looper.getMainLooper())
        .buildAsync()

    private val continuationListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            val descriptor = PlaybackMediaItems.descriptor(mediaItem) ?: return
            prefetchFollowingChapter(descriptor)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState != Player.STATE_ENDED) return
            val descriptor = PlaybackMediaItems.descriptor(connectedController?.currentMediaItem) ?: return
            prefetchFollowingChapter(descriptor)
        }
    }

    init {
        controllerFuture.addListener(
            {
                runCatching(controllerFuture::get).getOrNull()?.let { controller ->
                    connectedController = controller
                    controller.addListener(continuationListener)
                }
            },
            directExecutor,
        )
    }

    override val cursor: StateFlow<PlaybackCursor?> = PlaybackRuntime.cursor

    override suspend fun playBook(bookId: String, chapterId: String?) {
        val queue = queueSource.load(bookId, chapterId).getOrThrow()
        val mediaItems = PlaybackMediaItems.create(queue)
        val startIndex = queue.startPassageId
            ?.let { passageId -> mediaItems.indexOfFirst { PlaybackMediaItems.descriptor(it)?.passageId == passageId } }
            ?.takeIf { it >= 0 }
            ?: 0
        val startDuration = PlaybackMediaItems.descriptor(mediaItems[startIndex])?.segmentDurationMs ?: 0L
        val startPosition = queue.startSegmentPositionMs.coerceIn(0L, startDuration)

        withController { controller ->
            nextChapterJob?.cancel()
            prefetchedAfterChapterKey = null
            controller.setMediaItems(mediaItems, startIndex, startPosition)
            controller.prepare()
            controller.play()
            PlaybackMediaItems.descriptor(controller.currentMediaItem)?.let(::prefetchFollowingChapter)
        }
    }

    override suspend fun play() = withController { controller ->
        if (controller.playbackState == Player.STATE_IDLE) controller.prepare()
        controller.play()
    }

    override suspend fun pause() = withController(MediaController::pause)

    override suspend fun seekBy(deltaMs: Long) = withController { controller ->
        val descriptor = PlaybackMediaItems.descriptor(controller.currentMediaItem) ?: return@withController
        val chapterPositionMs = descriptor.chapterStartMs + controller.currentPosition
        seekToChapterPosition(controller, descriptor.chapterId, chapterPositionMs + deltaMs)
    }

    suspend fun seekBack15Seconds() = seekBy(-SEEK_INCREMENT_MS)

    suspend fun seekForward15Seconds() = seekBy(SEEK_INCREMENT_MS)

    suspend fun seekToChapterPosition(positionMs: Long) = withController { controller ->
        val chapterId = PlaybackMediaItems.descriptor(controller.currentMediaItem)?.chapterId
            ?: return@withController
        seekToChapterPosition(controller, chapterId, positionMs)
    }

    override suspend fun seekToPassage(passageId: String) = withController { controller ->
        val index = (0 until controller.mediaItemCount).firstOrNull { mediaIndex ->
            PlaybackMediaItems.descriptor(controller.getMediaItemAt(mediaIndex))?.passageId == passageId
        } ?: return@withController
        controller.seekTo(index, 0L)
    }

    override suspend fun setSpeed(speed: Float) {
        require(speed.isFinite() && speed in MIN_SPEED..MAX_SPEED) {
            "Playback speed must be between $MIN_SPEED and $MAX_SPEED"
        }
        withController { controller -> controller.playbackParameters = PlaybackParameters(speed) }
    }

    override suspend fun setSleepTimer(minutes: Int?) {
        if (minutes != null) require(minutes > 0) { "Sleep timer minutes must be positive" }
        withController { controller ->
            val arguments = Bundle().apply {
                putBoolean(PlaybackCommands.KEY_ENABLED, minutes != null)
                if (minutes != null) putInt(PlaybackCommands.KEY_MINUTES, minutes)
            }
            val result = controller.sendCustomCommand(PlaybackCommands.setSleepTimer, arguments).await()
            check(result.resultCode == SessionResult.RESULT_SUCCESS) {
                "Playback service rejected sleep timer: ${result.resultCode}"
            }
        }
    }

    override fun close() {
        nextChapterJob?.cancel()
        connectedController = null
        continuationScope.cancel()
        if (Looper.myLooper() == Looper.getMainLooper()) {
            MediaController.releaseFuture(controllerFuture)
        } else {
            Handler(Looper.getMainLooper()).post {
                MediaController.releaseFuture(controllerFuture)
            }
        }
    }

    private fun prefetchFollowingChapter(current: PlaybackMediaDescriptor) {
        val controller = connectedController ?: return
        val chapterKey = "${current.bookId}\u0000${current.chapterId}"
        if (prefetchedAfterChapterKey == chapterKey) return
        if (controller.hasChapterAfter(current.chapterId)) {
            prefetchedAfterChapterKey = chapterKey
            return
        }

        nextChapterJob?.cancel()
        prefetchedAfterChapterKey = chapterKey
        nextChapterJob = continuationScope.launch {
            val nextQueue = queueSource.loadNext(current.bookId, current.chapterId).getOrNull() ?: return@launch
            val nextItems = runCatching { PlaybackMediaItems.create(nextQueue) }.getOrNull() ?: return@launch
            if (nextItems.isEmpty()) return@launch

            val latest = PlaybackMediaItems.descriptor(controller.currentMediaItem) ?: return@launch
            if (latest.bookId != current.bookId) return@launch
            if (controller.hasChapter(nextQueue.chapterId)) return@launch

            val firstNextIndex = controller.mediaItemCount
            controller.addMediaItems(nextItems)
            if (controller.playbackState == Player.STATE_ENDED && latest.chapterId == current.chapterId) {
                controller.seekTo(firstNextIndex, 0L)
                controller.prepare()
                controller.play()
            }
        }
    }

    private fun MediaController.hasChapter(chapterId: String): Boolean =
        (0 until mediaItemCount).any { index ->
            PlaybackMediaItems.descriptor(getMediaItemAt(index))?.chapterId == chapterId
        }

    private fun MediaController.hasChapterAfter(chapterId: String): Boolean {
        var foundCurrentChapter = false
        return (0 until mediaItemCount).any { index ->
            val queuedChapterId = PlaybackMediaItems.descriptor(getMediaItemAt(index))?.chapterId
                ?: return@any false
            if (queuedChapterId == chapterId) {
                foundCurrentChapter = true
                false
            } else {
                foundCurrentChapter
            }
        }
    }

    private suspend fun seekToChapterPosition(
        controller: MediaController,
        chapterId: String,
        requestedPositionMs: Long,
    ) {
        val chapterItems = (0 until controller.mediaItemCount).mapNotNull { index ->
            val descriptor = PlaybackMediaItems.descriptor(controller.getMediaItemAt(index))
                ?.takeIf { it.chapterId == chapterId }
                ?: return@mapNotNull null
            IndexedDescriptor(index, descriptor)
        }
        if (chapterItems.isEmpty()) return

        val chapterDurationMs = chapterItems.maxOf { it.descriptor.chapterDurationMs }
        val targetMs = requestedPositionMs.coerceIn(0L, chapterDurationMs)
        val target = chapterItems.firstOrNull { item ->
            targetMs < item.descriptor.chapterStartMs + item.descriptor.segmentDurationMs
        } ?: chapterItems.last()
        val segmentPositionMs = (targetMs - target.descriptor.chapterStartMs)
            .coerceIn(0L, target.descriptor.segmentDurationMs)
        controller.seekTo(target.index, segmentPositionMs)
    }

    private suspend fun <T> withController(block: suspend (MediaController) -> T): T =
        withContext(Dispatchers.Main.immediate) { block(controllerFuture.await()) }

    private data class IndexedDescriptor(
        val index: Int,
        val descriptor: PlaybackMediaDescriptor,
    )

    private companion object {
        const val SEEK_INCREMENT_MS = 15_000L
        const val MIN_SPEED = 0.5f
        const val MAX_SPEED = 3f
    }
}

private val directExecutor = Executor(Runnable::run)

private suspend fun <T> ListenableFuture<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addListener(
        {
            if (!continuation.isActive) return@addListener
            runCatching(::get).fold(
                onSuccess = continuation::resume,
                onFailure = continuation::resumeWithException,
            )
        },
        directExecutor,
    )
    continuation.invokeOnCancellation { cancel(false) }
}
