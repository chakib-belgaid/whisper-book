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
    fun `first person prose is not treated as a Roman numeral heading`() {
        val paragraphs = listOf(
            "I slept.",
            "The rain kept tapping against the window until dawn.",
        )

        val chapters = ChapterDetector().detect(paragraphs)

        assertEquals(1, chapters.size)
        assertEquals(ChapterDetectionRule.FALLBACK, chapters.single().rule)
        assertEquals(paragraphs, chapters.single().paragraphs)
    }

    @Test
    fun `numbered prose and instructions do not create chapter boundaries`() {
        val paragraphs = listOf(
            "1. Open the settings menu.",
            "2) Select a voice.",
            "3. Continue reading the story",
        )

        val chapters = ChapterDetector().detect(paragraphs)

        assertEquals(1, chapters.size)
        assertEquals(ChapterDetectionRule.FALLBACK, chapters.single().rule)
        assertEquals(paragraphs, chapters.single().paragraphs)
    }

    @Test
    fun `isolated numbered sentence is not treated as a chapter`() {
        val paragraphs = listOf(
            "1. Open the settings menu.",
            "This instruction explains how to choose the narration voice.",
        )

        val chapters = ChapterDetector().detect(paragraphs)

        assertEquals(1, chapters.size)
        assertEquals(ChapterDetectionRule.FALLBACK, chapters.single().rule)
        assertEquals(paragraphs, chapters.single().paragraphs)
    }

    @Test
    fun `bare page numbers do not create chapter boundaries`() {
        val paragraphs = listOf(
            "12",
            "The first page of prose continued without a chapter break.",
            "13",
            "The next page continued the same scene.",
        )

        val chapters = ChapterDetector().detect(paragraphs)

        assertEquals(1, chapters.size)
        assertEquals(ChapterDetectionRule.FALLBACK, chapters.single().rule)
        assertEquals(paragraphs, chapters.single().paragraphs)
    }

    @Test
    fun `preserves explicit numbered and structural heading forms`() {
        val headings = listOf(
            "CHAPTER I",
            "IV",
            "IV. The Key",
            "IV The Key",
            "2. The Door",
            "1. What happened?",
            "1. The beginning of the journey",
            "1. Le début du voyage",
            "1. بداية الرحلة",
            "١. بداية الرحلة",
            "1 The Beginning",
            "THE SECOND DOOR",
        )

        assertTrue(headings.all(ChapterDetector::looksLikeHeading))
    }

    @Test
    fun `unpunctuated numbered titles create real boundaries without matching first person prose`() {
        val chapters = ChapterDetector().detect(
            listOf(
                "1 The Beginning",
                "The road opened beyond the gate.",
                "IV The Key",
                "A key lay beneath the leaves.",
            ),
        )

        assertEquals(listOf("1 The Beginning", "IV The Key"), chapters.map { it.title })
        assertTrue(chapters.all { it.rule == ChapterDetectionRule.HEADING })
    }

    @Test
    fun `Arabic Indic and Persian numbered titles create boundaries`() {
        val chapters = ChapterDetector().detect(
            listOf(
                "١. بداية الرحلة",
                "انفتح الطريق خلف البوابة.",
                "۲. ادامه سفر",
                "کلید زیر برگ‌ها پنهان بود.",
            ),
        )

        assertEquals(listOf("١. بداية الرحلة", "۲. ادامه سفر"), chapters.map { it.title })
        assertTrue(chapters.all { it.rule == ChapterDetectionRule.HEADING })
    }

    @Test
    fun `adjacent Roman numbered instructions stay prose`() {
        val paragraphs = listOf(
            "I. Open Settings",
            "II) Select Voice",
            "III. Continue Reading",
        )

        val chapters = ChapterDetector().detect(paragraphs)

        assertEquals(1, chapters.size)
        assertEquals(ChapterDetectionRule.FALLBACK, chapters.single().rule)
        assertEquals(paragraphs, chapters.single().paragraphs)
    }

    @Test
    fun `consecutive headings do not create an empty chapter`() {
        val chapters = ChapterDetector().detect(
            listOf(
                "CHAPTER I",
                "THE KEY",
                "A key lay beneath the leaves.",
            ),
        )

        assertEquals(1, chapters.size)
        assertEquals("THE KEY", chapters.single().title)
        assertEquals(listOf("A key lay beneath the leaves."), chapters.single().paragraphs)
        assertTrue(chapters.all { it.paragraphs.isNotEmpty() })
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
