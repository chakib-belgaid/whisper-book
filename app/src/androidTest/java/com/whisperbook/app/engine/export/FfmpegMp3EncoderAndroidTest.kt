package com.whisperbook.app.engine.export

import android.content.Context
import android.media.MediaExtractor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.whisperbook.app.engine.audio.Pcm16WavWriter
import java.io.File
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FfmpegMp3EncoderAndroidTest {
    @Test
    fun twoFinalizedWavsBecomeOnePlayableMp3() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.cacheDir, "mp3-encoder-test").apply { mkdirs() }
        val first = File(directory, "first.wav")
        val second = File(directory, "second.wav")
        val manifest = File(directory, "inputs.txt")
        val output = File(directory, "book.mp3")
        try {
            Pcm16WavWriter.writeAtomic(first, tone(220.0), SAMPLE_RATE)
            Pcm16WavWriter.writeAtomic(second, tone(330.0), SAMPLE_RATE)
            manifest.writeText(ffmpegConcatManifest(listOf(first, second)))

            FfmpegMp3Encoder().encode(
                manifest = manifest,
                destination = output,
                title = "Test Book",
                artist = "Whisperbook",
            )

            assertTrue(output.isFile)
            assertTrue(output.length() > 1_000L)
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(output.absolutePath)
                assertEquals(1, extractor.trackCount)
                assertEquals("audio/mpeg", extractor.getTrackFormat(0).getString("mime"))
            } finally {
                extractor.release()
            }
        } finally {
            listOf(first, second, manifest, output).forEach(File::delete)
            directory.delete()
        }
    }

    private fun tone(frequencyHz: Double): ShortArray = ShortArray(SAMPLE_RATE / 5) { sample ->
        (sin(2.0 * PI * frequencyHz * sample / SAMPLE_RATE) * Short.MAX_VALUE * 0.2).toInt().toShort()
    }

    private companion object {
        const val SAMPLE_RATE = 24_000
    }
}
