package com.whisperbook.app.engine.attribution

import com.whisperbook.app.domain.ExtractedChapter
import com.whisperbook.app.domain.ExtractedPublication
import com.whisperbook.app.domain.model.BuiltInCharacters
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeuristicSpeakerAttributorTest {
    @Test
    fun `attributes explicit tags aliases and conservative two speaker alternation`() = runTest {
        val publication = ExtractedPublication(
            title = "The Wood",
            author = null,
            chapters = listOf(
                ExtractedChapter(
                    "The Moonlit Wood",
                    listOf(
                        "\"Stay close,\" said Elara.",
                        "The fox lifted his lantern.",
                        "\"I will,\" the Fox replied.",
                        "\"Can you hear it?\"",
                    ),
                ),
            ),
        )

        val result = HeuristicSpeakerAttributor().attribute("book", publication)
        val dialogue = result.chapters.single().passages.filter { it.text in setOf("Stay close,", "I will,", "Can you hear it?") }
        val elara = result.characters.single { it.displayName == "Elara" }
        val fox = result.characters.single { it.displayName == "Fox" }

        assertEquals(listOf(elara.id, fox.id, elara.id), dialogue.map { it.speakerId })
        assertTrue(dialogue[0].attributionRule.startsWith("explicit-after-dialogue"))
        assertTrue(dialogue[2].attributionRule.startsWith("two-speaker-carry-over"))
        assertEquals(2, elara.dialogueLineCount)
        assertEquals(1, fox.dialogueLineCount)
    }

    @Test
    fun `known aliases resolve to the seeded character`() = runTest {
        val result = HeuristicSpeakerAttributor(
            seeds = listOf(KnownCharacterSeed("Elara", setOf("Ellie"))),
        ).attribute(
            "book",
            ExtractedPublication("Story", null, listOf(ExtractedChapter("One", listOf("“Run,” whispered Ellie.")))),
        )

        val elara = result.characters.single { it.displayName == "Elara" }
        assertEquals(elara.id, result.chapters.single().passages.first().speakerId)
    }

    @Test
    fun `unattributed dialogue falls back to narrator with evidence`() = runTest {
        val result = HeuristicSpeakerAttributor().attribute(
            "book",
            ExtractedPublication("Story", null, listOf(ExtractedChapter("One", listOf("The night was still.", "«Who is there?»")))),
        )
        val passages = result.chapters.single().passages

        assertEquals(BuiltInCharacters.NARRATOR_ID, passages[0].speakerId)
        assertEquals(1f, passages[0].confidence)
        assertEquals(BuiltInCharacters.NARRATOR_ID, passages[1].speakerId)
        assertEquals(0.30f, passages[1].confidence)
        assertTrue(passages[1].attributionRule.startsWith("narrator-fallback"))
    }

    @Test
    fun `recognizes explicit em dash speech tags`() = runTest {
        val result = HeuristicSpeakerAttributor().attribute(
            "book",
            ExtractedPublication("Story", null, listOf(ExtractedChapter("One", listOf("— Come quickly, called Rowan.")))),
        )
        val rowan = result.characters.single { it.displayName == "Rowan" }
        val dialogue = result.chapters.single().passages.single()

        assertEquals(rowan.id, dialogue.speakerId)
        assertTrue(dialogue.attributionRule.startsWith("explicit-em-dash-tag"))
    }
}
