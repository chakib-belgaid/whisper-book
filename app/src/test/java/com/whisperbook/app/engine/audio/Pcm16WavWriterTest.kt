package com.whisperbook.app.engine.audio

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class Pcm16WavWriterTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `encodes a little endian mono PCM16 wave header and payload`() {
        val encoded = Pcm16WavWriter.encode(
            pcm16 = shortArrayOf(Short.MIN_VALUE, 0, Short.MAX_VALUE),
            sampleRate = 24_000,
        )

        assertEquals("RIFF", encoded.ascii(0, 4))
        assertEquals(42L, encoded.uint32Le(4))
        assertEquals("WAVE", encoded.ascii(8, 4))
        assertEquals("fmt ", encoded.ascii(12, 4))
        assertEquals(1, encoded.uint16Le(20))
        assertEquals(1, encoded.uint16Le(22))
        assertEquals(24_000L, encoded.uint32Le(24))
        assertEquals(48_000L, encoded.uint32Le(28))
        assertEquals(2, encoded.uint16Le(32))
        assertEquals(16, encoded.uint16Le(34))
        assertEquals("data", encoded.ascii(36, 4))
        assertEquals(6L, encoded.uint32Le(40))
        assertArrayEquals(
            byteArrayOf(0x00, 0x80.toByte(), 0x00, 0x00, 0xff.toByte(), 0x7f),
            encoded.copyOfRange(44, encoded.size),
        )
    }

    @Test
    fun `atomic writer leaves only the complete target`() {
        val target = File(temporaryFolder.root, "segment.wav")

        Pcm16WavWriter.writeAtomic(target, shortArrayOf(1, -2, 3), sampleRate = 16_000)

        assertEquals(50L, target.length())
        assertFalse(temporaryFolder.root.listFiles().orEmpty().any { it.extension == "part" })
        assertArrayEquals(
            Pcm16WavWriter.encode(shortArrayOf(1, -2, 3), sampleRate = 16_000),
            target.readBytes(),
        )
    }

    private fun ByteArray.ascii(offset: Int, length: Int) =
        copyOfRange(offset, offset + length).toString(Charsets.US_ASCII)

    private fun ByteArray.uint16Le(offset: Int): Int =
        (this[offset].toInt() and 0xff) or ((this[offset + 1].toInt() and 0xff) shl 8)

    private fun ByteArray.uint32Le(offset: Int): Long =
        (0 until 4).fold(0L) { value, index ->
            value or ((this[offset + index].toLong() and 0xffL) shl (index * 8))
        }
}
