package com.whisperbook.app.engine.audio

import java.util.ArrayDeque
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Serializes process-local model generation across WorkManager and on-demand playback.
 *
 * The embedded model is expensive to run more than once at a time. Keeping the cache check inside
 * this guard also lets a chapter request reuse audio that background preparation just completed.
 */
internal object LocalAudioGenerationCoordinator {
    private val gate = LocalAudioGenerationGate()

    /** On-demand playback is serialized and gets the first turn when background work is waiting. */
    suspend fun <T> run(block: suspend () -> T): T = gate.runOnDemand(block)

    /** Background preparation yields to playback once, then gets a bounded turn of its own. */
    suspend fun <T> runBackground(block: suspend () -> T): T = gate.runBackground(block)
}

/** Testable process-local gate behind [LocalAudioGenerationCoordinator]. */
internal class LocalAudioGenerationGate {
    private val stateMutex = Mutex()
    private val onDemandWaiters = ArrayDeque<GenerationWaiter>()
    private val backgroundWaiters = ArrayDeque<GenerationWaiter>()
    private var activeWaiter: GenerationWaiter? = null
    private var onDemandTurnUsedWhileBackgroundWaits = false

    suspend fun <T> runOnDemand(block: suspend () -> T): T =
        run(GenerationPriority.ON_DEMAND, block)

    suspend fun <T> runBackground(block: suspend () -> T): T =
        run(GenerationPriority.BACKGROUND, block)

    private suspend fun <T> run(
        priority: GenerationPriority,
        block: suspend () -> T,
    ): T {
        val waiter = GenerationWaiter(priority)
        var registered = false
        try {
            stateMutex.withLock {
                queueFor(priority).addLast(waiter)
                registered = true
                if (
                    priority == GenerationPriority.BACKGROUND &&
                    backgroundWaiters.size == 1 &&
                    activeWaiter?.priority == GenerationPriority.ON_DEMAND
                ) {
                    // The active playback segment counts as this background waiter's priority turn.
                    onDemandTurnUsedWhileBackgroundWaits = true
                }
                grantNextLocked()
            }
            waiter.granted.await()
            return block()
        } finally {
            if (registered) {
                withContext(NonCancellable) {
                    stateMutex.withLock {
                        when (waiter.state) {
                            WaiterState.QUEUED -> queueFor(priority).remove(waiter)
                            WaiterState.GRANTED -> {
                                check(activeWaiter === waiter)
                                activeWaiter = null
                            }
                            WaiterState.RELEASED -> Unit
                        }
                        waiter.state = WaiterState.RELEASED
                        if (backgroundWaiters.isEmpty()) {
                            onDemandTurnUsedWhileBackgroundWaits = false
                        }
                        grantNextLocked()
                    }
                }
            }
        }
    }

    private fun grantNextLocked() {
        if (activeWaiter != null) return
        val next = when {
            onDemandWaiters.isNotEmpty() &&
                (backgroundWaiters.isEmpty() || !onDemandTurnUsedWhileBackgroundWaits) -> {
                onDemandWaiters.removeFirst().also {
                    if (backgroundWaiters.isNotEmpty()) {
                        onDemandTurnUsedWhileBackgroundWaits = true
                    }
                }
            }
            backgroundWaiters.isNotEmpty() -> {
                backgroundWaiters.removeFirst().also {
                    onDemandTurnUsedWhileBackgroundWaits = false
                }
            }
            else -> null
        } ?: return
        next.state = WaiterState.GRANTED
        activeWaiter = next
        next.granted.complete(Unit)
    }

    private fun queueFor(priority: GenerationPriority): ArrayDeque<GenerationWaiter> =
        when (priority) {
            GenerationPriority.ON_DEMAND -> onDemandWaiters
            GenerationPriority.BACKGROUND -> backgroundWaiters
        }

    private class GenerationWaiter(val priority: GenerationPriority) {
        val granted = CompletableDeferred<Unit>()
        var state = WaiterState.QUEUED
    }

    private enum class GenerationPriority { ON_DEMAND, BACKGROUND }
    private enum class WaiterState { QUEUED, GRANTED, RELEASED }
}
