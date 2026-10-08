package com.whisperbook.app.domain

/**
 * Produces the small, stable text units used by both background narration and live playback.
 *
 * [PassageTextChunker.MAX_CHARS] remains the storage/reader safety bound. Narration uses a much
 * smaller cap so the first playable WAV is available quickly and an on-demand request never waits
 * behind a long background inference. [NarrationPhraseSplitter] prefers sentence endings, then
 * clause punctuation, then whitespace, and knows common English and French abbreviations, so
 * Supertonic receives natural phrases instead of text cut at an arbitrary space.
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
        require(passageId.isNotBlank()) { "passageId must not be blank" }
        val phrases = NarrationPhraseSplitter.split(text, maxChars)
        return phrases.mapIndexed { index, phrase ->
            PassageTextChunk(
                id = if (phrases.size == 1) passageId else "$passageId::chunk:${index + 1}",
                text = phrase,
            )
        }
    }

    fun normalizeMaxChars(value: Int): Int = value
        .takeIf { it in MIN_CONFIGURABLE_CHARS..MAX_CONFIGURABLE_CHARS }
        ?: MAX_CHARS
}
