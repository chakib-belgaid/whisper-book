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
    private var preparingChapter: PreparingChapter? = null

    @Volatile
    internal var checkpointSink: PlaybackCheckpointSink? = null

    fun installCheckpointSink(sink: PlaybackCheckpointSink?) {
        checkpointSink = sink
    }

    internal fun publish(cursor: PlaybackCursor?) {
        mutableCursor.value = cursor
    }

    internal fun markChapterPreparing(
        bookId: String,
        chapterId: String,
        generation: Long,
    ) {
        preparingChapter = PreparingChapter(bookId, chapterId, generation)
    }

    internal fun clearChapterPreparing(
        bookId: String,
        chapterId: String,
        generation: Long,
    ) {
        val expected = PreparingChapter(bookId, chapterId, generation)
        if (preparingChapter == expected) preparingChapter = null
    }

    internal fun clearChapterPreparing() {
        preparingChapter = null
    }

    internal fun isChapterPreparing(bookId: String, chapterId: String): Boolean =
        preparingChapter?.let { it.bookId == bookId && it.chapterId == chapterId } == true

    private data class PreparingChapter(
        val bookId: String,
        val chapterId: String,
        val generation: Long,
    )
}
