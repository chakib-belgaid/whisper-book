package com.whisperbook.app.engine.document

import org.junit.Assert.assertEquals
import org.junit.Test

class ParsingComputePathTest {
    @Test
    fun `structural parsing accurately reports cpu as non neural work`() {
        assertEquals("structural_cpu", ParsingComputePath.STRUCTURAL_CPU.diagnosticName)
        assertEquals("not_applicable", ParsingComputePath.STRUCTURAL_CPU.acceleratorControl)
    }

    @Test
    fun `ocr reports runtime managed delegate selection`() {
        assertEquals("mlkit_ocr", ParsingComputePath.ML_KIT_OCR_RUNTIME_MANAGED.diagnosticName)
        assertEquals("runtime_managed", ParsingComputePath.ML_KIT_OCR_RUNTIME_MANAGED.acceleratorControl)
    }
}
