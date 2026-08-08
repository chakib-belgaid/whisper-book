package com.whisperbook.app.engine.audio

import com.whisperbook.app.domain.SynthesisRequest
import java.security.MessageDigest

/**
 * Builds stable, filesystem-safe cache keys for locally synthesized audio.
 *
 * Every field which can change the emitted waveform is length-delimited before it is hashed. This
 * avoids ambiguous concatenations while keeping book text and voice choices out of filenames.
 */
object AudioCacheKey {
    private const val SCHEMA = "whisperbook-pcm16-v1"
    private val SHA_256_PATTERN = Regex("^[0-9a-f]{64}$")

    fun create(
        text: String,
        voiceId: String,
        speakerIndex: Int,
        modelVersion: String,
        speed: Float,
        sampleRate: Int,
        languageCode: String = "en",
    ): String {
        require(voiceId.isNotBlank()) { "voiceId must not be blank" }
        require(modelVersion.isNotBlank()) { "modelVersion must not be blank" }
        require(speed.isFinite() && speed > 0f) { "speed must be finite and positive" }
        require(sampleRate > 0) { "sampleRate must be positive" }
        require(languageCode.isNotBlank()) { "languageCode must not be blank" }

        val digest = MessageDigest.getInstance("SHA-256")
        digest.updateField(SCHEMA)
        digest.updateField(text)
        digest.updateField(voiceId)
        digest.updateInt(speakerIndex)
        digest.updateField(modelVersion)
        digest.updateInt(speed.toRawBits())
        digest.updateInt(sampleRate)
        // Keep existing English audio reusable after the language-pack update. Every optional
        // language receives a distinct suffix, so French/Arabic can never collide with English.
        if (languageCode != DEFAULT_LANGUAGE_CODE) digest.updateField("lang:$languageCode")
        return digest.digest().toLowerHexString()
    }

    fun fromRequest(
        request: SynthesisRequest,
        modelVersion: String,
        sampleRate: Int,
    ): String = create(
        text = request.text,
        voiceId = request.voice.id,
        speakerIndex = request.voice.speakerIndex,
        modelVersion = modelVersion,
        speed = request.speed,
        sampleRate = sampleRate,
        languageCode = request.languageCode,
    )

    /**
     * Produces a passage-scoped key for persistence models where one segment row owns one passage.
     * The nested waveform digest keeps the output deterministic without exposing the passage text.
     */
    fun forPassage(
        passageId: String,
        request: SynthesisRequest,
        modelVersion: String,
        sampleRate: Int,
    ): String {
        require(passageId.isNotBlank()) { "passageId must not be blank" }
        val waveformKey = fromRequest(request, modelVersion, sampleRate)
        return create(
            text = "$passageId\u0000$waveformKey",
            voiceId = request.voice.id,
            speakerIndex = request.voice.speakerIndex,
            modelVersion = modelVersion,
            speed = request.speed,
            sampleRate = sampleRate,
            languageCode = request.languageCode,
        )
    }

    fun isValid(value: String): Boolean = SHA_256_PATTERN.matches(value)

    private fun MessageDigest.updateField(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        updateInt(bytes.size)
        update(bytes)
    }

    private fun MessageDigest.updateInt(value: Int) {
        update((value ushr 24).toByte())
        update((value ushr 16).toByte())
        update((value ushr 8).toByte())
        update(value.toByte())
    }

    private fun ByteArray.toLowerHexString(): String {
        val encoded = CharArray(size * 2)
        forEachIndexed { index, byte ->
            val value = byte.toInt() and 0xff
            encoded[index * 2] = HEX_DIGITS[value ushr 4]
            encoded[index * 2 + 1] = HEX_DIGITS[value and 0x0f]
        }
        return encoded.concatToString()
    }

    private const val HEX_DIGITS = "0123456789abcdef"
    private const val DEFAULT_LANGUAGE_CODE = "en"
}
