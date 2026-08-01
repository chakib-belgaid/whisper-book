package com.whisperbook.app.data.local.db

import com.whisperbook.app.domain.model.BookFormat
import com.whisperbook.app.domain.model.CharacterColorRole
import com.whisperbook.app.domain.model.PreparationStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MappersTest {
    @Test
    fun `book aggregate maps preparation and bounds stored progress`() {
        val aggregate = BookAggregate(
            book = BookEntity(
                id = "book-1",
                title = "A Quiet Tale",
                author = "A. Reader",
                format = BookFormat.PDF.name,
                sourceUri = null,
                privateSourcePath = "/private/book.pdf",
                sourceSha256 = "abc123",
                coverPath = null,
                currentChapterId = "chapter-1",
                currentPassageId = null,
                progressFraction = 1.4f,
                lastOpenedAtEpochMs = 42L,
            ),
            preparationJobs = listOf(
                PreparationJobEntity(
                    bookId = "book-1",
                    stage = PreparationStage.FINDING_CHARACTERS.name,
                    completedUnits = 3,
                    totalUnits = 10,
                    progressFraction = 0.3f,
                    message = "Finding voices",
                    retryable = false,
                    attemptCount = 1,
                    updatedAtEpochMs = 42L,
                ),
            ),
        )

        val mapped = aggregate.toDomain()

        assertEquals(BookFormat.PDF, mapped.format)
        assertEquals(1f, mapped.progressFraction)
        assertEquals(PreparationStage.FINDING_CHARACTERS, mapped.preparation.stage)
        assertEquals(3, mapped.preparation.completedUnits)
        assertNull(mapped.sourceUri)
        assertEquals("abc123", mapped.toEntity(sourceSha256 = "abc123").sourceSha256)
    }

    @Test
    fun `chapter mapping sorts passages by ordinal`() {
        val aggregate = ChapterAggregate(
            chapter = ChapterEntity("chapter-1", "book-1", 0, "Chapter One"),
            passages = listOf(
                PassageEntity("passage-2", "chapter-1", 2, "Last", "narrator", 1f, "default"),
                PassageEntity("passage-1", "chapter-1", 1, "First", "narrator", 1f, "default"),
            ),
        )

        assertEquals(listOf("passage-1", "passage-2"), aggregate.toDomain().passages.map { it.id })
    }

    @Test
    fun `character aliases map deterministically`() {
        val aggregate = CharacterAggregate(
            character = StoryCharacterEntity(
                id = "elara",
                bookId = "book-1",
                displayName = "Elara",
                colorRole = CharacterColorRole.ELARA_BURGUNDY.name,
                dialogueLineCount = 12,
            ),
            aliases = listOf(
                CharacterAliasEntity("elara", "the traveller"),
                CharacterAliasEntity("elara", "El"),
            ),
            voiceAssignments = emptyList(),
        )

        val character = aggregate.toDomain()

        assertEquals(CharacterColorRole.ELARA_BURGUNDY, character.colorRole)
        assertEquals(listOf("El", "the traveller"), character.aliases.toList())
    }
}
