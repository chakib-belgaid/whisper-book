package com.whisperbook.app.engine.document

import com.whisperbook.app.domain.ImportedBook
import com.whisperbook.app.domain.model.BookFormat
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class EpubPublicationParserTest {
    @Test
    fun `extracts metadata toc and spine content from a synthetic epub`() {
        val file = File.createTempFile("publication", ".epub")
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                zip.entry("mimetype", "application/epub+zip")
                zip.entry(
                    "META-INF/container.xml",
                    """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles></container>""",
                )
                zip.entry(
                    "OEBPS/content.opf",
                    """<package xmlns="http://www.idpf.org/2007/opf"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Lantern Tales</dc:title><dc:creator>A. Storyteller</dc:creator></metadata><manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/><item id="c1" href="text/chapter1.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="c1"/></spine></package>""",
                )
                zip.entry(
                    "OEBPS/nav.xhtml",
                    """<html xmlns="http://www.w3.org/1999/xhtml"><body><nav role="doc-toc"><a href="text/chapter1.xhtml#start">The Moonlit Wood</a></nav></body></html>""",
                )
                zip.entry(
                    "OEBPS/text/chapter1.xhtml",
                    """<html xmlns="http://www.w3.org/1999/xhtml"><body><h1>CHAPTER I</h1><p>The forest woke.</p><p>“Follow me,” said Elara.</p></body></html>""",
                )
            }

            val result = EpubPublicationParser(ChapterDetector()).extract(
                ImportedBook("fallback", null, BookFormat.EPUB, file, "hash"),
            )

            assertEquals("Lantern Tales", result.title)
            assertEquals("A. Storyteller", result.author)
            assertEquals("The Moonlit Wood", result.chapters.single().title)
            assertEquals(listOf("The forest woke.", "“Follow me,” said Elara."), result.chapters.single().paragraphs)
        } finally {
            file.delete()
        }
    }

    private fun ZipOutputStream.entry(path: String, content: String) {
        putNextEntry(ZipEntry(path))
        write(content.toByteArray())
        closeEntry()
    }
}
