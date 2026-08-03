package com.whisperbook.app.engine.tts

/** Asset paths for the Supertonic 3 model shipped in the APK. */
internal object SupertonicAssets {
    const val MODEL_VERSION = "sherpa-onnx-supertonic-3-tts-int8-2026-05-11"
    const val ASSET_ROOT = "tts/$MODEL_VERSION"

    val durationPredictorAssetPath = assetPath("duration_predictor.int8.onnx")
    val textEncoderAssetPath = assetPath("text_encoder.int8.onnx")
    val vectorEstimatorAssetPath = assetPath("vector_estimator.int8.onnx")
    val vocoderAssetPath = assetPath("vocoder.int8.onnx")
    val ttsJsonAssetPath = assetPath("tts.json")
    val unicodeIndexerAssetPath = assetPath("unicode_indexer.bin")
    val voiceStyleAssetPath = assetPath("voice.bin")

    fun assetPath(relativePath: String): String {
        val safePath = requireSafeRelativePath(relativePath)
        return "$ASSET_ROOT/$safePath"
    }
}

internal fun requireSafeRelativePath(path: String): String {
    val normalized = path.replace('\\', '/')
    require(normalized.isNotBlank()) { "Asset path must not be blank" }
    require(!normalized.startsWith('/')) { "Asset path must be relative: $path" }
    require(normalized.split('/').none { it.isBlank() || it == "." || it == ".." }) {
        "Unsafe asset path: $path"
    }
    require('\n' !in normalized && '\r' !in normalized && '\t' !in normalized) {
        "Asset path contains a control character"
    }
    return normalized
}
