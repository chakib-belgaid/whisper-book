package com.whisperbook.app.integration

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackResumeResolverTest {
    private val plan = listOf(
        PlannedPlaybackSegment("passage::chunk:1", "passage", "segment-new-1"),
        PlannedPlaybackSegment("passage::chunk:2", "passage", "segment-new-2"),
    )

    @Test
    fun `exact segment checkpoint keeps its microsegment offset`() {
        val target = resolvePlaybackResumeTarget(
            checkpoint = SavedPlaybackResume(
                passageId = "passage::chunk:2",
                segmentId = "segment-new-2",
                segmentPositionMs = 4_200L,
            ),
            currentPassageId = null,
            plannedSegments = plan,
        )

        assertEquals("passage::chunk:2", target.passageId)
        assertEquals("segment-new-2", target.segmentId)
        assertEquals(4_200L, target.segmentPositionMs)
    }

    @Test
    fun `legacy chunk checkpoint maps to first current chunk and resets its offset`() {
        val target = resolvePlaybackResumeTarget(
            checkpoint = SavedPlaybackResume(
                passageId = "passage::chunk:1",
                segmentId = "segment-from-old-large-chunk",
                segmentPositionMs = 21_000L,
            ),
            currentPassageId = null,
            plannedSegments = plan,
        )

        assertEquals("passage::chunk:1", target.passageId)
        assertEquals("segment-new-1", target.segmentId)
        assertEquals(0L, target.segmentPositionMs)
    }

    @Test
    fun `book passage fallback starts at the first microsegment`() {
        val target = resolvePlaybackResumeTarget(
            checkpoint = null,
            currentPassageId = "passage",
            plannedSegments = plan,
        )

        assertEquals("passage::chunk:1", target.passageId)
        assertEquals("segment-new-1", target.segmentId)
        assertEquals(0L, target.segmentPositionMs)
    }
}
