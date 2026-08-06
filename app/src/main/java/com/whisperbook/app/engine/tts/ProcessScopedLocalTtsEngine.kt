package com.whisperbook.app.engine.tts

import com.whisperbook.app.domain.LocalTtsEngine
import com.whisperbook.app.domain.SynthesisRequest
import com.whisperbook.app.domain.SynthesisResult
import com.whisperbook.app.domain.model.VoiceDescriptor

/**
 * Keeps the expensive native model alive while short-lived preparation/playback clients borrow it.
 * Client [close] calls intentionally release only their logical lease; the app container owns the
 * one real [shutdown] call at the process boundary.
 */
internal class ProcessScopedLocalTtsEngine(
    private val delegate: LocalTtsEngine,
) : LocalTtsEngine {
    override suspend fun warmUp(): Result<Unit> = delegate.warmUp()

    override suspend fun voices(): List<VoiceDescriptor> = delegate.voices()

    override suspend fun synthesize(request: SynthesisRequest): Result<SynthesisResult> =
        delegate.synthesize(request)

    override fun close() = Unit

    fun shutdown() = delegate.close()
}
