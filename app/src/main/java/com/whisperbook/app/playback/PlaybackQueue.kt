package com.whisperbook.app.playback

import com.whisperbook.app.domain.model.AudioSegment
import com.whisperbook.app.domain.model.AudioSegmentState

/** Supplies a ready, local-only chapter queue to the controller gateway. */
fun interface PlaybackQueueSource {
    suspend fun load(bookId: String, chapterId: String?): Result<PlaybackChapterQueue>

    /** Resolves the chapter immediately after [chapterId], or null at the end of the book. */
    suspend fun loadNext(bookId: String, chapterId: String): Result<PlaybackChapterQueue?> =
        Result.success(null)
}

data class PlaybackChapterQueue(
    val bookId: String,
    val chapterId: String,
    val bookTitle: String,
    val chapterTitle: String,
    val segments: List<PlayableSegment>,
    val startPassageId: String? = null,
    val startSegmentPositionMs: Long = 0L,
) {
    init {
        require(bookId.isNotBlank())
        require(chapterId.isNotBlank())
        require(segments.isNotEmpty()) { "A playback queue must contain at least one segment" }
        require(startSegmentPositionMs >= 0L)
        require(segments.all { it.audioSegment.state == AudioSegmentState.READY }) {
            "Only ready audio segments may be queued"
        }
        require(segments.all { !it.audioSegment.path.isNullOrBlank() }) {
            "Every queued segment must have a local path"
        }
    }

    val durationMs: Long
        get() = segments.sumOf { it.audioSegment.durationMs.coerceAtLeast(0L) }
}

data class PlayableSegment(
    val passageId: String,
    val passageOrdinal: Int,
    val speakerName: String,
    val audioSegment: AudioSegment,
) {
    init {
        require(passageId.isNotBlank())
        require(passageOrdinal >= 0)
        require(audioSegment.durationMs >= 0L)
    }
}
