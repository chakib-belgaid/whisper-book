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
}
