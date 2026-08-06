package com.whisperbook.app.engine.tts

import com.whisperbook.app.domain.model.CharacterAgeGroup
import com.whisperbook.app.domain.model.CharacterColorRole
import com.whisperbook.app.domain.model.CharacterGender
import com.whisperbook.app.domain.model.NarrationPerspective
import com.whisperbook.app.domain.model.StoryCharacter
import com.whisperbook.app.domain.model.VocalAge
import com.whisperbook.app.domain.model.VoiceDescriptor
import kotlin.math.abs

/** Matches confidence-bearing textual traits to the closest embedded vocal profile. */
object CharacterVoiceCaster {
    fun select(
        character: StoryCharacter,
        voices: List<VoiceDescriptor>,
        preferredNarrator: VoiceDescriptor? = null,
        alreadyUsedVoiceIds: Set<String> = emptySet(),
    ): VoiceDescriptor {
        require(voices.isNotEmpty()) { "No voices are available for automatic casting" }
        val isNarrator = character.colorRole == CharacterColorRole.NARRATOR
        val narratorHasProfile = character.narrationPerspective == NarrationPerspective.FIRST_PERSON &&
            (
                character.gender != CharacterGender.UNKNOWN && character.genderConfidence >= PROFILE_THRESHOLD ||
                    character.ageGroup != CharacterAgeGroup.UNKNOWN && character.ageConfidence >= PROFILE_THRESHOLD
                )
        if (isNarrator && !narratorHasProfile) return preferredNarrator?.takeIf { it in voices } ?: voices.first()

        return voices.maxWithOrNull(
            compareBy<VoiceDescriptor> { voice ->
                profileScore(character, voice) +
                    if (voice.id !in alreadyUsedVoiceIds) DIVERSITY_BONUS else 0f
            }.thenBy { voice -> deterministicTieBreak(character.id, voice.id) },
        ) ?: voices.first()
    }

    private fun profileScore(character: StoryCharacter, voice: VoiceDescriptor): Float {
        val genderScore = when (character.gender) {
            CharacterGender.FEMALE -> when (voice.gender) {
                CharacterGender.FEMALE -> GENDER_MATCH
                CharacterGender.UNKNOWN, CharacterGender.NON_BINARY -> 0f
                CharacterGender.MALE -> -GENDER_MISMATCH
            }
            CharacterGender.MALE -> when (voice.gender) {
                CharacterGender.MALE -> GENDER_MATCH
                CharacterGender.UNKNOWN, CharacterGender.NON_BINARY -> 0f
                CharacterGender.FEMALE -> -GENDER_MISMATCH
            }
            CharacterGender.NON_BINARY, CharacterGender.UNKNOWN -> 0f
        } * character.genderConfidence.coerceIn(0f, 1f)

        val desiredVocalAge = when (character.ageGroup) {
            CharacterAgeGroup.CHILD, CharacterAgeGroup.TEEN -> VocalAge.YOUTHFUL
            CharacterAgeGroup.YOUNG_ADULT -> VocalAge.YOUTHFUL
            CharacterAgeGroup.ADULT -> VocalAge.ADULT
            CharacterAgeGroup.OLDER_ADULT -> VocalAge.MATURE
            CharacterAgeGroup.UNKNOWN -> VocalAge.UNKNOWN
        }
        val ageScore = when {
            desiredVocalAge == VocalAge.UNKNOWN || voice.vocalAge == VocalAge.UNKNOWN -> 0f
            voice.vocalAge == desiredVocalAge -> AGE_MATCH
            areAdjacent(desiredVocalAge, voice.vocalAge) -> AGE_ADJACENT
            else -> -AGE_MISMATCH
        } * character.ageConfidence.coerceIn(0f, 1f)
        return genderScore + ageScore
    }

    private fun areAdjacent(first: VocalAge, second: VocalAge): Boolean {
        val ordered = listOf(VocalAge.YOUTHFUL, VocalAge.ADULT, VocalAge.MATURE)
        val firstIndex = ordered.indexOf(first)
        val secondIndex = ordered.indexOf(second)
        return firstIndex >= 0 && secondIndex >= 0 && abs(firstIndex - secondIndex) == 1
    }

    private fun deterministicTieBreak(characterId: String, voiceId: String): Int =
        Math.floorMod("$characterId|$voiceId".hashCode(), 10_000)

    private const val PROFILE_THRESHOLD = 0.60f
    private const val GENDER_MATCH = 40f
    private const val GENDER_MISMATCH = 34f
    private const val AGE_MATCH = 24f
    private const val AGE_ADJACENT = 5f
    private const val AGE_MISMATCH = 14f
    private const val DIVERSITY_BONUS = 4f
}
