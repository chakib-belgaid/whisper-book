package com.whisperbook.app.engine.tts

import com.whisperbook.app.domain.model.CharacterGender
import com.whisperbook.app.domain.model.VocalAge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KittenVoiceMappingTest {
    @Test
    fun `friendly voices map to gender and age appropriate supertonic presets`() {
        val expected = listOf(
            Triple("bella", "Bella", 4), // F5: mature, calm
            Triple("jasper", "Jasper", 9), // M5: older, measured
            Triple("luna", "Luna", 1), // F2: young, lively
            Triple("bruno", "Bruno", 8), // M4: warm, mature
            Triple("rosie", "Rosie", 2), // F3: older, measured
            Triple("hugo", "Hugo", 6), // M2: grounded adult
            Triple("kiki", "Kiki", 3), // F4: youthful, energetic
            Triple("leo", "Leo", 7),
        )

        assertEquals(expected, SherpaKittenTtsEngine.KITTEN_VOICES.map {
            Triple(it.id, it.displayName, it.speakerIndex)
        })
        assertTrue(SherpaKittenTtsEngine.KITTEN_VOICES.all { it.embedded && it.localeTag == "en-US" })
        assertEquals(
            setOf("bella", "luna", "rosie", "kiki"),
            SherpaKittenTtsEngine.KITTEN_VOICES
                .filter { it.speakerIndex in 0..4 }
                .mapTo(linkedSetOf()) { it.id },
        )
        assertEquals(
            setOf("jasper", "bruno", "hugo", "leo"),
            SherpaKittenTtsEngine.KITTEN_VOICES
                .filter { it.speakerIndex in 5..9 }
                .mapTo(linkedSetOf()) { it.id },
        )
        assertEquals(
            setOf("bella", "luna", "rosie", "kiki"),
            SherpaKittenTtsEngine.KITTEN_VOICES
                .filter { it.gender == CharacterGender.FEMALE }
                .mapTo(linkedSetOf()) { it.id },
        )
        assertEquals(
            setOf("luna", "kiki", "leo"),
            SherpaKittenTtsEngine.KITTEN_VOICES
                .filter { it.vocalAge == VocalAge.YOUTHFUL }
                .mapTo(linkedSetOf()) { it.id },
        )
        assertEquals(
            "supertonic-3-int8-2026-05-11+sherpa-onnx-1.13.4",
            SherpaKittenTtsEngine.MODEL_VERSION,
        )
        assertEquals(44_100, SherpaKittenTtsEngine.EXPECTED_SAMPLE_RATE)
    }
}
