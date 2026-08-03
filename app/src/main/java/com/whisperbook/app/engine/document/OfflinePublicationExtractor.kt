package com.whisperbook.app.engine.document

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.whisperbook.app.domain.ExtractedChapter
import com.whisperbook.app.domain.ExtractedPublication
import com.whisperbook.app.domain.ImportedBook
import com.whisperbook.app.domain.PublicationExtractor
import com.whisperbook.app.domain.model.BookFormat
import java.io.File
import java.io.IOException
import java.net.URI
import java.util.zip.ZipFile
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import org.w3c.dom.Document

/** Injection boundary used by the production page-at-a-time bundled OCR and deterministic tests. */
fun interface PdfOcrHook {
    suspend fun extractText(file: File): String?

    suspend fun extractText(
        file: File,
        onProgress: suspend (completedUnits: Int, totalUnits: Int) -> Unit,
    ): String? {
        val text = extractText(file)
        onProgress(1, 1)
        return text
    }
}

class OfflinePublicationExtractor(
    context: Context,
    private val chapterDetector: ChapterDetector = ChapterDetector(),
    private val pdfOcrHook: PdfOcrHook = AndroidPdfOcrHook(),
) : PublicationExtractor {
    private val applicationContext = context.applicationContext

    override suspend fun extract(book: ImportedBook): Result<ExtractedPublication> =
        extract(book) { _, _ -> }

    override suspend fun extract(
        book: ImportedBook,
        onProgress: suspend (completedUnits: Int, totalUnits: Int) -> Unit,
    ): Result<ExtractedPublication> =
        withContext(Dispatchers.IO) {
            try {
                Result.success(
                    when (book.format) {
                        BookFormat.EPUB -> EpubPublicationParser(chapterDetector).extract(book)
                            .also { onProgress(1, 1) }
                        BookFormat.PDF -> extractPdf(book, onProgress)
                    },
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Result.failure(throwable)
            }
        }

    private suspend fun extractPdf(
        book: ImportedBook,
        onProgress: suspend (completedUnits: Int, totalUnits: Int) -> Unit,
    ): ExtractedPublication {
        PDFBoxResourceLoader.init(applicationContext)
        val pdfData = try {
            PDDocument.load(book.privateFile).use { document ->
                val pageCount = document.numberOfPages
                if (pageCount <= 0) throw EmptyPdfException("The PDF contains no pages.")
                val text = StringBuilder()
                for (firstPage in 1..pageCount step PDF_TEXT_BATCH_SIZE) {
                    coroutineContext.ensureActive()
                    val lastPage = minOf(firstPage + PDF_TEXT_BATCH_SIZE - 1, pageCount)
                    val batch = PDFTextStripper().apply {
                        sortByPosition = true
                        startPage = firstPage
                        endPage = lastPage
                    }.getText(document)
                    if (text.isNotEmpty() && batch.isNotBlank()) text.append('\n')
                    text.append(batch)
                    onProgress(lastPage, pageCount)
                }
                Triple(
                    text.toString(),
                    document.documentInformation?.title?.trim()?.takeIf(String::isNotBlank),
                    document.documentInformation?.author?.trim()?.takeIf(String::isNotBlank),
                )
            }
        } catch (encrypted: InvalidPasswordException) {
            throw EncryptedPdfException(encrypted)
        } catch (io: IOException) {
            throw CorruptPdfException(io)
        }

        val extractedText = if (pdfData.first.isBlank()) {
            pdfOcrHook.extractText(book.privateFile, onProgress).orEmpty().takeIf(String::isNotBlank)
                ?: throw EmptyPdfException("No text was recognized on any PDF page.")
        } else pdfData.first

        val chapters = chapterDetector.detect(ParagraphNormalizer.normalize(extractedText))
        check(chapters.isNotEmpty()) { "No readable text was found in the PDF" }
        return ExtractedPublication(
            title = pdfData.second ?: book.title,
            author = pdfData.third ?: book.author,
            chapters = chapters.map { ExtractedChapter(it.title, it.paragraphs) },
        )
    }

    private companion object {
        const val PDF_TEXT_BATCH_SIZE = 8
    }
}

internal class EpubPublicationParser(
    private val chapterDetector: ChapterDetector,
) {
    fun extract(book: ImportedBook): ExtractedPublication = ZipFile(book.privateFile).use { zip ->
        val rootPath = readRootFilePath(zip)
        val packageDocument = parseXml(zip.readEntry(rootPath))
        val packageDirectory = rootPath.substringBeforeLast('/', "")
        val manifest = readManifest(packageDocument, packageDirectory)
        val spineIds = readSpine(packageDocument)
        check(spineIds.isNotEmpty()) { "The EPUB package has no reading-order spine" }

        val navItem = manifest.values.firstOrNull { item ->
            item.properties.split(Regex("\\s+")).any { it == "nav" }
        }
        val ncxItem = manifest.values.firstOrNull { it.mediaType == "application/x-dtbncx+xml" }
        val toc = when {
            navItem != null -> readNavigation(zip, navItem.path)
            ncxItem != null -> readNcxNavigation(zip, ncxItem.path)
            else -> emptyMap()
        }

        val sections = spineIds.mapNotNull { id ->
            val item = manifest[id] ?: return@mapNotNull null
            if (!item.mediaType.contains("html", ignoreCase = true) &&
                !item.path.endsWith(".xhtml", ignoreCase = true) &&
                !item.path.endsWith(".html", ignoreCase = true)
            ) return@mapNotNull null

            val html = zip.readEntryOrNull(item.path) ?: return@mapNotNull null
            val document = Jsoup.parse(html.inputStream(), null, "", Parser.xmlParser())
            document.select("script,style,nav,noscript,svg").remove()
            val content = extractReadableElements(document.body() ?: document)
            if (content.isEmpty()) return@mapNotNull null
            val heading = document.selectFirst("h1,h2,h3,title")?.text()?.trim()?.takeIf(String::isNotBlank)
            DocumentSection(
                title = heading,
                paragraphs = content,
                tocTitle = toc[item.path]?.firstOrNull(),
                additionalTocTitles = toc[item.path]?.drop(1).orEmpty(),
                sourceReference = item.path,
            )
        }

        val chapters = chapterDetector.detectSections(sections)
        check(chapters.isNotEmpty()) { "No readable chapters were found in the EPUB" }
        ExtractedPublication(
            title = packageDocument.metadata("title") ?: book.title,
            author = packageDocument.metadata("creator") ?: book.author,
            chapters = chapters.map { ExtractedChapter(it.title, it.paragraphs) },
        )
    }

    private fun readRootFilePath(zip: ZipFile): String {
        val container = parseXml(zip.readEntry("META-INF/container.xml"))
        val rootFiles = container.getElementsByTagNameNS("*", "rootfile")
        check(rootFiles.length > 0) { "EPUB container.xml has no rootfile" }
        return rootFiles.item(0).attributes.getNamedItem("full-path")?.nodeValue
            ?.let(::normalizeArchivePath)
            ?.takeIf(String::isNotBlank)
            ?: error("EPUB rootfile path is missing")
    }

    private fun readManifest(document: Document, packageDirectory: String): Map<String, ManifestItem> {
        val items = document.getElementsByTagNameNS("*", "item")
        return buildMap {
            for (index in 0 until items.length) {
                val attributes = items.item(index).attributes
                val id = attributes.getNamedItem("id")?.nodeValue ?: continue
                val href = attributes.getNamedItem("href")?.nodeValue ?: continue
                put(
                    id,
                    ManifestItem(
                        id = id,
                        path = resolveArchivePath(packageDirectory, href),
                        mediaType = attributes.getNamedItem("media-type")?.nodeValue.orEmpty(),
                        properties = attributes.getNamedItem("properties")?.nodeValue.orEmpty(),
                    ),
                )
            }
        }
    }

    private fun readSpine(document: Document): List<String> {
        val references = document.getElementsByTagNameNS("*", "itemref")
        return buildList {
            for (index in 0 until references.length) {
                references.item(index).attributes.getNamedItem("idref")?.nodeValue?.let(::add)
            }
        }
    }

    private fun readNavigation(zip: ZipFile, navPath: String): Map<String, List<String>> {
        val navDocument = Jsoup.parse(zip.readEntry(navPath).inputStream(), null, "", Parser.xmlParser())
        val navDirectory = navPath.substringBeforeLast('/', "")
        val tocNav = navDocument.select("nav").firstOrNull { element ->
            element.attr("epub:type").equals("toc", ignoreCase = true) ||
                element.attr("role").equals("doc-toc", ignoreCase = true)
        } ?: navDocument.selectFirst("nav")
        return buildMap<String, MutableList<String>> {
            tocNav?.select("a[href]")?.forEach { anchor ->
                val title = anchor.text().trim()
                if (title.isNotBlank()) getOrPut(resolveArchivePath(navDirectory, anchor.attr("href")), ::mutableListOf).add(title)
            }
        }
    }

    private fun readNcxNavigation(zip: ZipFile, ncxPath: String): Map<String, List<String>> {
        val document = parseXml(zip.readEntry(ncxPath))
        val directory = ncxPath.substringBeforeLast('/', "")
        val points = document.getElementsByTagNameNS("*", "navPoint")
        return buildMap<String, MutableList<String>> {
            for (index in 0 until points.length) {
                val point = points.item(index) as? org.w3c.dom.Element ?: continue
                val labels = point.getElementsByTagNameNS("*", "text")
                val contents = point.getElementsByTagNameNS("*", "content")
                val title = labels.item(0)?.textContent?.trim()?.takeIf(String::isNotBlank) ?: continue
                val href = contents.item(0)?.attributes?.getNamedItem("src")?.nodeValue ?: continue
                getOrPut(resolveArchivePath(directory, href), ::mutableListOf).add(title)
            }
        }
    }

    private fun extractReadableElements(root: Element): List<String> {
        val readableTags = setOf("h1", "h2", "h3", "p", "blockquote", "li")
        return root.select(readableTags.joinToString(","))
            .asSequence()
            .filter { element -> element.parents().none { it !== root && it.normalName() in readableTags } }
            .map(Element::text)
            .map(ParagraphNormalizer::normalizeParagraph)
            .filter(String::isNotBlank)
            .toList()
    }

    private data class ManifestItem(
        val id: String,
        val path: String,
        val mediaType: String,
        val properties: String,
    )
}

private fun ZipFile.readEntry(path: String): ByteArray =
    readEntryOrNull(path) ?: error("EPUB entry is missing: $path")

private fun ZipFile.readEntryOrNull(path: String): ByteArray? {
    val safePath = normalizeArchivePath(path)
    val entry = getEntry(safePath) ?: return null
    check(!entry.isDirectory) { "Expected EPUB file entry: $safePath" }
    check(entry.size < 0 || entry.size <= MAX_EPUB_ENTRY_BYTES) { "EPUB entry is too large: $safePath" }
    return getInputStream(entry).use { input ->
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read == 0) continue
            total += read
            check(total <= MAX_EPUB_ENTRY_BYTES) { "EPUB entry expands beyond its safe limit: $safePath" }
            output.write(buffer, 0, read)
        }
        output.toByteArray()
    }
}

