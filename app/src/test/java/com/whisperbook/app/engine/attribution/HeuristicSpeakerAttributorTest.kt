package com.whisperbook.app.engine.attribution

import com.whisperbook.app.domain.ExtractedChapter
import com.whisperbook.app.domain.ExtractedPublication
import com.whisperbook.app.domain.PassageTextChunker
import com.whisperbook.app.domain.model.BuiltInCharacters
import com.whisperbook.app.domain.model.CharacterAgeGroup
import com.whisperbook.app.domain.model.CharacterColorRole
import com.whisperbook.app.domain.model.CharacterGender
import com.whisperbook.app.domain.model.NarrationPerspective
import com.whisperbook.app.domain.model.StoryCharacter
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

    @Test
    fun `bounds oversized narration before it becomes a persisted passage`() = runTest {
        val oversized = "The forest continued beyond the ridge. ".repeat(200)

        val result = HeuristicSpeakerAttributor().attribute(
            "book",
            ExtractedPublication("Story", null, listOf(ExtractedChapter("One", listOf(oversized)))),
        )
        val passages = result.chapters.single().passages

        assertTrue(passages.size > 1)
        assertTrue(passages.all { it.text.length <= PassageTextChunker.MAX_CHARS })
        assertEquals(oversized.trim(), passages.joinToString(" ") { it.text })
        assertEquals(passages.indices.toList(), passages.map { it.ordinal })
    }

    @Test
    fun `split dialogue passages retain one recoverable dialogue unit`() = runTest {
        val dialogue = "Please keep moving through the forest. ".repeat(120)
        val result = HeuristicSpeakerAttributor().attribute(
            "book",
            ExtractedPublication(
                "Story",
                null,
                listOf(ExtractedChapter("One", listOf("\"$dialogue\" Alice said."))),
            ),
        )

        val alice = result.characters.single { it.displayName == "Alice" }
        val spoken = result.chapters.single().passages.filter { it.speakerId == alice.id }

        assertTrue(spoken.size > 1)
        assertEquals(1, alice.dialogueLineCount)
        assertTrue(spoken.all { ";dialogue-unit-" in it.attributionRule })
        assertEquals(1, spoken.map { it.attributionRule.substringAfter(";dialogue-unit-") }.distinct().size)
    }

    @Test
    fun `infers explicit character age and gender without guessing from the name`() = runTest {
        val result = HeuristicSpeakerAttributor().attribute(
            "book",
            ExtractedPublication(
                "Story",
                null,
                listOf(
                    ExtractedChapter(
                        "One",
                        listOf(
                            "Mara was a sixteen-year-old girl with a red coat.",
                            "\"Wait for me,\" said Mara.",
                            "Rowan opened the gate. He waited in silence.",
                            "\"This way,\" Rowan said.",
                        ),
                    ),
                ),
            ),
        )

        val mara = result.characters.single { it.displayName == "Mara" }
        val rowan = result.characters.single { it.displayName == "Rowan" }
        assertEquals(CharacterGender.FEMALE, mara.gender)
        assertTrue(mara.genderConfidence >= 0.90f)
        assertEquals(CharacterAgeGroup.TEEN, mara.ageGroup)
        assertTrue(mara.ageConfidence >= 0.90f)
        assertEquals(CharacterGender.UNKNOWN, rowan.gender)
        assertEquals(CharacterAgeGroup.UNKNOWN, rowan.ageGroup)
    }

    @Test
    fun `profiles and attributes an identified first person narrator`() = runTest {
        val result = HeuristicSpeakerAttributor().attribute(
            "book",
            ExtractedPublication(
                "Memoir",
                null,
                listOf(
                    ExtractedChapter(
                        "One",
                        listOf(
                            "Call me Elias. I was a seventy-year-old man when the winter came.",
                            "\"Leave now,\" I said. I had heard the warning twice.",
                        ),
                    ),
                ),
            ),
        )

        val narrator = result.characters.single { it.id == BuiltInCharacters.NARRATOR_ID }
        val dialogue = result.chapters.single().passages.single { it.text == "Leave now," }
        assertEquals(setOf("Narrator", "Elias"), narrator.aliases)
        assertEquals("Elias", narrator.narratorIdentity)
        assertEquals(NarrationPerspective.FIRST_PERSON, narrator.narrationPerspective)
        assertTrue(narrator.perspectiveConfidence >= 0.80f)
        assertEquals(CharacterGender.MALE, narrator.gender)
        assertEquals(CharacterAgeGroup.OLDER_ADULT, narrator.ageGroup)
        assertEquals(BuiltInCharacters.NARRATOR_ID, dialogue.speakerId)
        assertTrue(dialogue.attributionRule.startsWith("first-person-narrator-tag"))
        assertTrue(result.characters.none { it.displayName == "Elias" })
    }

    @Test
    fun `recognizes explicit non binary evidence but rejects conflicting gender cues`() = runTest {
        val result = HeuristicSpeakerAttributor().attribute(
            "book",
            ExtractedPublication(
                "Story",
                null,
                listOf(
                    ExtractedChapter(
                        "One",
                        listOf(
                            "Alex was a non-binary adult.",
                            "\"Ready,\" Alex said.",
                            "Rowan was a woman. Later, Rowan was a man.",
                            "\"Listen,\" Rowan said.",
                        ),
                    ),
                ),
            ),
        )

        val alex = result.characters.single { it.displayName == "Alex" }
        val rowan = result.characters.single { it.displayName == "Rowan" }
        assertEquals(CharacterGender.NON_BINARY, alex.gender)
        assertEquals(CharacterAgeGroup.ADULT, alex.ageGroup)
        assertEquals(CharacterGender.UNKNOWN, rowan.gender)
        assertEquals(0f, rowan.genderConfidence)
    }

    @Test
    fun `does not project third person character traits onto the narrator`() = runTest {
        val result = HeuristicSpeakerAttributor().attribute(
            "book",
            ExtractedPublication(
                "Story",
                null,
                listOf(
                    ExtractedChapter(
                        "One",
                        listOf(
                            "Mara was an elderly woman who lived beyond the hill.",
                            "She watched the road every morning, and she carried an old silver bell.",
                            "The villagers knew that she would ring it when strangers approached.",
                            "\"They are here,\" Mara said.",
                        ),
                    ),
                ),
            ),
        )

        val narrator = result.characters.single { it.id == BuiltInCharacters.NARRATOR_ID }
        assertEquals(NarrationPerspective.THIRD_PERSON, narrator.narrationPerspective)
        assertEquals(CharacterGender.UNKNOWN, narrator.gender)
        assertEquals(CharacterAgeGroup.UNKNOWN, narrator.ageGroup)
    }

    @Test
    fun `incremental attribution uses authoritative chapter identity and persisted character id`() = runTest {
        val persistedElara = StoryCharacter(
            id = "catalog-character-elara",
            bookId = "book",
            displayName = "Elara",
            aliases = setOf("Elara", "Ellie"),
            colorRole = CharacterColorRole.ORANGE,
            dialogueLineCount = 4,
            gender = CharacterGender.FEMALE,
            genderConfidence = 0.99f,
            ageGroup = CharacterAgeGroup.ADULT,
            ageConfidence = 0.94f,
        )

        val result = HeuristicSpeakerAttributor().attributeChapter(
            bookId = "book",
            chapterId = "database-chapter-42",
            chapterOrdinal = 41,
            chapter = ExtractedChapter(
                title = "Forty Two",
                paragraphs = listOf(
                    "Elara was a man.",
                    "\"Stay here,\" said Ellie.",
                ),
            ),
            knownCharacters = listOf(persistedElara),
        )

        val chapter = result.chapters.single()
        val elara = result.characters.single { it.id == persistedElara.id }
        assertEquals("database-chapter-42", chapter.id)
        assertEquals(41, chapter.ordinal)
        assertTrue(chapter.passages.all { it.id.startsWith("database-chapter-42-passage-") })
        assertEquals(
            persistedElara.id,
            chapter.passages.single { it.text == "Stay here," }.speakerId,
        )
        assertEquals(CharacterColorRole.ORANGE, elara.colorRole)
        assertEquals(CharacterGender.FEMALE, elara.gender)
        assertEquals(CharacterAgeGroup.ADULT, elara.ageGroup)
        assertEquals(5, elara.dialogueLineCount)
        assertTrue(result.chapters.none { it.id == "book-chapter-1" })
    }

    @Test
    fun `incremental attribution preserves a book scoped narrator seed`() = runTest {
        val narratorId = "book-character-narrator"
        val persistedNarrator = StoryCharacter(
            id = narratorId,
            bookId = "book",
            displayName = "Narrator",
            aliases = setOf("Narrator", "Elias"),
            colorRole = CharacterColorRole.NARRATOR,
            dialogueLineCount = 0,
            narrationPerspective = NarrationPerspective.FIRST_PERSON,
            perspectiveConfidence = 0.92f,
            narratorIdentity = "Elias",
        )

        val result = HeuristicSpeakerAttributor().attributeChapter(
            bookId = "book",
            chapterId = "chapter-two",
            chapterOrdinal = 1,
            chapter = ExtractedChapter(
                title = "Two",
                paragraphs = listOf("I crossed the frozen river.", "\"Go,\" I said."),
            ),
            knownCharacters = listOf(persistedNarrator),
        )

        val narrator = result.characters.single { it.colorRole == CharacterColorRole.NARRATOR }
        assertEquals(narratorId, narrator.id)
        assertEquals("Elias", narrator.narratorIdentity)
        assertTrue(result.chapters.single().passages.all { it.speakerId == narratorId })
        assertTrue(result.characters.none { it.id == BuiltInCharacters.NARRATOR_ID })
    }
}
