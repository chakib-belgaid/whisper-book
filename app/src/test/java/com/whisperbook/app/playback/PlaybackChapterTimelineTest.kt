package com.whisperbook.app.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackChapterTimelineTest {
    @Test
    fun `live queue grows the duration of an already playing opening segment`() {
        val opening = descriptor("segment-1", "passage-1", durationMs = 1_000, partialChapterMs = 1_000)
        val middle = descriptor("segment-2", "passage-2", durationMs = 2_000, partialChapterMs = 3_000)
        val ending = descriptor("segment-3", "passage-3", durationMs = 3_000, partialChapterMs = 6_000)

        val timeline = playbackChapterTimeline(listOf(opening, middle, ending), opening)

        assertEquals(0L, timeline?.currentSegmentStartMs)
        assertEquals(6_000L, timeline?.chapterDurationMs)
    }

    @Test
    fun `current segment start is derived from preceding live items`() {
        val opening = descriptor("segment-1", "passage-1", durationMs = 1_000, partialChapterMs = 1_000)
        val middle = descriptor("segment-2", "passage-2", durationMs = 2_000, partialChapterMs = 3_000)
        val ending = descriptor("segment-3", "passage-3", durationMs = 3_000, partialChapterMs = 6_000)

        val timeline = playbackChapterTimeline(listOf(opening, middle, ending), middle)

        assertEquals(1_000L, timeline?.currentSegmentStartMs)
        assertEquals(6_000L, timeline?.chapterDurationMs)
    }

    private fun descriptor(
        segmentId: String,
        passageId: String,
        durationMs: Long,
        partialChapterMs: Long,
    ) = PlaybackMediaDescriptor(
        bookId = "book",
        chapterId = "chapter",
        passageId = passageId,
        segmentId = segmentId,
        segmentDurationMs = durationMs,
        chapterStartMs = 0L,
        chapterDurationMs = partialChapterMs,
    )
}
