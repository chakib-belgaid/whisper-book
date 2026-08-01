package com.whisperbook.app.playback

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerControllerTest {
    @Test
    fun `expires once at its monotonic deadline`() = runTest {
        var expirationCount = 0
        val timer = SleepTimerController(
            scope = this,
            elapsedRealtimeMs = { testScheduler.currentTime },
            onExpired = { expirationCount++ },
        )

        timer.setDurationMs(1_000L)
        assertEquals(1_000L, timer.remainingMs())
        advanceTimeBy(999L)
        runCurrent()
        assertEquals(0, expirationCount)
        assertEquals(1L, timer.remainingMs())

        advanceTimeBy(1L)
        runCurrent()
        assertEquals(1, expirationCount)
        assertNull(timer.remainingMs())
    }

    @Test
    fun `rescheduling replaces the previous deadline and cancel prevents expiry`() = runTest {
        var expirationCount = 0
        val timer = SleepTimerController(
            scope = this,
            elapsedRealtimeMs = { testScheduler.currentTime },
            onExpired = { expirationCount++ },
        )

        timer.setDurationMs(1_000L)
        advanceTimeBy(500L)
        timer.setDurationMs(2_000L)
        advanceTimeBy(1_500L)
        runCurrent()
        assertEquals(0, expirationCount)
        assertEquals(500L, timer.remainingMs())

        timer.cancel()
        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(0, expirationCount)
        assertNull(timer.remainingMs())
    }
}