private fun parseXml(bytes: ByteArray): Document {
    val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        // Android's platform factory inherits the JAXP base implementation for these optional
        // capabilities. Calling setXIncludeAware(false) can therefore throw the misleading
        // "Unknown 0.0 specification" error even though XInclude is already disabled. Keep the
        // secure intent while treating unsupported optional switches as unavailable.
        runCatching { isXIncludeAware = false }
        runCatching { setExpandEntityReferences(false) }
        runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        // Android's XMLConstants stubs omit these JAXP 1.5 constants, while supported parser
        // implementations still accept their standard property URIs.
        runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "") }
        runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "") }
    }
    return factory.newDocumentBuilder().parse(bytes.inputStream())
}

private fun Document.metadata(localName: String): String? {
    val nodes = getElementsByTagNameNS("*", localName)
    for (index in 0 until nodes.length) {
        nodes.item(index).textContent?.trim()?.takeIf(String::isNotBlank)?.let { return it }
    }
    return null
}

private fun resolveArchivePath(directory: String, href: String): String {
    val cleanHref = href.substringBefore('#').substringBefore('?')
    val base = if (directory.isBlank()) URI("epub:/") else URI("epub:/$directory/")
    return normalizeArchivePath(base.resolve(cleanHref).normalize().path)
}

private fun normalizeArchivePath(path: String): String {
    val normalized = path.replace('\\', '/').trimStart('/')
    check(normalized.isNotBlank() && normalized.split('/').none { it == ".." }) {
        "Unsafe or empty EPUB entry path"
    }
    return normalized
}

private const val MAX_EPUB_ENTRY_BYTES = 32L * 1024L * 1024L
