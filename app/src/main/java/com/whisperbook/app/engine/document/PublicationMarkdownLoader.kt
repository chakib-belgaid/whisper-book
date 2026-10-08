package com.whisperbook.app.engine.document

import android.content.Context
import com.whisperbook.app.domain.ImportedBook
import java.io.File
import kotlinx.coroutines.CancellationException

/** Loads a generated sidecar, rebuilding it from the private PDF/EPUB for pre-existing books. */
class PublicationMarkdownLoader(
    context: Context,
    private val extractor: OfflinePublicationExtractor = OfflinePublicationExtractor(context.applicationContext),
) {
    suspend fun load(book: ImportedBook): Result<File> {
        val existing = runCatching { PublicationMarkdownFiles.forSource(book.privateFile) }
            .getOrNull()
            ?.takeIf(File::isFile)
        if (existing != null) return Result.success(existing)

        return try {
            extractor.extract(book).mapCatching {
                PublicationMarkdownFiles.forSource(book.privateFile).also { file ->
                    check(file.isFile && file.length() > 0L) { "The Markdown file was not created" }
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        }
    }
}
