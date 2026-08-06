package com.whisperbook.app.engine.metadata

import com.whisperbook.app.domain.model.CharacterAgeGroup
import com.whisperbook.app.domain.model.CharacterColorRole
import com.whisperbook.app.domain.model.CharacterGender
import com.whisperbook.app.domain.model.NarrationPerspective
import com.whisperbook.app.domain.model.StoryCharacter
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Versioned, derived character metadata for one book.
 *
 * Room remains authoritative. This catalog is a restart-friendly mirror of incremental chapter
 * analysis and must never contain voice choices, audio paths, or source text.
 */
data class CharacterMetadataCatalogSnapshot(
    val schemaVersion: Int = CharacterMetadataCatalog.SCHEMA_VERSION,
    val bookId: String,
    val sourceSha256: String?,
    val analysisVersion: String,
    val revision: Long,
    val updatedAtEpochMs: Long,
    val complete: Boolean,
    val chapters: List<ChapterCharacterMetadata>,
    val cumulativeCharacters: List<CharacterMetadataRecord>,
) {
    init {
        require(schemaVersion == CharacterMetadataCatalog.SCHEMA_VERSION) {
            "Unsupported character metadata schema: $schemaVersion"
        }
        require(bookId.isNotBlank()) { "bookId must not be blank" }
        require(sourceSha256 == null || sourceSha256.isNotBlank()) {
            "sourceSha256 must be null or non-blank"
        }
        require(analysisVersion.isNotBlank()) { "analysisVersion must not be blank" }
        require(revision > 0L) { "revision must be positive" }
        require(updatedAtEpochMs >= 0L) { "updatedAtEpochMs must not be negative" }
        require(chapters.distinctBy(ChapterCharacterMetadata::chapterId).size == chapters.size) {
            "chapter IDs must be unique"
        }
        require(chapters.distinctBy(ChapterCharacterMetadata::ordinal).size == chapters.size) {
            "chapter ordinals must be unique"
        }
        require(cumulativeCharacters.distinctBy(CharacterMetadataRecord::id).size == cumulativeCharacters.size) {
            "character IDs must be unique"
        }
        val expectedDialogueCounts = linkedMapOf<String, Int>()
        chapters.forEach { chapter ->
            chapter.contributions.forEach { contribution ->
                expectedDialogueCounts[contribution.characterId] = Math.addExact(
                    expectedDialogueCounts[contribution.characterId] ?: 0,
                    contribution.dialogueLineCount,
                )
            }
        }
        require(
            cumulativeCharacters.mapTo(linkedSetOf(), CharacterMetadataRecord::id)
                .containsAll(expectedDialogueCounts.keys),
        ) {
            "every chapter contribution must have cumulative character metadata"
        }
        require(cumulativeCharacters.all { character ->
            character.dialogueLineCount == (expectedDialogueCounts[character.id] ?: 0)
        }) {
            "cumulative dialogue counts must equal chapter contributions"
        }
    }
}

data class ChapterCharacterMetadata(
    val chapterId: String,
    val ordinal: Int,
    val textSha256: String,
    val contributions: List<CharacterDialogueContribution>,
) {
    init {
        require(chapterId.isNotBlank()) { "chapterId must not be blank" }
        require(ordinal >= 0) { "chapter ordinal must not be negative" }
        require(textSha256.isNotBlank()) { "textSha256 must not be blank" }
        require(contributions.distinctBy(CharacterDialogueContribution::characterId).size == contributions.size) {
            "a character may contribute only once per chapter"
        }
    }
}

data class CharacterDialogueContribution(
    val characterId: String,
    val dialogueLineCount: Int,
) {
    init {
        require(characterId.isNotBlank()) { "characterId must not be blank" }
        require(dialogueLineCount >= 0) { "dialogueLineCount must not be negative" }
    }
}

data class CharacterMetadataRecord(
    val id: String,
    val displayName: String,
    val aliases: Set<String>,
    val colorRole: CharacterColorRole,
    val dialogueLineCount: Int,
    val gender: CharacterGender = CharacterGender.UNKNOWN,
    val genderConfidence: Float = 0f,
    val ageGroup: CharacterAgeGroup = CharacterAgeGroup.UNKNOWN,
    val ageConfidence: Float = 0f,
    val narrationPerspective: NarrationPerspective = NarrationPerspective.UNKNOWN,
    val perspectiveConfidence: Float = 0f,
    val narratorIdentity: String? = null,
) {
    init {
        require(id.isNotBlank()) { "character id must not be blank" }
        require(displayName.isNotBlank()) { "character displayName must not be blank" }
        require(aliases.none(String::isBlank)) { "character aliases must not be blank" }
        require(dialogueLineCount >= 0) { "dialogueLineCount must not be negative" }
        require(genderConfidence.isFinite() && genderConfidence in 0f..1f) {
            "genderConfidence must be between zero and one"
        }
        require(ageConfidence.isFinite() && ageConfidence in 0f..1f) {
            "ageConfidence must be between zero and one"
        }
        require(perspectiveConfidence.isFinite() && perspectiveConfidence in 0f..1f) {
            "perspectiveConfidence must be between zero and one"
        }
        require(narratorIdentity == null || narratorIdentity.isNotBlank()) {
            "narratorIdentity must be null or non-blank"
        }
    }

    companion object {
        fun from(character: StoryCharacter): CharacterMetadataRecord = CharacterMetadataRecord(
            id = character.id,
            displayName = character.displayName,
            aliases = character.aliases,
            colorRole = character.colorRole,
            dialogueLineCount = character.dialogueLineCount,
            gender = character.gender,
            genderConfidence = character.genderConfidence,
            ageGroup = character.ageGroup,
            ageConfidence = character.ageConfidence,
            narrationPerspective = character.narrationPerspective,
            perspectiveConfidence = character.perspectiveConfidence,
            narratorIdentity = character.narratorIdentity,
        )
    }
}

data class CharacterMetadataChapterUpdate(
    val bookId: String,
    val sourceSha256: String?,
    val analysisVersion: String,
    val chapter: ChapterCharacterMetadata,
    /** Character records discovered so far or improved by this chapter. */
    val characters: List<CharacterMetadataRecord>,
    val complete: Boolean,
) {
    init {
        require(bookId.isNotBlank()) { "bookId must not be blank" }
        require(sourceSha256 == null || sourceSha256.isNotBlank()) {
            "sourceSha256 must be null or non-blank"
        }
        require(analysisVersion.isNotBlank()) { "analysisVersion must not be blank" }
        require(characters.distinctBy(CharacterMetadataRecord::id).size == characters.size) {
            "character IDs in an update must be unique"
        }
    }
}

interface CharacterMetadataCatalog {
    suspend fun read(bookId: String): CharacterMetadataCatalogSnapshot?

    /**
     * Replaces this chapter's contribution and merges its profile evidence into the cumulative
     * character list. Repeating an identical update is a no-op and does not advance [revision].
     */
    suspend fun recordChapter(update: CharacterMetadataChapterUpdate): CharacterMetadataCatalogSnapshot

    /** Deletes only this book's derived metadata. Returns true when something was removed. */
    suspend fun delete(bookId: String): Boolean

    companion object {
        const val SCHEMA_VERSION: Int = 1
    }
}

object CharacterMetadataFingerprint {
    fun sha256Utf8(text: String): String = sha256(text.toByteArray(StandardCharsets.UTF_8))

    internal fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return buildString(digest.size * 2) {
            digest.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(HEX[value ushr 4])
                append(HEX[value and 0x0f])
            }
        }
    }

    private const val HEX = "0123456789abcdef"
}
