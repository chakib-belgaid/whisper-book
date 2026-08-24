package com.whisperbook.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class CurrentChapterReaderProgressTest {
    @Test
    fun `reader passage number follows grouped playback ids`() {
        val passages = listOf(
            passage("first", "First").copy(playbackPassageIds = listOf("first-a", "first-b")),
            passage("second", "Second").copy(playbackPassageIds = listOf("second-a")),
        )

        assertEquals(1, activeReaderPassageNumber(passages, "first-b"))
        assertEquals(2, activeReaderPassageNumber(passages, "second-a"))
        assertEquals(1, activeReaderPassageNumber(passages, "missing"))
        assertEquals(0, activeReaderPassageNumber(emptyList(), "missing"))
    }

    @Test
    fun `active segment progress is cumulative across a grouped reader passage`() {
        val passages = listOf(
            passage("first", "12345"),
            passage("second", "1234567890"),
            passage("third", "12345"),
        )
        val groupedIds = passages.map(PassageUi::id)

        assertEquals(
            0.5f,
            groupedReaderProgress(
                groupedIds,
                playbackPassageTextWeights(passages),
                "second",
                activePassageProgress = 0.5f,
            ),
            0.0001f,
        )
        assertEquals(
            0.75f,
            groupedReaderProgress(
                groupedIds,
                playbackPassageTextWeights(passages),
                "third",
                activePassageProgress = 0f,
            ),
            0.0001f,
        )
    }

    @Test
    fun `segment weighting uses unicode code points rather than utf16 units`() {
        val passages = listOf(
            passage("emoji", "📖"),
            passage("letters", "abc"),
        )

        assertEquals(
            0.25f,
            groupedReaderProgress(
                playbackPassageIds = listOf("emoji", "letters"),
                playbackPassageWeights = playbackPassageTextWeights(passages),
                activePassageId = "letters",
                activePassageProgress = 0f,
            ),
            0.0001f,
        )
    }

    @Test
    fun `local progress is clamped before being mapped into the group`() {
        val passages = listOf(passage("first", "abcd"), passage("second", "abcd"))
        val groupedIds = passages.map(PassageUi::id)
        val weights = playbackPassageTextWeights(passages)

        assertEquals(0f, groupedReaderProgress(groupedIds, weights, "first", Float.NaN), 0f)
        assertEquals(0f, groupedReaderProgress(groupedIds, weights, "first", -1f), 0f)
        assertEquals(0.5f, groupedReaderProgress(groupedIds, weights, "first", 2f), 0.0001f)
    }

    @Test
    fun `empty or unavailable segment text retains deterministic progress`() {
        val passages = listOf(passage("first", ""), passage("third", ""))
        val groupedIds = listOf("first", "missing", "third")
        val weights = playbackPassageTextWeights(passages)

        assertEquals(
            0.5f,
            groupedReaderProgress(groupedIds, weights, "missing", activePassageProgress = 0.5f),
            0.0001f,
        )
        assertEquals(0f, groupedReaderProgress(groupedIds, weights, "unknown", 0.5f), 0f)
        assertEquals(0f, groupedReaderProgress(emptyList(), weights, "first", 0.5f), 0f)
    }

    private fun passage(id: String, text: String) = PassageUi(
        id = id,
        speaker = SpeakerRole.Narrator,
        text = text,
    )
}
