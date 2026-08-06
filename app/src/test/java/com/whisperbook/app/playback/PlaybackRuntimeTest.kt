package com.whisperbook.app.playback

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRuntimeTest {
    @After
    fun resetRuntime() {
        PlaybackRuntime.clearChapterPreparing()
    }

    @Test
    fun `stale generation cannot clear current progressive chapter`() {
        PlaybackRuntime.markChapterPreparing("book", "chapter", generation = 2L)
        PlaybackRuntime.clearChapterPreparing("book", "chapter", generation = 1L)

        assertTrue(PlaybackRuntime.isChapterPreparing("book", "chapter"))

        PlaybackRuntime.clearChapterPreparing("book", "chapter", generation = 2L)
        assertFalse(PlaybackRuntime.isChapterPreparing("book", "chapter"))
    }

    @Test
    fun `progressive chapter identity is scoped to the active book and chapter`() {
        PlaybackRuntime.markChapterPreparing("book", "chapter", generation = 1L)

        assertTrue(PlaybackRuntime.isChapterPreparing("book", "chapter"))
        assertFalse(PlaybackRuntime.isChapterPreparing("other-book", "chapter"))
        assertFalse(PlaybackRuntime.isChapterPreparing("book", "other-chapter"))
    }
}
