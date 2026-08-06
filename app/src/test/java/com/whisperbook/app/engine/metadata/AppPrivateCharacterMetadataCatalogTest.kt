package com.whisperbook.app.engine.metadata

import com.whisperbook.app.domain.model.CharacterAgeGroup
import com.whisperbook.app.domain.model.CharacterColorRole
import com.whisperbook.app.domain.model.CharacterGender
import com.whisperbook.app.domain.model.NarrationPerspective
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppPrivateCharacterMetadataCatalogTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `catalog survives restart with versioned cumulative character metadata`() = runTest {
        var now = 1_000L
        val root = File(temporaryFolder.root, "metadata")
        val catalog = AppPrivateCharacterMetadataCatalog(root, nowEpochMs = { now })

        val first = catalog.recordChapter(
            update(
                chapter = chapter(
                    id = "book-a-chapter-1",
                    ordinal = 0,
                    text = "Chapter one text",
                    "book-a-character-alice" to 2,
                    "book-a-character-narrator" to 4,
                ),
                characters = listOf(
                    alice(),
                    bob(),
                    narrator(),
                ),
            ),
        )
        assertEquals(1L, first.revision)
        assertFalse(first.complete)
        assertEquals(0, first.cumulativeCharacters.single { it.id == "book-a-character-bob" }.dialogueLineCount)
        assertTrue(first.chapters.single().contributions.none { it.characterId == "book-a-character-bob" })

        now = 2_000L
        val second = catalog.recordChapter(
            update(
                chapter = chapter(
                    id = "book-a-chapter-2",
                    ordinal = 1,
                    text = "Chapter two text",
                    "book-a-character-alice" to 1,
                    "book-a-character-bob" to 3,
                    "book-a-character-narrator" to 2,
                ),
                characters = listOf(
                    alice(aliases = setOf("Alice", "Ms Alice")),
                    bob(),
                    narrator(),
                ),
                complete = true,
            ),
        )

        assertEquals(2L, second.revision)
        assertEquals(2_000L, second.updatedAtEpochMs)
        assertTrue(second.complete)
        assertEquals(listOf("book-a-chapter-1", "book-a-chapter-2"), second.chapters.map { it.chapterId })
        assertEquals(
            mapOf(
                "book-a-character-alice" to 3,
                "book-a-character-bob" to 3,
                "book-a-character-narrator" to 6,
            ),
            second.cumulativeCharacters.associate { it.id to it.dialogueLineCount },
        )
        val persistedAlice = second.cumulativeCharacters.single { it.id == "book-a-character-alice" }
        assertEquals(setOf("Alice", "Al", "Ms Alice"), persistedAlice.aliases)
        assertEquals(CharacterGender.FEMALE, persistedAlice.gender)
        assertEquals(CharacterAgeGroup.ADULT, persistedAlice.ageGroup)

        val restarted = AppPrivateCharacterMetadataCatalog(root)
        assertEquals(second, restarted.read("book-a"))

        val rawText = catalog.metadataFile("book-a").readText()
        assertFalse(rawText.contains("Chapter one text"))
        assertFalse(rawText.contains("Chapter two text"))
        val rawJson = Json.parseToJsonElement(rawText).jsonObject
        assertEquals("1", rawJson.getValue("schemaVersion").jsonPrimitive.content)
        assertEquals("source-sha", rawJson.getValue("sourceSha256").jsonPrimitive.content)
        assertEquals("attribution-v2", rawJson.getValue("analysisVersion").jsonPrimitive.content)
        assertEquals(2, rawJson.getValue("chapters").jsonArray.size)
        assertEquals(3, rawJson.getValue("cumulativeCharacters").jsonArray.size)
        assertTrue(requireNotNull(catalog.metadataFile("book-a").parentFile).listFiles().orEmpty().none {
            it.name.contains(".tmp-")
        })
    }

    @Test
    fun `same chapter retry is a no-op and changed chapter replaces rather than adds`() = runTest {
        var now = 10_000L
        val root = File(temporaryFolder.root, "retry-metadata")
        val catalog = AppPrivateCharacterMetadataCatalog(root, nowEpochMs = { now })
        val initialUpdate = update(
            chapter = chapter(
                id = "book-a-chapter-1",
                ordinal = 0,
                text = "Original text",
                "book-a-character-alice" to 3,
                "book-a-character-narrator" to 5,
            ),
            characters = listOf(alice(), narrator()),
        )

        val initial = catalog.recordChapter(initialUpdate)
        val initialBytes = catalog.metadataFile("book-a").readBytes()
        now = 20_000L
        val retry = catalog.recordChapter(initialUpdate)

        assertEquals(initial, retry)
        assertEquals(1L, retry.revision)
        assertTrue(initialBytes.contentEquals(catalog.metadataFile("book-a").readBytes()))

        now = 30_000L
        val replacement = catalog.recordChapter(
            initialUpdate.copy(
                chapter = chapter(
                    id = "book-a-chapter-1",
                    ordinal = 0,
                    text = "Corrected text",
                    "book-a-character-alice" to 1,
                    "book-a-character-bob" to 2,
                    "book-a-character-narrator" to 4,
                ),
                characters = listOf(alice(), bob(), narrator()),
            ),
        )

        assertEquals(2L, replacement.revision)
        assertEquals(30_000L, replacement.updatedAtEpochMs)
        assertEquals(1, replacement.chapters.size)
        assertEquals(
            mapOf(
                "book-a-character-alice" to 1,
                "book-a-character-bob" to 2,
                "book-a-character-narrator" to 4,
            ),
            replacement.cumulativeCharacters.associate { it.id to it.dialogueLineCount },
        )
        assertNotEquals(initial.chapters.single().textSha256, replacement.chapters.single().textSha256)
    }

    @Test
    fun `corrupt or mismatched JSON is ignored and safely rebuilt`() = runTest {
        val root = File(temporaryFolder.root, "corrupt-metadata")
        val catalog = AppPrivateCharacterMetadataCatalog(root, nowEpochMs = { 42L })
        val file = catalog.metadataFile("book-a")
        assertTrue(requireNotNull(file.parentFile).mkdirs())
        file.writeText("{ definitely not valid JSON")

        assertNull(catalog.read("book-a"))

        val rebuilt = catalog.recordChapter(
            update(
                chapter = chapter(
                    id = "book-a-chapter-1",
                    ordinal = 0,
                    text = "Recovered",
                    "book-a-character-narrator" to 1,
                ),
                characters = listOf(narrator()),
            ),
        )
        assertEquals(1L, rebuilt.revision)
        assertEquals(rebuilt, catalog.read("book-a"))

        file.writeText(file.readText().replace("\"bookId\": \"book-a\"", "\"bookId\": \"book-b\""))
        assertNull(catalog.read("book-a"))
    }

    @Test
    fun `hashed book directory prevents path traversal and delete is book scoped`() = runTest {
        val root = File(temporaryFolder.root, "safe-root")
        val suspiciousBookId = "../../outside/../book?name=unsafe"
        val otherBookId = "other-book"
        val catalog = AppPrivateCharacterMetadataCatalog(root, nowEpochMs = { 100L })

        catalog.recordChapter(
            update(
                bookId = suspiciousBookId,
                chapter = chapter(
                    id = "unsafe-chapter-1",
                    ordinal = 0,
                    text = "Safe contents",
                    "unsafe-narrator" to 1,
                ),
                characters = listOf(narrator(id = "unsafe-narrator")),
            ),
        )
        catalog.recordChapter(
            update(
                bookId = otherBookId,
                chapter = chapter(
                    id = "other-chapter-1",
                    ordinal = 0,
                    text = "Other contents",
                    "other-narrator" to 1,
                ),
                characters = listOf(narrator(id = "other-narrator")),
            ),
        )

        val suspiciousFile = catalog.metadataFile(suspiciousBookId)
        val otherFile = catalog.metadataFile(otherBookId)
        assertTrue(suspiciousFile.isFile)
        assertTrue(otherFile.isFile)
        assertTrue(suspiciousFile.canonicalPath.startsWith(root.canonicalPath + File.separator))
        assertTrue(requireNotNull(suspiciousFile.parentFile).name.matches(Regex("[a-f0-9]{64}")))
        assertFalse(suspiciousFile.path.contains(suspiciousBookId))
        assertEquals("characters.json", suspiciousFile.name)

        assertTrue(catalog.delete(suspiciousBookId))
        assertFalse(suspiciousFile.exists())
        assertTrue(otherFile.isFile)
        assertFalse(catalog.delete(suspiciousBookId))
    }

    private fun update(
        bookId: String = "book-a",
        chapter: ChapterCharacterMetadata,
        characters: List<CharacterMetadataRecord>,
        complete: Boolean = false,
    ) = CharacterMetadataChapterUpdate(
        bookId = bookId,
        sourceSha256 = "source-sha",
        analysisVersion = "attribution-v2",
        chapter = chapter,
        characters = characters,
        complete = complete,
    )

    private fun chapter(
        id: String,
        ordinal: Int,
        text: String,
        vararg contributions: Pair<String, Int>,
    ) = ChapterCharacterMetadata(
        chapterId = id,
        ordinal = ordinal,
        textSha256 = CharacterMetadataFingerprint.sha256Utf8(text),
        contributions = contributions.map { (characterId, count) ->
            CharacterDialogueContribution(characterId, count)
        },
    )

    private fun alice(
        aliases: Set<String> = setOf("Alice", "Al"),
    ) = CharacterMetadataRecord(
        id = "book-a-character-alice",
        displayName = "Alice",
        aliases = aliases,
        colorRole = CharacterColorRole.BURGUNDY,
        dialogueLineCount = 0,
        gender = CharacterGender.FEMALE,
        genderConfidence = 0.9f,
        ageGroup = CharacterAgeGroup.ADULT,
        ageConfidence = 0.7f,
    )

    private fun bob() = CharacterMetadataRecord(
        id = "book-a-character-bob",
        displayName = "Bob",
        aliases = setOf("Bob"),
        colorRole = CharacterColorRole.BLUE,
        dialogueLineCount = 0,
        gender = CharacterGender.MALE,
        genderConfidence = 0.8f,
    )

    private fun narrator(
        id: String = "book-a-character-narrator",
    ) = CharacterMetadataRecord(
        id = id,
        displayName = "Narrator",
        aliases = setOf("Narrator"),
        colorRole = CharacterColorRole.NARRATOR,
        dialogueLineCount = 0,
        narrationPerspective = NarrationPerspective.THIRD_PERSON,
        perspectiveConfidence = 0.85f,
    )
}
