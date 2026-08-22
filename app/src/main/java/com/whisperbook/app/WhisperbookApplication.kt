package com.whisperbook.app

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.whisperbook.app.diagnostics.BetaDiagnostics
import com.whisperbook.app.integration.WhisperbookAppContainer

class WhisperbookApplication : Application() {
    lateinit var container: WhisperbookAppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        BetaDiagnostics.initialize(this)
        PDFBoxResourceLoader.init(this)
        container = WhisperbookAppContainer(this)
    }
}
