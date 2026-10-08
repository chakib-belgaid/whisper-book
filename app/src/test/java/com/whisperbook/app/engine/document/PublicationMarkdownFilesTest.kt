package com.whisperbook.app.engine.document

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicationMarkdownFilesTest {
    @Test
    fun `writes and replaces a UTF-8 Markdown sidecar beside the private source`() {
        val directory = Files.createTempDirectory("publication-markdown").toFile()
        val source = File(directory, "book-hash.epub").apply { writeText("epub") }
        try {
            val markdownFile = PublicationMarkdownFiles.write(source, "# Mémoire\n")

            assertEquals(File(directory, "book-hash.md"), markdownFile)
            assertTrue(markdownFile.isFile)
            assertEquals("# Mémoire\n", markdownFile.readText(Charsets.UTF_8))

            PublicationMarkdownFiles.write(source, "# Mémoire corrigée\n")

            assertEquals("# Mémoire corrigée\n", markdownFile.readText(Charsets.UTF_8))
            assertFalse(directory.listFiles().orEmpty().any { it.name.endsWith(".pending") })
        } finally {
            directory.deleteRecursively()
        }
    }
}
