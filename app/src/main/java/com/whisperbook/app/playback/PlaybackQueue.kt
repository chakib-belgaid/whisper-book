package com.whisperbook.app.playback

import com.whisperbook.app.domain.model.AudioSegment
import com.whisperbook.app.domain.model.AudioSegmentState

/** Supplies a ready, local-only chapter queue to the controller gateway. */
fun interface PlaybackQueueSource {
    suspend fun load(bookId: String, chapterId: String?): Result<PlaybackChapterQueue>

    /**
     * Emits each ready prefix of a chapter queue. Consumers can start the first item immediately
     * and append later items instead of waiting for the entire chapter to be synthesized.
     */
    suspend fun loadProgressively(
        bookId: String,
        chapterId: String?,
        onProgress: suspend (
            readyQueue: PlaybackChapterQueue?,
            completedSegments: Int,
            totalSegments: Int,
        ) -> Unit,
    ): Result<PlaybackChapterQueue> {
        val result = load(bookId, chapterId)
        result.getOrNull()?.let { queue ->
            onProgress(queue, queue.segments.size, queue.segments.size)
        }
        return result
    }

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
