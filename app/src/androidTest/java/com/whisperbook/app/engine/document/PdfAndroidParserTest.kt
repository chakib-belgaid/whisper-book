package com.whisperbook.app.engine.document

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.whisperbook.app.domain.ImportedBook
import com.whisperbook.app.domain.model.BookFormat
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfAndroidParserTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun extractsTextLayerPdfFullyOffline() = runBlocking {
        val file = File(context.cacheDir, "text-layer-story.pdf")
        writePdf(file, scanned = false)

        val publication = OfflinePublicationExtractor(context).extract(imported(file)).getOrThrow()
        val text = publication.chapters.flatMap { it.paragraphs }.joinToString(" ")

        assertTrue(publication.chapters.isNotEmpty())
        assertTrue(text.contains("moonlit bridge", ignoreCase = true))
        assertTrue(text.contains("Elara", ignoreCase = true))
    }

    @Test
    fun recognizesImageOnlyPdfWithBundledOcr() = runBlocking {
        val file = File(context.cacheDir, "scanned-story.pdf")
        writePdf(file, scanned = true)

        val publication = OfflinePublicationExtractor(context).extract(imported(file)).getOrThrow()
        val text = publication.chapters.flatMap { it.paragraphs }.joinToString(" ")

        assertTrue(publication.chapters.isNotEmpty())
        assertTrue(text.contains("moonlit bridge", ignoreCase = true))
        assertTrue(text.contains("forest", ignoreCase = true))
    }

    private fun imported(file: File) = ImportedBook(
        title = "Offline PDF Story",
        author = "Device Test",
        format = BookFormat.PDF,
        privateFile = file,
        sha256 = "device-test",
    )

    private fun writePdf(file: File, scanned: Boolean) {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        if (scanned) {
            val bitmap = Bitmap.createBitmap(PAGE_WIDTH, PAGE_HEIGHT, Bitmap.Config.ARGB_8888)
            drawStory(Canvas(bitmap))
            page.canvas.drawBitmap(bitmap, 0f, 0f, null)
            bitmap.recycle()
        } else {
            drawStory(page.canvas)
        }
        document.finishPage(page)
        file.outputStream().use(document::writeTo)
        document.close()
    }

    private fun drawStory(canvas: Canvas) {
        canvas.drawColor(Color.WHITE)
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 72f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 42f
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        }
        canvas.drawText("CHAPTER ONE", 80f, 150f, title)
        canvas.drawText("Elara crossed the moonlit bridge.", 80f, 280f, body)
        canvas.drawText("\"The forest remembers us,\" she said.", 80f, 365f, body)
        canvas.drawText("A fox waited beside the old lantern.", 80f, 450f, body)
    }

    private companion object {
        const val PAGE_WIDTH = 1_200
        const val PAGE_HEIGHT = 1_600
    }
}
