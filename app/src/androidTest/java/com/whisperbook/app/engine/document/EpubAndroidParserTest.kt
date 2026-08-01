package com.whisperbook.app.engine.document

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.whisperbook.app.domain.ImportedBook
import com.whisperbook.app.domain.model.BookFormat
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EpubAndroidParserTest {
    @Test
    fun platformXmlFactory_extractsAValidEpubWithoutOptionalJaxpSupport() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val epub = File(context.cacheDir, "android-parser-${System.nanoTime()}.epub")
        try {
            ZipOutputStream(epub.outputStream().buffered()).use { zip ->
                zip.entry(
                    "META-INF/container.xml",
                    """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles></container>""",
                )
                zip.entry(
                    "OEBPS/content.opf",
                    """<package xmlns="http://www.idpf.org/2007/opf"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Device Story</dc:title></metadata><manifest><item id="c1" href="chapter.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="c1"/></spine></package>""",
                )
                zip.entry(
                    "OEBPS/chapter.xhtml",
                    """<html xmlns="http://www.w3.org/1999/xhtml"><body><h1>Chapter One</h1><p>The lantern glowed.</p><p>“Walk softly,” Elara said.</p></body></html>""",
                )
            }

            val result = OfflinePublicationExtractor(context).extract(
                ImportedBook(
                    title = "Fallback",
                    author = null,
                    format = BookFormat.EPUB,
                    privateFile = epub,
                    sha256 = "fixture",
                ),
            ).getOrThrow()

            assertEquals("Device Story", result.title)
            assertTrue(result.chapters.isNotEmpty())
            assertTrue(result.chapters.flatMap { it.paragraphs }.any { "lantern" in it })
        } finally {
            epub.delete()
        }
    }

    private fun ZipOutputStream.entry(path: String, body: String) {
        putNextEntry(ZipEntry(path))
        write(body.toByteArray(Charsets.UTF_8))
        closeEntry()
    }
}
