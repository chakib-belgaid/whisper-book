package com.whisperbook.app.engine.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfPageStructureCleanerTest {
    @Test
    fun `removes recurring running headers footers and changing page numbers`() {
        val cleaned = PdfPageStructureCleaner.clean(
            listOf(
                """
                    THE MOONLIT WOOD
                    CHAPTER ONE
                    Elara entered the forest.
                    Woodland Classics
                    17
                """.trimIndent(),
                """
                    THE MOONLIT WOOD
                    The path narrowed beneath the trees.
                    A fox watched from the ferns.
                    Woodland Classics
                    18
                """.trimIndent(),
                """
                    THE MOONLIT WOOD
                    She followed the lantern glow.
                    The old bridge appeared ahead.
                    Woodland Classics
                    19
                """.trimIndent(),
            ),
        )

        assertFalse(cleaned.contains("THE MOONLIT WOOD"))
        assertFalse(cleaned.contains("Woodland Classics"))
        assertFalse(cleaned.lineSequence().any { it in setOf("17", "18", "19") })
        assertTrue(cleaned.contains("CHAPTER ONE"))
        assertTrue(cleaned.contains("Elara entered the forest."))
        assertTrue(cleaned.contains("The old bridge appeared ahead."))
    }

    @Test
    fun `supports alternating running headers while preserving unique chapter headings`() {
        val cleaned = PdfPageStructureCleaner.clean(
            listOf(
                "Book title\nCHAPTER I\nThe first page begins.\n1",
                "Chapter one\nThe story continues.\nAnother paragraph.\n2",
                "Book title\nA storm crossed the valley.\nThe fire went out.\n3",
                "Chapter one\nMorning came quietly.\nCHAPTER II\n4",
            ),
        )

        assertFalse(cleaned.contains("Book title"))
        assertFalse(cleaned.contains("Chapter one"))
        assertTrue(cleaned.contains("CHAPTER I"))
        assertTrue(cleaned.contains("CHAPTER II"))
        assertTrue(cleaned.contains("The story continues."))
    }

    @Test
    fun `does not infer running furniture from a single page`() {
        val cleaned = PdfPageStructureCleaner.clean(
            listOf("CHAPTER I\nThe forest woke beneath the stars.\nPage 1"),
        )

        assertEquals("CHAPTER I\nThe forest woke beneath the stars.", cleaned)
    }

    @Test
    fun `recurring-line heuristics never erase a sparse page`() {
        val repeatedPage = "CHAPTER ONE\nElara crossed the bridge.\nThe forest answered.\nA fox waited."

        val cleaned = PdfPageStructureCleaner.clean(List(8) { repeatedPage })

        assertEquals(List(8) { repeatedPage }.joinToString("\n"), cleaned)
    }

    @Test
    fun `blank pages do not hide recurring furniture on readable pages`() {
        val cleaned = PdfPageStructureCleaner.clean(
            listOf(
                "Running title\nFirst body paragraph.\n1",
                "",
                "",
                "Running title\nSecond body paragraph.\n4",
            ),
        )

        assertFalse(cleaned.contains("Running title"))
        assertEquals("First body paragraph.\nSecond body paragraph.", cleaned)
    }
}
