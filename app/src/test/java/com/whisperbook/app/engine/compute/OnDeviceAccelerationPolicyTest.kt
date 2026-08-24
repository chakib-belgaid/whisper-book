package com.whisperbook.app.engine.compute

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class OnDeviceAccelerationPolicyTest {
    @Test
    fun `api 29 and newer prefer nnapi before cpu`() {
        assertEquals(
            listOf(NeuralExecutionBackend.NNAPI, NeuralExecutionBackend.CPU),
            OnDeviceAccelerationPolicy.ttsCandidates(androidSdk = 29),
        )
    }

    @Test
    fun `older Android uses cpu directly`() {
        assertEquals(
            listOf(NeuralExecutionBackend.CPU),
            OnDeviceAccelerationPolicy.ttsCandidates(androidSdk = 28),
        )
    }

    @Test
    fun `loader retains accelerator failure and selects cpu`() {
        val acceleratorFailure = IllegalStateException("NNAPI driver rejected graph")

        val loaded = loadFirstAvailableBackend(
            listOf(NeuralExecutionBackend.NNAPI, NeuralExecutionBackend.CPU),
        ) { backend ->
            if (backend == NeuralExecutionBackend.NNAPI) throw acceleratorFailure
            "cpu-runtime"
        }

        assertEquals(NeuralExecutionBackend.CPU, loaded.backend)
        assertEquals("cpu-runtime", loaded.value)
        assertEquals(1, loaded.earlierFailures.size)
        assertSame(acceleratorFailure, loaded.earlierFailures.single().cause)
    }

    @Test
    fun `loader never converts cancellation into fallback`() {
        val cancellation = CancellationException("cancelled")

        val thrown = assertThrows(CancellationException::class.java) {
            loadFirstAvailableBackend(
                listOf(NeuralExecutionBackend.NNAPI, NeuralExecutionBackend.CPU),
            ) { throw cancellation }
        }

        assertSame(cancellation, thrown)
    }
}
