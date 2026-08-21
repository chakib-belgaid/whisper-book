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
}
