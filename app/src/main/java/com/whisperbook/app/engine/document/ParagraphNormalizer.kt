package com.whisperbook.app.engine.document

/**
 * Turns the noisy text produced by ebook and PDF extractors into stable paragraphs.
 * The normalizer intentionally does not perform language-specific rewriting: the returned
 * text must still match the publication closely enough for highlighting and seeking.
 */
object ParagraphNormalizer {
    private val horizontalWhitespace = Regex("[\\t\\u000B\\f \\u00A0]+");
    private val blankLines = Regex("\\n[ \\t]*\\n+")
    private val dehyphenation = Regex("([\\p{Ll}])[-\\u2010]\\n[ \\t]*([\\p{Ll}])")

    fun normalize(rawText: String): List<String> {
        if (rawText.isBlank()) return emptyList()

        val canonical = rawText
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace("\u00AD", "")
            .replace(dehyphenation) { match ->
                match.groupValues[1] + match.groupValues[2]
            }

        return canonical
            .split(blankLines)
            .asSequence()
            .flatMap { joinSoftWrappedLines(it).splitToSequence('\n') }
            .map(::normalizeParagraph)
            .filter(String::isNotBlank)
            .toList()
    }

    fun normalizeParagraph(paragraph: String): String = paragraph
        .replace('\u00A0', ' ')
        .replace('\u2007', ' ')
        .replace('\u202F', ' ')
        .replace(horizontalWhitespace, " ")
        .trim()

    private fun joinSoftWrappedLines(block: String): String {
        val lines = block.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        if (lines.size <= 1) return lines.firstOrNull().orEmpty()

        return buildString {
            lines.forEachIndexed { index, line ->
                if (index > 0 && isHardBreak(lines[index - 1], line)) append('\n')
                else if (index > 0) append(' ')
                append(line)
            }
        }
    }

    private fun isHardBreak(previous: String, next: String): Boolean {
        // Preserve dialogue lines and compact, heading-like lines. Normal prose from PDFBox is
        // otherwise joined because its line breaks reflect page layout rather than paragraphs.
        if (next.startsWith('—') || next.startsWith('–')) return true
        if (ChapterDetector.looksLikeHeading(previous) || ChapterDetector.looksLikeHeading(next)) {
            return true
        }
        if (previous.length >= 24 && previous.lastOrNull() in sentenceEndings && next.firstOrNull()?.isUpperCase() == true) {
            return true
        }
        return false
    }

    private val sentenceEndings = setOf('.', '!', '?', '”', '»')
}
