package com.whisperbook.app.engine.document

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition

/**
 * Extracts PDF text without flattening the document into an unstructured stream.
 *
 * PDFBox's positional coordinates let us discard text inside conservative physical margin bands.
 * Explicit page separators then preserve the remaining page boundaries for the recurring-furniture
 * pass below.
 */
internal class PdfPageTextExtractor {
    fun extract(
        document: PDDocument,
        firstPage: Int,
        lastPage: Int,
    ): List<String> {
        require(firstPage > 0 && lastPage >= firstPage) { "Invalid PDF page range" }
        val expectedPages = lastPage - firstPage + 1
        val output = LayoutAwarePdfTextStripper(firstPage, lastPage).getText(document)
        return splitPages(output, expectedPages)
            ?: (firstPage..lastPage).map { pageNumber ->
                val page = LayoutAwarePdfTextStripper(pageNumber, pageNumber).getText(document)
                splitPages(page, expectedPages = 1)?.singleOrNull()
                    ?: page.removeSuffix(PDF_PAGE_BOUNDARY)
            }
    }

    private fun splitPages(output: String, expectedPages: Int): List<String>? {
        val pages = mutableListOf<String>()
        var cursor = 0
        while (true) {
            val boundary = output.indexOf(PDF_PAGE_BOUNDARY, startIndex = cursor)
            if (boundary < 0) break
            pages += output.substring(cursor, boundary)
            cursor = boundary + PDF_PAGE_BOUNDARY.length
        }
        if (cursor < output.length) pages += output.substring(cursor)
        return pages.takeIf { it.size == expectedPages }
    }
}

private class LayoutAwarePdfTextStripper(
    firstPage: Int,
    lastPage: Int,
) : PDFTextStripper() {
    init {
        sortByPosition = true
        startPage = firstPage
        endPage = lastPage
        pageStart = ""
        pageEnd = PDF_PAGE_BOUNDARY
    }

    override fun processTextPosition(text: TextPosition) {
        val pageHeight = text.pageHeight
        val y = text.yDirAdj
        val insideBody = pageHeight <= 0f ||
            (y >= pageHeight * TOP_MARGIN_RATIO && y <= pageHeight * (1f - BOTTOM_MARGIN_RATIO))
        if (insideBody) super.processTextPosition(text)
    }
}

/**
 * Removes page furniture whose meaning is visible only when pages are considered together.
 * Recurring short lines are compared only inside the first/last two non-empty lines of each page,
 * so repeated phrases in the body remain untouched.
 */
internal object PdfPageStructureCleaner {
    fun clean(pages: List<String>): String {
        if (pages.isEmpty()) return ""
        val structuredPages = pages.map(::StructuredPage)
        val repeatedTop = recurringKeys(structuredPages, PageEdge.TOP)
        val repeatedBottom = recurringKeys(structuredPages, PageEdge.BOTTOM)
        val topHasPagination = hasPaginationSequence(structuredPages, PageEdge.TOP)

        return structuredPages
            .map { page ->
                val hardRemovals = buildSet {
                    page.indices(PageEdge.TOP).forEach { lineIndex ->
                        val line = page.lines[lineIndex]
                        if (
                            isExplicitPageLabel(line) ||
                            (topHasPagination && standalonePageNumber(line) != null)
                        ) {
                            add(lineIndex)
                        }
                    }
                    page.indices(PageEdge.BOTTOM).forEach { lineIndex ->
                        val line = page.lines[lineIndex]
                        if (
                            isExplicitPageLabel(line) ||
                            standalonePageNumber(line) != null
                        ) {
                            add(lineIndex)
                        }
                    }
                }
                val recurringRemovals = buildSet {
                    page.indices(PageEdge.TOP).forEach { lineIndex ->
                        if (recurringKey(page.lines[lineIndex]) in repeatedTop) add(lineIndex)
                    }
                    page.indices(PageEdge.BOTTOM).forEach { lineIndex ->
                        if (recurringKey(page.lines[lineIndex]) in repeatedBottom) add(lineIndex)
                    }
                }
                val proposedRemovals = hardRemovals + recurringRemovals
                val removals = if (page.hasSubstantiveLineOutside(proposedRemovals)) {
                    proposedRemovals
                } else {
                    hardRemovals
                }
                page.lines
                    .filterIndexed { index, _ -> index !in removals }
                    .joinToString("\n")
                    .trim()
            }
            .filter(String::isNotBlank)
            .joinToString("\n")
            .trim()
    }

