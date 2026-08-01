package com.whisperbook.app.engine.attribution

import com.whisperbook.app.domain.model.BuiltInCharacters
import com.whisperbook.app.domain.model.CharacterColorRole
import com.whisperbook.app.domain.model.StoryCharacter
import java.util.Locale

data class KnownCharacterSeed(
    val displayName: String,
    val aliases: Set<String> = emptySet(),
)

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

    init {
        val narrator = MutableCharacter(
            id = BuiltInCharacters.NARRATOR_ID,
            displayName = "Narrator",
            aliases = linkedSetOf("Narrator"),
            colorRole = CharacterColorRole.NARRATOR,
        )
        byId[narrator.id] = narrator
        aliasToId[aliasKey(narrator.displayName)] = narrator.id
        seeds.forEach { register(it.displayName, it.aliases) }
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

    fun resolve(mention: String): String? {
        val key = aliasKey(mention)
        return aliasToId[key] ?: aliasToId[key.removePrefix("the ")]
    }

    fun displayName(id: String): String? = byId[id]?.displayName

    fun incrementDialogue(id: String) {
        if (id != BuiltInCharacters.NARRATOR_ID) byId[id]?.let { it.dialogueLineCount++ }
    }

    fun characters(): List<StoryCharacter> = byId.values.map { character ->
        StoryCharacter(
            id = character.id,
            bookId = bookId,
            displayName = character.displayName,
            aliases = character.aliases.filter(String::isNotBlank).toSet(),
            colorRole = character.colorRole,
            dialogueLineCount = character.dialogueLineCount,
        )
    }

    private fun addAliases(id: String, aliases: Set<String>) {
        val character = byId[id] ?: return
        aliases.filter(String::isNotBlank).forEach { alias ->
            character.aliases += alias
            aliasToId.putIfAbsent(aliasKey(alias), id)
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
