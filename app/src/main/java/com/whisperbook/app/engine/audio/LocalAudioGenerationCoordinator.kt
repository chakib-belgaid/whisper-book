package com.whisperbook.app.engine.audio

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes process-local model generation across WorkManager and on-demand playback.
 *
 * The embedded model is expensive to run more than once at a time. Keeping the cache check inside
 * this guard also lets a chapter request reuse audio that background preparation just completed.
 */
internal object LocalAudioGenerationCoordinator {
    private val gate = LocalAudioGenerationGate()

    /** On-demand playback work is serialized and takes the next available generation slot. */
    suspend fun <T> run(block: suspend () -> T): T = gate.runOnDemand(block)

    /** Background preparation yields between passages while a playback request is waiting. */
    suspend fun <T> runBackground(block: suspend () -> T): T = gate.runBackground(block)
}

/** Testable process-local gate behind [LocalAudioGenerationCoordinator]. */
internal class LocalAudioGenerationGate {
    private val generationMutex = Mutex()
    private val onDemandWaiters = AtomicInteger(0)

    suspend fun <T> runOnDemand(block: suspend () -> T): T {
        onDemandWaiters.incrementAndGet()
        return try {
            generationMutex.withLock { block() }
        } finally {
            onDemandWaiters.decrementAndGet()
        }
    }

    suspend fun <T> runBackground(block: suspend () -> T): T {
        while (true) {
            if (onDemandWaiters.get() == 0 && generationMutex.tryLock()) {
                // A playback request may have arrived in the same scheduling turn. Let it go first
                // instead of beginning another CPU-heavy passage.
                if (onDemandWaiters.get() > 0) {
                    generationMutex.unlock()
                } else {
                    try {
                        return block()
                    } finally {
                        generationMutex.unlock()
                    }
                }
            }
            delay(BACKGROUND_RETRY_DELAY_MS)
        }
    }

    private companion object {
        const val BACKGROUND_RETRY_DELAY_MS = 10L
    }
}
