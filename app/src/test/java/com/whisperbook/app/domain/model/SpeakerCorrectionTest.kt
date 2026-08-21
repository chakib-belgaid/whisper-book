package com.whisperbook.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SpeakerCorrectionTest {
    @Test
    fun `matching key ignores case punctuation and repeated whitespace`() {
        assertEquals(
            speakerPhraseMatchKey("  WAIT... for me!  "),
            speakerPhraseMatchKey("wait for me"),
        )
    }

    @Test
    fun `matching key does not collapse different wording`() {
        assertNotEquals(
            speakerPhraseMatchKey("Wait for me"),
            speakerPhraseMatchKey("Wait here for me"),
        )
    }
}
