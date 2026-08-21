package com.whisperbook.app.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
        hideSystemChrome()
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

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemChrome()
    }

    private fun hideSystemChrome() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    companion object {
        const val EXTRA_SCREEN = "screen"
        const val NOW_PLAYING = "now-playing"
        const val CURRENT_CHAPTER = "current-chapter"
    }
}
