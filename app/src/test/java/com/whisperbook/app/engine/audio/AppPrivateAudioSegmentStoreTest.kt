package com.whisperbook.app.engine.audio

import com.whisperbook.app.domain.SynthesisRequest
import com.whisperbook.app.domain.SynthesisResult
import com.whisperbook.app.domain.model.CharacterVoiceAssignment
import com.whisperbook.app.domain.model.VoiceDescriptor
import java.io.File
import org.junit.Assert.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppPrivateAudioSegmentStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val voice = VoiceDescriptor("bella", "Bella", 0)

    @Test
    fun `retained generation survives restart and trim then restores its prior assignment`() = runTest {
        var now = 1_000L
        val root = File(temporaryFolder.root, "segments")
        val store = AppPrivateAudioSegmentStore(
            root = root,
            nowEpochMs = { now },
            generationId = { "generation-a" },
        )
        val first = request("First passage")
        val second = request("Second passage")
        val synthesis = SynthesisResult(shortArrayOf(1, 2, 3), 24_000, durationMs = 1L)
        val previousAssignment = CharacterVoiceAssignment("character-a", "bella", "test-v1", 1f)

        store.writeForPassage("passage-1", "character-a", first, synthesis)
        store.writeForPassage("passage-2", "character-b", second, synthesis)
        val originalPath = requireNotNull(store.find(first.cacheKey)).path
        assertNotNull(store.find(second.cacheKey))

        val retained = requireNotNull(
            store.retainForCharacter(
                characterId = "character-a",
                previousAssignment = previousAssignment,
            ),
        )
        assertEquals("generation-a", retained.id)
        assertEquals(previousAssignment, retained.previousAssignment)
        assertEquals(1, retained.segmentCount)
        assertEquals(originalPath, requireNotNull(store.find(first.cacheKey)).path)

        store.trimTo(0L)
        assertNull(store.find(second.cacheKey))
        assertEquals(originalPath, requireNotNull(store.find(first.cacheKey)).path)

        val restartedStore = AppPrivateAudioSegmentStore(
            root = root,
            nowEpochMs = { now },
            generationId = { "generation-b" },
        )
        val discovered = requireNotNull(restartedStore.latestRetainedVoiceChange("character-a"))
        assertEquals(previousAssignment, discovered.previousAssignment)
        val restored = requireNotNull(restartedStore.restoreRetainedGeneration(discovered.id))
        assertEquals(previousAssignment, restored.previousAssignment)
        assertTrue(restartedStore.retainedAudioGenerations("character-a").isEmpty())

        now += 2L * 24L * 60L * 60L * 1_000L
        assertEquals(0, restartedStore.cleanupExpiredRetainedAudio())
        assertEquals(originalPath, requireNotNull(restartedStore.find(first.cacheKey)).path)
    }

    @Test
    fun `expired retained generation is deleted once and cannot be restored`() = runTest {
        var now = 2_000L
        val store = AppPrivateAudioSegmentStore(
            root = File(temporaryFolder.root, "expiry-segments"),
            nowEpochMs = { now },
            generationId = { "expiring-generation" },
        )
        val request = request("An expiring passage")
        store.writeForPassage(
            "passage-expiring",
            "character-a",
            request,
            SynthesisResult(shortArrayOf(4, 5), 24_000, durationMs = 1L),
        )
        val retained = requireNotNull(
            store.retainForCharacter("character-a", gracePeriodMs = 100L),
        )

        now = retained.expiresAtEpochMs
        assertEquals(1, store.cleanupExpiredRetainedAudio())
        assertNull(store.find(request.cacheKey))
        assertNull(store.restoreRetainedGeneration(retained.id))
        assertTrue(store.retainedAudioGenerations().isEmpty())
        assertEquals(0, store.cleanupExpiredRetainedAudio())
    }

    @Test
    fun `passage scoped retention protects only selected chapter audio`() = runTest {
        val store = AppPrivateAudioSegmentStore(
            root = File(temporaryFolder.root, "scoped-segments"),
            nowEpochMs = { 3_000L },
            generationId = { "scoped-generation" },
        )
        val first = request("Earlier chapter")
        val second = request("Next chapter")
        val synthesis = SynthesisResult(shortArrayOf(6, 7), 24_000, durationMs = 1L)
        store.writeForPassage("passage-earlier", "character-a", first, synthesis)
        store.writeForPassage("passage-next", "character-a", second, synthesis)

        val retained = requireNotNull(
            store.retainForCharacter(
                characterId = "character-a",
                passageIds = setOf("passage-next"),
            ),
        )
        assertEquals(setOf("passage-next"), retained.passageIds)
        assertEquals(1, retained.segmentCount)

        store.trimTo(0L)
        assertNull(store.find(first.cacheKey))
        assertNotNull(store.find(second.cacheKey))
    }

    @Test
    fun `retention persists prior assignment even before any audio exists`() = runTest {
        val root = File(temporaryFolder.root, "assignment-only")
        val previousAssignment = CharacterVoiceAssignment("character-a", "bella", "test-v1", 0.9f)
        val store = AppPrivateAudioSegmentStore(
            root = root,
            nowEpochMs = { 4_000L },
            generationId = { "assignment-only-generation" },
        )

        val retained = requireNotNull(
            store.retainForCharacter("character-a", previousAssignment = previousAssignment),
        )
        assertEquals(0, retained.segmentCount)

        val restartedStore = AppPrivateAudioSegmentStore(root = root, nowEpochMs = { 4_001L })
        val restored = requireNotNull(restartedStore.restoreRetainedGeneration(retained.id))
        assertEquals(previousAssignment, restored.previousAssignment)
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
