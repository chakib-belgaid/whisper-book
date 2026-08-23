package com.whisperbook.app.domain

/**
 * Produces the small, stable text units used by both background narration and live playback.
 *
 * [PassageTextChunker.MAX_CHARS] remains the storage safety bound. Narration treats the configured
 * value as a pacing target, not a hard cut: complete paragraphs stay together up to [MAX_CHARS],
 * and longer paragraphs split at the nearest sentence ending. The strict ceiling remains below
 * Supertonic's default internal text limit.
 */
object NarrationTextChunker {
    const val TARGET_CHARS = 160
    const val MAX_CHARS = 240
    const val MIN_CONFIGURABLE_CHARS = 80
    const val MAX_CONFIGURABLE_CHARS = 240
    val CONFIGURABLE_SIZES = listOf(MIN_CONFIGURABLE_CHARS, TARGET_CHARS, MAX_CONFIGURABLE_CHARS)

    fun chunks(
        passageId: String,
        text: String,
        maxChars: Int = TARGET_CHARS,
    ): List<PassageTextChunk> {
        require(maxChars in MIN_CONFIGURABLE_CHARS..MAX_CONFIGURABLE_CHARS) {
            "maxChars must be between $MIN_CONFIGURABLE_CHARS and $MAX_CONFIGURABLE_CHARS"
        }
        val pieces = PassageTextChunker.splitNaturally(
            text = text,
            targetChars = maxChars,
            maxChars = MAX_CHARS,
        )
        return pieces.mapIndexed { index, piece ->
            PassageTextChunk(
                id = if (pieces.size == 1) passageId else "$passageId::chunk:${index + 1}",
                text = piece,
            )
        }
    }

    fun normalizeMaxChars(value: Int): Int = value
        .takeIf { it in MIN_CONFIGURABLE_CHARS..MAX_CONFIGURABLE_CHARS }
        ?: TARGET_CHARS
}
