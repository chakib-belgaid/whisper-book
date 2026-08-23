package com.whisperbook.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderTextHighlightTest {
    private val speakerAccent = Color(0xFF355A83)

    @Test
    fun `highlight end follows playback progress and clamps invalid values`() {
        val text = "Read me"

        assertEquals(0, readHighlightEnd(text, -0.25f))
        assertEquals(0, readHighlightEnd(text, 0f))
        assertEquals(4, readHighlightEnd(text, 0.5f))
        assertEquals(text.length, readHighlightEnd(text, 1f))
        assertEquals(text.length, readHighlightEnd(text, 1.25f))
        assertEquals(0, readHighlightEnd(text, Float.NaN))
    }

    @Test
    fun `zero progress and empty text produce no highlighted range`() {
        assertTrue(readAlongText("Waiting", 0f, speakerAccent).spanStyles.isEmpty())
        assertTrue(readAlongText("", 0.8f, speakerAccent).spanStyles.isEmpty())
    }

    @Test
    fun `highlight boundary never splits a unicode code point`() {
        val text = "A📖B"

        assertEquals(3, readHighlightEnd(text, 0.5f))
        assertEquals("A📖", text.substring(0, readHighlightEnd(text, 0.5f)))
    }

    @Test
    fun `rendered read portion uses speaker color plus non-color emphasis`() {
        val rendered = readAlongText("Read me", 0.5f, speakerAccent)
        val highlight = rendered.spanStyles.single()

        assertEquals("Read me", rendered.text)
        assertEquals(0, highlight.start)
        assertEquals(4, highlight.end)
        assertEquals(speakerAccent.copy(alpha = .22f), highlight.item.background)
        assertEquals(FontWeight.SemiBold, highlight.item.fontWeight)
    }
}
