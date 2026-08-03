package com.whisperbook.app.engine.audio

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LocalAudioGenerationCoordinatorTest {
    @Test
    fun `on demand generation stays serialized`() = runTest {
        val gate = LocalAudioGenerationGate()
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        var concurrentBlocks = 0
        var maximumConcurrentBlocks = 0

        val first = launch {
            gate.runOnDemand {
                concurrentBlocks += 1
                maximumConcurrentBlocks = maxOf(maximumConcurrentBlocks, concurrentBlocks)
                firstStarted.complete(Unit)
                releaseFirst.await()
                concurrentBlocks -= 1
            }
        }
        firstStarted.await()
        val second = async {
            gate.runOnDemand {
                concurrentBlocks += 1
                maximumConcurrentBlocks = maxOf(maximumConcurrentBlocks, concurrentBlocks)
                concurrentBlocks -= 1
                "second"
            }
        }

        yield()
        assertFalse(second.isCompleted)
        releaseFirst.complete(Unit)

        assertEquals("second", second.await())
        first.join()
        assertEquals(1, maximumConcurrentBlocks)
    }

    @Test
    fun `waiting playback runs before another background passage`() = runTest {
        val gate = LocalAudioGenerationGate()
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()

        val firstBackground = launch {
            gate.runBackground {
                order += "background-1"
                firstStarted.complete(Unit)
                releaseFirst.await()
            }
        }
        firstStarted.await()
        val secondBackground = launch {
            gate.runBackground { order += "background-2" }
        }
        val playback = launch {
            gate.runOnDemand { order += "playback" }
        }
        yield()
        releaseFirst.complete(Unit)

        firstBackground.join()
        playback.join()
        secondBackground.join()
        assertEquals(listOf("background-1", "playback", "background-2"), order)
    }
}
