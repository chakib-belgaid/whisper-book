package com.whisperbook.app.engine.export

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.whisperbook.app.data.local.db.WhisperBookDatabase
import com.whisperbook.app.domain.BookMp3Exporter
import com.whisperbook.app.domain.model.BookMp3ExportProgress
import com.whisperbook.app.domain.model.BookMp3ExportResult
import com.whisperbook.app.domain.model.BookMp3ExportStage
import com.whisperbook.app.playback.PlaybackQueueSource
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Builds one local MP3 from the same finalized WAV segments used by Media3 playback. */
class FfmpegBookMp3Exporter(
    context: Context,
    private val database: WhisperBookDatabase,
    private val queueSource: PlaybackQueueSource,
    private val encoder: FfmpegMp3Encoder = FfmpegMp3Encoder(),
) : BookMp3Exporter {
    private val appContext = context.applicationContext
    private val exportCache = File(appContext.cacheDir, "book-mp3-exports")

    override suspend fun export(
        bookId: String,
        destination: Uri,
        onProgress: (BookMp3ExportProgress) -> Unit,
    ): BookMp3ExportResult = withContext(Dispatchers.IO) {
        require(bookId.isNotBlank()) { "Choose a book to export" }
        val book = database.bookDao().getById(bookId)
            ?: error("This book is no longer in the library")
        val chapters = database.chapterDao().getHeadersForBook(bookId).sortedBy { it.ordinal }
        check(chapters.isNotEmpty()) { "This book has no chapters to export yet" }
        check(exportCache.exists() || exportCache.mkdirs()) { "Could not prepare the MP3 export" }

        val exportId = UUID.randomUUID().toString()
        val manifest = File(exportCache, "$exportId.concat.txt")
        val encoded = File(exportCache, "$exportId.mp3")
        try {
            val segmentFiles = ArrayList<File>()
            var durationMs = 0L
            chapters.forEachIndexed { chapterIndex, chapter ->
                coroutineContext.ensureActive()
                val queue = queueSource.loadProgressively(bookId, chapter.id) { _, completed, total ->
                    val withinChapter = if (total > 0) completed.toFloat() / total else 0f
                    val overall = PREPARATION_WEIGHT *
                        (chapterIndex + withinChapter).div(chapters.size).coerceIn(0f, 1f)
                    onProgress(
                        BookMp3ExportProgress(
                            stage = BookMp3ExportStage.PREPARING_AUDIO,
                            progressFraction = overall,
                            chapterNumber = chapterIndex + 1,
                            totalChapters = chapters.size,
                        ),
                    )
                }.getOrThrow()
                queue.segments.forEach { segment ->
                    val path = requireNotNull(segment.audioSegment.path)
                    val file = File(path)
                    check(file.isFile && file.length() > 44L) {
                        "A narrated passage is unavailable for export"
                    }
                    segmentFiles += file
                    durationMs += segment.audioSegment.durationMs.coerceAtLeast(0L)
                }
            }
            check(segmentFiles.isNotEmpty()) { "This book has no narrated audio to export" }

            onProgress(
                BookMp3ExportProgress(
                    stage = BookMp3ExportStage.ENCODING_MP3,
                    progressFraction = PREPARATION_WEIGHT,
                    chapterNumber = chapters.size,
                    totalChapters = chapters.size,
                ),
            )
            manifest.writeText(ffmpegConcatManifest(segmentFiles), Charsets.UTF_8)
            encoder.encode(
                manifest = manifest,
                destination = encoded,
                title = book.title,
                artist = book.author,
            )
            coroutineContext.ensureActive()

            onProgress(
                BookMp3ExportProgress(
                    stage = BookMp3ExportStage.SAVING,
                    progressFraction = SAVING_PROGRESS,
                    chapterNumber = chapters.size,
                    totalChapters = chapters.size,
                ),
            )
            val bytesWritten = copyToDestination(encoded, destination)
            onProgress(
                BookMp3ExportProgress(
                    stage = BookMp3ExportStage.SAVING,
                    progressFraction = 1f,
                    chapterNumber = chapters.size,
                    totalChapters = chapters.size,
                ),
            )
            BookMp3ExportResult(
                chapterCount = chapters.size,
                durationMs = durationMs,
                bytesWritten = bytesWritten,
            )
        } finally {
            manifest.delete()
            encoded.delete()
        }
    }

    private fun copyToDestination(source: File, destination: Uri): Long {
        val resolver = appContext.contentResolver
        val output = resolver.openOutputStream(destination, "wt")
            ?: error("The selected MP3 file could not be opened")
        return BufferedInputStream(FileInputStream(source)).use { input ->
            BufferedOutputStream(output).use { target ->
                val bytes = input.copyTo(target)
                target.flush()
                bytes
            }
        }.also { bytes ->
            check(bytes > 0L) { "The MP3 export was empty" }
        }
    }

    private companion object {
        const val PREPARATION_WEIGHT = 0.78f
        const val SAVING_PROGRESS = 0.96f
    }
}
class FfmpegMp3Encoder {
    fun encode(
        manifest: File,
        destination: File,
        title: String,
        artist: String?,
    ) {
        val arguments = buildList {
            addAll(listOf("-hide_banner", "-loglevel", "error"))
            addAll(listOf("-f", "concat", "-safe", "0", "-i", manifest.absolutePath))
            addAll(listOf("-vn", "-codec:a", "libmp3lame", "-b:a", "96k", "-ac", "1"))
            addAll(listOf("-id3v2_version", "3", "-metadata", "title=$title"))
            artist?.trim()?.takeIf(String::isNotBlank)?.let { addAll(listOf("-metadata", "artist=$it")) }
            addAll(listOf("-y", destination.absolutePath))
        }.toTypedArray()
        val session = FFmpegKit.executeWithArguments(arguments)
        check(ReturnCode.isSuccess(session.returnCode) && destination.isFile && destination.length() > 0L) {
            "The MP3 encoder could not finish this book"
        }
    }
}

internal fun ffmpegConcatManifest(files: List<File>): String {
    require(files.isNotEmpty())
    return files.joinToString(separator = "\n", postfix = "\n") { file ->
        "file '${file.absolutePath.replace("'", "'\\''")}'"
    }
}

internal fun defaultBookMp3FileName(title: String): String {
    val safeStem = title
        .replace(Regex("[\\p{Cntrl}/\\\\:*?\"<>|]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim(' ', '.')
        .take(96)
        .ifBlank { "Whisperbook audiobook" }
    return "$safeStem.mp3"
}
