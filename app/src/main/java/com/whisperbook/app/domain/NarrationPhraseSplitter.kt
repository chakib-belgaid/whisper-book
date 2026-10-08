package com.whisperbook.app.domain

import kotlin.math.abs

/**
 * Cuts narration text into phrase-sized pieces for speech synthesis.
 *
 * Every phrase is an exact, trimmed substring of the input, so read-along highlighting and seeking
 * stay aligned with the publication text. Cut points are chosen in this order:
 *
 * 1. sentence ends (`.`, `!`, `?`, `…`, including closing quotes such as `»` or `”`),
 * 2. clause punctuation (`,`, `;`, `:`, a spaced dash, an opening parenthesis or guillemet),
 * 3. whitespace,
 * 4. a hard cut that never splits a surrogate pair.
 *
 * English and French abbreviations ("Mr.", "Dr.", "M.", "Mme", "J. K.") are not sentence ends,
 * and a terminator followed by a lowercase word ("« Viens ! » dit-il") is treated as a clause
 * boundary rather than a sentence end. French spaced punctuation ("Quoi ?") is never orphaned.
 *
 * [maxChars] is a strict ceiling. An optional smaller `targetChars` paces the output: the sentence
 * end nearest the target wins over one further away, so phrases settle around the target while a
 * sentence may still run up to the ceiling rather than being cut at a clause.
 */
object NarrationPhraseSplitter {
    fun split(text: String, maxChars: Int, targetChars: Int = maxChars): List<String> {
        require(maxChars >= MIN_MAX_CHARS) { "maxChars must be at least $MIN_MAX_CHARS" }
        require(targetChars in MIN_MAX_CHARS..maxChars) {
            "targetChars must be between $MIN_MAX_CHARS and maxChars"
        }
        val source = text.trim()
        if (source.isEmpty()) return emptyList()
        if (source.length <= maxChars) return listOf(source)

        val boundaries = scanBoundaries(source)
        return buildList {
            var start = 0
            while (start < source.length) {
                while (start < source.length && source[start].isWhitespace()) start += 1
                if (start >= source.length) break

                val end = if (source.length - start <= maxChars) {
                    source.length
                } else {
                    chooseCut(source, boundaries, start, targetChars, maxChars)
                }
                source.substring(start, end).trim().takeIf(String::isNotEmpty)?.let(::add)
                start = end
            }
        }
    }

    private fun chooseCut(
        source: String,
        boundaries: List<Boundary>,
        start: Int,
        targetChars: Int,
        maxChars: Int,
    ): Int {
        val limit = start + maxChars
        val window = boundaries.filter { it.position in (start + 1)..limit }

        fun furthest(strength: Int, minFraction: Float): Int? = window
            .lastOrNull { it.strength >= strength && it.position >= start + (maxChars * minFraction).toInt() }
            ?.position

        // With targetChars == maxChars the nearest sentence end is simply the furthest one.
        val target = start + targetChars
        val nearestSentence = window
            .filter { it.strength >= SENTENCE && it.position >= start + (targetChars * SENTENCE_MIN_FRACTION).toInt() }
            .minWithOrNull(compareBy<Boundary> { abs(it.position - target) }.thenByDescending { it.position })
            ?.position

        return nearestSentence
            ?: furthest(CLAUSE, CLAUSE_MIN_FRACTION)
            ?: furthest(SPACE, SPACE_MIN_FRACTION)
            ?: furthest(CLAUSE, 0f)
            ?: furthest(SPACE, 0f)
            ?: furthest(WEAK_SPACE, 0f)
            ?: safeHardBoundary(source, start, limit)
    }

    private fun scanBoundaries(source: String): List<Boundary> {
        val result = mutableListOf<Boundary>()
        var index = 0
        while (index < source.length) {
            val char = source[index]
            when {
                char in TERMINATORS || char in CLAUSE_PUNCTUATION -> {
                    val end = consumePunctuationRun(source, index)
                    val followedBySpace = end >= source.length || source[end].isWhitespace()
                    if (followedBySpace && !isAbbreviationDot(source, index, end)) {
                        val isSentence = source.substring(index, end).any { it in TERMINATORS } &&
                            nextWordStart(source, end)?.isLowerCase() != true
                        result += Boundary(end, if (isSentence) SENTENCE else CLAUSE)
                    }
                    index = end
                    continue
                }

                char.isWhitespace() && index > 0 && !source[index - 1].isWhitespace() -> {
                    val next = nextWordStart(source, index)
                    val strength = when {
                        next == null -> WEAK_SPACE
                        next in CLAUSE_OPENERS -> CLAUSE
                        // French typography: never orphan "?", "!", ";", ":" or "»" at a phrase start.
                        next in TERMINATORS || next in CLAUSE_PUNCTUATION || next in CLOSERS -> WEAK_SPACE
                        source[index - 1] in OPENERS -> WEAK_SPACE
                        endsWithAbbreviation(source, index) -> WEAK_SPACE
                        else -> SPACE
                    }
                    result += Boundary(index, strength)
                }
            }
            index += 1
        }
        return result
    }

