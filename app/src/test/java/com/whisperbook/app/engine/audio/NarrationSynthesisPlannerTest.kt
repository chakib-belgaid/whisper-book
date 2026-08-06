package com.whisperbook.app.engine.audio

import com.whisperbook.app.domain.NarrationTextChunker
import com.whisperbook.app.domain.SynthesisRequest
import com.whisperbook.app.domain.model.VoiceDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NarrationSynthesisPlannerTest {
    private val voice = VoiceDescriptor("voice-a", "Voice A", 2)

    @Test
    fun `long passage produces stable ordered unique synthesis units`() {
        val text = List(80) { index -> "Sentence $index ends cleanly." }.joinToString(" ")

        val first = plan(text)
        val second = plan(text)

        assertEquals(first, second)
        assertTrue(first.size > 1)
        assertTrue(first.all { it.request.text.length <= NarrationTextChunker.MAX_CHARS })
        assertEquals(text, first.joinToString(" ") { it.request.text })
        assertEquals(first.size, first.map { it.passageId }.distinct().size)
        assertEquals(first.size, first.map { it.request.cacheKey }.distinct().size)
    }

    @Test
    fun `short passage keeps the legacy passage cache identity`() {
        val text = "A short opening line."
        val unit = plan(text).single()
        val provisional = SynthesisRequest(text, voice, 1f, "pending")

        assertEquals("passage-1", unit.passageId)
        assertEquals(
            AudioCacheKey.forPassage("passage-1", provisional, MODEL_VERSION, SAMPLE_RATE),
            unit.request.cacheKey,
        )
    }

    private fun plan(text: String) = NarrationSynthesisPlanner.plan(
        passageId = "passage-1",
        text = text,
        voice = voice,
        speed = 1f,
        modelVersion = MODEL_VERSION,
        sampleRate = SAMPLE_RATE,
    )

    private companion object {
        const val MODEL_VERSION = "test-model-v1"
        const val SAMPLE_RATE = 24_000
    }
}