    private fun recurringKeys(
        pages: List<StructuredPage>,
        edge: PageEdge,
    ): Set<String> {
        if (pages.size < 2) return emptySet()
        val occurrences = mutableMapOf<String, MutableSet<Int>>()
        val eligiblePageCount = pages.count { it.indices(edge).isNotEmpty() }
        pages.forEachIndexed { pageIndex, page ->
            page.indices(edge)
                .asSequence()
                .map { page.lines[it] }
                .filter(::canBeRunningFurniture)
                .map(::recurringKey)
                .filter(String::isNotBlank)
                .forEach { key -> occurrences.getOrPut(key, ::mutableSetOf).add(pageIndex) }
        }
        return occurrences
            .filterValues { pageIndexes ->
                pageIndexes.size >= MIN_RECURRING_PAGES &&
                    pageIndexes.size.toFloat() / eligiblePageCount.coerceAtLeast(1) >= MIN_RECURRING_PAGE_RATIO
            }
            .keys
    }

    private fun hasPaginationSequence(
        pages: List<StructuredPage>,
        edge: PageEdge,
    ): Boolean {
        val candidates = pages.mapIndexedNotNull { pageIndex, page ->
            page.indices(edge)
                .firstNotNullOfOrNull { lineIndex ->
                    standalonePageNumber(page.lines[lineIndex])?.let { pageIndex to it }
                }
        }
        val eligiblePageCount = pages.count { it.indices(edge).isNotEmpty() }
        return candidates.size >= MIN_RECURRING_PAGES &&
            candidates.size.toFloat() / eligiblePageCount.coerceAtLeast(1) >= MIN_RECURRING_PAGE_RATIO &&
            candidates.map(Pair<Int, Int>::second).distinct().size >= MIN_RECURRING_PAGES
    }

    private fun canBeRunningFurniture(line: String): Boolean {
        val normalized = ParagraphNormalizer.normalizeParagraph(line)
        if (normalized.isBlank() || normalized.length > MAX_RUNNING_LINE_CHARS) return false
        if (normalized.split(Regex("\\s+")).size > MAX_RUNNING_LINE_WORDS) return false
        return normalized.any { it.isLetterOrDigit() }
    }

    private fun recurringKey(line: String): String = ParagraphNormalizer
        .normalizeParagraph(line)
        .lowercase()
        .replace(Regex("[\\p{P}\\p{S}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun isExplicitPageLabel(line: String): Boolean = explicitPageLabel.matches(
        ParagraphNormalizer.normalizeParagraph(line),
    )

    private fun standalonePageNumber(line: String): Int? {
        val value = ParagraphNormalizer.normalizeParagraph(line)
            .trim(' ', '-', '\u2013', '\u2014', '(', ')', '[', ']')
        if (value.matches(decimalPageNumber)) {
            var result = 0
            value.forEach { character ->
                val digit = Character.digit(character, 10)
                if (digit < 0) return null
                result = result * 10 + digit
            }
            return result
        }
        if (!value.matches(romanPageNumber)) return null
        var result = 0
        value.uppercase().forEachIndexed { index, character ->
            val current = romanDigit(character) ?: return null
            val next = value.getOrNull(index + 1)?.uppercaseChar()?.let(::romanDigit) ?: 0
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

    private data class StructuredPage(
        val rawText: String,
    ) {
        val lines: List<String> = rawText
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .split('\n')
        private val nonBlankIndices = lines.indices.filter { lines[it].isNotBlank() }

        fun indices(edge: PageEdge): List<Int> = when (edge) {
            PageEdge.TOP -> nonBlankIndices.take(EDGE_LINE_COUNT)
            PageEdge.BOTTOM -> nonBlankIndices.takeLast(EDGE_LINE_COUNT)
        }

        fun hasSubstantiveLineOutside(removals: Set<Int>): Boolean = nonBlankIndices.any { it !in removals }
    }

    private enum class PageEdge { TOP, BOTTOM }

    private val explicitPageLabel = Regex(
        "^(?:page|p\\.)\\s*[\\p{Nd}]+(?:\\s*(?:of|/)\\s*[\\p{Nd}]+)?$",
        RegexOption.IGNORE_CASE,
    )
    private val decimalPageNumber = Regex("^[\\p{Nd}]{1,7}$")
    private val romanPageNumber = Regex("^[ivxlcdm]{1,10}$", RegexOption.IGNORE_CASE)

    private const val EDGE_LINE_COUNT = 2
    private const val MIN_RECURRING_PAGES = 2
    private const val MIN_RECURRING_PAGE_RATIO = 0.35f
    private const val MAX_RUNNING_LINE_CHARS = 120
    private const val MAX_RUNNING_LINE_WORDS = 16
}

private const val TOP_MARGIN_RATIO = 0.06f
private const val BOTTOM_MARGIN_RATIO = 0.06f
private const val PDF_PAGE_BOUNDARY = "\n\u000CWHISPERBOOK_PAGE_END\u000C\n"
