package com.whisperbook.app.engine.document

import com.whisperbook.app.domain.model.BookFormat
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SignatureBookFormatDetectorTest {
    @Test
    fun `detects pdf from bytes even with the wrong extension`() {
        val file = File.createTempFile("book", ".bin")
        try {
            file.writeBytes("%PDF-1.7\nsynthetic".toByteArray())
            assertEquals(BookFormat.PDF, SignatureBookFormatDetector.detect(file))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `rejects a generic zip as epub`() {
        val file = File.createTempFile("book", ".epub")
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("notes.txt"))
                zip.write("not an epub".toByteArray())
                zip.closeEntry()
            }
            assertThrows(UnsupportedPublicationException::class.java) {
                SignatureBookFormatDetector.detect(file)
            }
        } finally {
            file.delete()
        }
    }
}
