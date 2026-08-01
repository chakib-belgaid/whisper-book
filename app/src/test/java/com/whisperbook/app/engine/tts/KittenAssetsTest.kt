package com.whisperbook.app.engine.tts

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class KittenAssetsTest {
    @Test
    fun `model and extraction paths are versioned`() {
        val noBackup = File("/private/app/no_backup")

        assertEquals(
            "tts/kitten-nano-en-v0_8-int8/model.int8.onnx",
            KittenAssets.modelAssetPath,
        )
        assertEquals(
            File(noBackup, "whisperbook/tts/kitten-nano-en-v0_8-int8/espeak-ng-data"),
            KittenAssets.extractedDataDir(noBackup),
        )
    }

    @Test
    fun `relative asset helper rejects traversal and absolute paths`() {
        assertEquals("tts/kitten-nano-en-v0_8-int8/lang/en", KittenAssets.assetPath("lang/en"))
        listOf("../model", "lang/../model", "/absolute", "lang//en", "./model").forEach { path ->
            assertThrows(IllegalArgumentException::class.java) { KittenAssets.assetPath(path) }
        }
    }

    @Test
    fun `manifest round trips deterministically and rejects a different model version`() {
        val manifest = AssetExtractionManifest(
            schemaVersion = KittenAssets.EXTRACTION_SCHEMA,
            modelVersion = KittenAssets.MODEL_VERSION,
            sourceAssetRoot = KittenAssets.ESPEAK_ASSET_ROOT,
            files = listOf(
                ExtractedAsset("phontab", 42),
                ExtractedAsset("lang/en", 12),
            ),
        )

        val encoded = manifest.encode()
        val decoded = AssetExtractionManifest.decode(encoded)

        assertNotNull(decoded)
        assertEquals(listOf("lang/en", "phontab"), decoded!!.files.map { it.relativePath })
        assertTrue(decoded.isCurrentFor(KittenAssets.MODEL_VERSION, KittenAssets.ESPEAK_ASSET_ROOT))
        assertFalse(decoded.isCurrentFor("a-new-model-version", KittenAssets.ESPEAK_ASSET_ROOT))
    }

    @Test
    fun `manifest parser fails closed for incomplete metadata`() {
        assertNull(AssetExtractionManifest.decode("schema=1\nmodel=kitten\n"))
        assertNull(
            AssetExtractionManifest.decode(
                "schema=1\nmodel=kitten\nsource=tts/kitten\nfile=../escape\t1\n",
            ),
        )
    }
}
