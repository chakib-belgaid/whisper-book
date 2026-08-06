package com.whisperbook.app.engine.attribution

import com.whisperbook.app.domain.model.CharacterAgeGroup
import com.whisperbook.app.domain.model.CharacterGender
import com.whisperbook.app.domain.model.NarrationPerspective
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterProfileInferencerTest {
    @Test(timeout = 5_000L)
    fun `bounds narration analysis for a very large book`() {
        val paragraphs = List(50_000) { index ->
            "She crossed the empty road at hour $index and watched the rain settle over the valley."
        }

        val analysis = CharacterProfileInferencer.analyzeNarration(paragraphs)

        assertEquals(NarrationPerspective.THIRD_PERSON, analysis.perspective)
        assertTrue(analysis.proseParagraphs.size < paragraphs.size)
        assertTrue(analysis.proseParagraphs.sumOf(String::length) <= 80_000)
    }

    @Test(timeout = 5_000L)
    fun `indexes aliases once when profiling a large book`() {
        val targets = List(10_000) { index ->
            CharacterProfileTarget(
                id = "character-$index",
                displayName = "Character $index",
                aliases = setOf("Character $index"),
            )
        }
        val paragraphs = List(3_000) { index ->
            "Rain crossed the empty road while the distant clock marked hour $index."
        }

        val profiles = CharacterProfileInferencer.infer(
            paragraphs = paragraphs,
            targets = targets,
            narration = NarrationAnalysis(
                perspective = NarrationPerspective.THIRD_PERSON,
                confidence = 0.8f,
                narratorIdentity = null,
                proseParagraphs = emptyList(),
            ),
        )

        assertEquals(targets.size, profiles.size)
        profiles.values.forEach { profile ->
            assertEquals(CharacterGender.UNKNOWN, profile.gender)
            assertEquals(CharacterAgeGroup.UNKNOWN, profile.ageGroup)
        }
    }
}
