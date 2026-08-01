package com.whisperbook.app.engine.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KittenVoiceMappingTest {
    @Test
    fun `kitten voices keep stable ids names and speaker indices`() {
        val expected = listOf(
            Triple("bella", "Bella", 0),
            Triple("jasper", "Jasper", 1),
            Triple("luna", "Luna", 2),
            Triple("bruno", "Bruno", 3),
            Triple("rosie", "Rosie", 4),
            Triple("hugo", "Hugo", 5),
            Triple("kiki", "Kiki", 6),
            Triple("leo", "Leo", 7),
        )

        assertEquals(expected, SherpaKittenTtsEngine.KITTEN_VOICES.map {
            Triple(it.id, it.displayName, it.speakerIndex)
        })
        assertTrue(SherpaKittenTtsEngine.KITTEN_VOICES.all { it.embedded && it.localeTag == "en-US" })
        assertEquals(
            "kitten-nano-en-v0_8-int8+sherpa-onnx-1.13.4",
            SherpaKittenTtsEngine.MODEL_VERSION,
        )
        assertEquals(24_000, SherpaKittenTtsEngine.EXPECTED_SAMPLE_RATE)
    }
}
