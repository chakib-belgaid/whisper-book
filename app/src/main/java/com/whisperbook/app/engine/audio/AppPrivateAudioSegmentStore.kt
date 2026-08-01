package com.whisperbook.app.engine.audio

import android.content.Context
import com.whisperbook.app.domain.AudioSegmentStore
import com.whisperbook.app.domain.SynthesisRequest
import com.whisperbook.app.domain.SynthesisResult
import com.whisperbook.app.domain.model.AudioSegment
import com.whisperbook.app.domain.model.AudioSegmentState
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * App-private, bounded cache of synthesized passage WAV files.
 *
 * Call [writeForPassage] when ownership is known so changing a character's voice can invalidate
 * only that character's audio. The interface-compatible [write] path remains available for callers
 * that do not yet carry passage ownership.
 */
class AppPrivateAudioSegmentStore internal constructor(
    private val root: File,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) : AudioSegmentStore {
    constructor(context: Context) : this(
        root = File(context.filesDir, "audio/segments"),
        nowEpochMs = System::currentTimeMillis,
    )

    private val mutex = Mutex()

    override suspend fun find(cacheKey: String): AudioSegment? = mutex.withLock {
        requireValidKey(cacheKey)
        val wav = wavFile(cacheKey)
        val metadata = metadataFile(cacheKey)
        if (!wav.isFile || !metadata.isFile) return@withLock null

        val record = runCatching { SegmentRecord.read(metadata) }.getOrNull() ?: return@withLock null
        if (record.cacheKey != cacheKey || record.sampleRate <= 0 || record.durationMs < 0L) {
            return@withLock null
        }

        val accessedAt = nowEpochMs()
        wav.setLastModified(accessedAt)
        metadata.setLastModified(accessedAt)
        record.toAudioSegment(wav)
    }

    override suspend fun write(request: SynthesisRequest, result: SynthesisResult): AudioSegment =
        writeInternal(
            passageId = request.cacheKey,
            characterId = null,
            request = request,
            result = result,
        )

    suspend fun writeForPassage(
        passageId: String,
        characterId: String,
        request: SynthesisRequest,
        result: SynthesisResult,
    ): AudioSegment {
        require(passageId.isNotBlank()) { "passageId must not be blank" }
        require(characterId.isNotBlank()) { "characterId must not be blank" }
        return writeInternal(passageId, characterId, request, result)
    }

    override suspend fun invalidateForCharacter(characterId: String) = mutex.withLock {
        if (!root.isDirectory) return@withLock
        root.listFiles { file -> file.extension == METADATA_EXTENSION }
            .orEmpty()
            .forEach { metadata ->
                val record = runCatching { SegmentRecord.read(metadata) }.getOrNull()
                if (record?.characterId == characterId) {
                    wavFile(record.cacheKey).delete()
                    metadata.delete()
                }
            }
    }

    override suspend fun trimTo(limitBytes: Long) = mutex.withLock {
        require(limitBytes >= 0L) { "limitBytes must not be negative" }
        if (!root.isDirectory) return@withLock

        val entries = root.listFiles { file -> file.extension == WAV_EXTENSION }
            .orEmpty()
            .map { wav ->
                val metadata = metadataFile(wav.nameWithoutExtension)
                CacheEntry(
                    wav = wav,
                    metadata = metadata,
                    bytes = wav.length() + metadata.takeIf(File::isFile).orEmptyLength(),
                    lastAccessedAt = maxOf(wav.lastModified(), metadata.takeIf(File::isFile)?.lastModified() ?: 0L),
                )
            }
            .sortedBy(CacheEntry::lastAccessedAt)

        var totalBytes = entries.sumOf(CacheEntry::bytes)
        for (entry in entries) {
            if (totalBytes <= limitBytes) break
            entry.wav.delete()
            entry.metadata.delete()
            totalBytes -= entry.bytes
        }
        removeOrphanedMetadata()
    }

    private suspend fun writeInternal(
        passageId: String,
        characterId: String?,
        request: SynthesisRequest,
        result: SynthesisResult,
    ): AudioSegment = mutex.withLock {
        requireValidKey(request.cacheKey)
        require(result.sampleRate > 0) { "sampleRate must be positive" }
        require(result.durationMs >= 0L) { "durationMs must not be negative" }
        ensureRoot()

        val wav = wavFile(request.cacheKey)
        Pcm16WavWriter.writeAtomic(wav, result.pcm16, result.sampleRate)
        val record = SegmentRecord(
            id = request.cacheKey,
            passageId = passageId,
            characterId = characterId,
            cacheKey = request.cacheKey,
            durationMs = result.durationMs,
            sampleRate = result.sampleRate,
        )
        writePropertiesAtomic(metadataFile(request.cacheKey), record.toProperties())
        val accessedAt = nowEpochMs()
        wav.setLastModified(accessedAt)
        metadataFile(request.cacheKey).setLastModified(accessedAt)
        record.toAudioSegment(wav)
    }

    private fun removeOrphanedMetadata() {
        root.listFiles { file -> file.extension == METADATA_EXTENSION }
            .orEmpty()
            .forEach { metadata ->
                if (!wavFile(metadata.nameWithoutExtension).isFile) metadata.delete()
            }
    }

    private fun ensureRoot() {
        check(root.exists() || root.mkdirs()) { "Could not create ${root.absolutePath}" }
        check(root.isDirectory) { "Audio cache root is not a directory: ${root.absolutePath}" }
    }

    private fun requireValidKey(cacheKey: String) {
        require(AudioCacheKey.isValid(cacheKey)) { "cacheKey must be a lowercase SHA-256 digest" }
    }

    private fun wavFile(cacheKey: String) = File(root, "$cacheKey.$WAV_EXTENSION")

    private fun metadataFile(cacheKey: String) = File(root, "$cacheKey.$METADATA_EXTENSION")

    private fun writePropertiesAtomic(target: File, properties: Properties) {
        val temporary = File.createTempFile(".${target.name}.", ".part", root)
        try {
            FileOutputStream(temporary).use { fileOutput ->
                BufferedOutputStream(fileOutput).use { buffered ->
                    properties.store(buffered, null)
                    buffered.flush()
                    fileOutput.fd.sync()
                }
            }
            try {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private data class CacheEntry(
        val wav: File,
        val metadata: File,
        val bytes: Long,
        val lastAccessedAt: Long,
    )

    private data class SegmentRecord(
        val id: String,
        val passageId: String,
        val characterId: String?,
        val cacheKey: String,
        val durationMs: Long,
        val sampleRate: Int,
    ) {
        fun toAudioSegment(wav: File) = AudioSegment(
            id = id,
            passageId = passageId,
            cacheKey = cacheKey,
            state = AudioSegmentState.READY,
            path = wav.absolutePath,
            durationMs = durationMs,
            sampleRate = sampleRate,
        )

        fun toProperties() = Properties().apply {
            setProperty("schema", METADATA_SCHEMA)
            setProperty("id", id)
            setProperty("passageId", passageId)
            characterId?.let { setProperty("characterId", it) }
            setProperty("cacheKey", cacheKey)
            setProperty("durationMs", durationMs.toString())
            setProperty("sampleRate", sampleRate.toString())
        }

        companion object {
            fun read(file: File): SegmentRecord {
                val properties = Properties().apply {
                    FileInputStream(file).use(::load)
                }
                require(properties.getProperty("schema") == METADATA_SCHEMA)
                return SegmentRecord(
                    id = requireNotNull(properties.getProperty("id")),
                    passageId = requireNotNull(properties.getProperty("passageId")),
                    characterId = properties.getProperty("characterId"),
                    cacheKey = requireNotNull(properties.getProperty("cacheKey")),
                    durationMs = requireNotNull(properties.getProperty("durationMs")).toLong(),
                    sampleRate = requireNotNull(properties.getProperty("sampleRate")).toInt(),
                )
            }
        }
    }

    private companion object {
        const val WAV_EXTENSION = "wav"
        const val METADATA_EXTENSION = "properties"
        const val METADATA_SCHEMA = "whisperbook-segment-v1"

        fun File?.orEmptyLength(): Long = this?.length() ?: 0L
    }
}
