package com.whisperbook.app.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.whisperbook.app.diagnostics.BetaDiagnostics
import com.whisperbook.app.domain.PlaybackGateway
import com.whisperbook.app.domain.model.PlaybackCursor
import com.whisperbook.app.domain.model.PlaybackPreparationProgress
import com.whisperbook.app.domain.model.PlaybackNarrationReload
import java.io.Closeable
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.ensureActive
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
    private var chapterPreparationJob: Job? = null
    private var nextChapterJob: Job? = null
    private var prefetchedAfterChapterKey: String? = null
    private val queueGeneration = AtomicLong()
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
    override val preparationProgress = MutableStateFlow<PlaybackPreparationProgress?>(null)

    override suspend fun playBook(bookId: String, chapterId: String?) =
        playBookInternal(bookId, chapterId, autoPlay = true)

    private suspend fun playBookInternal(
        bookId: String,
        chapterId: String?,
        autoPlay: Boolean,
    ) {
        Log.i(LOG_TAG, "playBook book=$bookId chapter=$chapterId")
        BetaDiagnostics.info("playback_requested", mapOf("has_chapter" to (chapterId != null)))
        val requestedAtMs = SystemClock.elapsedRealtime()
        val generation = queueGeneration.incrementAndGet()
        withContext(Dispatchers.Main.immediate) {
            connectedController?.pause()
            chapterPreparationJob?.cancel()
            nextChapterJob?.cancel()
            prefetchedAfterChapterKey = null
            preparationProgress.value = null
        }
        val firstQueueReady = CompletableDeferred<Unit>()
        chapterPreparationJob = continuationScope.launch {
            var progressivelyPreparingChapterId: String? = null
            try {
                queueSource.loadProgressively(bookId, chapterId) { queue, completed, total ->
                    if (generation != queueGeneration.get()) return@loadProgressively
                    val progressChapterId = queue?.chapterId ?: chapterId
                    if (progressChapterId != null) {
                        preparationProgress.value = PlaybackPreparationProgress(
                            bookId = bookId,
                            chapterId = progressChapterId,
                            completedSegments = completed,
                            totalSegments = total,
                        )
                    }
                    if (queue != null) {
                        if (progressivelyPreparingChapterId != queue.chapterId) {
                            progressivelyPreparingChapterId?.let { previousChapterId ->
                                PlaybackRuntime.clearChapterPreparing(
                                    bookId,
                                    previousChapterId,
                                    generation,
                                )
                            }
                            progressivelyPreparingChapterId = queue.chapterId
                            PlaybackRuntime.markChapterPreparing(bookId, queue.chapterId, generation)
                        }
                        applyProgressiveQueue(
                            generation,
                            queue,
                            firstQueueReady,
                            requestedAtMs,
                            autoPlay,
                        )
                    }
                }.getOrThrow()
                if (!firstQueueReady.isCompleted) {
                    firstQueueReady.completeExceptionally(
                        IllegalStateException("This chapter did not produce playable audio"),
                    )
                }
                progressivelyPreparingChapterId?.let { preparedChapterId ->
                    // Only a successful full load makes the queued prefix a final chapter. If
                    // generation is canceled or fails, keep it marked incomplete so an underrun
                    // cannot be persisted as 100% chapter progress.
                    PlaybackRuntime.clearChapterPreparing(bookId, preparedChapterId, generation)
                }
                if (generation == queueGeneration.get()) {
                    preparationProgress.value = null
                    PlaybackMediaItems.descriptor(connectedController?.currentMediaItem)
                        ?.let(::prefetchFollowingChapter)
                }
            } catch (cancellation: CancellationException) {
                if (!firstQueueReady.isCompleted) firstQueueReady.complete(Unit)
                throw cancellation
            } catch (failure: Throwable) {
                if (!firstQueueReady.isCompleted) firstQueueReady.completeExceptionally(failure)
                if (generation == queueGeneration.get()) preparationProgress.value = null
            }
        }
        try {
            firstQueueReady.await()
        } catch (cancellation: CancellationException) {
            if (generation == queueGeneration.get()) {
                chapterPreparationJob?.cancel()
                preparationProgress.value = null
            }
            throw cancellation
        }
        coroutineContext.ensureActive()
    }

    override suspend fun play() = withController { controller ->
        if (controller.playbackState == Player.STATE_IDLE) controller.prepare()
        controller.play()
    }

    override suspend fun pause() = withController { controller ->
        Log.i(LOG_TAG, "pause requested by gateway")
        controller.pause()
    }

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

    override suspend fun invalidateQueuedChapters(bookId: String, chapterIds: Set<String>) {
        if (chapterIds.isEmpty()) return
        withController { controller ->
            nextChapterJob?.cancel()
            nextChapterJob = null
            prefetchedAfterChapterKey = null

            val current = PlaybackMediaItems.descriptor(controller.currentMediaItem)
            val currentChapterId = current?.takeIf { it.bookId == bookId }?.chapterId
            val removableIndices = (0 until controller.mediaItemCount).filter { index ->
                val descriptor = PlaybackMediaItems.descriptor(controller.getMediaItemAt(index))
                    ?: return@filter false
                descriptor.bookId == bookId &&
                    descriptor.chapterId in chapterIds &&
                    descriptor.chapterId != currentChapterId
            }
            removableIndices.asReversed().forEach(controller::removeMediaItem)
        }
    }

    override suspend fun invalidateNarrationProfile(
        bookId: String,
        chapterIds: Set<String>,
    ): PlaybackNarrationReload? {
        if (chapterIds.isEmpty()) return null
        var reload: PlaybackNarrationReload? = null
        withController { controller ->
            val currentDescriptor = PlaybackMediaItems.descriptor(controller.currentMediaItem)
            val currentCursor = cursor.value
            val affectsCurrent = currentDescriptor?.bookId == bookId &&
                currentDescriptor.chapterId in chapterIds
            if (affectsCurrent) {
                reload = PlaybackNarrationReload(
                    bookId = bookId,
                    chapterId = currentDescriptor.chapterId,
                    passageId = currentCursor?.takeIf { it.bookId == bookId }?.passageId
                        ?: currentDescriptor.passageId,
                    wasPlaying = controller.playWhenReady,
                )
                queueGeneration.incrementAndGet()
                chapterPreparationJob?.cancel()
                chapterPreparationJob = null
                nextChapterJob?.cancel()
                nextChapterJob = null
                prefetchedAfterChapterKey = null
                preparationProgress.value = null
                PlaybackRuntime.clearChapterPreparing()
                controller.pause()
                controller.clearMediaItems()
            }
        }
        if (reload == null) invalidateQueuedChapters(bookId, chapterIds)
        return reload
    }

    override suspend fun reloadNarrationProfile(reload: PlaybackNarrationReload) {
        playBookInternal(reload.bookId, reload.chapterId, autoPlay = reload.wasPlaying)
        seekToPassage(reload.passageId)
        if (!reload.wasPlaying) pause()
    }

    override fun close() {
        queueGeneration.incrementAndGet()
        chapterPreparationJob?.cancel()
        nextChapterJob?.cancel()
        preparationProgress.value = null
        PlaybackRuntime.clearChapterPreparing()
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
        val activePreparation = preparationProgress.value
        if (activePreparation?.bookId == current.bookId && activePreparation.chapterId == current.chapterId) return
        if (prefetchedAfterChapterKey == chapterKey) return
        if (controller.hasChapterAfter(current.chapterId)) {
            prefetchedAfterChapterKey = chapterKey
            return
        }

        nextChapterJob?.cancel()
        prefetchedAfterChapterKey = chapterKey
        val generation = queueGeneration.get()
        nextChapterJob = continuationScope.launch {
            var prefetched = false
            try {
                val nextQueue = queueSource.loadNext(current.bookId, current.chapterId).getOrNull()
                    ?: return@launch
                if (generation != queueGeneration.get()) return@launch
                val nextItems = withContext(Dispatchers.Default) {
                    runCatching { PlaybackMediaItems.create(nextQueue) }.getOrNull()
                } ?: return@launch
                if (nextItems.isEmpty()) return@launch

                val latest = PlaybackMediaItems.descriptor(controller.currentMediaItem) ?: return@launch
                if (latest.bookId != current.bookId) return@launch
                if (controller.hasChapter(nextQueue.chapterId)) {
                    prefetched = true
                    return@launch
                }

                val firstNextIndex = controller.mediaItemCount
                val wasWaitingForNextChapter =
                    controller.playbackState == Player.STATE_ENDED && latest.chapterId == current.chapterId
                controller.addMediaItems(nextItems)
                prefetched = true
                if (wasWaitingForNextChapter) {
                    controller.seekTo(firstNextIndex, 0L)
                    controller.prepare()
                    controller.play()
                }
            } finally {
                // An unattributed next chapter is expected while a large book is still streaming.
                // Do not latch the failed prefetch forever; a later playback callback can retry
                // after that chapter's character catalog and voice assignments arrive.
                if (
                    !prefetched &&
                    generation == queueGeneration.get() &&
                    prefetchedAfterChapterKey == chapterKey
                ) {
                    prefetchedAfterChapterKey = null
                }
            }
        }
    }

    private suspend fun applyProgressiveQueue(
        generation: Long,
        queue: PlaybackChapterQueue,
        firstQueueReady: CompletableDeferred<Unit>,
        requestedAtMs: Long,
        autoPlay: Boolean,
    ) {
        val existingChapterSegments = if (!firstQueueReady.isCompleted) {
            0
        } else {
            withController { controller ->
                (0 until controller.mediaItemCount).count { index ->
                    PlaybackMediaItems.descriptor(controller.getMediaItemAt(index))?.let { descriptor ->
                        descriptor.bookId == queue.bookId && descriptor.chapterId == queue.chapterId
                    } == true
                }
            }
        }
        if (existingChapterSegments >= queue.segments.size && firstQueueReady.isCompleted) return
        val mediaItems = withContext(Dispatchers.Default) {
            PlaybackMediaItems.create(queue, fromSegmentIndex = existingChapterSegments)
        }
        withController { controller ->
            if (generation != queueGeneration.get()) return@withController
            if (!firstQueueReady.isCompleted) {
                val startIndex = queue.startPassageId
                    ?.let { passageId ->
                        mediaItems.indexOfFirst { PlaybackMediaItems.descriptor(it)?.passageId == passageId }
                    }
                    ?.takeIf { it >= 0 }
                    ?: 0
                val startDuration = PlaybackMediaItems.descriptor(mediaItems[startIndex])?.segmentDurationMs ?: 0L
                val startPosition = queue.startSegmentPositionMs.coerceIn(0L, startDuration)
                controller.setMediaItems(mediaItems, startIndex, startPosition)
                controller.prepare()
                if (autoPlay) controller.play()
                Log.i(
                    LOG_TAG,
                    "first_audio_ready book=${queue.bookId} chapter=${queue.chapterId} " +
                        "elapsedMs=${SystemClock.elapsedRealtime() - requestedAtMs} " +
                        "bufferMs=${queue.durationMs}",
                )
                BetaDiagnostics.performance(
                    "first_audio_ready",
                    mapOf(
                        "elapsed_ms" to (SystemClock.elapsedRealtime() - requestedAtMs),
                        "buffered_audio_ms" to queue.durationMs,
                        "segment_count" to queue.segments.size,
                    ),
                )
                firstQueueReady.complete(Unit)
                return@withController
            }

            if (mediaItems.isEmpty()) return@withController
            val resumeFromIndex = controller.mediaItemCount
            val wasWaitingForAudio = controller.playbackState == Player.STATE_ENDED
            controller.addMediaItems(mediaItems)
            if (wasWaitingForAudio) {
                controller.seekTo(resumeFromIndex, 0L)
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
        const val LOG_TAG = "WhisperPlayback"
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
