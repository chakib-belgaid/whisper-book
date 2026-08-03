package com.whisperbook.app.engine.audio

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.whisperbook.app.engine.tts.SherpaKittenTtsEngine
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalVoicePreviewPlayerAndroidTest {
    @Test
    fun embeddedVoiceSynthesizesAndPlaysThroughAndroidAudioTrack() = runBlocking {
        val engine = SherpaKittenTtsEngine(ApplicationProvider.getApplicationContext())
        val player = LocalVoicePreviewPlayer(engine)
        try {
            player.play(
                text = "This is a short voice preview.",
                voice = SherpaKittenTtsEngine.KITTEN_VOICES.first(),
                speed = 1f,
            ).getOrThrow()
        } finally {
            player.close()
        }
    }
}
