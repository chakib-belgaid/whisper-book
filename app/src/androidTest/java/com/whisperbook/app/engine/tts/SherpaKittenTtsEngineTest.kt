package com.whisperbook.app.engine.tts

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.whisperbook.app.domain.SynthesisRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SherpaKittenTtsEngineTest {
    @Test
    fun embeddedModelWarmsUpAndSynthesizesPcm() = runBlocking {
        val engine = SherpaKittenTtsEngine(ApplicationProvider.getApplicationContext())
        try {
            engine.warmUp().getOrThrow()
            val voices = engine.voices()
            assertEquals(8, voices.size)

            val audio = engine.synthesize(
                SynthesisRequest(
                    text = "Once upon a quiet moonlit evening.",
                    voice = voices.first(),
                    speed = 1f,
                    cacheKey = "instrumented-smoke-test",
                ),
            ).getOrThrow()

            assertEquals(SherpaKittenTtsEngine.EXPECTED_SAMPLE_RATE, audio.sampleRate)
            assertTrue(audio.pcm16.isNotEmpty())
            assertTrue(audio.durationMs > 0)
        } finally {
            engine.close()
        }
    }

    @Test
    fun brunoAndJasperUseReachableMasculinePresets() = runBlocking {
        val engine = SherpaKittenTtsEngine(ApplicationProvider.getApplicationContext())
        try {
            engine.warmUp().getOrThrow()
            val voices = engine.voices().associateBy { it.id }

            listOf("bruno", "jasper").forEach { voiceId ->
                val voice = requireNotNull(voices[voiceId])
                assertTrue("$voiceId should use an M1-M5 preset", voice.speakerIndex in 5..9)
                val audio = engine.synthesize(
                    SynthesisRequest(
                        text = "The lantern is ready.",
                        voice = voice,
                        speed = 1f,
                        cacheKey = "instrumented-$voiceId-voice-test",
                    ),
                ).getOrThrow()

                assertEquals(SherpaKittenTtsEngine.EXPECTED_SAMPLE_RATE, audio.sampleRate)
                assertTrue(audio.pcm16.isNotEmpty())
            }
        } finally {
            engine.close()
        }
    }
}
