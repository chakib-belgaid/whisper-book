package com.whisperbook.app.engine.audio

import com.whisperbook.app.domain.SynthesisRequest
import com.whisperbook.app.domain.model.VoiceDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioCacheKeyTest {
    private val voice = VoiceDescriptor(
        id = "bella",
        displayName = "Bella",
        speakerIndex = 0,
    )

    @Test
    fun `same synthesis inputs produce the same lowercase SHA-256 key`() {
        val request = SynthesisRequest(
            text = "Once upon a time…",
            voice = voice,
            speed = 0.9f,
            cacheKey = "unused",
        )

        val first = AudioCacheKey.fromRequest(request, modelVersion = "kitten-0.8-int8", sampleRate = 24_000)
        val second = AudioCacheKey.fromRequest(request.copy(), modelVersion = "kitten-0.8-int8", sampleRate = 24_000)

        assertEquals(first, second)
        assertEquals(
            "18b5416e4242dba1b31fd662fd20b7f5982bc99dbb4d201fabb5295e47477428",
            first,
        )
        assertTrue(AudioCacheKey.isValid(first))
        assertEquals(64, first.length)
    }

    @Test
    fun `every waveform-affecting input participates in the cache key`() {
        fun key(
            text: String = "hello",
            voiceId: String = "bella",
            speakerIndex: Int = 0,
            modelVersion: String = "v1",
            speed: Float = 1f,
            sampleRate: Int = 24_000,
            languageCode: String = "en",
        ) = AudioCacheKey.create(text, voiceId, speakerIndex, modelVersion, speed, sampleRate, languageCode)

        val baseline = key()
        assertNotEquals(baseline, key(text = "hello!"))
        assertNotEquals(baseline, key(voiceId = "jasper"))
        assertNotEquals(baseline, key(speakerIndex = 1))
        assertNotEquals(baseline, key(modelVersion = "v2"))
        assertNotEquals(baseline, key(speed = 1.1f))
        assertNotEquals(baseline, key(sampleRate = 16_000))
        assertNotEquals(baseline, key(languageCode = "fr"))
    }

    @Test
    fun `passage scoped keys keep repeated prose in separate persistence rows`() {
        val request = SynthesisRequest("The end.", voice, 1f, cacheKey = "unused")

        val first = AudioCacheKey.forPassage("passage-1", request, "v1", 24_000)
        val second = AudioCacheKey.forPassage("passage-2", request, "v1", 24_000)

        assertNotEquals(first, second)
        assertTrue(AudioCacheKey.isValid(first))
        assertTrue(AudioCacheKey.isValid(second))
    }
}
