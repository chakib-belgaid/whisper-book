package com.whisperbook.app.playback

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.whisperbook.app.domain.model.PlaybackCursor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Media3 session service for uninterrupted, local-file audiobook playback. */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class WhisperPlaybackService : MediaSessionService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession
    private lateinit var sleepTimer: SleepTimerController
    private var cursorTicker: Job? = null
    private var lastCheckpointElapsedMs = -CHECKPOINT_INTERVAL_MS
    private var lastCheckpointCursor: PlaybackCursor? = null

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            val forceCheckpoint = events.containsAny(
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_POSITION_DISCONTINUITY,
                Player.EVENT_IS_PLAYING_CHANGED,
                Player.EVENT_PLAYBACK_STATE_CHANGED,
            )
            publishCursor(forceCheckpoint)
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            Log.i(LOG_TAG, "playWhenReady=$playWhenReady reason=$reason")
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            Log.i(LOG_TAG, "playbackState=$playbackState item=${player.currentMediaItemIndex}")
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            Log.e(LOG_TAG, "Playback failed", error)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val audiobookAudioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audiobookAudioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        MIN_AUDIO_BUFFER_MS,
                        MAX_AUDIO_BUFFER_MS,
                        BUFFER_FOR_PLAYBACK_MS,
                        BUFFER_AFTER_REBUFFER_MS,
                    )
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build(),
            )
            .setSeekBackIncrementMs(SEEK_INCREMENT_MS)
            .setSeekForwardIncrementMs(SEEK_INCREMENT_MS)
            .build()
            .also { it.addListener(playerListener) }
        sleepTimer = SleepTimerController(
            scope = serviceScope,
            elapsedRealtimeMs = SystemClock::elapsedRealtime,
            onExpired = {
                player.pause()
                publishCursor(forceCheckpoint = true)
            },
        )
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(SessionCallback())
            .build()
        setShowNotificationForIdlePlayer(SHOW_NOTIFICATION_FOR_IDLE_PLAYER_AFTER_STOP_OR_ERROR)

        cursorTicker = serviceScope.launch {
            while (isActive) {
                if (player.isPlaying) publishCursor(forceCheckpoint = false)
                delay(CURSOR_UPDATE_INTERVAL_MS)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

    override fun onDestroy() {
        publishCursor(forceCheckpoint = true)
        cursorTicker?.cancel()
        sleepTimer.cancel()
        player.removeListener(playerListener)
        mediaSession.release()
        player.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun publishCursor(forceCheckpoint: Boolean) {
        val itemIndex = player.currentMediaItemIndex
        if (itemIndex == C.INDEX_UNSET) {
            PlaybackRuntime.publish(null)
            return
        }
        val descriptor = PlaybackMediaItems.descriptor(player.currentMediaItem) ?: return
        val timeline = playbackChapterTimeline(
            queuedDescriptors = (0 until player.mediaItemCount).mapNotNull { index ->
                PlaybackMediaItems.descriptor(player.getMediaItemAt(index))
            },
            current = descriptor,
        )
        val chapterStartMs = timeline?.currentSegmentStartMs ?: descriptor.chapterStartMs
        val chapterDurationMs = timeline?.chapterDurationMs ?: descriptor.chapterDurationMs
        val chapterDurationIsFinal = !PlaybackRuntime.isChapterPreparing(
            bookId = descriptor.bookId,
            chapterId = descriptor.chapterId,
        )
        val segmentPositionMs = player.currentPosition.coerceAtLeast(0L)
        val cursor = PlaybackCursor(
            bookId = descriptor.bookId,
            chapterId = descriptor.chapterId,
            passageId = descriptor.passageId,
            segmentId = descriptor.segmentId,
            segmentPositionMs = segmentPositionMs,
            chapterPositionMs = (chapterStartMs + segmentPositionMs)
                .coerceAtMost(chapterDurationMs),
            chapterDurationMs = chapterDurationMs,
            isPlaying = player.isPlaying,
            speed = player.playbackParameters.speed,
            segmentDurationMs = descriptor.segmentDurationMs,
            chapterDurationIsFinal = chapterDurationIsFinal,
        )
        PlaybackRuntime.publish(cursor)
        maybeCheckpoint(cursor, forceCheckpoint)
    }

    private fun maybeCheckpoint(cursor: PlaybackCursor, force: Boolean) {
        val now = SystemClock.elapsedRealtime()
        val meaningfulChange = cursor != lastCheckpointCursor
        if (!meaningfulChange || (!force && now - lastCheckpointElapsedMs < CHECKPOINT_INTERVAL_MS)) return

        lastCheckpointElapsedMs = now
        lastCheckpointCursor = cursor
        val sink = PlaybackRuntime.checkpointSink ?: return
        serviceScope.launch { sink.save(cursor) }
    }

    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult = MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            .setAvailableSessionCommands(
                MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                    .buildUpon()
                    .add(PlaybackCommands.setSleepTimer)
                    .build(),
            )
            .build()

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand != PlaybackCommands.setSleepTimer) {
                return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
            }
            val resultCode = runCatching {
                if (args.getBoolean(PlaybackCommands.KEY_ENABLED, false)) {
                    sleepTimer.setMinutes(args.getInt(PlaybackCommands.KEY_MINUTES))
                } else {
                    sleepTimer.cancel()
                }
            }.fold(
                onSuccess = { SessionResult.RESULT_SUCCESS },
                onFailure = { SessionResult.RESULT_ERROR_BAD_VALUE },
            )
            return Futures.immediateFuture(SessionResult(resultCode))
        }
    }

    private companion object {
        const val SEEK_INCREMENT_MS = 15_000L
        const val CURSOR_UPDATE_INTERVAL_MS = 250L
        const val CHECKPOINT_INTERVAL_MS = 5_000L
        const val MIN_AUDIO_BUFFER_MS = 2 * 60_000
        const val MAX_AUDIO_BUFFER_MS = 5 * 60_000
        const val BUFFER_FOR_PLAYBACK_MS = 500
        const val BUFFER_AFTER_REBUFFER_MS = 1_000
        const val LOG_TAG = "WhisperPlayback"
    }
}

internal object PlaybackCommands {
    const val ACTION_SET_SLEEP_TIMER = "com.whisperbook.app.playback.SET_SLEEP_TIMER"
    const val KEY_ENABLED = "enabled"
    const val KEY_MINUTES = "minutes"
    val setSleepTimer = SessionCommand(ACTION_SET_SLEEP_TIMER, Bundle.EMPTY)
}
