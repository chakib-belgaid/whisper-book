package com.whisperbook.app.engine.tts

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PcmConversionTest {
    @Test
    fun `float conversion clips and uses the full signed pcm range`() {
        val samples = floatArrayOf(
            Float.NEGATIVE_INFINITY,
            -1.5f,
            -1f,
            -0.5f,
            0f,
            0.5f,
            1f,
            1.5f,
            Float.POSITIVE_INFINITY,
            Float.NaN,
        )

        assertArrayEquals(
            shortArrayOf(-32768, -32768, -32768, -16384, 0, 16384, 32767, 32767, 32767, 0),
            floatsToPcm16(samples),
        )
    }

    @Test
    fun `duration uses the provided sample rate without floating point drift`() {
        assertEquals(1_500L, pcmDurationMs(sampleCount = 36_000, sampleRate = 24_000))
        assertThrows(IllegalArgumentException::class.java) { pcmDurationMs(1, 0) }
    }
}
