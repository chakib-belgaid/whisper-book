package com.whisperbook.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NarrationPhraseSplitterTest {
    @Test
    fun `short text is returned as a single trimmed phrase`() {
        assertEquals(listOf("Hello there."), NarrationPhraseSplitter.split("  Hello there.  ", 80))
        assertEquals(emptyList<String>(), NarrationPhraseSplitter.split("   ", 80))
    }

    @Test
    fun `every phrase is an exact slice of the source and bounded`() {
        val source = List(30) { index ->
            "Phrase $index, with a clause; and « une citation », puis… la suite !"
        }.joinToString(" ")

        val phrases = NarrationPhraseSplitter.split(source, 120)

        assertTrue(phrases.all { it.length <= 120 })
        var cursor = 0
        phrases.forEach { phrase ->
            val found = source.indexOf(phrase, cursor)
            assertTrue("phrase must be a slice of the source: $phrase", found >= cursor)
            assertTrue(source.substring(cursor, found).isBlank())
            cursor = found + phrase.length
        }
        assertTrue(source.substring(cursor).isBlank())
    }

    @Test
    fun `english honorifics and initials do not end a sentence`() {
        val source = "Mr. Smith met Dr. Jones and J. K. Rowling at St. Mary's church yesterday. " +
            "They talked for hours about books and the weather."

        val phrases = NarrationPhraseSplitter.split(source, 90)

        assertEquals(
            listOf(
                "Mr. Smith met Dr. Jones and J. K. Rowling at St. Mary's church yesterday.",
                "They talked for hours about books and the weather.",
            ),
            phrases,
        )
    }

    @Test
    fun `french honorifics do not end a sentence`() {
        val source = "M. Dupont salua Mme Martin et Mlle. Durand devant la mairie du village. " +
            "Puis ils partirent ensemble vers la gare."

        val phrases = NarrationPhraseSplitter.split(source, 80)

        assertEquals(
            listOf(
                "M. Dupont salua Mme Martin et Mlle. Durand devant la mairie du village.",
                "Puis ils partirent ensemble vers la gare.",
            ),
            phrases,
        )
    }

    @Test
    fun `pronoun I still ends a sentence`() {
        val source = "Nobody else was there, so the one who opened the door was I. " +
            "The house was cold and silent."

        val phrases = NarrationPhraseSplitter.split(source, 70)

        assertEquals("Nobody else was there, so the one who opened the door was I.", phrases.first())
    }

    @Test
    fun `long sentence is cut at clause punctuation instead of a random space`() {
        val source = "The rain had been falling since dawn over the grey roofs of the old town, " +
            "and nobody in the narrow streets seemed to notice the stranger walking alone."

        val phrases = NarrationPhraseSplitter.split(source, 100)

        assertEquals(
            "The rain had been falling since dawn over the grey roofs of the old town,",
            phrases.first(),
        )
    }

    @Test
    fun `french guillemets and spaced punctuation stay attached`() {
        val source = "« Tu viens avec nous ce soir ? » demanda-t-elle doucement à son frère. " +
            "« Non ! » répondit-il sans même lever les yeux de son livre."

        val phrases = NarrationPhraseSplitter.split(source, 80)

        assertEquals(
            listOf(
                "« Tu viens avec nous ce soir ? » demanda-t-elle doucement à son frère.",
                "« Non ! » répondit-il sans même lever les yeux de son livre.",
            ),
            phrases,
        )
        assertFalse(phrases.any { it.startsWith("?") || it.startsWith("!") || it.startsWith("»") })
    }

    @Test
    fun `closing punctuation inside quotes ends the sentence after the quote`() {
        val source = "“We should leave before the storm reaches the valley.” " +
            "She nodded and picked up the lantern from the table."

        val phrases = NarrationPhraseSplitter.split(source, 70)

        assertEquals("“We should leave before the storm reaches the valley.”", phrases.first())
    }

    @Test
    fun `decimal numbers are never split`() {
        val source = "The temperature dropped to 3.5 degrees during the night, " +
            "which was the coldest reading of the entire winter season so far."

        val phrases = NarrationPhraseSplitter.split(source, 70)

        assertTrue(phrases.none { it.endsWith("3.") })
    }

    @Test
    fun `text without whitespace is hard cut without breaking surrogate pairs`() {
        val source = "A".repeat(63) + "🌙" + "B".repeat(70)

        val phrases = NarrationPhraseSplitter.split(source, 64)

        assertTrue(phrases.all { it.length <= 64 })
        assertEquals(source, phrases.joinToString(separator = ""))
    }

    @Test
    fun `narration chunk ids keep the legacy format`() {
        val text = List(20) { index -> "Sentence $index ends cleanly." }.joinToString(" ")

        val chunks = NarrationTextChunker.chunks("passage-3", text, maxChars = 80)

        assertTrue(chunks.size > 1)
        assertEquals((1..chunks.size).map { "passage-3::chunk:$it" }, chunks.map { it.id })
        assertEquals(text, chunks.joinToString(" ") { it.text })
    }
}
