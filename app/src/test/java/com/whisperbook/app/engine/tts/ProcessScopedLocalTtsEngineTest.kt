package com.whisperbook.app.engine.tts

import com.whisperbook.app.domain.LocalTtsEngine
import com.whisperbook.app.domain.SynthesisRequest
import com.whisperbook.app.domain.SynthesisResult
import com.whisperbook.app.domain.model.VoiceDescriptor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessScopedLocalTtsEngineTest {
    @Test
    fun `borrower close keeps shared runtime alive until shutdown`() = runTest {
        val delegate = RecordingEngine()
        val shared = ProcessScopedLocalTtsEngine(delegate)

        shared.warmUp().getOrThrow()
        shared.close()
        shared.synthesize(request()).getOrThrow()

        assertFalse(delegate.closed)
        assertEquals(1, delegate.syntheses)

        shared.shutdown()
        assertTrue(delegate.closed)
    }

    private fun request() = SynthesisRequest(
        text = "Opening line.",
        voice = VoiceDescriptor("voice", "Voice", 0),
        speed = 1f,
        cacheKey = "cache",
    )
}

private class RecordingEngine : LocalTtsEngine {
    var closed = false
    var syntheses = 0

    override suspend fun warmUp(): Result<Unit> = Result.success(Unit)

    override suspend fun voices(): List<VoiceDescriptor> = emptyList()

    override suspend fun synthesize(request: SynthesisRequest): Result<SynthesisResult> {
        check(!closed)
        syntheses += 1
        return Result.success(SynthesisResult(shortArrayOf(1), 24_000, 1L))
    }

    override fun close() {
        closed = true
    }
}
