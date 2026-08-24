package com.whisperbook.app.engine.tts

import com.whisperbook.app.domain.SynthesisRequest
import com.whisperbook.app.engine.compute.NeuralExecutionBackend
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupertonicAccelerationTest {
    @Test
    fun `initialization failure falls back from nnapi to cpu`() = runTest {
        val attempts = mutableListOf<NeuralExecutionBackend>()
        val engine = engine(androidSdk = 29) { backend ->
            attempts += backend
            if (backend == NeuralExecutionBackend.NNAPI) error("unsupported NNAPI graph")
            FakeRuntime(backend)
        }

        try {
            engine.warmUp().getOrThrow()

            assertEquals(
                listOf(NeuralExecutionBackend.NNAPI, NeuralExecutionBackend.CPU),
                attempts,
            )
        } finally {
            engine.close()
        }
    }

    @Test
    fun `runtime accelerator failure closes nnapi and retries synthesis on cpu`() = runTest {
        val created = mutableListOf<FakeRuntime>()
        val engine = engine(androidSdk = 29) { backend ->
            FakeRuntime(
                backend = backend,
                synthesisFailure = if (backend == NeuralExecutionBackend.NNAPI) {
                    IllegalStateException("driver failed while executing")
                } else {
                    null
                },
            ).also(created::add)
        }

        try {
            val audio = engine.synthesize(request()).getOrThrow()

            assertEquals(
                listOf(NeuralExecutionBackend.NNAPI, NeuralExecutionBackend.CPU),
                created.map(FakeRuntime::backend),
            )
            assertTrue(created.first().closed)
            assertEquals(1, created.last().syntheses)
            assertTrue(audio.pcm16.isNotEmpty())
        } finally {
            engine.close()
        }
    }

    @Test
    fun `api 28 never attempts nnapi`() = runTest {
        val attempts = mutableListOf<NeuralExecutionBackend>()
        val engine = engine(androidSdk = 28) { backend ->
            attempts += backend
            FakeRuntime(backend)
        }

        try {
            engine.warmUp().getOrThrow()
            assertEquals(listOf(NeuralExecutionBackend.CPU), attempts)
        } finally {
            engine.close()
        }
    }

    @Test
    fun `slow representative accelerator sample selects cpu for later work`() {
        assertTrue(shouldFallbackFromSlowAccelerator(audioMs = 2_412, realTimeFactorMilli = 2_394))
        assertTrue(!shouldFallbackFromSlowAccelerator(audioMs = 900, realTimeFactorMilli = 9_000))
        assertTrue(!shouldFallbackFromSlowAccelerator(audioMs = 2_412, realTimeFactorMilli = 1_951))
    }

    @Suppress("OPT_IN_USAGE")
    private fun engine(
        androidSdk: Int,
        createRuntime: (NeuralExecutionBackend) -> SupertonicRuntime,
    ) = SherpaKittenTtsEngine(
        workerDispatcher = UnconfinedTestDispatcher(),
        androidSdk = androidSdk,
        runtimeFactory = SupertonicRuntimeFactory(createRuntime),
    )

    private fun request() = SynthesisRequest(
        text = "The accelerated opening line.",
        voice = SherpaKittenTtsEngine.KITTEN_VOICES.first(),
        speed = 1f,
        cacheKey = "acceleration-test",
    )

    private class FakeRuntime(
        override val backend: NeuralExecutionBackend,
        private val synthesisFailure: Throwable? = null,
    ) : SupertonicRuntime {
        override val sampleRate = SherpaKittenTtsEngine.EXPECTED_SAMPLE_RATE
        override val speakerCount = 10
        var closed = false
        var syntheses = 0

        override fun synthesize(
            text: String,
            languageCode: String,
            speakerIndex: Int,
            speed: Float,
        ): FloatArray {
            syntheses += 1
            synthesisFailure?.let { throw it }
            return floatArrayOf(-0.25f, 0f, 0.25f)
        }

        override fun close() {
            closed = true
        }
    }
}
