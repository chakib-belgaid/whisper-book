package com.whisperbook.app.integration.flux

internal data class WhisperbookFluxState(
    val selectedBookId: String? = null,
    val selectedChapterId: String? = null,
    val loadingChapterId: String? = null,
    val operation: OperationState = OperationState(),
    val preparationSchedulingErrors: Map<String, String> = emptyMap(),
    val storageRefreshVersion: Long = 0L,
    val voiceRetentionRefreshVersion: Long = 0L,
)

internal sealed interface WhisperbookMutation {
    data class BookSelected(
        val bookId: String?,
        val chapterId: String?,
    ) : WhisperbookMutation

    data class ChapterSelected(val chapterId: String?) : WhisperbookMutation
    data class ChapterLoadingChanged(val chapterId: String?) : WhisperbookMutation
    data class OperationChanged(val operation: OperationState) : WhisperbookMutation
    data class SchedulingErrorsRetained(val bookIds: Set<String>) : WhisperbookMutation
    data class SchedulingErrorRecorded(val bookId: String, val message: String) : WhisperbookMutation
    data class SchedulingErrorCleared(val bookId: String) : WhisperbookMutation
    data object StorageRefreshRequested : WhisperbookMutation
    data object VoiceRetentionRefreshRequested : WhisperbookMutation
}

internal val whisperbookReducer = FluxReducer<WhisperbookFluxState, WhisperbookMutation> { state, action ->
    when (action) {
        is WhisperbookMutation.BookSelected -> state.copy(
            selectedBookId = action.bookId,
            selectedChapterId = action.chapterId,
        )
        is WhisperbookMutation.ChapterSelected -> state.copy(selectedChapterId = action.chapterId)
        is WhisperbookMutation.ChapterLoadingChanged -> state.copy(loadingChapterId = action.chapterId)
        is WhisperbookMutation.OperationChanged -> state.copy(operation = action.operation)
        is WhisperbookMutation.SchedulingErrorsRetained -> state.copy(
            preparationSchedulingErrors = state.preparationSchedulingErrors
                .filterKeys(action.bookIds::contains),
        )
        is WhisperbookMutation.SchedulingErrorRecorded -> state.copy(
            preparationSchedulingErrors = state.preparationSchedulingErrors +
                (action.bookId to action.message),
        )
        is WhisperbookMutation.SchedulingErrorCleared -> state.copy(
            preparationSchedulingErrors = state.preparationSchedulingErrors - action.bookId,
        )
        WhisperbookMutation.StorageRefreshRequested -> state.copy(
            storageRefreshVersion = state.storageRefreshVersion + 1L,
        )
        WhisperbookMutation.VoiceRetentionRefreshRequested -> state.copy(
            voiceRetentionRefreshVersion = state.voiceRetentionRefreshVersion + 1L,
        )
    }
}

internal data class OperationState(
    val isBusy: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val progressFraction: Float? = null,
    val kind: OperationKind? = null,
    val targetBookId: String? = null,
)

internal enum class OperationKind { BOOK_MP3_EXPORT }
