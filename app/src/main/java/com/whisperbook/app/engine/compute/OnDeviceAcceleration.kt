package com.whisperbook.app.engine.compute

import kotlinx.coroutines.CancellationException

/**
 * Execution paths which Whisperbook can select explicitly.
 *
 * NNAPI is the Android compatibility layer that can dispatch supported operators to a vendor GPU,
 * DSP, or NPU. The exact accelerator remains a driver decision, so diagnostics deliberately say
 * `nnapi` instead of claiming a specific chip ran the graph.
 */
internal enum class NeuralExecutionBackend(val diagnosticName: String) {
    NNAPI("nnapi"),
    CPU("cpu"),
}

internal object OnDeviceAccelerationPolicy {
    /**
     * Accelerator-only NNAPI routing is available from API 29. Unsupported graph partitions still
     * run through ONNX Runtime's CPU kernels; older Android releases use CPU directly.
     */
    fun ttsCandidates(androidSdk: Int): List<NeuralExecutionBackend> = if (androidSdk >= 29) {
        listOf(NeuralExecutionBackend.NNAPI, NeuralExecutionBackend.CPU)
    } else {
        listOf(NeuralExecutionBackend.CPU)
    }
}

internal data class LoadedBackend<T>(
    val backend: NeuralExecutionBackend,
    val value: T,
    val earlierFailures: List<BackendFailure>,
)

internal data class BackendFailure(
    val backend: NeuralExecutionBackend,
    val cause: Throwable,
)

/** Loads the first usable backend and retains failures so fallback is visible in diagnostics. */
internal inline fun <T> loadFirstAvailableBackend(
    candidates: List<NeuralExecutionBackend>,
    load: (NeuralExecutionBackend) -> T,
): LoadedBackend<T> {
    require(candidates.isNotEmpty()) { "At least one inference backend is required" }
    val failures = mutableListOf<BackendFailure>()
    candidates.distinct().forEach { backend ->
        try {
            return LoadedBackend(backend, load(backend), failures.toList())
        } catch (failure: Throwable) {
            failure.throwIfFatalOrCancelled()
            failures += BackendFailure(backend, failure)
        }
    }
    val terminal = IllegalStateException("No on-device inference backend could be initialized")
    failures.forEach { terminal.addSuppressed(it.cause) }
    throw terminal
}

/** Never reinterpret cancellation or a broken VM as a recoverable accelerator failure. */
internal fun Throwable.throwIfFatalOrCancelled() {
    when (this) {
        is CancellationException,
        is VirtualMachineError,
        is ThreadDeath,
        -> throw this
    }
}
