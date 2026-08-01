package com.whisperbook.app.engine.tts

import kotlin.math.roundToInt

internal fun floatsToPcm16(samples: FloatArray): ShortArray = ShortArray(samples.size) { index ->
    val sample = samples[index]
    when {
        sample.isNaN() -> 0
        sample >= 1f -> Short.MAX_VALUE
        sample <= -1f -> Short.MIN_VALUE
        sample < 0f -> (sample * 32_768f).roundToInt().toShort()
        else -> (sample * 32_767f).roundToInt().toShort()
    }
}

internal fun pcmDurationMs(sampleCount: Int, sampleRate: Int): Long {
    require(sampleCount >= 0) { "Sample count must not be negative" }
    require(sampleRate > 0) { "Sample rate must be positive" }
    return sampleCount.toLong() * 1_000L / sampleRate
}