    /** Returns the exclusive end of a punctuation run plus any closing quotes or brackets. */
    private fun consumePunctuationRun(source: String, start: Int): Int {
        var end = start + 1
        while (end < source.length && (source[end] in TERMINATORS || source[end] in CLAUSE_PUNCTUATION)) end += 1
        while (true) {
            var probe = end
            while (probe < source.length && source[probe] in INLINE_SPACES) probe += 1
            val closer = source.getOrNull(probe)
            // Only French guillemets may be separated from their punctuation by a space; a spaced
            // straight quote after a sentence end is the next sentence's opening quote.
            val accepted = closer != null &&
                if (probe == end) closer in CLOSERS else closer in SPACED_CLOSERS
            if (accepted) {
                end = probe + 1
                while (end < source.length && (source[end] in TERMINATORS || source[end] in CLAUSE_PUNCTUATION)) {
                    end += 1
                }
            } else {
                break
            }
        }
        return end
    }

    private fun isAbbreviationDot(source: String, start: Int, end: Int): Boolean {
        if (source[start] != '.' || end != start + 1) return false
        return isAbbreviationToken(tokenBefore(source, start))
    }

    private fun endsWithAbbreviation(source: String, whitespaceIndex: Int): Boolean {
        val dot = whitespaceIndex - 1
        return dot >= 0 && source[dot] == '.' && isAbbreviationToken(tokenBefore(source, dot))
    }

    private fun tokenBefore(source: String, dotIndex: Int): String {
        var tokenStart = dotIndex
        while (tokenStart > 0 && (source[tokenStart - 1].isLetter() || source[tokenStart - 1] == '.')) {
            tokenStart -= 1
        }
        return source.substring(tokenStart, dotIndex)
    }

    private fun isAbbreviationToken(token: String): Boolean = when {
        token.isEmpty() -> false
        // Dotted forms such as "e.g", "i.e", "U.S", "p.m".
        token.contains('.') -> true
        // Initials such as "J. K. Rowling" or "M. Dupont"; "I." and "A." usually end sentences.
        token.length == 1 && token[0].isUpperCase() -> token[0] !in NON_INITIAL_LETTERS
        else -> token in ABBREVIATIONS
    }

    private fun nextWordStart(source: String, from: Int): Char? {
        var index = from
        while (index < source.length && source[index].isWhitespace()) index += 1
        return source.getOrNull(index)
    }

    private fun safeHardBoundary(source: String, start: Int, limit: Int): Int =
        if (limit > start + 1 && limit < source.length &&
            source[limit - 1].isHighSurrogate() && source[limit].isLowSurrogate()
        ) {
            limit - 1
        } else {
            limit
        }

    private data class Boundary(val position: Int, val strength: Int)

    private const val SENTENCE = 3
    private const val CLAUSE = 2
    private const val SPACE = 1
    private const val WEAK_SPACE = 0

    private const val SENTENCE_MIN_FRACTION = 0.30f
    private const val CLAUSE_MIN_FRACTION = 0.40f
    private const val SPACE_MIN_FRACTION = 0.50f

    private const val MIN_MAX_CHARS = 32

    private val TERMINATORS = setOf('.', '!', '?', '…')
    private val CLAUSE_PUNCTUATION = setOf(',', ';', ':')
    private val CLOSERS = setOf('"', '\'', '’', '”', '»', '›', ')', ']')
    private val SPACED_CLOSERS = setOf('»', '›')
    private val OPENERS =setOf('«', '“', '‘', '(', '[', '‹')
    private val CLAUSE_OPENERS = setOf('—', '–', '(', '«', '“')
    private val INLINE_SPACES = setOf(' ', ' ', ' ', ' ', ' ')
    private val NON_INITIAL_LETTERS = setOf('I', 'A')

    /** Case-sensitive so that pronouns such as English "me." still end sentences. */
    private val ABBREVIATIONS = setOf(
        // English
        "Mr", "Mrs", "Ms", "Mx", "Dr", "Drs", "Prof", "St", "Sts", "Jr", "Sr", "Rev", "Gen", "Col",
        "Capt", "Lt", "Sgt", "Gov", "Sen", "Hon", "Mt", "Ft", "Fig", "fig", "Vol", "vol", "Ch", "ch",
        "vs", "cf", "Cf", "approx", "p", "pp",
        // French
        "Mme", "Mmes", "Mlle", "Mlles", "MM", "Me", "Mgr", "Pr", "Ste", "av", "apr", "env", "éd", "Éd",
    )
}
