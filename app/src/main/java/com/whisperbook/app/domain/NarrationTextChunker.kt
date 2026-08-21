package com.whisperbook.app.domain

/**
 * Produces the small, stable text units used by both background narration and live playback.
 *
 * [PassageTextChunker.MAX_CHARS] remains the storage/reader safety bound. Narration uses a much
 * smaller cap so the first playable WAV is available quickly and an on-demand request never waits
 * behind a long background inference. The underlying splitter prefers sentence endings after half
 * the cap, so normal chunks are roughly 80-160 characters without cutting prose mid-sentence.
 * Staying below 300 also keeps each app segment within Supertonic's default internal text limit.
 */
object NarrationTextChunker {
    const val MAX_CHARS = 160
    const val MIN_CONFIGURABLE_CHARS = 80
    const val MAX_CONFIGURABLE_CHARS = 240
    val CONFIGURABLE_SIZES = listOf(MIN_CONFIGURABLE_CHARS, MAX_CHARS, MAX_CONFIGURABLE_CHARS)

    fun chunks(
        passageId: String,
        text: String,
        maxChars: Int = MAX_CHARS,
    ): List<PassageTextChunk> {
        require(maxChars in MIN_CONFIGURABLE_CHARS..MAX_CONFIGURABLE_CHARS) {
            "maxChars must be between $MIN_CONFIGURABLE_CHARS and $MAX_CONFIGURABLE_CHARS"
        }
        return PassageTextChunker.chunks(
            passageId = passageId,
            text = text,
            maxChars = maxChars,
        )
    }

    fun normalizeMaxChars(value: Int): Int = value
        .takeIf { it in MIN_CONFIGURABLE_CHARS..MAX_CONFIGURABLE_CHARS }
        ?: MAX_CHARS
}
