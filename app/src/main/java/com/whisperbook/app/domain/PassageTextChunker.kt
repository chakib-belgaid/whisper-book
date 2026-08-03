package com.whisperbook.app.domain

import kotlin.math.min

/** A bounded piece of passage text with a stable ID suitable for UI and playback queues. */
data class PassageTextChunk(
    val id: String,
    val text: String,
)

/**
 * Splits unusually large extracted paragraphs without rewriting their prose.
 *
 * Sentence endings are preferred, then whitespace, with a hard character boundary only for text
 * that contains neither. The same deterministic IDs are used by the reader and Media3 queue so
 * legacy oversized passages remain seekable without rewriting the Room database.
 */
object PassageTextChunker {
    const val MAX_CHARS = 1_200

    fun split(text: String, maxChars: Int = MAX_CHARS): List<String> {
        require(maxChars >= MIN_MAX_CHARS) { "maxChars must be at least $MIN_MAX_CHARS" }
        val source = text.trim()
        if (source.isEmpty()) return emptyList()
        if (source.length <= maxChars) return listOf(source)

        return buildList {
            var start = 0
            while (start < source.length) {
                while (start < source.length && source[start].isWhitespace()) start += 1
                if (start >= source.length) break

                val limit = min(start + maxChars, source.length)
                val end = if (limit == source.length) {
                    limit
                } else {
                    findSentenceBoundary(source, start, limit, maxChars)
                        ?: findWhitespaceBoundary(source, start, limit, maxChars)
                        ?: safeHardBoundary(source, start, limit)
                }
                source.substring(start, end).trim().takeIf(String::isNotEmpty)?.let(::add)
                start = end
            }
        }
    }

    fun chunks(
        passageId: String,
        text: String,
        maxChars: Int = MAX_CHARS,
    ): List<PassageTextChunk> {
        require(passageId.isNotBlank()) { "passageId must not be blank" }
        val pieces = split(text, maxChars)
        return pieces.mapIndexed { index, piece ->
            PassageTextChunk(
                id = if (pieces.size == 1) passageId else "$passageId::chunk:${index + 1}",
                text = piece,
            )
        }
    }

    private fun findSentenceBoundary(
        source: String,
        start: Int,
        limit: Int,
        maxChars: Int,
    ): Int? {
        val preferredStart = start + maxChars / 2
        for (candidate in limit downTo preferredStart + 1) {
            val finalCharacterIndex = candidate - 1
            val finalCharacter = source[finalCharacterIndex]
            val endsSentence = finalCharacter in sentenceEndings ||
                (finalCharacter in sentenceClosers &&
                    finalCharacterIndex > start && source[finalCharacterIndex - 1] in sentenceEndings)
            if (endsSentence && source.getOrNull(candidate)?.isWhitespace() != false) return candidate
        }
        return null
    }

    private fun findWhitespaceBoundary(
        source: String,
        start: Int,
        limit: Int,
        maxChars: Int,
    ): Int? {
        val preferredStart = start + maxChars / 2
        for (candidate in limit downTo preferredStart + 1) {
            if (source[candidate - 1].isWhitespace()) return candidate - 1
        }
        return null
    }

    private fun safeHardBoundary(source: String, start: Int, limit: Int): Int =
        if (limit > start && limit < source.length &&
            source[limit - 1].isHighSurrogate() && source[limit].isLowSurrogate()
        ) {
            limit - 1
        } else {
            limit
        }

    private const val MIN_MAX_CHARS = 32
    private val sentenceEndings = setOf('.', '!', '?', '\u2026')
    private val sentenceClosers = setOf('"', '\'', '\u2019', '\u201d', '\u00bb', ')', ']')
}
