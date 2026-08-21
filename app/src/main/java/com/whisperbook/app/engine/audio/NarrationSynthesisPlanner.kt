package com.whisperbook.app.engine.audio

import com.whisperbook.app.domain.NarrationTextChunker
import com.whisperbook.app.domain.SynthesisRequest
import com.whisperbook.app.domain.model.VoiceDescriptor

/** One deterministic, cacheable synthesis unit shared by WorkManager and live playback. */
data class NarrationSynthesisUnit(
    val passageId: String,
    val request: SynthesisRequest,
)

object NarrationSynthesisPlanner {
    fun plan(
        passageId: String,
        text: String,
        voice: VoiceDescriptor,
        speed: Float,
        modelVersion: String,
        sampleRate: Int,
        languageCode: String = "en",
        maxChars: Int = NarrationTextChunker.MAX_CHARS,
    ): List<NarrationSynthesisUnit> = NarrationTextChunker.chunks(passageId, text, maxChars).map { chunk ->
        val provisional = SynthesisRequest(
            text = chunk.text,
            voice = voice,
            speed = speed,
            cacheKey = "pending",
            languageCode = languageCode,
        )
        NarrationSynthesisUnit(
            passageId = chunk.id,
            request = provisional.copy(
                cacheKey = AudioCacheKey.forPassage(
                    passageId = chunk.id,
                    request = provisional,
                    modelVersion = modelVersion,
                    sampleRate = sampleRate,
                ),
            ),
        )
    }
}
