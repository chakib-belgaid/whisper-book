package com.whisperbook.app.engine.audio

import android.content.Context
import com.whisperbook.app.domain.AudioRetentionGeneration
import com.whisperbook.app.domain.AudioSegmentStore
import com.whisperbook.app.domain.SynthesisRequest
import com.whisperbook.app.domain.SynthesisResult
import com.whisperbook.app.domain.model.AudioSegment
import com.whisperbook.app.domain.model.AudioSegmentState
import com.whisperbook.app.domain.model.CharacterVoiceAssignment
import com.whisperbook.app.domain.model.ChapterVoiceAssignmentSnapshot
import com.whisperbook.app.domain.model.VoiceRegenerationScope
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * App-private, bounded cache of synthesized passage WAV files.
 *
 * Call [writeForPassage] when ownership is known so changing a character's voice can retain only
 * that character's audio. Retention only annotates sidecars: WAV paths remain unchanged and
 * playable until the grace period expires. The interface-compatible [write] path remains available
 * for callers that do not yet carry passage ownership.
 */
class AppPrivateAudioSegmentStore internal constructor(
    private val root: File,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
    private val generationId: () -> String = { UUID.randomUUID().toString() },
    private val fileDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AudioSegmentStore {
    constructor(context: Context) : this(
        root = File(context.filesDir, "audio/segments"),
        nowEpochMs = System::currentTimeMillis,
    )

    private val mutex = Mutex()

    override suspend fun find(cacheKey: String): AudioSegment? = withContext(fileDispatcher) {
        mutex.withLock {
            requireValidKey(cacheKey)
            val wav = wavFile(cacheKey)
            val metadata = metadataFile(cacheKey)
            if (!wav.isFile || !metadata.isFile) return@withLock null

            val record = runCatching { SegmentRecord.read(metadata) }.getOrNull() ?: return@withLock null
            if (record.cacheKey != cacheKey || record.sampleRate <= 0 || record.durationMs < 0L) {
                return@withLock null
            }

            if (record.isExpired(nowEpochMs())) {
                deleteSegment(record, metadata)
                removeManifestIfEmpty(record.retentionGenerationId)
                return@withLock null
            }

            val accessedAt = nowEpochMs()
            wav.setLastModified(accessedAt)
            metadata.setLastModified(accessedAt)
            record.toAudioSegment(wav)
        }
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

    override suspend fun invalidateForCharacter(characterId: String) {
        retainForCharacter(characterId = characterId)
    }

    override suspend fun retainForCharacter(
        characterId: String,
        previousAssignment: CharacterVoiceAssignment?,
        passageIds: Set<String>,
        gracePeriodMs: Long,
        bookId: String?,
        previousChapterAssignments: List<ChapterVoiceAssignmentSnapshot>,
        scope: VoiceRegenerationScope,
        fromChapterOrdinal: Int,
    ): AudioRetentionGeneration? = withContext(fileDispatcher) {
        mutex.withLock {
            require(characterId.isNotBlank()) { "characterId must not be blank" }
            require(gracePeriodMs > 0L) { "gracePeriodMs must be positive" }
            require(previousAssignment == null || previousAssignment.characterId == characterId) {
                "previousAssignment must belong to characterId"
            }
            require(passageIds.none(String::isBlank)) { "passageIds must not contain blank values" }
            require(bookId == null || bookId.isNotBlank()) { "bookId must not be blank" }
            require(previousChapterAssignments.all { it.assignment.characterId == characterId }) {
                "chapter assignment snapshots must belong to characterId"
            }
            require(fromChapterOrdinal >= 0) { "fromChapterOrdinal must not be negative" }

            ensureRoot()
            cleanupExpiredLocked(nowEpochMs())
            val createdAt = nowEpochMs()
            val expiresAt = createdAt.saturatedPlus(gracePeriodMs)
            val id = generationId().also { requireValidGenerationId(it) }
            val targets = segmentMetadataFiles().mapNotNull { metadata ->
                val record = runCatching { SegmentRecord.read(metadata) }.getOrNull()
                record?.takeIf {
                    it.characterId == characterId &&
                        it.retentionGenerationId == null &&
                        (passageIds.isEmpty() || it.passageId in passageIds) &&
                        wavFile(it.cacheKey).isFile
                }?.let { metadata to it }
            }
            val generation = AudioRetentionGeneration(
                id = id,
                characterId = characterId,
                createdAtEpochMs = createdAt,
                expiresAtEpochMs = expiresAt,
                segmentCount = targets.size,
                passageIds = passageIds,
                previousAssignment = previousAssignment,
                bookId = bookId,
                previousChapterAssignments = previousChapterAssignments,
                voiceRegenerationScope = scope,
                fromChapterOrdinal = fromChapterOrdinal,
            )
            val manifest = RetentionRecord(
                generation = generation,
                segmentKeys = targets.mapTo(linkedSetOf()) { it.second.cacheKey },
            )
            writePropertiesAtomic(retentionFile(id), manifest.toProperties())
            targets.forEach { (metadata, record) ->
                rewriteRecord(
                    metadata,
                    record.copy(
                        retentionGenerationId = id,
                        retainedAtEpochMs = createdAt,
                        deleteAfterEpochMs = expiresAt,
                    ),
                )
            }
            generation
        }
    }

    override suspend fun restoreRetainedGeneration(
        generationId: String,
    ): AudioRetentionGeneration? = withContext(fileDispatcher) {
        mutex.withLock {
            if (!root.isDirectory || !isValidGenerationId(generationId)) return@withLock null
            val manifestFile = retentionFile(generationId)
            val manifest = manifestFile.takeIf(File::isFile)
                ?.let { runCatching { RetentionRecord.read(it) }.getOrNull() }
                ?.takeIf { it.generation.id == generationId }
                ?: return@withLock null
            if (manifest.generation.expiresAtEpochMs <= nowEpochMs()) {
                cleanupGenerationLocked(manifestFile, manifest)
                return@withLock null
            }
            manifest.segmentKeys.forEach { cacheKey ->
                val metadata = metadataFile(cacheKey)
                val record = metadata.takeIf(File::isFile)
                    ?.let { runCatching { SegmentRecord.read(it) }.getOrNull() }
                if (record?.retentionGenerationId == generationId && wavFile(cacheKey).isFile) {
                    rewriteRecord(metadata, record.clearRetention())
                }
            }
            manifestFile.delete()
            manifest.generation
        }
    }

    override suspend fun retainedAudioGenerations(
        characterId: String?,
    ): List<AudioRetentionGeneration> = withContext(fileDispatcher) {
        mutex.withLock {
            if (!root.isDirectory) return@withLock emptyList()
            characterId?.let { require(it.isNotBlank()) { "characterId must not be blank" } }
            cleanupExpiredLocked(nowEpochMs())
            retentionFiles().mapNotNull { file ->
                runCatching { RetentionRecord.read(file) }.getOrNull()?.generation
            }
                .filter { characterId == null || it.characterId == characterId }
                .sortedByDescending(AudioRetentionGeneration::createdAtEpochMs)
        }
    }

    override suspend fun cleanupExpiredRetainedAudio(): Int = withContext(fileDispatcher) {
        mutex.withLock { cleanupExpiredLocked(nowEpochMs()) }
    }

    override suspend fun trimTo(limitBytes: Long) = withContext(fileDispatcher) {
        mutex.withLock {
            require(limitBytes >= 0L) { "limitBytes must not be negative" }
            if (!root.isDirectory) return@withLock
            val now = nowEpochMs()
            cleanupExpiredLocked(now)

            val entries = root.listFiles { file -> file.extension == WAV_EXTENSION }
                .orEmpty()
                .map { wav ->
                    val metadata = metadataFile(wav.nameWithoutExtension)
                    val record = metadata.takeIf(File::isFile)
                        ?.let { runCatching { SegmentRecord.read(it) }.getOrNull() }
                    CacheEntry(
                        wav = wav,
                        metadata = metadata,
                        bytes = wav.length() + metadata.takeIf(File::isFile).orEmptyLength(),
                        lastAccessedAt = maxOf(wav.lastModified(), metadata.takeIf(File::isFile)?.lastModified() ?: 0L),
                        protectedUntilEpochMs = record?.deleteAfterEpochMs
                            ?.takeIf { record.retentionGenerationId != null && it > now },
                    )
                }
                .sortedBy(CacheEntry::lastAccessedAt)

            var totalBytes = entries.sumOf(CacheEntry::bytes)
            for (entry in entries) {
                if (totalBytes <= limitBytes) break
                if (entry.protectedUntilEpochMs != null) continue
                entry.wav.delete()
                entry.metadata.delete()
                totalBytes -= entry.bytes
            }
            removeOrphanedMetadata()
        }
    }

    private suspend fun writeInternal(
        passageId: String,
        characterId: String?,
        request: SynthesisRequest,
        result: SynthesisResult,
    ): AudioSegment = withContext(fileDispatcher) {
        mutex.withLock {
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

    private fun retentionFile(generationId: String) =
        File(root, "$RETENTION_FILE_PREFIX$generationId.$RETENTION_EXTENSION")

    private fun segmentMetadataFiles(): List<File> =
        root.listFiles { file -> file.extension == METADATA_EXTENSION }.orEmpty().toList()

    private fun retentionFiles(): List<File> =
        root.listFiles { file ->
            file.extension == RETENTION_EXTENSION && file.name.startsWith(RETENTION_FILE_PREFIX)
        }.orEmpty().toList()

    private fun rewriteRecord(metadata: File, record: SegmentRecord) {
        val previousLastModified = metadata.lastModified()
        writePropertiesAtomic(metadata, record.toProperties())
        if (previousLastModified > 0L) metadata.setLastModified(previousLastModified)
    }

    private fun deleteSegment(record: SegmentRecord, metadata: File): Boolean {
        val wav = wavFile(record.cacheKey)
        val existed = wav.isFile
        wav.delete()
        metadata.delete()
        return existed
    }

    private fun cleanupExpiredLocked(now: Long): Int {
        var deletedSegments = 0
        retentionFiles().forEach { manifestFile ->
            val manifest = runCatching { RetentionRecord.read(manifestFile) }.getOrNull()
            if (manifest != null && manifest.generation.expiresAtEpochMs <= now) {
                deletedSegments += cleanupGenerationLocked(manifestFile, manifest)
            }
        }

        // A crash between writing a manifest and updating every sidecar may leave an orphaned
        // retention annotation. Its own expiry remains authoritative and safe to clean.
        segmentMetadataFiles().forEach { metadata ->
            val record = runCatching { SegmentRecord.read(metadata) }.getOrNull()
            if (record?.isExpired(now) == true) {
                if (deleteSegment(record, metadata)) deletedSegments += 1
                removeManifestIfEmpty(record.retentionGenerationId)
            }
        }
        removeOrphanedMetadata()
        return deletedSegments
    }

    private fun cleanupGenerationLocked(
        manifestFile: File,
        manifest: RetentionRecord,
    ): Int {
        var deletedSegments = 0
        manifest.segmentKeys.forEach { cacheKey ->
            val metadata = metadataFile(cacheKey)
            val record = metadata.takeIf(File::isFile)
                ?.let { runCatching { SegmentRecord.read(it) }.getOrNull() }
            // A later synthesis may legitimately reuse the same deterministic cache key. Never
            // delete it unless its sidecar still belongs to this exact retired generation.
            if (record?.retentionGenerationId == manifest.generation.id) {
                if (deleteSegment(record, metadata)) deletedSegments += 1
            }
        }
        manifestFile.delete()
        return deletedSegments
    }

    private fun removeManifestIfEmpty(generationId: String?) {
        generationId ?: return
        val hasRetainedSegment = segmentMetadataFiles().any { metadata ->
            runCatching { SegmentRecord.read(metadata) }.getOrNull()?.retentionGenerationId == generationId
        }
        if (!hasRetainedSegment) retentionFile(generationId).delete()
    }

    private fun requireValidGenerationId(value: String) {
        require(isValidGenerationId(value)) { "generationId contains unsupported characters" }
    }

    private fun isValidGenerationId(value: String): Boolean = GENERATION_ID_PATTERN.matches(value)

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
        val protectedUntilEpochMs: Long?,
    )

    private data class SegmentRecord(
        val id: String,
        val passageId: String,
        val characterId: String?,
        val cacheKey: String,
        val durationMs: Long,
        val sampleRate: Int,
        val retentionGenerationId: String? = null,
        val retainedAtEpochMs: Long? = null,
        val deleteAfterEpochMs: Long? = null,
    ) {
        init {
            require(
                retentionGenerationId == null ||
                    (retainedAtEpochMs != null && deleteAfterEpochMs != null),
            ) { "Retained segments require retention timestamps" }
        }

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
            retentionGenerationId?.let { setProperty("retentionGenerationId", it) }
            retainedAtEpochMs?.let { setProperty("retainedAtEpochMs", it.toString()) }
            deleteAfterEpochMs?.let { setProperty("deleteAfterEpochMs", it.toString()) }
        }

        fun isExpired(nowEpochMs: Long): Boolean =
            retentionGenerationId != null && deleteAfterEpochMs?.let { it <= nowEpochMs } == true

        fun clearRetention(): SegmentRecord = copy(
            retentionGenerationId = null,
            retainedAtEpochMs = null,
            deleteAfterEpochMs = null,
        )

        companion object {
            fun read(file: File): SegmentRecord {
                val properties = Properties().apply {
                    FileInputStream(file).use(::load)
                }
                require(properties.getProperty("schema") in SUPPORTED_METADATA_SCHEMAS)
                return SegmentRecord(
                    id = requireNotNull(properties.getProperty("id")),
                    passageId = requireNotNull(properties.getProperty("passageId")),
                    characterId = properties.getProperty("characterId"),
                    cacheKey = requireNotNull(properties.getProperty("cacheKey")),
                    durationMs = requireNotNull(properties.getProperty("durationMs")).toLong(),
                    sampleRate = requireNotNull(properties.getProperty("sampleRate")).toInt(),
                    retentionGenerationId = properties.getProperty("retentionGenerationId"),
                    retainedAtEpochMs = properties.getProperty("retainedAtEpochMs")?.toLong(),
                    deleteAfterEpochMs = properties.getProperty("deleteAfterEpochMs")?.toLong(),
                )
            }
        }
    }

    private data class RetentionRecord(
        val generation: AudioRetentionGeneration,
        val segmentKeys: Set<String>,
    ) {
        fun toProperties() = Properties().apply {
            setProperty("schema", RETENTION_SCHEMA)
            setProperty("id", generation.id)
            setProperty("characterId", generation.characterId)
            setProperty("createdAtEpochMs", generation.createdAtEpochMs.toString())
            setProperty("expiresAtEpochMs", generation.expiresAtEpochMs.toString())
            setProperty("segmentCount", generation.segmentCount.toString())
            writeIndexedValues("segmentKey", segmentKeys)
            setProperty("scopeAllPassages", generation.passageIds.isEmpty().toString())
            writeIndexedValues("passageId", generation.passageIds)
            generation.previousAssignment?.let { assignment ->
                setProperty("previousVoiceId", assignment.voiceId)
                setProperty("previousModelVersion", assignment.modelVersion)
                setProperty("previousSpeed", assignment.speed.toString())
            }
            generation.bookId?.let { setProperty("bookId", it) }
            setProperty("voiceRegenerationScope", generation.voiceRegenerationScope.name)
            setProperty("fromChapterOrdinal", generation.fromChapterOrdinal.toString())
            setProperty("chapterAssignmentCount", generation.previousChapterAssignments.size.toString())
            generation.previousChapterAssignments.forEachIndexed { index, snapshot ->
                setProperty("chapterAssignment.$index.chapterId", snapshot.chapterId)
                setProperty("chapterAssignment.$index.voiceId", snapshot.assignment.voiceId)
                setProperty("chapterAssignment.$index.modelVersion", snapshot.assignment.modelVersion)
                setProperty("chapterAssignment.$index.speed", snapshot.assignment.speed.toString())
            }
        }

        companion object {
            fun read(file: File): RetentionRecord {
                val properties = Properties().apply {
                    FileInputStream(file).use(::load)
                }
                require(properties.getProperty("schema") in SUPPORTED_RETENTION_SCHEMAS)
                val id = requireNotNull(properties.getProperty("id"))
                require(GENERATION_ID_PATTERN.matches(id))
                val characterId = requireNotNull(properties.getProperty("characterId"))
                val segmentKeys = properties.readIndexedValues("segmentKey")
                    .onEach { require(AudioCacheKey.isValid(it)) }
                    .toSet()
                val scopeAll = requireNotNull(properties.getProperty("scopeAllPassages")).toBooleanStrict()
                val passageIds = if (scopeAll) emptySet() else {
                    properties.readIndexedValues("passageId").toSet()
                }
                val voiceId = properties.getProperty("previousVoiceId")
                val modelVersion = properties.getProperty("previousModelVersion")
                val speed = properties.getProperty("previousSpeed")
                val previousAssignment = if (voiceId != null && modelVersion != null && speed != null) {
                    CharacterVoiceAssignment(characterId, voiceId, modelVersion, speed.toFloat())
                } else {
                    null
                }
                val chapterAssignmentCount = properties.getProperty("chapterAssignmentCount")
                    ?.toIntOrNull()
                    ?.coerceAtLeast(0)
                    ?: 0
                val previousChapterAssignments = (0 until chapterAssignmentCount).map { index ->
                    val chapterId = requireNotNull(properties.getProperty("chapterAssignment.$index.chapterId"))
                    val chapterVoiceId = requireNotNull(properties.getProperty("chapterAssignment.$index.voiceId"))
                    val chapterModelVersion = requireNotNull(
                        properties.getProperty("chapterAssignment.$index.modelVersion"),
                    )
                    val chapterSpeed = requireNotNull(properties.getProperty("chapterAssignment.$index.speed")).toFloat()
                    ChapterVoiceAssignmentSnapshot(
                        chapterId,
                        CharacterVoiceAssignment(characterId, chapterVoiceId, chapterModelVersion, chapterSpeed),
                    )
                }
                val voiceScope = properties.getProperty("voiceRegenerationScope")
                    ?.let { encoded -> VoiceRegenerationScope.entries.firstOrNull { it.name == encoded } }
                    ?: VoiceRegenerationScope.WHOLE_BOOK
                val fromChapterOrdinal = properties.getProperty("fromChapterOrdinal")
                    ?.toIntOrNull()
                    ?.coerceAtLeast(0)
                    ?: 0
                val segmentCount = requireNotNull(properties.getProperty("segmentCount")).toInt()
                require(segmentCount >= 0)
                return RetentionRecord(
                    generation = AudioRetentionGeneration(
                        id = id,
                        characterId = characterId,
                        createdAtEpochMs = requireNotNull(properties.getProperty("createdAtEpochMs")).toLong(),
                        expiresAtEpochMs = requireNotNull(properties.getProperty("expiresAtEpochMs")).toLong(),
                        segmentCount = segmentCount,
                        passageIds = passageIds,
                        previousAssignment = previousAssignment,
                        bookId = properties.getProperty("bookId"),
                        previousChapterAssignments = previousChapterAssignments,
                        voiceRegenerationScope = voiceScope,
                        fromChapterOrdinal = fromChapterOrdinal,
                    ),
                    segmentKeys = segmentKeys,
                )
            }
        }
    }

    private companion object {
        const val WAV_EXTENSION = "wav"
        const val METADATA_EXTENSION = "properties"
        const val RETENTION_EXTENSION = "retention"
        const val RETENTION_FILE_PREFIX = "generation-"
        const val LEGACY_METADATA_SCHEMA = "whisperbook-segment-v1"
        const val METADATA_SCHEMA = "whisperbook-segment-v2"
        const val LEGACY_RETENTION_SCHEMA = "whisperbook-audio-retention-v1"
        const val RETENTION_SCHEMA = "whisperbook-audio-retention-v2"
        val SUPPORTED_METADATA_SCHEMAS = setOf(LEGACY_METADATA_SCHEMA, METADATA_SCHEMA)
        val SUPPORTED_RETENTION_SCHEMAS = setOf(LEGACY_RETENTION_SCHEMA, RETENTION_SCHEMA)
        val GENERATION_ID_PATTERN = Regex("^[A-Za-z0-9_-]{1,128}$")

        fun File?.orEmptyLength(): Long = this?.length() ?: 0L

        fun Long.saturatedPlus(other: Long): Long =
            if (this > Long.MAX_VALUE - other) Long.MAX_VALUE else this + other

        fun Properties.writeIndexedValues(prefix: String, values: Collection<String>) {
            setProperty("${prefix}Count", values.size.toString())
            values.forEachIndexed { index, value -> setProperty("$prefix.$index", value) }
        }

        fun Properties.readIndexedValues(prefix: String): List<String> {
            val count = requireNotNull(getProperty("${prefix}Count")).toInt()
            require(count >= 0)
            return List(count) { index -> requireNotNull(getProperty("$prefix.$index")) }
        }
    }
}
