package com.whisperbook.app.integration.flux

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Pure state transition used by a [FluxStore]. */
fun interface FluxReducer<State, Action> {
    fun reduce(state: State, action: Action): State
}

/**
 * Small synchronous Flux store.
 *
 * Dispatch is intentionally side-effect free: effects live in the ViewModel and report their
 * results back as actions. A synchronous reducer keeps a sequence of related dispatches ordered
 * without introducing another coroutine queue.
 */
class FluxStore<State, Action>(
    initialState: State,
    private val reducer: FluxReducer<State, Action>,
) {
    private val mutableState = MutableStateFlow(initialState)

    val state: StateFlow<State> = mutableState.asStateFlow()
    val currentState: State get() = mutableState.value

    fun dispatch(action: Action) {
        mutableState.update { current -> reducer.reduce(current, action) }
    }
}
