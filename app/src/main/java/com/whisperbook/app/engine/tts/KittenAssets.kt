package com.whisperbook.app.engine.tts

import android.content.res.AssetManager
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/** Paths and the extraction version for the Kitten model shipped in the APK. */
internal object KittenAssets {
    const val MODEL_VERSION = "kitten-nano-en-v0_8-int8"
    const val ASSET_ROOT = "tts/$MODEL_VERSION"
    const val ESPEAK_ASSET_ROOT = "$ASSET_ROOT/espeak-ng-data"
    const val EXTRACTION_SCHEMA = 1

    val modelAssetPath: String = assetPath("model.int8.onnx")
    val voicesAssetPath: String = assetPath("voices.bin")
    val tokensAssetPath: String = assetPath("tokens.txt")

    fun assetPath(relativePath: String): String {
        val safePath = requireSafeRelativePath(relativePath)
        return "$ASSET_ROOT/$safePath"
    }

    fun extractionRoot(noBackupFilesDir: File): File =
        File(noBackupFilesDir, "whisperbook/tts/$MODEL_VERSION")

    fun extractedDataDir(noBackupFilesDir: File): File =
        File(extractionRoot(noBackupFilesDir), "espeak-ng-data")

    fun manifestFile(extractionRoot: File): File = File(extractionRoot, "extraction.manifest")
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

internal data class ExtractedAsset(
    val relativePath: String,
    val byteCount: Long,
)

/**
 * The marker is written into a staging directory only after every asset has been copied.
 * Renaming that directory makes a complete model version visible in one operation.
 */
internal data class AssetExtractionManifest(
    val schemaVersion: Int,
    val modelVersion: String,
    val sourceAssetRoot: String,
    val files: List<ExtractedAsset>,
) {
    fun encode(): String = buildString {
        append("schema=").append(schemaVersion).append('\n')
        append("model=").append(modelVersion).append('\n')
        append("source=").append(sourceAssetRoot).append('\n')
        files.sortedBy(ExtractedAsset::relativePath).forEach { file ->
            append("file=")
                .append(requireSafeRelativePath(file.relativePath))
                .append('\t')
                .append(file.byteCount)
                .append('\n')
        }
    }

    fun isCurrentFor(modelVersion: String, sourceAssetRoot: String): Boolean =
        schemaVersion == KittenAssets.EXTRACTION_SCHEMA &&
            this.modelVersion == modelVersion &&
            this.sourceAssetRoot == sourceAssetRoot &&
            files.isNotEmpty() &&
            files.distinctBy(ExtractedAsset::relativePath).size == files.size &&
            files.all { it.byteCount >= 0 && runCatching { requireSafeRelativePath(it.relativePath) }.isSuccess }

    companion object {
        fun decode(text: String): AssetExtractionManifest? = runCatching {
            val lines = text.lineSequence().filter(String::isNotBlank).toList()
            val schema = lines.single { it.startsWith("schema=") }.substringAfter('=').toInt()
            val model = lines.single { it.startsWith("model=") }.substringAfter('=')
            val source = lines.single { it.startsWith("source=") }.substringAfter('=')
            val files = lines.filter { it.startsWith("file=") }.map { line ->
                val fields = line.substringAfter("file=").split('\t', limit = 2)
                require(fields.size == 2)
                ExtractedAsset(requireSafeRelativePath(fields[0]), fields[1].toLong())
            }
            AssetExtractionManifest(schema, model, source, files)
        }.getOrNull()
    }
}

internal class EspeakAssetExtractor(
    private val assetManager: AssetManager,
    private val noBackupFilesDir: File,
) {
    fun prepare(): File = synchronized(EXTRACTION_MONITOR) {
        prepareWithProcessLock()
    }

    private fun prepareWithProcessLock(): File {
        val extractionRoot = KittenAssets.extractionRoot(noBackupFilesDir)
        val parent = extractionRoot.parentFile
            ?: throw IOException("Cannot resolve the local TTS extraction directory")
        if (!parent.exists() && !parent.mkdirs()) {
            throw IOException("Cannot create local TTS directory: ${parent.absolutePath}")
        }

        val lockFile = File(parent, ".${KittenAssets.MODEL_VERSION}.lock")
        FileOutputStream(lockFile, true).channel.use { channel ->
            channel.lock().use {
                recoverInterruptedExtraction(parent, extractionRoot)
                if (isComplete(extractionRoot)) return KittenAssets.extractedDataDir(noBackupFilesDir)
                return extractAtomically(parent, extractionRoot)
            }
        }
    }

    private fun extractAtomically(parent: File, extractionRoot: File): File {
        cleanAbandonedDirectories(parent)
        val staging = File(parent, ".${KittenAssets.MODEL_VERSION}.staging-${UUID.randomUUID()}")
        val stale = File(parent, ".${KittenAssets.MODEL_VERSION}.stale-${UUID.randomUUID()}")

        try {
            val dataDir = File(staging, "espeak-ng-data")
            if (!dataDir.mkdirs()) throw IOException("Cannot create TTS extraction staging directory")

            val copied = mutableListOf<ExtractedAsset>()
            copyAssetTree(KittenAssets.ESPEAK_ASSET_ROOT, dataDir, dataDir, copied)
            validateRequiredFiles(dataDir)

            val manifest = AssetExtractionManifest(
                schemaVersion = KittenAssets.EXTRACTION_SCHEMA,
                modelVersion = KittenAssets.MODEL_VERSION,
                sourceAssetRoot = KittenAssets.ESPEAK_ASSET_ROOT,
                files = copied,
            )
            writeAndSync(KittenAssets.manifestFile(staging), manifest.encode())

            if (extractionRoot.exists()) move(extractionRoot, stale)
            try {
                move(staging, extractionRoot)
            } catch (failure: Throwable) {
                if (!extractionRoot.exists() && stale.exists()) runCatching { move(stale, extractionRoot) }
                throw failure
            }
            stale.deleteRecursively()

            if (!isComplete(extractionRoot)) {
                throw IOException("Local TTS data failed verification after extraction")
            }
            return File(extractionRoot, "espeak-ng-data")
        } catch (failure: Throwable) {
            staging.deleteRecursively()
            throw IOException("Unable to prepare the embedded pronunciation data", failure)
        }
    }

    private fun copyAssetTree(
        assetPath: String,
        destination: File,
        dataRoot: File,
        copied: MutableList<ExtractedAsset>,
    ) {
        val children = assetManager.list(assetPath)
            ?: throw IOException("Cannot list embedded asset: $assetPath")
        if (children.isNotEmpty()) {
            if (!destination.exists() && !destination.mkdirs()) {
                throw IOException("Cannot create extracted asset directory: ${destination.absolutePath}")
            }
            children.sorted().forEach { child ->
                copyAssetTree(
                    assetPath = "$assetPath/$child",
                    destination = File(destination, child),
                    dataRoot = dataRoot,
                    copied = copied,
                )
            }
            return
        }

        destination.parentFile?.let { parent ->
            if (!parent.exists() && !parent.mkdirs()) {
                throw IOException("Cannot create extracted asset directory: ${parent.absolutePath}")
            }
        }
        val byteCount = assetManager.open(assetPath, AssetManager.ACCESS_STREAMING).use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output)
                output.fd.sync()
            }
            destination.length()
        }
        copied += ExtractedAsset(
            relativePath = destination.relativeTo(dataRoot).invariantSeparatorsPath,
            byteCount = byteCount,
        )
    }

    private fun isComplete(extractionRoot: File): Boolean {
        val dataDir = File(extractionRoot, "espeak-ng-data")
        val marker = KittenAssets.manifestFile(extractionRoot)
        if (!dataDir.isDirectory || !marker.isFile) return false
        val manifest = AssetExtractionManifest.decode(runCatching { marker.readText() }.getOrNull() ?: return false)
            ?: return false
        if (!manifest.isCurrentFor(KittenAssets.MODEL_VERSION, KittenAssets.ESPEAK_ASSET_ROOT)) return false
        if (manifest.files.any { entry ->
                val file = File(dataDir, entry.relativePath)
                !file.isFile || file.length() != entry.byteCount
            }
        ) return false
        return runCatching { validateRequiredFiles(dataDir) }.isSuccess
    }

    private fun validateRequiredFiles(dataDir: File) {
        REQUIRED_ESPEAK_FILES.forEach { relativePath ->
            val file = File(dataDir, relativePath)
            if (!file.isFile || file.length() == 0L) {
                throw IOException("Embedded pronunciation asset is missing: $relativePath")
            }
        }
    }

    private fun cleanAbandonedDirectories(parent: File) {
        parent.listFiles().orEmpty()
            .filter { file ->
                file.name.startsWith(".${KittenAssets.MODEL_VERSION}.staging-") ||
                    file.name.startsWith(".${KittenAssets.MODEL_VERSION}.stale-")
            }
            .forEach { it.deleteRecursively() }
    }

    private fun recoverInterruptedExtraction(parent: File, extractionRoot: File) {
        if (isComplete(extractionRoot)) return
        val recoverable = parent.listFiles().orEmpty()
            .asSequence()
            .filter { file ->
                file.name.startsWith(".${KittenAssets.MODEL_VERSION}.stale-") ||
                    file.name.startsWith(".${KittenAssets.MODEL_VERSION}.staging-")
            }
            .filter(::isComplete)
            .maxByOrNull(File::lastModified)
            ?: return

        val invalid = if (extractionRoot.exists()) {
            File(parent, ".${KittenAssets.MODEL_VERSION}.invalid-${UUID.randomUUID()}")
        } else {
            null
        }
        if (invalid != null) move(extractionRoot, invalid)
        try {
            move(recoverable, extractionRoot)
            invalid?.deleteRecursively()
        } catch (failure: Throwable) {
            if (!extractionRoot.exists() && invalid?.exists() == true) {
                runCatching { move(invalid, extractionRoot) }
            }
            throw failure
        }
    }

    private fun move(source: File, target: File) {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun writeAndSync(file: File, text: String) {
        FileOutputStream(file).use { output ->
            output.write(text.toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
    }

    private companion object {
        val EXTRACTION_MONITOR = Any()
        val REQUIRED_ESPEAK_FILES = listOf("en_dict", "phondata", "phonindex", "phontab")
    }
}
