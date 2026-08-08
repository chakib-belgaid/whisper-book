package com.whisperbook.app.engine.tts

import com.whisperbook.app.domain.model.CharacterAgeGroup
import com.whisperbook.app.domain.model.CharacterColorRole
import com.whisperbook.app.domain.model.CharacterGender
import com.whisperbook.app.domain.model.NarrationPerspective
import com.whisperbook.app.domain.model.StoryCharacter
import com.whisperbook.app.domain.model.VocalAge
import org.junit.Assert.assertEquals
import org.junit.Test

class CharacterVoiceCasterTest {
    private val voices = SherpaKittenTtsEngine.KITTEN_VOICES

    @Test
    fun `matches known gender and story age to a compatible vocal profile`() {
        val youthful = CharacterVoiceCaster.select(
            character = character(
                gender = CharacterGender.FEMALE,
                genderConfidence = 0.95f,
                age = CharacterAgeGroup.TEEN,
                ageConfidence = 0.95f,
            ),
            voices = voices,
        )
        val older = CharacterVoiceCaster.select(
            character = character(
                gender = CharacterGender.MALE,
                genderConfidence = 0.95f,
                age = CharacterAgeGroup.OLDER_ADULT,
                ageConfidence = 0.95f,
            ),
            voices = voices,
        )

        assertEquals(CharacterGender.FEMALE, youthful.gender)
        assertEquals(VocalAge.YOUTHFUL, youthful.vocalAge)
        assertEquals(CharacterGender.MALE, older.gender)
        assertEquals(VocalAge.MATURE, older.vocalAge)
    }

    @Test
    fun `reliably inferred narrator gender overrides a conflicting voice preference`() {
        val preferred = voices.single { it.id == "bella" }
        val selected = CharacterVoiceCaster.select(
            character = character(
                role = CharacterColorRole.NARRATOR,
                gender = CharacterGender.MALE,
                genderConfidence = 0.98f,
                age = CharacterAgeGroup.OLDER_ADULT,
                ageConfidence = 0.98f,
                perspective = NarrationPerspective.THIRD_PERSON,
            ),
            voices = voices,
            preferredNarrator = preferred,
        )

        assertEquals(CharacterGender.MALE, selected.gender)
        assertEquals(VocalAge.MATURE, selected.vocalAge)
    }

    @Test
    fun `narrator without reliable gender retains the preferred narrator voice`() {
        val preferred = voices.single { it.id == "jasper" }
        val selected = CharacterVoiceCaster.select(
            character = character(
                role = CharacterColorRole.NARRATOR,
                gender = CharacterGender.UNKNOWN,
                genderConfidence = 0f,
                age = CharacterAgeGroup.OLDER_ADULT,
                ageConfidence = 0.98f,
                perspective = NarrationPerspective.FIRST_PERSON,
            ),
            voices = voices,
            preferredNarrator = preferred,
        )

        assertEquals(preferred, selected)
    }

    @Test
    fun `weak narrator gender evidence retains the preferred narrator voice`() {
        val preferred = voices.single { it.id == "bella" }
        val selected = CharacterVoiceCaster.select(
            character = character(
                role = CharacterColorRole.NARRATOR,
                gender = CharacterGender.MALE,
                genderConfidence = 0.59f,
                age = CharacterAgeGroup.OLDER_ADULT,
                ageConfidence = 0.98f,
                perspective = NarrationPerspective.FIRST_PERSON,
            ),
            voices = voices,
            preferredNarrator = preferred,
        )

        assertEquals(preferred, selected)
    }

    @Test
    fun `narrator gender without a compatible embedded voice uses the preference`() {
        val preferred = voices.single { it.id == "bella" }
        val selected = CharacterVoiceCaster.select(
            character = character(
                role = CharacterColorRole.NARRATOR,
                gender = CharacterGender.NON_BINARY,
                genderConfidence = 0.99f,
                perspective = NarrationPerspective.FIRST_PERSON,
            ),
            voices = voices,
            preferredNarrator = preferred,
        )

        assertEquals(preferred, selected)
    }

    private fun character(
        role: CharacterColorRole = CharacterColorRole.BLUE,
        gender: CharacterGender = CharacterGender.UNKNOWN,
        genderConfidence: Float = 0f,
        age: CharacterAgeGroup = CharacterAgeGroup.UNKNOWN,
        ageConfidence: Float = 0f,
        perspective: NarrationPerspective = NarrationPerspective.UNKNOWN,
    ) = StoryCharacter(
        id = "book-character-test",
        bookId = "book",
        displayName = "Test",
        aliases = setOf("Test"),
        colorRole = role,
        dialogueLineCount = 1,
        gender = gender,
        genderConfidence = genderConfidence,
        ageGroup = age,
        ageConfidence = ageConfidence,
        narrationPerspective = perspective,
        perspectiveConfidence = if (perspective == NarrationPerspective.UNKNOWN) 0f else 0.95f,
    )
}
