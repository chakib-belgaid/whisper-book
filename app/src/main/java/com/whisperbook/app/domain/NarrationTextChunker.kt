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

    fun chunks(passageId: String, text: String): List<PassageTextChunk> =
        PassageTextChunker.chunks(
            passageId = passageId,
            text = text,
            maxChars = MAX_CHARS,
        )
}
