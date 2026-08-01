package com.whisperbook.app.engine.audio

import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Writes mono or interleaved PCM16 samples to a standards-compliant RIFF/WAVE container. */
object Pcm16WavWriter {
    private const val HEADER_BYTES = 44L
    private const val PCM_FORMAT = 1
    private const val BITS_PER_SAMPLE = 16

    fun encode(
        pcm16: ShortArray,
        sampleRate: Int,
        channelCount: Int = 1,
    ): ByteArray {
        val expectedSize = checkedFileSize(pcm16, sampleRate, channelCount)
        require(expectedSize <= Int.MAX_VALUE) { "WAV is too large to encode in memory" }
        return ByteArrayOutputStream(expectedSize.toInt()).use { output ->
            write(output, pcm16, sampleRate, channelCount)
            output.toByteArray()
        }
    }

    /**
     * Writes in the target directory and atomically replaces [target] only after data has been
     * flushed to disk. A same-filesystem rename fallback is used on filesystems without ATOMIC_MOVE.
     */
    fun writeAtomic(
        target: File,
        pcm16: ShortArray,
        sampleRate: Int,
        channelCount: Int = 1,
    ) {
        checkedFileSize(pcm16, sampleRate, channelCount)
        val parent = requireNotNull(target.parentFile) { "Target must have a parent directory" }
        check(parent.exists() || parent.mkdirs()) { "Could not create ${parent.absolutePath}" }
        val temporary = File.createTempFile(".${target.name}.", ".part", parent)

        try {
            FileOutputStream(temporary).use { fileOutput ->
                BufferedOutputStream(fileOutput).use { buffered ->
                    write(buffered, pcm16, sampleRate, channelCount)
                    buffered.flush()
                    fileOutput.fd.sync()
                }
            }
            moveReplacing(temporary, target)
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    fun write(
        output: OutputStream,
        pcm16: ShortArray,
        sampleRate: Int,
        channelCount: Int = 1,
    ) {
        val fileSize = checkedFileSize(pcm16, sampleRate, channelCount)
        val dataBytes = pcm16.size.toLong() * Short.SIZE_BYTES
        val byteRate = sampleRate.toLong() * channelCount * (BITS_PER_SAMPLE / 8)
        val blockAlign = channelCount * (BITS_PER_SAMPLE / 8)

        output.writeAscii("RIFF")
        output.writeUInt32Le(fileSize - 8L)
        output.writeAscii("WAVE")
        output.writeAscii("fmt ")
        output.writeUInt32Le(16L)
        output.writeUInt16Le(PCM_FORMAT)
        output.writeUInt16Le(channelCount)
        output.writeUInt32Le(sampleRate.toLong())
        output.writeUInt32Le(byteRate)
        output.writeUInt16Le(blockAlign)
        output.writeUInt16Le(BITS_PER_SAMPLE)
        output.writeAscii("data")
        output.writeUInt32Le(dataBytes)
        pcm16.forEach { sample -> output.writeUInt16Le(sample.toInt() and 0xffff) }
    }

    private fun checkedFileSize(pcm16: ShortArray, sampleRate: Int, channelCount: Int): Long {
        require(sampleRate > 0) { "sampleRate must be positive" }
        require(channelCount > 0) { "channelCount must be positive" }
        require(pcm16.size % channelCount == 0) { "PCM samples must contain complete frames" }
        val dataBytes = pcm16.size.toLong() * Short.SIZE_BYTES
        require(dataBytes <= 0xffff_ffffL) { "PCM payload exceeds RIFF/WAVE's 32-bit limit" }
        val fileSize = HEADER_BYTES + dataBytes
        require(fileSize - 8L <= 0xffff_ffffL) { "WAV file exceeds RIFF's 32-bit limit" }
        return fileSize
    }

    private fun moveReplacing(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun OutputStream.writeAscii(value: String) = write(value.toByteArray(Charsets.US_ASCII))

    private fun OutputStream.writeUInt16Le(value: Int) {
        require(value in 0..0xffff)
        write(value and 0xff)
        write((value ushr 8) and 0xff)
    }

    private fun OutputStream.writeUInt32Le(value: Long) {
        require(value in 0..0xffff_ffffL)
        repeat(4) { shift -> write(((value ushr (shift * 8)) and 0xff).toInt()) }
    }
}
