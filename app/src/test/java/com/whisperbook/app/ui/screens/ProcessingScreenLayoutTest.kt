package com.whisperbook.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class ProcessingScreenLayoutTest {
    @Test
    fun regularPhoneWidthsKeepNativeScale() {
        assertEquals(1f, processingContentScale(320f), 0.001f)
        assertEquals(1f, processingContentScale(400f), 0.001f)
    }

    @Test
    fun wideLogicalViewportsScaleUpAndRemainCapped() {
        assertEquals(1.5f, processingContentScale(600f), 0.001f)
        assertEquals(1.8f, processingContentScale(752.8f), 0.001f)
        assertEquals(1.8f, processingContentScale(1_200f), 0.001f)
    }
}
