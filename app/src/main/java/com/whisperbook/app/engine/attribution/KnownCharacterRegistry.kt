package com.whisperbook.app.engine.attribution

import com.whisperbook.app.domain.model.BuiltInCharacters
import com.whisperbook.app.domain.model.CharacterAgeGroup
import com.whisperbook.app.domain.model.CharacterColorRole
import com.whisperbook.app.domain.model.CharacterGender
import com.whisperbook.app.domain.model.NarrationPerspective
import com.whisperbook.app.domain.model.StoryCharacter
import java.util.Locale

data class KnownCharacterSeed(
    val displayName: String,
    val aliases: Set<String> = emptySet(),
    val id: String? = null,
    val colorRole: CharacterColorRole? = null,
    val dialogueLineCount: Int = 0,
    val gender: CharacterGender = CharacterGender.UNKNOWN,
    val genderConfidence: Float = 0f,
    val ageGroup: CharacterAgeGroup = CharacterAgeGroup.UNKNOWN,
    val ageConfidence: Float = 0f,
    val narrationPerspective: NarrationPerspective = NarrationPerspective.UNKNOWN,
    val perspectiveConfidence: Float = 0f,
    val narratorIdentity: String? = null,
) {
    companion object {
        fun from(character: StoryCharacter): KnownCharacterSeed = KnownCharacterSeed(
            displayName = character.displayName,
            aliases = character.aliases,
            id = character.id,
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

/** Book-scoped character and alias registry used by the deterministic heuristic engine. */
class KnownCharacterRegistry(
    private val bookId: String,
    seeds: List<KnownCharacterSeed> = emptyList(),
) {
    private data class MutableCharacter(
        val id: String,
        val displayName: String,
        val aliases: LinkedHashSet<String>,
        val colorRole: CharacterColorRole,
        var dialogueLineCount: Int = 0,
        var gender: CharacterGender = CharacterGender.UNKNOWN,
        var genderConfidence: Float = 0f,
        var ageGroup: CharacterAgeGroup = CharacterAgeGroup.UNKNOWN,
        var ageConfidence: Float = 0f,
        var narrationPerspective: NarrationPerspective = NarrationPerspective.UNKNOWN,
        var perspectiveConfidence: Float = 0f,
        var narratorIdentity: String? = null,
    )

    private val byId = linkedMapOf<String, MutableCharacter>()
    private val aliasToId = linkedMapOf<String, String>()
    private val colorRoles = listOf(
        CharacterColorRole.ELARA_BURGUNDY,
        CharacterColorRole.FOX_ORANGE,
        CharacterColorRole.BLUE,
        CharacterColorRole.BURGUNDY,
        CharacterColorRole.ORANGE,
    )

    val narratorId: String

    init {
        val narratorSeed = seeds.firstOrNull { seed ->
            seed.colorRole == CharacterColorRole.NARRATOR ||
                seed.id == BuiltInCharacters.NARRATOR_ID
        }
        narratorId = narratorSeed?.id?.takeIf(String::isNotBlank)
            ?: BuiltInCharacters.NARRATOR_ID
        val narrator = MutableCharacter(
            id = narratorId,
            displayName = narratorSeed?.displayName?.takeIf(String::isNotBlank) ?: "Narrator",
            aliases = linkedSetOf("Narrator").apply {
                narratorSeed?.displayName?.takeIf(String::isNotBlank)?.let(::add)
                narratorSeed?.aliases?.filter(String::isNotBlank)?.let(::addAll)
            },
            colorRole = CharacterColorRole.NARRATOR,
            dialogueLineCount = narratorSeed?.dialogueLineCount?.coerceAtLeast(0) ?: 0,
            gender = narratorSeed?.gender ?: CharacterGender.UNKNOWN,
            genderConfidence = narratorSeed?.genderConfidence?.coerceIn(0f, 1f) ?: 0f,
            ageGroup = narratorSeed?.ageGroup ?: CharacterAgeGroup.UNKNOWN,
            ageConfidence = narratorSeed?.ageConfidence?.coerceIn(0f, 1f) ?: 0f,
            narrationPerspective = narratorSeed?.narrationPerspective
                ?: NarrationPerspective.UNKNOWN,
            perspectiveConfidence = narratorSeed?.perspectiveConfidence?.coerceIn(0f, 1f) ?: 0f,
            narratorIdentity = narratorSeed?.narratorIdentity,
        )
        byId[narrator.id] = narrator
        narrator.aliases.forEach { alias -> aliasToId[aliasKey(alias)] = narrator.id }
        seeds.filterNot { it === narratorSeed }.forEach(::register)
    }

    fun register(displayName: String, aliases: Set<String> = emptySet()): String? {
        val cleaned = cleanMention(displayName) ?: return null
        resolve(cleaned)?.let { existing ->
            addAliases(existing, aliases + displayName)
            return existing
        }
        val slug = cleaned.lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), "-")
            .trim('-')
            .ifBlank { "speaker" }
        var id = "$bookId-character-$slug"
        var suffix = 2
        while (id in byId) id = "$bookId-character-$slug-${suffix++}"
        val allAliases = linkedSetOf(cleaned, displayName).apply { addAll(aliases) }
        val character = MutableCharacter(
            id = id,
            displayName = cleaned,
            aliases = allAliases,
            colorRole = colorRoles[(byId.size - 1) % colorRoles.size],
        )
        byId[id] = character
        allAliases.forEach { alias -> aliasKey(alias).takeIf(String::isNotBlank)?.let { aliasToId.putIfAbsent(it, id) } }
        return id
    }

    private fun register(seed: KnownCharacterSeed): String? {
        val cleaned = cleanMention(seed.displayName) ?: return null
        val stableId = seed.id?.takeIf(String::isNotBlank)
        stableId?.let(byId::get)?.let { existing ->
            addAliases(existing.id, seed.aliases + seed.displayName)
            mergeProfile(existing, seed)
            return existing.id
        }

        if (stableId == null) return register(seed.displayName, seed.aliases)

        val allAliases = linkedSetOf(cleaned, seed.displayName).apply { addAll(seed.aliases) }
        val character = MutableCharacter(
            id = stableId,
            displayName = cleaned,
            aliases = allAliases,
            colorRole = seed.colorRole ?: colorRoles[(byId.size - 1) % colorRoles.size],
            dialogueLineCount = seed.dialogueLineCount.coerceAtLeast(0),
            gender = seed.gender,
            genderConfidence = seed.genderConfidence.coerceIn(0f, 1f),
            ageGroup = seed.ageGroup,
            ageConfidence = seed.ageConfidence.coerceIn(0f, 1f),
            narrationPerspective = seed.narrationPerspective,
            perspectiveConfidence = seed.perspectiveConfidence.coerceIn(0f, 1f),
            narratorIdentity = seed.narratorIdentity,
        )
        byId[stableId] = character
        allAliases.forEach { alias ->
            aliasKey(alias).takeIf(String::isNotBlank)?.let { aliasToId.putIfAbsent(it, stableId) }
        }
        return stableId
    }

    fun resolve(mention: String): String? {
        val key = aliasKey(mention)
        return aliasToId[key] ?: aliasToId[key.removePrefix("the ")]
    }

    fun displayName(id: String): String? = byId[id]?.displayName

    internal fun addNarratorAlias(alias: String) {
        addAliases(narratorId, setOf(alias))
    }

    internal fun profileTargets(): List<CharacterProfileTarget> = byId.values.map { character ->
        CharacterProfileTarget(
            id = character.id,
            displayName = character.displayName,
            aliases = character.aliases.filter(String::isNotBlank).toSet(),
        )
    }

    internal fun applyProfile(id: String, profile: InferredCharacterProfile) {
        byId[id]?.apply {
            val inferredGenderConfidence = profile.genderConfidence.coerceIn(0f, 1f)
            if (
                profile.gender != CharacterGender.UNKNOWN &&
                (gender == CharacterGender.UNKNOWN || inferredGenderConfidence > genderConfidence)
            ) {
                gender = profile.gender
                genderConfidence = inferredGenderConfidence
            }
            val inferredAgeConfidence = profile.ageConfidence.coerceIn(0f, 1f)
            if (
                profile.ageGroup != CharacterAgeGroup.UNKNOWN &&
                (ageGroup == CharacterAgeGroup.UNKNOWN || inferredAgeConfidence > ageConfidence)
            ) {
                ageGroup = profile.ageGroup
                ageConfidence = inferredAgeConfidence
            }
            val inferredPerspectiveConfidence = profile.perspectiveConfidence.coerceIn(0f, 1f)
            val replacesPerspective =
                profile.narrationPerspective != NarrationPerspective.UNKNOWN &&
                (
                    narrationPerspective == NarrationPerspective.UNKNOWN ||
                        inferredPerspectiveConfidence > perspectiveConfidence
                    )
            if (replacesPerspective) {
                narrationPerspective = profile.narrationPerspective
                perspectiveConfidence = inferredPerspectiveConfidence
            }
            profile.narratorIdentity?.takeIf(String::isNotBlank)?.let {
                if (narratorIdentity.isNullOrBlank() || replacesPerspective) {
                    narratorIdentity = it
                }
            }
        }
    }

    fun incrementDialogue(id: String) {
        if (id != narratorId) byId[id]?.let { it.dialogueLineCount++ }
    }

    fun characters(): List<StoryCharacter> = byId.values.map { character ->
        StoryCharacter(
            id = character.id,
            bookId = bookId,
            displayName = character.displayName,
            aliases = character.aliases.filter(String::isNotBlank).toSet(),
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

    private fun addAliases(id: String, aliases: Set<String>) {
        val character = byId[id] ?: return
        aliases.filter(String::isNotBlank).forEach { alias ->
            character.aliases += alias
            aliasToId.putIfAbsent(aliasKey(alias), id)
        }
    }

    private fun mergeProfile(character: MutableCharacter, seed: KnownCharacterSeed) {
        character.dialogueLineCount = maxOf(character.dialogueLineCount, seed.dialogueLineCount)
        if (
            seed.gender != CharacterGender.UNKNOWN &&
            (character.gender == CharacterGender.UNKNOWN || seed.genderConfidence > character.genderConfidence)
        ) {
            character.gender = seed.gender
            character.genderConfidence = seed.genderConfidence.coerceIn(0f, 1f)
        }
        if (
            seed.ageGroup != CharacterAgeGroup.UNKNOWN &&
            (character.ageGroup == CharacterAgeGroup.UNKNOWN || seed.ageConfidence > character.ageConfidence)
        ) {
            character.ageGroup = seed.ageGroup
            character.ageConfidence = seed.ageConfidence.coerceIn(0f, 1f)
        }
        if (
            seed.narrationPerspective != NarrationPerspective.UNKNOWN &&
            (
                character.narrationPerspective == NarrationPerspective.UNKNOWN ||
                    seed.perspectiveConfidence > character.perspectiveConfidence
                )
        ) {
            character.narrationPerspective = seed.narrationPerspective
            character.perspectiveConfidence = seed.perspectiveConfidence.coerceIn(0f, 1f)
            character.narratorIdentity = seed.narratorIdentity ?: character.narratorIdentity
        }
    }

    companion object {
        private val rejectedMentions = setOf(
            "he", "she", "they", "it", "i", "we", "you", "someone", "everyone",
            "nobody", "nothing", "something", "then", "and", "but",
        )

        fun cleanMention(raw: String): String? {
            var value = raw
                .trim()
                .trim(',', '.', ':', ';', '!', '?', '“', '”', '"', '«', '»')
                .replace(Regex("^(?i:the)\\s+"), "")
                .replace(Regex("\\s+"), " ")
            val words = value.split(' ').toMutableList()
            while (words.size > 1 && words.first().lowercase(Locale.ROOT) in setOf("then", "and", "but", "dear")) {
                words.removeAt(0)
            }
            value = words.joinToString(" ")
            if (value.isBlank() || value.lowercase(Locale.ROOT) in rejectedMentions) return null
            return value.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }

        private fun aliasKey(raw: String): String = raw
            .lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}'’]+"), " ")
            .trim()
    }
}
