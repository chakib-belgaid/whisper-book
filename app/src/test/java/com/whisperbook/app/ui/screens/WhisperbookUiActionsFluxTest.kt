package com.whisperbook.app.ui.screens

import com.whisperbook.app.integration.flux.WhisperbookAction
import org.junit.Assert.assertEquals
import org.junit.Test

class WhisperbookUiActionsFluxTest {
    @Test
    fun `screen action wrappers dispatch typed flux actions`() {
        val dispatched = mutableListOf<WhisperbookAction>()
        val actions = object : WhisperbookUiActions {
            override fun dispatch(action: WhisperbookAction) {
                dispatched += action
            }
        }

        actions.selectBook("book-a")
        actions.selectChapter("chapter-2")
        actions.seekByFraction(-1f)
        actions.retryPreparation()

        assertEquals(
            listOf(
                WhisperbookAction.SelectBook("book-a"),
                WhisperbookAction.SelectChapter("chapter-2"),
                WhisperbookAction.SeekBy(-15_000L),
                WhisperbookAction.ClearMessage,
                WhisperbookAction.RetryPreparation,
            ),
            dispatched,
        )
    }
}
