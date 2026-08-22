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
        val numberedListIndices = adjacentNumberedListIndices(normalized)

        val boundaries = normalized.mapIndexedNotNull { index, paragraph ->
            val tocTitle = tocLookup[headingKey(paragraph)]
            when {
                tocTitle != null -> Boundary(index, tocTitle, ChapterDetectionRule.TOC)
                chapterPattern.matches(paragraph) -> Boundary(index, paragraph, ChapterDetectionRule.REGEX)
                index !in numberedListIndices && looksLikeStructuralHeading(paragraph) ->
                    Boundary(index, paragraph, ChapterDetectionRule.HEADING)
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
            // Consecutive structural labels (for example `CHAPTER I` followed by `THE KEY`)
            // describe the same boundary. Never turn the label with no body into an empty chapter.
            if (body.isNotEmpty()) {
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
        private val bareRomanHeading = Regex("^[IVXLCDM]{1,10}$")
        private val numberedTitleHeading = Regex(
            "^(?:\\p{Nd}{1,3}|[IVXLCDM]{1,10})([.):\\-—])?\\s+(\\S.{0,69})$",
        )
        private val numberedListLine = Regex(
            "^(\\p{Nd}{1,3}|[IVXLCDM]{1,10})(?:[.)]\\s+|\\s+).+$",
        )

        fun looksLikeHeading(text: String): Boolean =
            chapterPattern.matches(text.trim()) || looksLikeStructuralHeading(text)

        private fun looksLikeStructuralHeading(text: String): Boolean {
            val value = text.trim()
            if (value.isEmpty() || value.length > 90) return false
            // A bare Roman numeral is a common book label, while a bare Arabic number is usually
            // a page number. Sentence-ending punctuation keeps first-person prose such as
            // `I slept.` out; an unpunctuated title must start like a title in cased scripts.
            if (bareRomanHeading.matches(value)) return true
            numberedTitleHeading.matchEntire(value)?.let { match ->
                val title = match.groupValues[2]
                if (looksLikeNumberedTitle(title)) return true
            }
            if (value.contains(Regex("[.!?].+\\s"))) return false
            val words = value.split(Regex("\\s+")).filter(String::isNotBlank)
            if (words.isEmpty() || words.size > 10) return false
            val letters = value.count(Char::isLetter)
            if (letters < 3) return false
            val uppercase = value.count(Char::isUpperCase)
            return uppercase.toFloat() / letters >= 0.8f && words.size <= 8
        }

        private fun looksLikeNumberedTitle(title: String): Boolean {
            // A final full stop strongly indicates a numbered sentence/instruction. Question and
            // exclamation marks remain valid because they are common intentional chapter titles.
            if (title.lastOrNull()?.let { it == '.' || it == '۔' } == true) return false
            val words = title.split(Regex("\\s+")).filter(String::isNotBlank)
            if (words.isEmpty() || words.size > 10) return false
            val letters = title.filter(Char::isLetter)
            if (letters.length < 2) return false
            val firstLetter = letters.first()
            return firstLetter.isUpperCase() || letters.none { it.isLowerCase() || it.isUpperCase() }
        }

        /**
         * Consecutive 1/2/3 paragraphs are overwhelmingly lists or instructions, not chapter
         * bodies. Suppress only that local run; genuine numbered chapters have prose between their
         * headings, and explicit TOC matches still take precedence.
         */
        private fun adjacentNumberedListIndices(paragraphs: List<String>): Set<Int> {
            val numbers = paragraphs.map { paragraph ->
                numberedListLine.matchEntire(paragraph)
                    ?.groupValues
                    ?.get(1)
                    ?.let(::numberingValue)
            }
            val result = mutableSetOf<Int>()
            var start = 0
            while (start < numbers.size) {
                val first = numbers[start]
                if (first == null) {
                    start += 1
                    continue
                }
                var end = start
                while (
                    end + 1 < numbers.size &&
                    numbers[end + 1] != null &&
                    numbers[end + 1] == numbers[end]!! + 1
                ) {
                    end += 1
                }
                if (end > start) result.addAll(start..end)
                start = end + 1
            }
            return result
        }

        private fun numberingValue(value: String): Int? {
            if (value.all(Char::isDigit)) {
                var result = 0
                value.forEach { character ->
                    val digit = Character.digit(character, 10)
                    if (digit < 0) return null
                    result = result * 10 + digit
                }
                return result
            }
            var result = 0
            value.forEachIndexed { index, character ->
                val current = romanDigit(character) ?: return null
                val next = value.getOrNull(index + 1)?.let(::romanDigit) ?: 0
                result += if (current < next) -current else current
            }
            return result.takeIf { it > 0 }
        }

        private fun romanDigit(character: Char): Int? = when (character) {
            'I' -> 1
            'V' -> 5
            'X' -> 10
            'L' -> 50
            'C' -> 100
            'D' -> 500
            'M' -> 1_000
            else -> null
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
