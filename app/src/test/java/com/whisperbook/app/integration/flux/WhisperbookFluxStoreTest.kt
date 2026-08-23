package com.whisperbook.app.integration.flux

import org.junit.Assert.assertEquals
import org.junit.Test

class WhisperbookFluxStoreTest {
    private val store = FluxStore(WhisperbookFluxState(), whisperbookReducer)

    @Test
    fun `selection actions update one immutable state tree`() {
        store.dispatch(WhisperbookMutation.BookSelected("book-a", "chapter-1"))
        val selectedBook = store.currentState

        store.dispatch(WhisperbookMutation.ChapterSelected("chapter-2"))

        assertEquals("book-a", selectedBook.selectedBookId)
        assertEquals("chapter-1", selectedBook.selectedChapterId)
        assertEquals("book-a", store.currentState.selectedBookId)
        assertEquals("chapter-2", store.currentState.selectedChapterId)
    }

    @Test
    fun `scheduling errors are reduced by book id`() {
        store.dispatch(WhisperbookMutation.SchedulingErrorRecorded("book-a", "A failed"))
        store.dispatch(WhisperbookMutation.SchedulingErrorRecorded("book-b", "B failed"))
        store.dispatch(WhisperbookMutation.SchedulingErrorCleared("book-a"))

        assertEquals(mapOf("book-b" to "B failed"), store.currentState.preparationSchedulingErrors)

        store.dispatch(WhisperbookMutation.SchedulingErrorsRetained(setOf("book-a")))

        assertEquals(emptyMap<String, String>(), store.currentState.preparationSchedulingErrors)
    }

    @Test
    fun `refresh requests are monotonic reducer transitions`() {
        repeat(2) { store.dispatch(WhisperbookMutation.StorageRefreshRequested) }
        store.dispatch(WhisperbookMutation.VoiceRetentionRefreshRequested)

        assertEquals(2L, store.currentState.storageRefreshVersion)
        assertEquals(1L, store.currentState.voiceRetentionRefreshVersion)
    }

    @Test
    fun `operation result replaces the previous operation atomically`() {
        store.dispatch(
            WhisperbookMutation.OperationChanged(
                OperationState(isBusy = true, statusMessage = "Working"),
            ),
        )
        store.dispatch(
            WhisperbookMutation.OperationChanged(
                OperationState(errorMessage = "Could not finish"),
            ),
        )

        assertEquals(
            OperationState(errorMessage = "Could not finish"),
            store.currentState.operation,
        )
    }
}
