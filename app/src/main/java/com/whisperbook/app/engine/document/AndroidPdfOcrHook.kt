package com.whisperbook.app.engine.document

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

open class PdfImportException(message: String, cause: Throwable? = null) : Exception(message, cause)

class EncryptedPdfException(cause: Throwable? = null) :
    PdfImportException("The PDF is encrypted or password protected and cannot be read offline.", cause)

class CorruptPdfException(cause: Throwable? = null) :
    PdfImportException("The PDF is corrupt or is not a readable PDF document.", cause)

class EmptyPdfException(message: String = "The PDF contains no pages or recognizable text.") :
    PdfImportException(message)

class PdfOcrRecognitionException(pageNumber: Int, cause: Throwable) :
    PdfImportException("Offline text recognition failed on PDF page $pageNumber.", cause)

/**
 * Fully local scanned-PDF OCR. Pages are intentionally rendered and recognized one at a time so
 * memory is bounded independently of document length and reading order cannot race.
 */
class AndroidPdfOcrHook private constructor(
    private val pageSourceFactory: PdfPageSourceFactory,
    private val recognizerFactory: OcrPageRecognizerFactory,
) : PdfOcrHook {
    constructor(
        maxPageDimensionPx: Int = DEFAULT_MAX_PAGE_DIMENSION_PX,
        maxBitmapBytes: Long = DEFAULT_MAX_BITMAP_BYTES,
    ) : this(
        pageSourceFactory = AndroidPdfPageSourceFactory(maxPageDimensionPx, maxBitmapBytes),
        recognizerFactory = MlKitPageRecognizerFactory,
    )

    internal constructor(
        pageSourceFactory: PdfPageSourceFactory,
        recognizerFactory: OcrPageRecognizerFactory,
        @Suppress("UNUSED_PARAMETER") testing: Unit = Unit,
    ) : this(pageSourceFactory, recognizerFactory)

    override suspend fun extractText(file: File): String = extractText(file) { _, _ -> }

    override suspend fun extractText(
        file: File,
        onProgress: suspend (completedUnits: Int, totalUnits: Int) -> Unit,
    ): String = withContext(Dispatchers.Default) {
        val source = openSource(file)
        source.use {
            if (source.pageCount <= 0) throw EmptyPdfException("The PDF contains no pages.")
            recognizerFactory.create().use { recognizer ->
                val results = ArrayList<IndexedValue<String>>(source.pageCount)
                for (pageIndex in 0 until source.pageCount) {
                    coroutineContext.ensureActive()
                    source.render(pageIndex).use { page ->
                        val text = try {
                            recognizer.recognize(page)
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (throwable: Throwable) {
                            throw PdfOcrRecognitionException(pageIndex + 1, throwable)
                        }
                        results += IndexedValue(pageIndex, text)
                    }
                    onProgress(pageIndex + 1, source.pageCount)
                }
                OcrPageTextAssembler.assemble(results)
                    .takeIf(String::isNotBlank)
                    ?: throw EmptyPdfException("No text was recognized on any PDF page.")
            }
        }
    }

    private fun openSource(file: File): PdfPageSource = try {
        pageSourceFactory.open(file)
    } catch (encrypted: EncryptedPdfException) {
        throw encrypted
    } catch (corrupt: CorruptPdfException) {
        throw corrupt
    } catch (security: SecurityException) {
        throw EncryptedPdfException(security)
    } catch (io: IOException) {
        throw CorruptPdfException(io)
    } catch (illegal: IllegalArgumentException) {
        throw CorruptPdfException(illegal)
    }

    companion object {
        const val DEFAULT_MAX_PAGE_DIMENSION_PX = 2_048
        const val DEFAULT_MAX_BITMAP_BYTES = 16L * 1024L * 1024L
    }
}

internal interface RenderedOcrPage : AutoCloseable {
    val pageIndex: Int
}

internal interface PdfPageSource : AutoCloseable {
    val pageCount: Int
    fun render(pageIndex: Int): RenderedOcrPage
}

internal fun interface PdfPageSourceFactory {
    fun open(file: File): PdfPageSource
}

internal interface OcrPageRecognizer : AutoCloseable {
    suspend fun recognize(page: RenderedOcrPage): String
}

internal fun interface OcrPageRecognizerFactory {
    fun create(): OcrPageRecognizer
}

internal object OcrPageTextAssembler {
    fun assemble(results: List<IndexedValue<String>>): String {
        if (results.isEmpty()) return ""
        require(results.map { it.index }.distinct().size == results.size) { "OCR page indexes must be unique" }
        return results
            .sortedBy(IndexedValue<String>::index)
            .mapNotNull { indexed ->
                ParagraphNormalizer.normalize(indexed.value)
                    .takeIf(List<String>::isNotEmpty)
                    ?.joinToString("\n\n")
            }
            .joinToString("\n\n")
            .trim()
    }
}

private class AndroidPdfPageSourceFactory(
    private val maxPageDimensionPx: Int,
    private val maxBitmapBytes: Long,
) : PdfPageSourceFactory {
    init {
        require(maxPageDimensionPx in 256..8_192) { "maxPageDimensionPx must be between 256 and 8192" }
        require(maxBitmapBytes in 1L * 1024L * 1024L..128L * 1024L * 1024L) {
            "maxBitmapBytes must be between 1 MiB and 128 MiB"
        }
    }

    override fun open(file: File): PdfPageSource {
        if (!file.isFile || file.length() == 0L) throw CorruptPdfException()
        val descriptor = try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                ?: throw CorruptPdfException()
        } catch (security: SecurityException) {
            throw EncryptedPdfException(security)
        } catch (io: IOException) {
            throw CorruptPdfException(io)
        }
        val renderer = try {
            PdfRenderer(descriptor)
        } catch (security: SecurityException) {
            descriptor.close()
            throw EncryptedPdfException(security)
        } catch (throwable: Throwable) {
            descriptor.close()
            throw CorruptPdfException(throwable)
        }
        return AndroidPdfPageSource(renderer, descriptor, maxPageDimensionPx, maxBitmapBytes)
    }
}

