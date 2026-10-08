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

    @Test
    fun `a French closing guillemet without sentence punctuation remains a soft wrap`() {
        val text = "Il parle longuement de son passé « algérien »\nAlgérien de naissance, il y retourne souvent."

        assertEquals(
            listOf("Il parle longuement de son passé « algérien » Algérien de naissance, il y retourne souvent."),
            ParagraphNormalizer.normalize(text),
        )
    }

    @Test
    fun `sentence punctuation inside French guillemets keeps a real boundary`() {
        val text = "Elle demanda soudain : « Qui est là ? »\nLa porte s'ouvrit lentement."

        assertEquals(
            listOf("Elle demanda soudain : « Qui est là ? »", "La porte s'ouvrit lentement."),
            ParagraphNormalizer.normalize(text),
        )
    }
}
