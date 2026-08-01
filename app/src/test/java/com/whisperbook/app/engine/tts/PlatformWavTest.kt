package com.whisperbook.app.engine.tts

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PlatformWavTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `wav reader preserves mono pcm and sample rate`() {
        val file = temporaryFolder.newFile("mono.wav")
        file.writeBytes(wav(sampleRate = 24_000, channels = 1, samples = shortArrayOf(-2, 0, 2)))

        val result = readPcm16Wav(file)

        assertArrayEquals(shortArrayOf(-2, 0, 2), result.pcm16)
        assertEquals(24_000, result.sampleRate)
    }

    @Test
    fun `wav reader downmixes stereo because synthesis result is mono`() {
        val file = temporaryFolder.newFile("stereo.wav")
        file.writeBytes(
            wav(
                sampleRate = 1_000,
                channels = 2,
                samples = shortArrayOf(10_000, -10_000, 20_000, 10_000),
            ),
        )

        val result = readPcm16Wav(file)

        assertArrayEquals(shortArrayOf(0, 15_000), result.pcm16)
        assertEquals(2L, result.durationMs)
    }

    private fun wav(sampleRate: Int, channels: Int, samples: ShortArray): ByteArray {
        val dataSize = samples.size * 2
        return ByteBuffer.allocate(44 + dataSize)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                put("RIFF".toByteArray(Charsets.US_ASCII))
                putInt(36 + dataSize)
                put("WAVE".toByteArray(Charsets.US_ASCII))
                put("fmt ".toByteArray(Charsets.US_ASCII))
                putInt(16)
                putShort(1)
                putShort(channels.toShort())
                putInt(sampleRate)
                putInt(sampleRate * channels * 2)
                putShort((channels * 2).toShort())
                putShort(16)
                put("data".toByteArray(Charsets.US_ASCII))
                putInt(dataSize)
                samples.forEach { putShort(it) }
            }
            .array()
    }
}
