package com.whisperbook.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PassageTextChunkerTest {
    @Test
    fun `prefers sentence boundaries and keeps every chunk bounded`() {
        val source = List(40) { index -> "Sentence $index ends cleanly." }.joinToString(" ")

        val chunks = PassageTextChunker.split(source, maxChars = 96)

        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 96 })
        assertTrue(chunks.dropLast(1).all { it.endsWith('.') })
        assertEquals(source, chunks.joinToString(" "))
    }

    @Test
    fun `hard splits text without whitespace without breaking surrogate pairs`() {
        val source = "A".repeat(63) + "🌙" + "B".repeat(70)

        val chunks = PassageTextChunker.split(source, maxChars = 64)

        assertTrue(chunks.all { it.length <= 64 })
        assertEquals(source, chunks.joinToString(separator = ""))
    }

    @Test
    fun `legacy passage chunks receive stable unique playback ids`() {
        val source = "A complete sentence. ".repeat(200)

        val first = PassageTextChunker.chunks("passage-7", source)
        val second = PassageTextChunker.chunks("passage-7", source)

        assertEquals(first, second)
        assertEquals(first.size, first.map { it.id }.distinct().size)
        assertTrue(first.all { it.id.startsWith("passage-7::chunk:") })
        assertTrue(first.all { it.text.length <= PassageTextChunker.MAX_CHARS })
    }

    @Test
    fun `narration chunks use short stable sentence bounded segments`() {
        val source = List(80) { index -> "Sentence $index ends cleanly." }.joinToString(" ")

        val first = NarrationTextChunker.chunks("passage-9", source)
        val second = NarrationTextChunker.chunks("passage-9", source)

        assertEquals(first, second)
        assertTrue(first.size > 1)
        assertTrue(first.all { it.text.length <= NarrationTextChunker.MAX_CHARS })
        assertTrue(first.dropLast(1).all { it.text.endsWith('.') })
        assertEquals(source, first.joinToString(" ") { it.text })
    }

    @Test
    fun `short narration passage keeps its source id for existing cache reuse`() {
        val chunks = NarrationTextChunker.chunks("passage-short", "A short opening line.")

        assertEquals(listOf(PassageTextChunk("passage-short", "A short opening line.")), chunks)
    }
}
