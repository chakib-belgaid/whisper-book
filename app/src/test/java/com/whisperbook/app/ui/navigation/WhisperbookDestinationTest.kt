package com.whisperbook.app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WhisperbookDestinationTest {
    @Test
    fun parameterizedRoutes_areDeterministic() {
        assertEquals("book/moonlit", WhisperbookDestination.BookDetails.route())
        assertEquals("book/moonlit/cast", WhisperbookDestination.VoiceCast.route())
        assertEquals("book/moonlit/chapter/chapter-7", WhisperbookDestination.CurrentChapter.route())
    }

    @Test
    fun bottomBarExcludesFocusedFlows() {
        assertTrue(WhisperbookDestination.Processing.route !in WhisperbookDestination.bottomBarRoutes)
        assertTrue(WhisperbookDestination.VoiceCast.route !in WhisperbookDestination.bottomBarRoutes)
    }
}
