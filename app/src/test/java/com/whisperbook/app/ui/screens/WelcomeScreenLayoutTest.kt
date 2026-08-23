package com.whisperbook.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WelcomeScreenLayoutTest {
    @Test
    fun shortAndNarrowPhonesUseCompactScrollableLayout() {
        assertTrue(usesCompactWelcomeLayout(320f, 503f))
        assertTrue(usesCompactWelcomeLayout(360f, 540f))
        assertTrue(usesCompactWelcomeLayout(330f, 720f))
    }

    @Test
    fun standardTallAndWidePhonesKeepApprovedPosterLayout() {
        assertFalse(usesCompactWelcomeLayout(360f, 575f))
        assertFalse(usesCompactWelcomeLayout(393f, 760f))
        assertFalse(usesCompactWelcomeLayout(412f, 820f))
    }

    @Test
    fun tallPhonesCapPosterHeightToKeepSpacingBalanced() {
        assertEquals(575f, welcomePosterHeightDp(575f), 0.001f)
        assertEquals(640f, welcomePosterHeightDp(760f), 0.001f)
        assertEquals(640f, welcomePosterHeightDp(820f), 0.001f)
    }
}
