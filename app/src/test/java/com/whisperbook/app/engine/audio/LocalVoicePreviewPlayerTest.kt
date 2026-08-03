package com.whisperbook.app.engine.audio

import com.whisperbook.app.domain.LocalTtsEngine
import com.whisperbook.app.domain.SynthesisRequest
import com.whisperbook.app.domain.SynthesisResult
import com.whisperbook.app.domain.model.VoiceDescriptor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalVoicePreviewPlayerTest {
    @Test
    fun previewSynthesizesSelectedVoiceAndPlaysPcm() = runTest {
        val engine = FakePreviewTtsEngine()
        val sink = RecordingPcmAudioSink()
        val player = LocalVoicePreviewPlayer(engine, sink)
        val voice = VoiceDescriptor("luna", "Luna", 2)

        player.play("A short sample.", voice, 1.2f).getOrThrow()

        assertTrue(engine.warmed)
        assertEquals("A short sample.", engine.request?.text)
        assertEquals(voice, engine.request?.voice)
        assertEquals(1.2f, engine.request?.speed)
        assertArrayEquals(shortArrayOf(10, -10, 20, -20), sink.pcm16)
        assertEquals(24_000, sink.sampleRate)
    }

    @Test
    fun previewUsesInstalledClipWithoutStartingTts() = runTest {
        val engine = FakePreviewTtsEngine()
        val sink = RecordingPcmAudioSink()
        val voice = VoiceDescriptor("luna", "Luna", 2)
        val cache = FakeVoicePreviewClipCache(
            VoicePreviewClip(shortArrayOf(90, -90), sampleRate = 44_100),
        )
        val player = LocalVoicePreviewPlayer(engine, sink, cache)

        player.play("Character-specific text is not synthesized on a cache hit.", voice, 1f)
            .getOrThrow()

        assertFalse(engine.warmed)
        assertNull(engine.request)
        assertEquals(voice, cache.requestedVoice)
        assertEquals(1f, cache.requestedSpeed)
        assertArrayEquals(shortArrayOf(90, -90), sink.pcm16)
        assertEquals(44_100, sink.sampleRate)
    }
}

private class FakePreviewTtsEngine : LocalTtsEngine {
    var warmed = false
    var request: SynthesisRequest? = null

    override suspend fun warmUp(): Result<Unit> {
        warmed = true
        return Result.success(Unit)
    }

    override suspend fun voices(): List<VoiceDescriptor> = emptyList()

    override suspend fun synthesize(request: SynthesisRequest): Result<SynthesisResult> {
        this.request = request
        return Result.success(
            SynthesisResult(
                pcm16 = shortArrayOf(10, -10, 20, -20),
                sampleRate = 24_000,
                durationMs = 1L,
            ),
        )
    }

    override fun close() = Unit
}

private class RecordingPcmAudioSink : PcmAudioSink {
    var pcm16 = shortArrayOf()
    var sampleRate = 0

    override suspend fun play(pcm16: ShortArray, sampleRate: Int) {
        this.pcm16 = pcm16
        this.sampleRate = sampleRate
    }

    override fun stop() = Unit
}

private class FakeVoicePreviewClipCache(
    private val clip: VoicePreviewClip?,
) : VoicePreviewClipCache {
    var requestedVoice: VoiceDescriptor? = null
    var requestedSpeed: Float? = null

    override suspend fun read(voice: VoiceDescriptor, speed: Float): VoicePreviewClip? {
        requestedVoice = voice
        requestedSpeed = speed
        return clip
    }

    override suspend fun write(voice: VoiceDescriptor, speed: Float, clip: VoicePreviewClip) = Unit
}
