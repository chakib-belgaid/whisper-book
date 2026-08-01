package com.whisperbook.app.engine.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterDetectorTest {
    @Test
    fun `toc title wins over a source heading`() {
        val chapters = ChapterDetector().detectSections(
            listOf(
                DocumentSection(
                    title = "CHAPTER I",
                    tocTitle = "The Moonlit Wood",
                    paragraphs = listOf("CHAPTER I", "The forest woke."),
                    sourceReference = "text/chapter1.xhtml",
                ),
            ),
        )

        assertEquals(1, chapters.size)
        assertEquals("The Moonlit Wood", chapters.single().title)
        assertEquals(listOf("The forest woke."), chapters.single().paragraphs)
        assertEquals(ChapterDetectionRule.TOC, chapters.single().rule)
    }

    @Test
    fun `detects common chapter labels and structural headings`() {
        val chapters = ChapterDetector().detect(
            listOf(
                "CHAPTER I: THE KEY",
                "A key lay beneath the leaves.",
                "THE SECOND DOOR",
                "It opened before dawn.",
            ),
        )

        assertEquals(listOf("CHAPTER I: THE KEY", "THE SECOND DOOR"), chapters.map { it.title })
        assertEquals(listOf(ChapterDetectionRule.REGEX, ChapterDetectionRule.HEADING), chapters.map { it.rule })
        assertEquals(listOf("A key lay beneath the leaves."), chapters.first().paragraphs)
    }

    @Test
    fun `falls back to deterministic word bounded chapters`() {
        val chapters = ChapterDetector(fallbackMaxWords = 6).detect(
            listOf("one two three", "four five six", "seven eight nine"),
        )

        assertEquals(2, chapters.size)
        assertEquals("Chapter 1", chapters[0].title)
        assertEquals(2, chapters[0].paragraphs.size)
        assertTrue(chapters.all { it.rule == ChapterDetectionRule.FALLBACK })
    }
}
