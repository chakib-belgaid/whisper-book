package com.whisperbook.app.engine.audio

import com.whisperbook.app.domain.LocalTtsEngine
import com.whisperbook.app.domain.SynthesisRequest
import com.whisperbook.app.domain.SynthesisResult
import com.whisperbook.app.domain.model.VoiceDescriptor
import com.whisperbook.app.engine.tts.SherpaKittenTtsEngine
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VoicePreviewBootstrapTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `first launch generates every embedded voice and relaunch reuses durable clips`() = runTest {
        val root = File(temporaryFolder.root, "previews")
        val cache = previewCache(root)
        val firstEngine = RecordingPreviewEngine()

        bootstrap(firstEngine, cache).generateMissing()

        assertTrue(firstEngine.warmed)
        assertEquals(
            SherpaKittenTtsEngine.KITTEN_VOICES.map(VoiceDescriptor::id),
            firstEngine.requests.map { it.voice.id },
        )
        SherpaKittenTtsEngine.KITTEN_VOICES.forEach { voice ->
            val clip = cache.read(voice, 1f)
            assertNotNull(clip)
            assertArrayEquals(shortArrayOf(voice.speakerIndex.toShort(), 7), clip?.pcm16)
        }

        val relaunchedEngine = RecordingPreviewEngine()
        bootstrap(relaunchedEngine, previewCache(root)).generateMissing()

        assertFalse(relaunchedEngine.warmed)
        assertTrue(relaunchedEngine.requests.isEmpty())
    }

    @Test
    fun `failed voice does not discard ready previews and retry only generates the missing clip`() = runTest {
        val cache = previewCache(File(temporaryFolder.root, "partial"))
        val failedVoice = SherpaKittenTtsEngine.KITTEN_VOICES[2]
        val firstEngine = RecordingPreviewEngine(failingVoiceId = failedVoice.id)

        val failure = runCatching { bootstrap(firstEngine, cache).generateMissing() }.exceptionOrNull()

        assertNotNull(failure)
        SherpaKittenTtsEngine.KITTEN_VOICES.forEach { voice ->
            if (voice == failedVoice) assertNull(cache.read(voice, 1f))
            else assertNotNull(cache.read(voice, 1f))
        }

        val retryEngine = RecordingPreviewEngine()
        bootstrap(retryEngine, cache).generateMissing()

        assertEquals(listOf(failedVoice.id), retryEngine.requests.map { it.voice.id })
        assertNotNull(cache.read(failedVoice, 1f))
    }

    @Test
    fun `model version and expected sample rate invalidate an older preview`() = runTest {
        val root = File(temporaryFolder.root, "versioned")
        val voice = SherpaKittenTtsEngine.KITTEN_VOICES.first()
        val original = AppPrivateVoicePreviewCache(root, "model-v1", 44_100)
        original.write(voice, 1f, VoicePreviewClip(shortArrayOf(3, 2, 1), 44_100))

        assertNotNull(AppPrivateVoicePreviewCache(root, "model-v1", 44_100).read(voice, 1f))
        assertNull(AppPrivateVoicePreviewCache(root, "model-v2", 44_100).read(voice, 1f))
        assertNull(AppPrivateVoicePreviewCache(root, "model-v1", 24_000).read(voice, 1f))
    }

    private fun previewCache(root: File) = AppPrivateVoicePreviewCache(
        root = root,
        modelVersion = "test-model-v1",
        expectedSampleRate = SAMPLE_RATE,
    )

    private fun bootstrap(
        engine: LocalTtsEngine,
        cache: VoicePreviewClipCache,
    ) = VoicePreviewBootstrapper(
        ttsEngine = engine,
        voices = SherpaKittenTtsEngine.KITTEN_VOICES,
        cache = cache,
        expectedSampleRate = SAMPLE_RATE,
    )

    private companion object {
        const val SAMPLE_RATE = 44_100
    }
}

private class RecordingPreviewEngine(
    private val failingVoiceId: String? = null,
) : LocalTtsEngine {
    var warmed = false
    val requests = mutableListOf<SynthesisRequest>()

    override suspend fun warmUp(): Result<Unit> {
        warmed = true
        return Result.success(Unit)
    }

    override suspend fun voices(): List<VoiceDescriptor> = SherpaKittenTtsEngine.KITTEN_VOICES

    override suspend fun synthesize(request: SynthesisRequest): Result<SynthesisResult> {
        requests += request
        if (request.voice.id == failingVoiceId) return Result.failure(IllegalStateException("test failure"))
        return Result.success(
            SynthesisResult(
                pcm16 = shortArrayOf(request.voice.speakerIndex.toShort(), 7),
                sampleRate = 44_100,
                durationMs = 1L,
            ),
        )
    }

    override fun close() = Unit
}
