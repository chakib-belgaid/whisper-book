package com.whisperbook.app.engine.document

import kotlin.math.min

data class DocumentSection(
    val title: String? = null,
    val paragraphs: List<String>,
    val tocTitle: String? = null,
    val additionalTocTitles: List<String> = emptyList(),
    val sourceReference: String? = null,
)

enum class ChapterDetectionRule {
    TOC,
    SOURCE_SECTION,
    HEADING,
    REGEX,
    FALLBACK,
}

data class DetectedChapter(
    val title: String,
    val paragraphs: List<String>,
    val rule: ChapterDetectionRule,
    val sourceReference: String? = null,
)

/** Deterministic, format-independent chapter boundary detection. */
class ChapterDetector(
    private val fallbackMaxWords: Int = 4_000,
) {
    init {
        require(fallbackMaxWords > 0) { "fallbackMaxWords must be positive" }
    }

    fun detect(
        paragraphs: List<String>,
        tocTitles: List<String> = emptyList(),
    ): List<DetectedChapter> {
        val normalized = paragraphs.map(ParagraphNormalizer::normalizeParagraph).filter(String::isNotBlank)
        if (normalized.isEmpty()) return emptyList()

        val tocLookup = tocTitles
            .map(ParagraphNormalizer::normalizeParagraph)
            .filter(String::isNotBlank)
            .associateBy(::headingKey)

        val boundaries = normalized.mapIndexedNotNull { index, paragraph ->
            val tocTitle = tocLookup[headingKey(paragraph)]
            when {
                tocTitle != null -> Boundary(index, tocTitle, ChapterDetectionRule.TOC)
                chapterPattern.matches(paragraph) -> Boundary(index, paragraph, ChapterDetectionRule.REGEX)
                looksLikeStructuralHeading(paragraph) -> Boundary(index, paragraph, ChapterDetectionRule.HEADING)
                else -> null
            }
        }

        if (boundaries.isEmpty()) return fallback(normalized)

        val result = mutableListOf<DetectedChapter>()
        val first = boundaries.first()
        if (first.index > 0) {
            val preface = normalized.subList(0, first.index)
            if (preface.any { it.split(Regex("\\s+")).size > 3 }) {
                result += DetectedChapter("Opening", preface, ChapterDetectionRule.FALLBACK)
            }
        }

        boundaries.forEachIndexed { boundaryIndex, boundary ->
            val end = boundaries.getOrNull(boundaryIndex + 1)?.index ?: normalized.size
            val bodyStart = min(boundary.index + 1, end)
            val body = normalized.subList(bodyStart, end)
            if (body.isNotEmpty() || result.isEmpty()) {
                result += DetectedChapter(
                    title = cleanHeading(boundary.title),
                    paragraphs = body,
                    rule = boundary.rule,
                )
            }
        }
        return result.ifEmpty { fallback(normalized) }
    }

    fun detectSections(sections: List<DocumentSection>): List<DetectedChapter> {
        val result = mutableListOf<DetectedChapter>()
        sections.forEach { section ->
            val paragraphs = section.paragraphs
                .map(ParagraphNormalizer::normalizeParagraph)
                .filter(String::isNotBlank)
            if (paragraphs.isEmpty()) return@forEach

            val explicitTitle = section.tocTitle?.takeIf(String::isNotBlank)
                ?: section.title?.takeIf(String::isNotBlank)
            val internal = detect(
                paragraphs,
                listOfNotNull(section.tocTitle, section.title) + section.additionalTocTitles,
            )

            if (internal.size == 1 && internal.single().rule == ChapterDetectionRule.FALLBACK && explicitTitle != null) {
                result += DetectedChapter(
                    title = cleanHeading(explicitTitle),
                    paragraphs = paragraphs.dropHeadingMatching(explicitTitle),
                    rule = if (!section.tocTitle.isNullOrBlank()) ChapterDetectionRule.TOC else ChapterDetectionRule.SOURCE_SECTION,
                    sourceReference = section.sourceReference,
                )
            } else {
                internal.forEachIndexed { index, chapter ->
                    result += chapter.copy(
                        title = when {
                            index == 0 && !section.tocTitle.isNullOrBlank() -> cleanHeading(section.tocTitle)
                            index == 0 && chapter.title == "Chapter 1" && explicitTitle != null -> cleanHeading(explicitTitle)
                            else -> chapter.title
                        },
                        rule = if (index == 0 && !section.tocTitle.isNullOrBlank()) ChapterDetectionRule.TOC else chapter.rule,
                        sourceReference = section.sourceReference,
                    )
                }
            }
        }
        return result
    }

    private fun fallback(paragraphs: List<String>): List<DetectedChapter> {
        val chapters = mutableListOf<DetectedChapter>()
        var start = 0
        var words = 0
        paragraphs.forEachIndexed { index, paragraph ->
            val count = paragraph.split(Regex("\\s+")).count(String::isNotBlank)
            if (words > 0 && words + count > fallbackMaxWords) {
                chapters += DetectedChapter(
                    title = "Chapter ${chapters.size + 1}",
                    paragraphs = paragraphs.subList(start, index),
                    rule = ChapterDetectionRule.FALLBACK,
                )
                start = index
                words = 0
            }
            words += count
        }
        if (start < paragraphs.size) {
            chapters += DetectedChapter(
                title = "Chapter ${chapters.size + 1}",
                paragraphs = paragraphs.subList(start, paragraphs.size),
                rule = ChapterDetectionRule.FALLBACK,
            )
        }
        return chapters
    }

    private fun List<String>.dropHeadingMatching(title: String): List<String> =
        if (firstOrNull()?.let(::headingKey) == headingKey(title)) drop(1) else this

    private data class Boundary(
        val index: Int,
        val title: String,
        val rule: ChapterDetectionRule,
    )

    companion object {
        private val chapterPattern = Regex(
            pattern = "^(?:(?:chapter|chapitre|cap[ií]tulo|kapitel|book|part|volume)\\s+(?:[0-9]+|[ivxlcdm]+|one|two|three|four|five|six|seven|eight|nine|ten)(?:\\s*[:.\\-—]\\s*.+)?|prologue|epilogue|introduction|foreword|afterword)$",
            option = RegexOption.IGNORE_CASE,
        )
        private val numberedHeading = Regex("^(?:[0-9]{1,3}|[IVXLCDM]{1,10})[.)]?(?:\\s+.{1,70})?$")

        fun looksLikeHeading(text: String): Boolean =
            chapterPattern.matches(text.trim()) || looksLikeStructuralHeading(text)

        private fun looksLikeStructuralHeading(text: String): Boolean {
            val value = text.trim()
            if (value.isEmpty() || value.length > 90 || value.contains(Regex("[.!?].+\\s"))) return false
            if (numberedHeading.matches(value)) return true
            val words = value.split(Regex("\\s+")).filter(String::isNotBlank)
            if (words.isEmpty() || words.size > 10) return false
            val letters = value.count(Char::isLetter)
            if (letters < 3) return false
            val uppercase = value.count(Char::isUpperCase)
            return uppercase.toFloat() / letters >= 0.8f && words.size <= 8
        }

        private fun headingKey(text: String): String = text
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()

        private fun cleanHeading(text: String): String = text
            .replace(Regex("\\s+"), " ")
            .trim(' ', ':', '.', '-', '—')
    }
}
