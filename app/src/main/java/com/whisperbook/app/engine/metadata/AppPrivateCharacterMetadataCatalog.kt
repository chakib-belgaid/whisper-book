package com.whisperbook.app.engine.metadata

import android.content.Context
import com.whisperbook.app.domain.model.CharacterAgeGroup
import com.whisperbook.app.domain.model.CharacterColorRole
import com.whisperbook.app.domain.model.CharacterGender
import com.whisperbook.app.domain.model.NarrationPerspective
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/** App-private, atomic JSON implementation of [CharacterMetadataCatalog]. */
class AppPrivateCharacterMetadataCatalog internal constructor(
    private val root: File,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
    private val fileDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CharacterMetadataCatalog {
    constructor(context: Context) : this(
        root = File(context.filesDir, "publications/metadata"),
    )

    private val json = Json { prettyPrint = true }

    override suspend fun read(bookId: String): CharacterMetadataCatalogSnapshot? =
        withContext(fileDispatcher) {
            processMutex.withLock {
                requireBookId(bookId)
                readLocked(bookId)
            }
        }

    override suspend fun recordChapter(
        update: CharacterMetadataChapterUpdate,
    ): CharacterMetadataCatalogSnapshot = withContext(fileDispatcher) {
        processMutex.withLock {
            val existing = readLocked(update.bookId)
            val sameAnalysis = existing != null &&
                existing.sourceSha256 == update.sourceSha256 &&
                existing.analysisVersion == update.analysisVersion
            val baseline = existing.takeIf { sameAnalysis }

            val chapters = baseline.orEmptyChapters()
                .associateByTo(linkedMapOf(), ChapterCharacterMetadata::chapterId)
                .apply { put(update.chapter.chapterId, normalize(update.chapter)) }
                .values
                .sortedWith(compareBy(ChapterCharacterMetadata::ordinal, ChapterCharacterMetadata::chapterId))

            val profiles = baseline.orEmptyCharacters()
                .associateByTo(linkedMapOf(), CharacterMetadataRecord::id)
            update.characters.forEach { supplied ->
                val normalized = normalize(supplied)
                profiles[normalized.id] = profiles[normalized.id]
                    ?.let { persisted -> mergeProfiles(persisted, normalized) }
                    ?: normalized
            }

            val totals = linkedMapOf<String, Int>()
            chapters.forEach { chapter ->
                chapter.contributions.forEach { contribution ->
                    totals[contribution.characterId] = Math.addExact(
                        totals[contribution.characterId] ?: 0,
                        contribution.dialogueLineCount,
                    )
                }
            }
            val missingProfiles = totals.keys - profiles.keys
            require(missingProfiles.isEmpty()) {
                "Missing metadata for chapter characters: ${missingProfiles.sorted().joinToString()}"
            }
            // Keep zero-dialogue discoveries in the cumulative character list even though they
            // have no per-chapter speaker contribution. This preserves the complete Room-derived
            // catalog without falsely claiming that a later character appeared in chapter one
            // during JSON reconstruction.
            val cumulativeCharacters = (totals.keys + profiles.keys).sorted().map { characterId ->
                profiles.getValue(characterId).copy(dialogueLineCount = totals[characterId] ?: 0)
            }
            val complete = update.complete || (baseline?.complete == true)
            val semanticCandidate = CharacterMetadataCatalogSnapshot(
                bookId = update.bookId,
                sourceSha256 = update.sourceSha256,
                analysisVersion = update.analysisVersion,
                revision = baseline?.revision ?: 1L,
                updatedAtEpochMs = baseline?.updatedAtEpochMs ?: 0L,
                complete = complete,
                chapters = chapters,
                cumulativeCharacters = cumulativeCharacters,
            )
            if (baseline != null && baseline.semanticContent() == semanticCandidate.semanticContent()) {
                return@withLock baseline
            }

            val nextRevision = if (existing == null) 1L else Math.addExact(existing.revision, 1L)
            val snapshot = semanticCandidate.copy(
                revision = nextRevision,
                updatedAtEpochMs = nowEpochMs().also { require(it >= 0L) },
            )
            writeLocked(snapshot)
            snapshot
        }
    }

    override suspend fun delete(bookId: String): Boolean = withContext(fileDispatcher) {
        processMutex.withLock {
            requireBookId(bookId)
            val directory = bookDirectory(bookId)
            if (!directory.exists()) return@withLock false
            directory.deleteRecursively()
        }
    }

    internal fun metadataFile(bookId: String): File {
        requireBookId(bookId)
        return File(bookDirectory(bookId), FILE_NAME)
    }

    private fun readLocked(bookId: String): CharacterMetadataCatalogSnapshot? {
        requireBookId(bookId)
        val file = metadataFile(bookId)
        if (!file.isFile) return null
        return runCatching {
            decode(json.parseToJsonElement(file.readText(Charsets.UTF_8))).also { decoded ->
                require(decoded.bookId == bookId) { "Catalog belongs to another book" }
            }
        }.getOrNull()
    }

    private fun writeLocked(snapshot: CharacterMetadataCatalogSnapshot) {
        val target = metadataFile(snapshot.bookId)
        val directory = target.parentFile ?: error("Metadata file has no parent")
        check(directory.mkdirs() || directory.isDirectory) {
            "Unable to create character metadata directory"
        }
        val temporary = File(directory, "$FILE_NAME.tmp-${UUID.randomUUID()}")
        try {
            val bytes = json.encodeToString(JsonElement.serializer(), encode(snapshot))
                .toByteArray(Charsets.UTF_8)
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            try {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        } finally {
            temporary.delete()
        }
    }

    private fun bookDirectory(bookId: String): File = File(
        root,
        CharacterMetadataFingerprint.sha256Utf8(bookId),
    )

    private fun encode(snapshot: CharacterMetadataCatalogSnapshot): JsonObject = JsonObject(
        linkedMapOf(
            "schemaVersion" to JsonPrimitive(snapshot.schemaVersion),
            "bookId" to JsonPrimitive(snapshot.bookId),
            "sourceSha256" to snapshot.sourceSha256.asNullableJson(),
            "analysisVersion" to JsonPrimitive(snapshot.analysisVersion),
            "revision" to JsonPrimitive(snapshot.revision),
            "updatedAtEpochMs" to JsonPrimitive(snapshot.updatedAtEpochMs),
            "complete" to JsonPrimitive(snapshot.complete),
            "chapters" to JsonArray(snapshot.chapters.map(::encodeChapter)),
            "cumulativeCharacters" to JsonArray(snapshot.cumulativeCharacters.map(::encodeCharacter)),
        ),
    )

    private fun encodeChapter(chapter: ChapterCharacterMetadata): JsonObject = JsonObject(
        linkedMapOf(
            "chapterId" to JsonPrimitive(chapter.chapterId),
            "ordinal" to JsonPrimitive(chapter.ordinal),
            "textSha256" to JsonPrimitive(chapter.textSha256),
            "contributions" to JsonArray(chapter.contributions.map { contribution ->
                JsonObject(
                    linkedMapOf(
                        "characterId" to JsonPrimitive(contribution.characterId),
                        "dialogueLineCount" to JsonPrimitive(contribution.dialogueLineCount),
                    ),
                )
            }),
        ),
    )

    private fun encodeCharacter(character: CharacterMetadataRecord): JsonObject = JsonObject(
        linkedMapOf(
            "id" to JsonPrimitive(character.id),
            "displayName" to JsonPrimitive(character.displayName),
            "aliases" to JsonArray(character.aliases.sorted().map(::JsonPrimitive)),
            "colorRole" to JsonPrimitive(character.colorRole.name),
            "dialogueLineCount" to JsonPrimitive(character.dialogueLineCount),
            "gender" to JsonPrimitive(character.gender.name),
            "genderConfidence" to JsonPrimitive(character.genderConfidence),
            "ageGroup" to JsonPrimitive(character.ageGroup.name),
            "ageConfidence" to JsonPrimitive(character.ageConfidence),
            "narrationPerspective" to JsonPrimitive(character.narrationPerspective.name),
            "perspectiveConfidence" to JsonPrimitive(character.perspectiveConfidence),
            "narratorIdentity" to character.narratorIdentity.asNullableJson(),
        ),
    )

    private fun decode(element: JsonElement): CharacterMetadataCatalogSnapshot {
        val root = element.jsonObject
        return CharacterMetadataCatalogSnapshot(
            schemaVersion = root.required("schemaVersion").jsonPrimitive.int,
            bookId = root.requiredString("bookId"),
            sourceSha256 = root.nullableString("sourceSha256"),
            analysisVersion = root.requiredString("analysisVersion"),
            revision = root.required("revision").jsonPrimitive.long,
            updatedAtEpochMs = root.required("updatedAtEpochMs").jsonPrimitive.long,
            complete = root.required("complete").jsonPrimitive.boolean,
            chapters = root.required("chapters").jsonArray.map(::decodeChapter),
            cumulativeCharacters = root.required("cumulativeCharacters").jsonArray.map(::decodeCharacter),
        )
    }

    private fun decodeChapter(element: JsonElement): ChapterCharacterMetadata {
        val chapter = element.jsonObject
        return ChapterCharacterMetadata(
            chapterId = chapter.requiredString("chapterId"),
            ordinal = chapter.required("ordinal").jsonPrimitive.int,
            textSha256 = chapter.requiredString("textSha256"),
            contributions = chapter.required("contributions").jsonArray.map { rawContribution ->
                val contribution = rawContribution.jsonObject
                CharacterDialogueContribution(
                    characterId = contribution.requiredString("characterId"),
                    dialogueLineCount = contribution.required("dialogueLineCount").jsonPrimitive.int,
                )
            },
        )
    }

    private fun decodeCharacter(element: JsonElement): CharacterMetadataRecord {
        val character = element.jsonObject
        return CharacterMetadataRecord(
            id = character.requiredString("id"),
            displayName = character.requiredString("displayName"),
            aliases = character.required("aliases").jsonArray
                .mapTo(linkedSetOf()) { it.jsonPrimitive.content },
            colorRole = CharacterColorRole.valueOf(character.requiredString("colorRole")),
            dialogueLineCount = character.required("dialogueLineCount").jsonPrimitive.int,
            gender = CharacterGender.valueOf(character.requiredString("gender")),
            genderConfidence = character.required("genderConfidence").jsonPrimitive.float,
            ageGroup = CharacterAgeGroup.valueOf(character.requiredString("ageGroup")),
            ageConfidence = character.required("ageConfidence").jsonPrimitive.float,
            narrationPerspective = NarrationPerspective.valueOf(
                character.requiredString("narrationPerspective"),
            ),
            perspectiveConfidence = character.required("perspectiveConfidence").jsonPrimitive.float,
            narratorIdentity = character.nullableString("narratorIdentity"),
        )
    }

    private fun normalize(chapter: ChapterCharacterMetadata): ChapterCharacterMetadata = chapter.copy(
        contributions = chapter.contributions
            .sortedBy(CharacterDialogueContribution::characterId),
    )

    private fun normalize(character: CharacterMetadataRecord): CharacterMetadataRecord = character.copy(
        displayName = character.displayName.trim(),
        aliases = character.aliases.mapTo(sortedSetOf(), String::trim),
        narratorIdentity = character.narratorIdentity?.trim(),
    )

    private fun mergeProfiles(
        persisted: CharacterMetadataRecord,
        supplied: CharacterMetadataRecord,
    ): CharacterMetadataRecord {
        require(persisted.id == supplied.id)
        val gender = if (
            supplied.gender != CharacterGender.UNKNOWN &&
            (
                persisted.gender == CharacterGender.UNKNOWN ||
                    supplied.genderConfidence > persisted.genderConfidence
                )
        ) {
            supplied.gender to supplied.genderConfidence
        } else {
            persisted.gender to persisted.genderConfidence
        }
        val age = if (
            supplied.ageGroup != CharacterAgeGroup.UNKNOWN &&
            (
                persisted.ageGroup == CharacterAgeGroup.UNKNOWN ||
                    supplied.ageConfidence > persisted.ageConfidence
                )
        ) {
            supplied.ageGroup to supplied.ageConfidence
        } else {
            persisted.ageGroup to persisted.ageConfidence
        }
        val perspective = if (
            supplied.narrationPerspective != NarrationPerspective.UNKNOWN &&
            (
                persisted.narrationPerspective == NarrationPerspective.UNKNOWN ||
                    supplied.perspectiveConfidence > persisted.perspectiveConfidence
                )
        ) {
            Triple(
                supplied.narrationPerspective,
                supplied.perspectiveConfidence,
                supplied.narratorIdentity ?: persisted.narratorIdentity,
            )
        } else {
            Triple(
                persisted.narrationPerspective,
                persisted.perspectiveConfidence,
                persisted.narratorIdentity ?: supplied.narratorIdentity,
            )
        }
        return supplied.copy(
            displayName = persisted.displayName,
            colorRole = persisted.colorRole,
            aliases = (persisted.aliases + supplied.aliases).toSortedSet(),
            gender = gender.first,
            genderConfidence = gender.second,
            ageGroup = age.first,
            ageConfidence = age.second,
            narrationPerspective = perspective.first,
            perspectiveConfidence = perspective.second,
            narratorIdentity = perspective.third,
        )
    }

    private fun CharacterMetadataCatalogSnapshot?.orEmptyChapters(): List<ChapterCharacterMetadata> =
        this?.chapters.orEmpty()

    private fun CharacterMetadataCatalogSnapshot?.orEmptyCharacters(): List<CharacterMetadataRecord> =
        this?.cumulativeCharacters.orEmpty()

    private fun CharacterMetadataCatalogSnapshot.semanticContent(): CharacterMetadataCatalogSnapshot = copy(
        revision = 1L,
        updatedAtEpochMs = 0L,
    )

    private fun JsonObject.required(name: String): JsonElement =
        get(name) ?: throw IllegalArgumentException("Missing JSON field: $name")

    private fun JsonObject.requiredString(name: String): String = required(name).jsonPrimitive.content

    private fun JsonObject.nullableString(name: String): String? = when (val value = required(name)) {
        JsonNull -> null
        else -> value.jsonPrimitive.contentOrNull
    }

    private fun String?.asNullableJson(): JsonElement = this?.let(::JsonPrimitive) ?: JsonNull

    private fun requireBookId(bookId: String) {
        require(bookId.isNotBlank()) { "bookId must not be blank" }
    }

    private companion object {
        const val FILE_NAME = "characters.json"
        val processMutex = Mutex()
    }
}
