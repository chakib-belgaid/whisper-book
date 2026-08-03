package com.whisperbook.app.engine.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SupertonicAssetsTest {
    @Test
    fun `all model component paths point at the bundled version`() {
        val root = "tts/sherpa-onnx-supertonic-3-tts-int8-2026-05-11"

        assertEquals("$root/duration_predictor.int8.onnx", SupertonicAssets.durationPredictorAssetPath)
        assertEquals("$root/text_encoder.int8.onnx", SupertonicAssets.textEncoderAssetPath)
        assertEquals("$root/vector_estimator.int8.onnx", SupertonicAssets.vectorEstimatorAssetPath)
        assertEquals("$root/vocoder.int8.onnx", SupertonicAssets.vocoderAssetPath)
        assertEquals("$root/tts.json", SupertonicAssets.ttsJsonAssetPath)
        assertEquals("$root/unicode_indexer.bin", SupertonicAssets.unicodeIndexerAssetPath)
        assertEquals("$root/voice.bin", SupertonicAssets.voiceStyleAssetPath)
    }

    @Test
    fun `relative asset helper rejects traversal and absolute paths`() {
        assertEquals(
            "tts/sherpa-onnx-supertonic-3-tts-int8-2026-05-11/lang/en",
            SupertonicAssets.assetPath("lang/en"),
        )
        listOf("../model", "lang/../model", "/absolute", "lang//en", "./model").forEach { path ->
            assertThrows(IllegalArgumentException::class.java) { SupertonicAssets.assetPath(path) }
        }
    }
}
