package com.whisperbook.app.ui.screens

import com.whisperbook.app.domain.model.Book
import com.whisperbook.app.domain.model.BookFormat
import com.whisperbook.app.domain.model.Chapter
import com.whisperbook.app.domain.model.CharacterAgeGroup
import com.whisperbook.app.domain.model.CharacterColorRole
import com.whisperbook.app.domain.model.CharacterGender
import com.whisperbook.app.domain.model.NarrationPerspective
import com.whisperbook.app.domain.model.Passage
import com.whisperbook.app.domain.model.PreparationStage
import com.whisperbook.app.domain.model.PreparationState
import com.whisperbook.app.domain.model.StoryCharacter
import com.whisperbook.app.integration.WhisperbookUiSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryPreviewProjectionTest {
    @Test
    fun `bible lists the narrator first then characters by line count`() {
        val bible = projectStoryBible(characters, chapters)

        assertEquals(listOf("narrator", "mara", "tom", "silent"), bible.map(StoryCharacterUi::id))
        assertTrue(bible.first().isNarrator)
        assertEquals(listOf(2, 3, 1, 0), bible.map(StoryCharacterUi::lineCount))
    }

    @Test
    fun `bible derives chapter appearances and the first sample line from passages`() {
        val bible = projectStoryBible(characters, chapters).associateBy(StoryCharacterUi::id)

        with(bible.getValue("mara")) {
            // Chapter numbers follow the listening order, but name the original chapter.
            assertEquals(listOf(3, 1), chapterNumbers)
            assertEquals("chapter-3", firstChapterId)
            assertEquals("c3-p1", firstPassageId)
            assertEquals("\"Wake up,\" Mara whispered.", sampleLine)
            assertEquals(listOf("Mara Lind"), aliases)
        }
        with(bible.getValue("silent")) {
            assertTrue(chapterNumbers.isEmpty())
            assertNull(firstPassageId)
            assertNull(sampleLine)
        }
    }

    @Test
    fun `long sample lines are truncated on a word boundary`() {
        val longLine = List(60) { "word$it" }.joinToString(" ")
        val bible = projectStoryBible(
            characters = characters.filter { it.id == "narrator" },
            chapters = listOf(chapter("chapter-1", 0, passage("c1-p1", "chapter-1", longLine, "narrator"))),
        )

        val sample = bible.single().sampleLine.orEmpty()
        assertTrue(sample.length <= STORY_SAMPLE_LINE_MAX_CHARS + 1)
        assertTrue(sample.endsWith("…"))
        assertTrue(longLine.startsWith(sample.removeSuffix("…")))
    }

    @Test
    fun `profile hints use the casting confidence threshold`() {
        val bible = projectStoryBible(characters, chapters).associateBy(StoryCharacterUi::id)

        assertFalse(bible.getValue("mara").profileUncertain)
        assertTrue(bible.getValue("tom").profileUncertain)
        assertEquals(NarrationPerspective.FIRST_PERSON, bible.getValue("narrator").narrationPerspective)
    }

    @Test
    fun `a speaker correction moves lines and appearances between characters`() {
        val corrected = chapters.map { chapter ->
            chapter.copy(
                passages = chapter.passages.map { passage ->
                    if (passage.id in correctedPassageIds) passage.copy(speakerId = "tom") else passage
                },
            )
        }

        val bible = projectStoryBible(characters, corrected).associateBy(StoryCharacterUi::id)

        assertEquals(1, bible.getValue("mara").lineCount)
        assertEquals(listOf(3), bible.getValue("mara").chapterNumbers)
        assertEquals(3, bible.getValue("tom").lineCount)
        assertEquals(listOf(3, 1), bible.getValue("tom").chapterNumbers)
    }

    @Test
    fun `app state exposes the review only at the story gate`() {
        val state = WhisperbookAppState()
        val awaiting = book(
            stage = PreparationStage.AWAITING_STORY_REVIEW,
            storyReviewConfirmed = false,
        )

        state.synchronize(
            WhisperbookUiSnapshot(
                books = listOf(awaiting),
                selectedBook = awaiting,
                characters = characters,
                preparation = awaiting.preparation,
                storyChapters = chapters,
            ),
        )

        assertTrue(state.storyReviewRequired)
        assertTrue(state.requiresStoryReview(awaiting.id))
        assertTrue(state.books.single().needsStoryReview)
        assertEquals(
            "Review the characters before voices are generated",
            state.books.single().libraryProgressLabel(),
        )
        assertFalse(state.canListen)
        assertEquals(listOf("chapter-3", "chapter-1"), state.storyChapters.map(StoryChapterUi::id))
        assertEquals(listOf(1, 2), state.storyChapters.map(StoryChapterUi::listeningPosition))
        assertEquals(
            listOf("c3-p1", "c3-p2"),
            state.storyChapters.first().passages.flatMap(PassageUi::sourcePassageIds),
        )
        assertEquals(listOf("narrator", "mara", "tom", "silent"), state.storyCharacters.map { it.id })
        // Before casting there are no voice assignments, but every character can be chosen.
        assertEquals(
            setOf("narrator", "mara", "tom", "silent"),
            state.storyCast.mapTo(linkedSetOf(), CastMemberUi::id),
        )

        val corrected = chapters.map { chapter ->
            chapter.copy(
                passages = chapter.passages.map { passage ->
                    if (passage.id in correctedPassageIds) passage.copy(speakerId = "tom") else passage
                },
            )
        }
        state.synchronize(
            WhisperbookUiSnapshot(
                books = listOf(awaiting),
                selectedBook = awaiting,
                characters = characters,
                preparation = awaiting.preparation,
                storyChapters = corrected,
            ),
        )
        assertEquals(3, state.storyCharacters.single { it.id == "tom" }.lineCount)
        assertEquals(listOf("narrator", "tom", "mara", "silent"), state.storyCharacters.map { it.id })

        val reviewed = awaiting.copy(
            preparation = awaiting.preparation.copy(stage = PreparationStage.ASSIGNING_VOICES),
            storyReviewConfirmed = true,
        )
        state.synchronize(
            WhisperbookUiSnapshot(
                books = listOf(reviewed),
                selectedBook = reviewed,
                characters = characters,
                preparation = reviewed.preparation,
            ),
        )
        assertFalse(state.storyReviewRequired)
        assertFalse(state.books.single().needsStoryReview)
        assertTrue(state.storyChapters.isEmpty())
    }

    @Test
    fun `finding characters is not yet a story review`() {
        val state = WhisperbookAppState()
        val reading = book(stage = PreparationStage.FINDING_CHARACTERS, storyReviewConfirmed = false).let {
            it.copy(preparation = it.preparation.copy(completedUnits = 1, totalUnits = 3))
        }

        state.synchronize(
            WhisperbookUiSnapshot(
                books = listOf(reading),
                selectedBook = reading,
                preparation = reading.preparation,
            ),
        )

        assertFalse(state.storyReviewRequired)
        assertFalse(state.books.single().needsStoryReview)
        assertTrue(state.isBookPreparing)
        assertEquals("Reading the story · 1 of 3 chapters", state.books.single().libraryProgressLabel())
    }

    private val correctedPassageIds = setOf("c1-p2", "c1-p3")

    private val characters = listOf(
        StoryCharacter(
            id = "tom",
            bookId = "book-1",
            displayName = "Tom",
            aliases = emptySet(),
            colorRole = CharacterColorRole.FOX_ORANGE,
            dialogueLineCount = 4,
            gender = CharacterGender.MALE,
            genderConfidence = 0.4f,
            ageGroup = CharacterAgeGroup.CHILD,
            ageConfidence = 0.9f,
        ),
        StoryCharacter(
            id = "silent",
            bookId = "book-1",
            displayName = "Silent",
            aliases = emptySet(),
            colorRole = CharacterColorRole.BLUE,
            dialogueLineCount = 0,
        ),
        StoryCharacter(
            id = "mara",
            bookId = "book-1",
            displayName = "Mara",
            aliases = setOf("Mara", "Mara Lind"),
            colorRole = CharacterColorRole.ELARA_BURGUNDY,
            dialogueLineCount = 3,
            gender = CharacterGender.FEMALE,
            genderConfidence = 0.9f,
            ageGroup = CharacterAgeGroup.ADULT,
            ageConfidence = 0.8f,
        ),
        StoryCharacter(
            id = "narrator",
            bookId = "book-1",
            displayName = "Narrator",
            aliases = emptySet(),
            colorRole = CharacterColorRole.NARRATOR,
            dialogueLineCount = 0,
            narrationPerspective = NarrationPerspective.FIRST_PERSON,
            perspectiveConfidence = 0.8f,
        ),
    )

    // Listening order puts the original third chapter first.
    private val chapters = listOf(
        chapter(
            "chapter-3",
            2,
            passage("c3-p1", "chapter-3", "\"Wake up,\" Mara whispered.", "mara"),
            passage("c3-p2", "chapter-3", "\"I am awake,\" said Tom.", "tom"),
        ),
        chapter(
            "chapter-1",
            0,
            passage("c1-p1", "chapter-1", "The house was quiet.", "narrator"),
            passage("c1-p2", "chapter-1", "\"Who is there?\"", "mara"),
            passage("c1-p3", "chapter-1", "\"Only me.\"", "mara"),
            passage("c1-p4", "chapter-1", "Nobody answered.", "narrator"),
        ),
    )

    private fun chapter(id: String, ordinal: Int, vararg passages: Passage) = Chapter(
        id = id,
        bookId = "book-1",
        ordinal = ordinal,
        title = "Chapter ${ordinal + 1}",
        passages = passages.toList(),
    )

    private fun passage(id: String, chapterId: String, text: String, speakerId: String) = Passage(
        id = id,
        chapterId = chapterId,
        ordinal = id.substringAfterLast("-p").toInt() - 1,
        text = text,
        speakerId = speakerId,
        confidence = 0.8f,
        attributionRule = "heuristic",
    )

    private fun book(stage: PreparationStage, storyReviewConfirmed: Boolean) = Book(
        id = "book-1",
        title = "Story",
        author = "A. Reader",
        format = BookFormat.EPUB,
        sourceUri = null,
        privateSourcePath = null,
        coverPath = null,
        preparation = PreparationState(stage = stage, chapterPlanConfirmed = true),
        currentChapterId = "chapter-3",
        currentPassageId = null,
        progressFraction = 0f,
        lastOpenedAtEpochMs = 1L,
        chapterCount = 3,
        narrationSetupConfirmed = true,
        storyReviewConfirmed = storyReviewConfirmed,
    )
}
