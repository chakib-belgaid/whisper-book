package com.whisperbook.app.ui.screens

import androidx.compose.runtime.Immutable
import com.whisperbook.app.domain.model.Chapter
import com.whisperbook.app.domain.model.CharacterAgeGroup
import com.whisperbook.app.domain.model.CharacterColorRole
import com.whisperbook.app.domain.model.CharacterGender
import com.whisperbook.app.domain.model.NarrationPerspective
import com.whisperbook.app.domain.model.StoryCharacter
import com.whisperbook.app.engine.tts.CharacterVoiceCaster

/** One character bible entry, derived from the attributed passages of the selected chapters. */
@Immutable
data class StoryCharacterUi(
    val id: String,
    val name: String,
    val role: SpeakerRole,
    val portraitRes: Int,
    val isNarrator: Boolean,
    val aliases: List<String>,
    val gender: CharacterGender,
    val ageGroup: CharacterAgeGroup,
    /** A known gender or age was inferred from weaker evidence than casting relies on. */
    val profileUncertain: Boolean,
    val narrationPerspective: NarrationPerspective,
    val lineCount: Int,
    /** Original chapter numbers, in listening order, where this character has a passage. */
    val chapterNumbers: List<Int>,
    val firstChapterId: String?,
    val firstPassageId: String?,
    val sampleLine: String?,
)

/** A selected chapter projected for the story preview, using the read-along passage cards. */
@Immutable
data class StoryChapterUi(
    val id: String,
    val number: Int,
    val listeningPosition: Int,
    val title: String,
    val passages: List<PassageUi>,
)

internal data class StoryPreviewProjection(
    val characters: List<StoryCharacterUi> = emptyList(),
    val chapters: List<StoryChapterUi> = emptyList(),
    val cast: List<CastMemberUi> = emptyList(),
)

internal const val STORY_SAMPLE_LINE_MAX_CHARS = 140

/**
 * Builds the character bible. Line counts, appearances and samples come from the current passages
 * rather than the stored attribution totals, so a speaker correction is reflected immediately.
 */
internal fun projectStoryBible(
    characters: List<StoryCharacter>,
    chapters: List<Chapter>,
): List<StoryCharacterUi> {
    val usage = mutableMapOf<String, StoryCharacterUsage>()
    chapters.forEach { chapter ->
        chapter.passages.forEach { passage ->
            val speaker = usage.getOrPut(passage.speakerId) { StoryCharacterUsage() }
            speaker.lineCount += 1
            speaker.chapterNumbers += chapter.ordinal + 1
            if (speaker.firstPassageId == null) {
                speaker.firstPassageId = passage.id
                speaker.firstChapterId = chapter.id
                speaker.sampleLine = passage.text.toSampleLine()
            }
        }
    }
    return characters.map { character ->
        val speaker = usage[character.id]
        val isNarrator = character.colorRole == CharacterColorRole.NARRATOR
        StoryCharacterUi(
            id = character.id,
            name = character.displayName,
            role = character.colorRole.toSpeakerRole(),
            portraitRes = characterPortraitRes(character.colorRole),
            isNarrator = isNarrator,
            aliases = character.aliases
                .filterNot { it.isBlank() || it.equals(character.displayName, ignoreCase = true) }
                .sorted(),
            gender = character.gender,
            ageGroup = character.ageGroup,
            profileUncertain = character.hasWeakProfileEvidence(),
            narrationPerspective = character.narrationPerspective,
            lineCount = speaker?.lineCount ?: 0,
            chapterNumbers = speaker?.chapterNumbers?.toList().orEmpty(),
            firstChapterId = speaker?.firstChapterId,
            firstPassageId = speaker?.firstPassageId,
            sampleLine = speaker?.sampleLine,
        )
    }.sortedWith(
        compareBy<StoryCharacterUi>(
            { !it.isNarrator },
            { -it.lineCount },
            { it.name.lowercase() },
            { it.id },
        ),
    )
}

/** Projects every selected chapter with the same passage grouping as the read-along reader. */
internal fun projectStoryChapters(
    chapters: List<Chapter>,
    characters: List<StoryCharacter>,
    maxChars: Int,
): List<StoryChapterUi> = chapters.mapIndexed { index, chapter ->
    val playbackPassages = projectPlaybackPassages(chapter, characters, maxChars)
    StoryChapterUi(
        id = chapter.id,
        number = chapter.ordinal + 1,
        listeningPosition = index + 1,
        title = chapter.title,
        passages = projectReaderPassages(chapter, playbackPassages),
    )
}

/** Every character can be chosen in a correction, even before any voice has been cast. */
internal fun projectStoryCast(bible: List<StoryCharacterUi>): List<CastMemberUi> = bible.map { character ->
    CastMemberUi(
        id = character.id,
        character = character.name,
        voice = "",
        confidence = 0,
        lines = character.lineCount,
        portraitRes = character.portraitRes,
        role = character.role,
    )
}

internal fun projectStoryPreview(
    chapters: List<Chapter>,
    characters: List<StoryCharacter>,
    maxChars: Int,
): StoryPreviewProjection {
    if (chapters.isEmpty()) return StoryPreviewProjection()
    val bible = projectStoryBible(characters, chapters)
    return StoryPreviewProjection(
        characters = bible,
        chapters = projectStoryChapters(chapters, characters, maxChars),
        cast = projectStoryCast(bible),
    )
}

private class StoryCharacterUsage {
    var lineCount: Int = 0
    val chapterNumbers: LinkedHashSet<Int> = linkedSetOf()
    var firstChapterId: String? = null
    var firstPassageId: String? = null
    var sampleLine: String? = null
}

private fun StoryCharacter.hasWeakProfileEvidence(): Boolean =
    (gender != CharacterGender.UNKNOWN && genderConfidence < CharacterVoiceCaster.PROFILE_THRESHOLD) ||
        (ageGroup != CharacterAgeGroup.UNKNOWN && ageConfidence < CharacterVoiceCaster.PROFILE_THRESHOLD)

private fun String.toSampleLine(): String {
    val normalized = trim().replace(WHITESPACE, " ")
    if (normalized.length <= STORY_SAMPLE_LINE_MAX_CHARS) return normalized
    val clipped = normalized.take(STORY_SAMPLE_LINE_MAX_CHARS)
    val wordBoundary = clipped.lastIndexOf(' ').takeIf { it >= STORY_SAMPLE_LINE_MAX_CHARS / 2 }
    return (wordBoundary?.let(clipped::take) ?: clipped).trimEnd() + "…"
}

private val WHITESPACE = Regex("\\s+")
