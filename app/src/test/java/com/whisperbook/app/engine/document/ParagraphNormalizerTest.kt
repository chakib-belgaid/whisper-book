package com.whisperbook.app.engine.document

import org.junit.Assert.assertEquals
import org.junit.Test

class ParagraphNormalizerTest {
    @Test
    fun `joins pdf soft wraps and removes soft hyphenation`() {
        val text = """
            The moon was shin-
            ing over the wood.

            “Wait,” said Elara.
        """.trimIndent()

        assertEquals(
            listOf("The moon was shining over the wood.", "“Wait,” said Elara."),
            ParagraphNormalizer.normalize(text),
        )
    }

    @Test
    fun `preserves a heading boundary without requiring a blank line`() {
        val text = "CHAPTER I\nThe forest woke slowly under the stars."

        assertEquals(
            listOf("CHAPTER I", "The forest woke slowly under the stars."),
            ParagraphNormalizer.normalize(text),
        )
    }
}
