package com.whisperbook.app.engine.document

import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidPdfOcrHookTest {
    @Test
    fun `recognizes sequentially preserves page order and normalizes text`() = runTest {
        val source = FakePageSource(2)
        val recognizer = FakeRecognizer(
            mapOf(
                0 to "  First   page.\n\nSecond paragraph.  ",
                1 to " Third\u00A0page. ",
            ),
        )
        val hook = AndroidPdfOcrHook(
            pageSourceFactory = PdfPageSourceFactory { source },
            recognizerFactory = OcrPageRecognizerFactory { recognizer },
        )
        val progress = mutableListOf<Pair<Int, Int>>()

        val output = hook.extractText(File("synthetic.pdf")) { completed, total ->
            progress += completed to total
        }

        assertEquals("First page.\n\nSecond paragraph.\n\nThird page.", output)
        assertEquals(listOf(1 to 2, 2 to 2), progress)
        assertEquals(listOf(0, 1), source.renderOrder)
        assertEquals(listOf(0, 1), recognizer.recognitionOrder)
        assertTrue(source.renderedPages.all(FakePage::closed))
        assertTrue(source.closed)
        assertTrue(recognizer.closed)
    }

    @Test
    fun `empty page source fails explicitly and is closed`() = runTest {
        val source = FakePageSource(0)
        val hook = AndroidPdfOcrHook(
            pageSourceFactory = PdfPageSourceFactory { source },
            recognizerFactory = OcrPageRecognizerFactory { FakeRecognizer(emptyMap()) },
        )

        val failure = runCatching { hook.extractText(File("empty.pdf")) }.exceptionOrNull()
        assertTrue(failure is EmptyPdfException)
        assertTrue(source.closed)
    }

    @Test
    fun `cancellation closes the current page recognizer and source`() = runTest {
        val source = FakePageSource(2)
        val started = CompletableDeferred<Unit>()
        val recognizer = object : OcrPageRecognizer {
            var closed = false

            override suspend fun recognize(page: RenderedOcrPage): String {
                started.complete(Unit)
                awaitCancellation()
            }

            override fun close() {
                closed = true
            }
        }
        val hook = AndroidPdfOcrHook(
            pageSourceFactory = PdfPageSourceFactory { source },
            recognizerFactory = OcrPageRecognizerFactory { recognizer },
        )

        val job = launch { hook.extractText(File("cancel.pdf")) }
        started.await()
        job.cancelAndJoin()

        assertEquals(listOf(0), source.renderOrder)
        assertTrue(source.renderedPages.single().closed)
        assertTrue(recognizer.closed)
        assertTrue(source.closed)
    }

    private class FakePageSource(
        override val pageCount: Int,
    ) : PdfPageSource {
        val renderOrder = mutableListOf<Int>()
        val renderedPages = mutableListOf<FakePage>()
        var closed = false

        override fun render(pageIndex: Int): RenderedOcrPage {
            renderOrder += pageIndex
            return FakePage(pageIndex).also(renderedPages::add)
        }

        override fun close() {
            closed = true
        }
    }

    private class FakePage(
        override val pageIndex: Int,
    ) : RenderedOcrPage {
        var closed = false
        override fun close() {
            closed = true
        }
    }

    private class FakeRecognizer(
        private val results: Map<Int, String>,
    ) : OcrPageRecognizer {
        val recognitionOrder = mutableListOf<Int>()
        var closed = false

        override suspend fun recognize(page: RenderedOcrPage): String {
            recognitionOrder += page.pageIndex
            return results.getValue(page.pageIndex)
        }

        override fun close() {
            closed = true
        }
    }
}
