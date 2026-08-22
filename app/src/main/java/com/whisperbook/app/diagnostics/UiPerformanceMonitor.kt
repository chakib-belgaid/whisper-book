package com.whisperbook.app.diagnostics

import android.os.SystemClock
import android.view.Choreographer

/** Lightweight foreground frame sampler for beta builds and local testing. */
class UiPerformanceMonitor {
    private val choreographer = Choreographer.getInstance()
    private var running = false
    private var windowStartedAtMs = 0L
    private var lastFrameNanos = 0L
    private var renderedFrames = 0L
    private var slowFrames = 0L
    private var frozenFrames = 0L
    private var worstFrameMs = 0L
    private val snapshotter = { reportAndReset("ui_frame_snapshot") }

    private val callback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            if (lastFrameNanos > 0L) {
                val frameMs = (frameTimeNanos - lastFrameNanos).coerceAtLeast(0L) / NANOS_PER_MS
                renderedFrames += 1
                if (frameMs >= SLOW_FRAME_MS) slowFrames += 1
                if (frameMs >= FROZEN_FRAME_MS) frozenFrames += 1
                worstFrameMs = maxOf(worstFrameMs, frameMs)
            }
            lastFrameNanos = frameTimeNanos
            choreographer.postFrameCallback(this)
        }
    }

    fun start() {
        if (running) return
        running = true
        resetWindow(SystemClock.elapsedRealtime())
        BetaDiagnostics.setUiPerformanceSnapshotter(snapshotter)
        choreographer.postFrameCallback(callback)
    }

    fun stop() {
        if (!running) return
        running = false
        choreographer.removeFrameCallback(callback)
        BetaDiagnostics.setUiPerformanceSnapshotter(null)
        BetaDiagnostics.flushSynthesisSummary()
        reportAndReset("ui_frame_summary")
    }

    private fun reportAndReset(event: String) {
        val nowMs = SystemClock.elapsedRealtime()
        BetaDiagnostics.performance(
            event,
            mapOf(
                "foreground_ms" to (nowMs - windowStartedAtMs),
                "rendered_frames" to renderedFrames,
                "slow_frames_50ms" to slowFrames,
                "frozen_frames_700ms" to frozenFrames,
                "worst_frame_ms" to worstFrameMs,
            ) + BetaDiagnostics.currentMemoryDetails(),
        )
        resetWindow(nowMs)
    }

    private fun resetWindow(startedAtMs: Long) {
        windowStartedAtMs = startedAtMs
        lastFrameNanos = 0L
        renderedFrames = 0L
        slowFrames = 0L
        frozenFrames = 0L
        worstFrameMs = 0L
    }

    private companion object {
        const val NANOS_PER_MS = 1_000_000L
        const val SLOW_FRAME_MS = 50L
        const val FROZEN_FRAME_MS = 700L
    }
}
