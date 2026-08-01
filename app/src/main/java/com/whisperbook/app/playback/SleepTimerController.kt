package com.whisperbook.app.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A monotonic-clock sleep timer, immune to wall-clock and timezone changes. */
internal class SleepTimerController(
    private val scope: CoroutineScope,
    private val elapsedRealtimeMs: () -> Long,
    private val onExpired: suspend () -> Unit,
) {
    private val mutableDeadlineElapsedMs = MutableStateFlow<Long?>(null)
    val deadlineElapsedMs: StateFlow<Long?> = mutableDeadlineElapsedMs.asStateFlow()

    private var timerJob: Job? = null

    fun setMinutes(minutes: Int?) {
        if (minutes == null) {
            cancel()
            return
        }
        require(minutes > 0) { "Sleep timer minutes must be positive" }
        setDurationMs(Math.multiplyExact(minutes.toLong(), MILLIS_PER_MINUTE))
    }

    internal fun setDurationMs(durationMs: Long?) {
        timerJob?.cancel()
        timerJob = null
        if (durationMs == null) {
            mutableDeadlineElapsedMs.value = null
            return
        }
        require(durationMs > 0L) { "Sleep timer duration must be positive" }

        val deadline = Math.addExact(elapsedRealtimeMs(), durationMs)
        mutableDeadlineElapsedMs.value = deadline
        timerJob = scope.launch {
            while (true) {
                val remaining = deadline - elapsedRealtimeMs()
                if (remaining <= 0L) break
                delay(remaining.coerceAtMost(MAX_DELAY_SLICE_MS))
            }
            if (mutableDeadlineElapsedMs.value == deadline) {
                mutableDeadlineElapsedMs.value = null
                timerJob = null
                onExpired()
            }
        }
    }

    fun remainingMs(): Long? = mutableDeadlineElapsedMs.value?.let { deadline ->
        (deadline - elapsedRealtimeMs()).coerceAtLeast(0L)
    }

    fun cancel() = setDurationMs(null)

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
        const val MAX_DELAY_SLICE_MS = 60_000L
    }
}
