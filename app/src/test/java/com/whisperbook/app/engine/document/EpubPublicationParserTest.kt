package com.whisperbook.app.engine.document

import com.whisperbook.app.domain.ImportedBook
import com.whisperbook.app.domain.model.BookFormat
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubPublicationParserTest {
    @Test
    fun `extracts metadata toc and spine content from a synthetic epub`() = runTest {
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

    @Test
    fun `reports monotonic progress for every item in a long spine`() = runTest {
        val spineSize = 96
        val file = longSpineEpub(spineSize)
        val progress = mutableListOf<Pair<Int, Int>>()
        try {
            val result = EpubPublicationParser(ChapterDetector()).extract(
                importedBook(file),
            ) { completed, total ->
                progress += completed to total
            }

            assertEquals((1..spineSize).map { completed -> completed to spineSize }, progress)
            assertEquals(spineSize, result.chapters.size)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `cancellation after a spine checkpoint prevents the next item from starting`() = runTest {
        val file = longSpineEpub(spineSize = 24)
        val completedItems = mutableListOf<Int>()
        try {
            val extraction = async {
                EpubPublicationParser(ChapterDetector()).extract(
                    importedBook(file),
                ) { completed, _ ->
                    completedItems += completed
                    if (completed == 3) {
                        currentCoroutineContext().cancel(
                            CancellationException("Cancel the long EPUB test after item 3"),
                        )
                    }
                }
            }

            val failure = runCatching { extraction.await() }.exceptionOrNull()

            assertTrue(failure is CancellationException)
            assertEquals(listOf(1, 2, 3), completedItems)
        } finally {
            file.delete()
        }
    }

    private fun longSpineEpub(spineSize: Int): File {
        require(spineSize > 0)
        return File.createTempFile("long-spine-publication", ".epub").also { file ->
            ZipOutputStream(file.outputStream()).use { zip ->
                zip.entry("mimetype", "application/epub+zip")
                zip.entry(
                    "META-INF/container.xml",
                    """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles></container>""",
                )
                val manifest = (1..spineSize).joinToString("") { index ->
                    """<item id="c$index" href="text/chapter$index.xhtml" media-type="application/xhtml+xml"/>"""
                }
                val spine = (1..spineSize).joinToString("") { index ->
                    """<itemref idref="c$index"/>"""
                }
                zip.entry(
                    "OEBPS/content.opf",
                    """<package xmlns="http://www.idpf.org/2007/opf"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Long Spine</dc:title></metadata><manifest>$manifest</manifest><spine>$spine</spine></package>""",
                )
                (1..spineSize).forEach { index ->
                    zip.entry(
                        "OEBPS/text/chapter$index.xhtml",
                        """<html xmlns="http://www.w3.org/1999/xhtml"><body><h1>Chapter $index</h1><p>Story text for section $index.</p></body></html>""",
                    )
                }
            }
        }
    }

    private fun importedBook(file: File) = ImportedBook(
        title = "Long Spine",
        author = null,
        format = BookFormat.EPUB,
        privateFile = file,
        sha256 = "test-hash",
    )

    private fun ZipOutputStream.entry(path: String, content: String) {
        putNextEntry(ZipEntry(path))
        write(content.toByteArray())
        closeEntry()
    }
}
