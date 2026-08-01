package com.whisperbook.app.engine.document

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.whisperbook.app.domain.BookImporter
import com.whisperbook.app.domain.ImportedBook
import com.whisperbook.app.domain.model.BookFormat
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Imports a SAF document into app-private storage. The source extension and MIME type are never
 * trusted to choose the parser; [SignatureBookFormatDetector] validates the copied bytes first.
 */
class SafBookImporter(
    private val context: Context,
    private val privateBookDirectory: File = File(context.filesDir, "publications"),
) : BookImporter {
    override suspend fun import(uri: Uri): Result<ImportedBook> = withContext(Dispatchers.IO) {
        try {
            check(privateBookDirectory.exists() || privateBookDirectory.mkdirs()) {
                "Could not create private publication directory"
            }

            val displayName = context.contentResolver.displayName(uri) ?: "Imported book"
            val temporary = File.createTempFile("import-", ".pending", privateBookDirectory)
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    temporary.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            if (read == 0) continue
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                        }
                    }
                } ?: error("The selected document cannot be opened")

                check(temporary.length() > 0L) { "The selected document is empty" }
                val format = SignatureBookFormatDetector.detect(temporary)
                val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
                val suffix = if (format == BookFormat.PDF) ".pdf" else ".epub"
                val destination = File(privateBookDirectory, "$sha256$suffix")
                if (destination.exists()) {
                    check(destination.isFile && destination.length() == temporary.length()) {
                        "A conflicting private publication already exists"
                    }
                    temporary.delete()
                } else if (!temporary.renameTo(destination)) {
                    temporary.copyTo(destination, overwrite = false)
                    check(temporary.delete()) { "Imported document copied but temporary file could not be removed" }
                }

                Result.success(ImportedBook(
                    title = displayName.substringBeforeLast('.').ifBlank { "Imported book" },
                    author = null,
                    format = format,
                    privateFile = destination,
                    sha256 = sha256,
                ))
            } catch (throwable: Throwable) {
                temporary.delete()
                throw throwable
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            Result.failure(throwable)
        }
    }
}

object SignatureBookFormatDetector {
    private val pdfSignature = "%PDF-".toByteArray(Charsets.US_ASCII)
    private val zipSignatures = setOf(
        listOf(0x50, 0x4B, 0x03, 0x04),
        listOf(0x50, 0x4B, 0x05, 0x06),
        listOf(0x50, 0x4B, 0x07, 0x08),
    )

    fun detect(file: File): BookFormat {
        require(file.isFile) { "Imported document is not a file" }
        val header = ByteArray(8)
        val count = FileInputStream(file).use { it.read(header) }
        if (count >= pdfSignature.size && header.copyOfRange(0, pdfSignature.size).contentEquals(pdfSignature)) {
            return BookFormat.PDF
        }

        val zipHeader = header.take(4).map(Byte::toUByte).map(UByte::toInt)
        if (count >= 4 && zipHeader in zipSignatures && isEpubArchive(file)) return BookFormat.EPUB
        throw UnsupportedPublicationException("Only signature-valid PDF and EPUB documents are supported")
    }

    private fun isEpubArchive(file: File): Boolean = runCatching {
        ZipFile(file).use { zip ->
            val mimetype = zip.getEntry("mimetype")?.let { entry ->
                zip.getInputStream(entry).bufferedReader(Charsets.US_ASCII).use { it.readText().trim() }
            }
            mimetype == "application/epub+zip" && zip.getEntry("META-INF/container.xml") != null
        }
    }.getOrDefault(false)
}

class UnsupportedPublicationException(message: String) : IllegalArgumentException(message)

private fun ContentResolver.displayName(uri: Uri): String? = runCatching {
    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0) cursor.getString(index) else null
    }
}.getOrNull()
