package com.whisperbook.app.playback

import com.whisperbook.app.domain.model.PlaybackCursor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Callback seam for persisting progress without coupling the playback service to a database. */
fun interface PlaybackCheckpointSink {
    suspend fun save(cursor: PlaybackCursor)
}

/** Same-process bridge shared by the MediaSessionService and controller-backed UI gateway. */
object PlaybackRuntime {
    private val mutableCursor = MutableStateFlow<PlaybackCursor?>(null)
    val cursor: StateFlow<PlaybackCursor?> = mutableCursor.asStateFlow()

    @Volatile
    internal var checkpointSink: PlaybackCheckpointSink? = null

    fun installCheckpointSink(sink: PlaybackCheckpointSink?) {
        checkpointSink = sink
    }

    internal fun publish(cursor: PlaybackCursor?) {
        mutableCursor.value = cursor
    }
}
