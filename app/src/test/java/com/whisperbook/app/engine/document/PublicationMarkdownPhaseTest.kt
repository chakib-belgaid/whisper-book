package com.whisperbook.app.engine.document

import org.junit.Assert.assertEquals
import org.junit.Test

class PublicationMarkdownPhaseTest {
    @Test
    fun `creates canonical Markdown with clean chapters and paragraphs`() {
        val publication = PublicationMarkdownPhase.transform(
            title = "  Mémoire du rivage  ",
            author = "  A. Auteur  ",
            chapters = listOf(
                DetectedChapter(
                    title = " Chapitre 1 ",
                    paragraphs = listOf(
                        "Il parle de son passé « algérien »",
                        "Algérien de naissance, il traverse la Méditerranée.",
                        "Une nouvelle journée commence.",
                    ),
                    rule = ChapterDetectionRule.REGEX,
                ),
            ),
        )

        assertEquals("Mémoire du rivage", publication.title)
        assertEquals("A. Auteur", publication.author)
        assertEquals(
            listOf(
                "Il parle de son passé « algérien » Algérien de naissance, il traverse la Méditerranée.",
                "Une nouvelle journée commence.",
            ),
            publication.chapters.single().paragraphs,
        )
        assertEquals(
            """
                # Mémoire du rivage

                ## Chapitre 1

                Il parle de son passé « algérien » Algérien de naissance, il traverse la Méditerranée.

                Une nouvelle journée commence.
            """.trimIndent() + "\n",
            publication.markdown,
        )
    }

    @Test
    fun `keeps completed quotations and dialogue as separate paragraphs`() {
        val publication = PublicationMarkdownPhase.transform(
            title = "Dialogue",
            author = null,
            chapters = listOf(
                DetectedChapter(
                    title = "Scène",
                    paragraphs = listOf(
                        "Elle demanda : « Qui est là ? »",
                        "La porte s'ouvrit.",
                        "— Entre, dit-elle.",
                    ),
                    rule = ChapterDetectionRule.HEADING,
                ),
            ),
        )

        assertEquals(
            listOf(
                "Elle demanda : « Qui est là ? »",
                "La porte s'ouvrit.",
                "— Entre, dit-elle.",
            ),
            publication.chapters.single().paragraphs,
        )
    }
}