private class AndroidPdfPageSource(
    private val renderer: PdfRenderer,
    private val descriptor: ParcelFileDescriptor,
    private val maxPageDimensionPx: Int,
    private val maxBitmapBytes: Long,
) : PdfPageSource {
    override val pageCount: Int get() = renderer.pageCount

    override fun render(pageIndex: Int): RenderedOcrPage {
        require(pageIndex in 0 until pageCount) { "PDF page index is out of bounds" }
        val page = try {
            renderer.openPage(pageIndex)
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            throw CorruptPdfException(throwable)
        }
        try {
            val (width, height) = boundedDimensions(page.width, page.height)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return AndroidRenderedOcrPage(pageIndex, bitmap)
            } catch (throwable: Throwable) {
                bitmap.recycle()
                throw throwable
            }
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            throw CorruptPdfException(throwable)
        } finally {
            page.close()
        }
    }

    private fun boundedDimensions(sourceWidth: Int, sourceHeight: Int): Pair<Int, Int> {
        check(sourceWidth > 0 && sourceHeight > 0) { "PDF page has invalid dimensions" }
        val sourcePixels = sourceWidth.toDouble() * sourceHeight.toDouble()
        val maxPixels = maxBitmapBytes.toDouble() / ARGB_8888_BYTES_PER_PIXEL
        val scale = min(
            min(1.0, maxPageDimensionPx.toDouble() / maxOf(sourceWidth, sourceHeight)),
            min(1.0, sqrt(maxPixels / sourcePixels)),
        )
        return floor(sourceWidth * scale).toInt().coerceAtLeast(1) to
            floor(sourceHeight * scale).toInt().coerceAtLeast(1)
    }

    override fun close() {
        try {
            renderer.close()
        } finally {
            descriptor.close()
        }
    }

    companion object {
        private const val ARGB_8888_BYTES_PER_PIXEL = 4.0
    }
}

private class AndroidRenderedOcrPage(
    override val pageIndex: Int,
    val bitmap: Bitmap,
) : RenderedOcrPage {
    override fun close() {
        if (!bitmap.isRecycled) bitmap.recycle()
    }
}

private object MlKitPageRecognizerFactory : OcrPageRecognizerFactory {
    override fun create(): OcrPageRecognizer = MlKitPageRecognizer()
}

private class MlKitPageRecognizer : OcrPageRecognizer {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun recognize(page: RenderedOcrPage): String {
        val androidPage = page as? AndroidRenderedOcrPage
            ?: error("ML Kit recognizer requires an Android bitmap page")
        val task = recognizer.process(InputImage.fromBitmap(androidPage.bitmap, 0))
        return try {
            task.awaitCancellable().text
        } catch (cancellation: CancellationException) {
            // ML Kit Tasks cannot be cancelled. Keep the bitmap alive until native recognition is
            // done, then propagate coroutine cancellation so the next page never starts.
            withContext(NonCancellable) { task.awaitCompletion().text }
            throw cancellation
        }
    }

    override fun close() = recognizer.close()
}

private suspend fun <T> Task<T>.awaitCancellable(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { value -> if (continuation.isActive) continuation.resume(value) }
    addOnFailureListener { error -> if (continuation.isActive) continuation.resumeWithException(error) }
    addOnCanceledListener { if (continuation.isActive) continuation.cancel() }
}

private suspend fun <T> Task<T>.awaitCompletion(): T = suspendCoroutine { continuation ->
    addOnSuccessListener(continuation::resume)
    addOnFailureListener(continuation::resumeWithException)
    addOnCanceledListener { continuation.resumeWithException(CancellationException("ML Kit task cancelled")) }
}
