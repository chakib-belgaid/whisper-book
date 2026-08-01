package com.whisperbook.app.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import com.whisperbook.app.ui.WhisperbookApp
import com.whisperbook.app.ui.navigation.WhisperbookDestination
import com.whisperbook.app.ui.screens.WhisperbookAppState

/** Debug-only deterministic surface used by the screenshot fidelity loop. */
class VisualHarnessActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val requested = intent.getStringExtra(EXTRA_SCREEN)
        val start = when (requested) {
            CURRENT_CHAPTER -> WhisperbookDestination.CurrentChapter.route()
            else -> WhisperbookDestination.NowPlaying.route
        }
        setContent {
            WhisperbookApp(
                appState = remember { WhisperbookAppState().apply { togglePlayback() } },
                startDestination = start,
            )
        }
    }

    companion object {
        const val EXTRA_SCREEN = "screen"
        const val NOW_PLAYING = "now-playing"
        const val CURRENT_CHAPTER = "current-chapter"
    }
}
