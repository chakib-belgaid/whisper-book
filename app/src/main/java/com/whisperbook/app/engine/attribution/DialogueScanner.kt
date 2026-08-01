package com.whisperbook.app.engine.attribution

enum class DialogueDelimiter {
    STRAIGHT_QUOTES,
    SMART_QUOTES,
    GUILLEMETS,
    EM_DASH,
}

data class DialogueSpan(
    val startInclusive: Int,
    val endExclusive: Int,
    val contentStartInclusive: Int,
    val contentEndExclusive: Int,
    val delimiter: DialogueDelimiter,
) {
    init {
        require(startInclusive <= contentStartInclusive)
        require(contentStartInclusive <= contentEndExclusive)
        require(contentEndExclusive <= endExclusive)
    }

    fun content(source: String): String = source.substring(contentStartInclusive, contentEndExclusive)
}

/**
 * A small state-machine scanner instead of a quote regex. It is deterministic for malformed input
 * and deliberately ignores unmatched delimiters so prose is never silently discarded.
 */
object DialogueScanner {
    fun scan(text: String): List<DialogueSpan> {
        if (text.isBlank()) return emptyList()
        val quoted = mutableListOf<DialogueSpan>()
        var index = 0
        while (index < text.length) {
            val opening = text[index]
            val closingCandidates = when (opening) {
                '"' -> charArrayOf('"')
                '“' -> charArrayOf('”')
                '„' -> charArrayOf('“', '”')
                '«' -> charArrayOf('»')
                else -> null
            }
            if (closingCandidates == null || (opening == '"' && isEscaped(text, index))) {
                index++
                continue
            }

            val close = findClosing(text, index + 1, closingCandidates)
            if (close < 0) {
                index++
                continue
            }
            if (text.substring(index + 1, close).isNotBlank()) {
                quoted += DialogueSpan(
                    startInclusive = index,
                    endExclusive = close + 1,
                    contentStartInclusive = index + 1,
                    contentEndExclusive = close,
                    delimiter = when (opening) {
                        '"' -> DialogueDelimiter.STRAIGHT_QUOTES
                        '«' -> DialogueDelimiter.GUILLEMETS
                        else -> DialogueDelimiter.SMART_QUOTES
                    },
                )
            }
            index = close + 1
        }
        if (quoted.isNotEmpty()) return quoted

        val firstContent = text.indexOfFirst { !it.isWhitespace() }
        if (firstContent >= 0 && text[firstContent] == '—') {
            val contentStart = (firstContent + 1 until text.length)
                .firstOrNull { !text[it].isWhitespace() }
                ?: text.length
            if (contentStart < text.length) {
                return listOf(
                    DialogueSpan(
                        startInclusive = firstContent,
                        endExclusive = text.length,
                        contentStartInclusive = contentStart,
                        contentEndExclusive = text.length,
                        delimiter = DialogueDelimiter.EM_DASH,
                    ),
                )
            }
        }
        return emptyList()
    }

    private fun findClosing(text: String, start: Int, candidates: CharArray): Int {
        for (index in start until text.length) {
            if (text[index] in candidates && !isEscaped(text, index)) return index
        }
        return -1
    }

    private fun isEscaped(text: String, index: Int): Boolean {
        var slashes = 0
        var cursor = index - 1
        while (cursor >= 0 && text[cursor] == '\\') {
            slashes++
            cursor--
        }
        return slashes % 2 == 1
    }
}
