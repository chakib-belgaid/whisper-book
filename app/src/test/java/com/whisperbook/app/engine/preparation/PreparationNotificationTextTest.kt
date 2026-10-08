package com.whisperbook.app.engine.preparation

import com.whisperbook.app.domain.model.PreparationStage
import com.whisperbook.app.domain.model.PreparationState
import org.junit.Assert.assertEquals
import org.junit.Test

class PreparationNotificationTextTest {
    @Test
    fun `audio notification reports fully recorded chapter count`() {
        val text = preparationNotificationText(
            PreparationState(
                stage = PreparationStage.PREPARING_AUDIO,
                completedUnits = 4,
                totalUnits = 19,
                progressFraction = 4f / 19f,
                message = "Chapter Four is ready to listen",
            ),
        )

        assertEquals("Prepared 4 of 19 chapters", text)
    }

    @Test
    fun `audio notification clamps stale progress to the total`() {
        val text = preparationNotificationText(
            PreparationState(
                stage = PreparationStage.PREPARING_AUDIO,
                completedUnits = 24,
                totalUnits = 19,
            ),
        )

        assertEquals("Prepared 19 of 19 chapters", text)
    }

    @Test
    fun `pre-audio notification keeps the extraction message`() {
        val text = preparationNotificationText(
            PreparationState(
                stage = PreparationStage.READING_CHAPTERS,
                completedUnits = 8,
                totalUnits = 40,
                message = "Reading page 8 of 40",
            ),
        )

        assertEquals("Reading page 8 of 40", text)
    }

    @Test
    fun `automatic retry notification explains that preparation is still active`() {
        val text = preparationNotificationText(
            PreparationState(
                stage = PreparationStage.PREPARING_AUDIO,
                completedUnits = 4,
                totalUnits = 19,
                progressFraction = 4f / 19f,
                message = "Local generation was interrupted — retrying automatically",
                retryable = true,
            ),
        )

        assertEquals("Local generation was interrupted — retrying automatically", text)
    }

    @Test
    fun `story attribution notification reports the chapter being read`() {
        val text = preparationNotificationText(
            PreparationState(
                stage = PreparationStage.FINDING_CHARACTERS,
                completedUnits = 2,
                totalUnits = 24,
                message = "Reading chapter 3 of 24",
            ),
        )

        assertEquals("Reading chapter 3 of 24", text)
    }

    @Test
    fun `story review notification asks for the review`() {
        val text = preparationNotificationText(
            PreparationState(stage = PreparationStage.AWAITING_STORY_REVIEW),
        )

        assertEquals("Review the characters before generating voices", text)
    }
}
