package com.whisperbook.app.engine.audio

import com.whisperbook.app.domain.SynthesisRequest
import com.whisperbook.app.domain.SynthesisResult
import com.whisperbook.app.domain.model.VoiceDescriptor
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppPrivateAudioSegmentStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val voice = VoiceDescriptor("bella", "Bella", 0)

    @Test
    fun `character invalidation is selective and zero limit trim clears remaining entries`() = runTest {
        var now = 1_000L
        val store = AppPrivateAudioSegmentStore(
            root = File(temporaryFolder.root, "segments"),
            nowEpochMs = { now++ },
        )
        val first = request("First passage")
        val second = request("Second passage")
        val synthesis = SynthesisResult(shortArrayOf(1, 2, 3), 24_000, durationMs = 1L)

        store.writeForPassage("passage-1", "character-a", first, synthesis)
        store.writeForPassage("passage-2", "character-b", second, synthesis)
        assertNotNull(store.find(first.cacheKey))
        assertNotNull(store.find(second.cacheKey))

        store.invalidateForCharacter("character-a")
        assertNull(store.find(first.cacheKey))
        assertNotNull(store.find(second.cacheKey))

        store.trimTo(0L)
        assertNull(store.find(second.cacheKey))
    }

    private fun request(text: String) = SynthesisRequest(
        text = text,
        voice = voice,
        speed = 1f,
        cacheKey = AudioCacheKey.create(
            text = text,
            voiceId = voice.id,
            speakerIndex = voice.speakerIndex,
            modelVersion = "test-v1",
            speed = 1f,
            sampleRate = 24_000,
        ),
    )
}
