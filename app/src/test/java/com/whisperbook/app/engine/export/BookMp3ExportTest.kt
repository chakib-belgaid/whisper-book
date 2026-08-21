package com.whisperbook.app.engine.export

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookMp3ExportTest {
    @Test
    fun suggestedFileNameRemovesProviderUnsafeCharacters() {
        assertEquals(
            "Moonlit Wood A Whisperbook.mp3",
            defaultBookMp3FileName("  Moonlit/Wood: A \\ Whisperbook?  "),
        )
        assertEquals("Whisperbook audiobook.mp3", defaultBookMp3FileName("..."))
    }

    @Test
    fun concatManifestPreservesOrderAndEscapesApostrophes() {
        val manifest = ffmpegConcatManifest(
            listOf(File("/private/first.wav"), File("/private/author's-second.wav")),
        )

        val lines = manifest.lines().filter(String::isNotBlank)
        assertEquals("file '/private/first.wav'", lines[0])
        assertEquals("file '/private/author'\\''s-second.wav'", lines[1])
        assertTrue(manifest.endsWith("\n"))
        assertFalse(manifest.contains("file:/"))
    }
}
