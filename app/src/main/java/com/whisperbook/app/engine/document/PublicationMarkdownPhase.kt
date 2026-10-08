package com.whisperbook.app.engine.document

import com.whisperbook.app.domain.ExtractedChapter
import com.whisperbook.app.domain.ExtractedPublication

/**
 * Canonical boundary between format-specific extraction and Whisperbook's passage model.
 *
 * PDF and EPUB input both pass through this phase. It removes false layout paragraph breaks,
 * retains real punctuation and dialogue boundaries, and emits a readable Markdown document whose
 * headings and blank lines mirror the cleaned chapter/paragraph structure used downstream.
 */
internal object PublicationMarkdownPhase {
    fun transform(
        title: String,
        author: String?,
        chapters: List<DetectedChapter>,
    ): ExtractedPublication {
        val cleanTitle = ParagraphNormalizer.normalizeParagraph(title).ifBlank { "Untitled book" }
        val cleanAuthor = author
            ?.let(ParagraphNormalizer::normalizeParagraph)
            ?.takeIf(String::isNotBlank)
        val cleanChapters = chapters.mapIndexedNotNull { index, chapter ->
            val paragraphs = cleanParagraphs(chapter.paragraphs)
            if (paragraphs.isEmpty()) {
                null
            } else {
                ExtractedChapter(
                    title = ParagraphNormalizer.normalizeParagraph(chapter.title)
                        .ifBlank { "Chapter ${index + 1}" },
                    paragraphs = paragraphs,
                )
            }
        }

        return ExtractedPublication(
            title = cleanTitle,
            author = cleanAuthor,
            chapters = cleanChapters,
            markdown = render(cleanTitle, cleanChapters),
        )
    }

    private fun cleanParagraphs(paragraphs: List<String>): List<String> {
        val result = mutableListOf<String>()
        paragraphs
            .asSequence()
            .map(ParagraphNormalizer::normalizeParagraph)
            .filter(String::isNotBlank)
            .forEach { paragraph ->
                val previous = result.lastOrNull()
                if (previous != null && isFalseParagraphBreak(previous, paragraph)) {
                    result[result.lastIndex] = join(previous, paragraph)
                } else {
                    result += paragraph
                }
            }
        return result
    }

    private fun isFalseParagraphBreak(previous: String, next: String): Boolean {
        if (startsIndependentBlock(next)) return false
        if (ParagraphNormalizer.endsWithSentencePunctuation(previous)) return false

        val previousTrimmed = previous.trimEnd()
        val coreEnding = previousTrimmed
            .dropLastWhile { it in trailingClosers }
            .trimEnd()
            .lastOrNull()
        val nextFirstLetter = next.firstOrNull(Char::isLetter)

        return previousTrimmed.lastOrNull() in trailingClosers ||
            coreEnding in continuationPunctuation ||
            nextFirstLetter?.isLowerCase() == true
    }

    private fun startsIndependentBlock(text: String): Boolean {
        val value = text.trimStart()
        if (value.isEmpty()) return false
        if (ChapterDetector.looksLikeHeading(value)) return true
        if (value.first() in dialogueOrBlockOpeners) return true
        return markdownListPrefix.containsMatchIn(value)
    }

    private fun join(previous: String, next: String): String {
        val separator = if (next.firstOrNull() in noLeadingSpacePunctuation) "" else " "
        return ParagraphNormalizer.normalizeParagraph(previous.trimEnd() + separator + next.trimStart())
    }

    private fun render(title: String, chapters: List<ExtractedChapter>): String = buildString {
        append("# ")
        append(title)
        chapters.forEach { chapter ->
            append("\n\n## ")
            append(chapter.title)
            chapter.paragraphs.forEach { paragraph ->
                append("\n\n")
                append(paragraph)
            }
        }
        append('\n')
    }

    private val trailingClosers = setOf('"', '\'', '’', '”', '»', ')', ']', '}')
    private val continuationPunctuation = setOf(',', ';', ':', '–', '—', '(', '[', '{', '«', '“')
    private val dialogueOrBlockOpeners = setOf('—', '–', '•', '«', '“', '"', '>', '#')
    private val noLeadingSpacePunctuation = setOf(',', '.', ';', ':', '!', '?', '…', ')', ']', '}', '»', '”')
    private val markdownListPrefix = Regex("^(?:[-+*]|\\p{Nd}{1,3}[.)])\\s+")
}
