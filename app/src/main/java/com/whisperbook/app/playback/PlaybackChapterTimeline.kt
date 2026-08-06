package com.whisperbook.app.playback

internal data class PlaybackChapterTimeline(
    val currentSegmentStartMs: Long,
    val chapterDurationMs: Long,
)

/** Derives a current chapter timeline from the live queue as progressive segments are appended. */
internal fun playbackChapterTimeline(
    queuedDescriptors: List<PlaybackMediaDescriptor>,
    current: PlaybackMediaDescriptor,
): PlaybackChapterTimeline? {
    val chapterItems = queuedDescriptors.filter { descriptor ->
        descriptor.bookId == current.bookId && descriptor.chapterId == current.chapterId
    }
    if (chapterItems.isEmpty()) return null

    var runningStartMs = 0L
    var currentStartMs: Long? = null
    chapterItems.forEach { descriptor ->
        if (
            currentStartMs == null &&
            descriptor.segmentId == current.segmentId &&
            descriptor.passageId == current.passageId
        ) {
            currentStartMs = runningStartMs
        }
        runningStartMs += descriptor.segmentDurationMs.coerceAtLeast(0L)
    }
    return currentStartMs?.let { startMs ->
        PlaybackChapterTimeline(
            currentSegmentStartMs = startMs,
            chapterDurationMs = runningStartMs,
        )
    }
}
