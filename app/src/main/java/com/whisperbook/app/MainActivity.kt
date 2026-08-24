package com.whisperbook.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.doOnPreDraw
import com.whisperbook.app.diagnostics.BetaDiagnostics
import com.whisperbook.app.diagnostics.UiPerformanceMonitor
import com.whisperbook.app.integration.WhisperbookViewModel
import com.whisperbook.app.ui.WhisperbookApp
import com.whisperbook.app.ui.system.enableWhisperbookEdgeToEdge

class MainActivity : ComponentActivity() {
    private lateinit var performanceMonitor: UiPerformanceMonitor

    private val viewModel: WhisperbookViewModel by viewModels {
        val application = application as WhisperbookApplication
        WhisperbookViewModel.Factory(application.container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableWhisperbookEdgeToEdge()
        performanceMonitor = UiPerformanceMonitor()
        setContent {
            WhisperbookApp(viewModel = viewModel)
        }
        window.decorView.doOnPreDraw { BetaDiagnostics.recordFirstFrame() }
    }

    override fun onResume() {
        super.onResume()
        performanceMonitor.start()
    }

    override fun onPause() {
        performanceMonitor.stop()
        super.onPause()
    }
}
