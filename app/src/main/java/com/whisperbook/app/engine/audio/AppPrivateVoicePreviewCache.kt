package com.whisperbook.app.engine.audio

import android.content.Context
import com.whisperbook.app.domain.model.VoiceDescriptor
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class VoicePreviewClip(
    val pcm16: ShortArray,
    val sampleRate: Int,
)

internal interface VoicePreviewClipCache {
    suspend fun read(voice: VoiceDescriptor, speed: Float): VoicePreviewClip?
    suspend fun write(voice: VoiceDescriptor, speed: Float, clip: VoicePreviewClip)
}

/** Durable, app-private cache for the fixed samples generated during first-run initialization. */
internal class AppPrivateVoicePreviewCache(
    private val root: File,
    modelVersion: String,
    private val expectedSampleRate: Int,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : VoicePreviewClipCache {
    constructor(
        context: Context,
        modelVersion: String,
        expectedSampleRate: Int,
    ) : this(
        root = File(context.noBackupFilesDir, "whisperbook/voice-previews"),
        modelVersion = modelVersion,
        expectedSampleRate = expectedSampleRate,
    )

    private val namespace = sha256(
        listOf(CACHE_FORMAT_VERSION, SCRIPT_VERSION, modelVersion, expectedSampleRate)
            .joinToString("|"),
    )

    init {
        require(modelVersion.isNotBlank()) { "Voice preview model version must not be blank" }
        require(expectedSampleRate > 0) { "Voice preview sample rate must be positive" }
    }

    override suspend fun read(voice: VoiceDescriptor, speed: Float): VoicePreviewClip? =
        withContext(ioDispatcher) {
            val file = entryFile(voice, speed)
            if (!file.isFile) return@withContext null
            runCatching {
                DataInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
                    check(input.readInt() == MAGIC) { "Invalid voice preview header" }
                    check(input.readInt() == CACHE_FORMAT_VERSION) { "Unsupported voice preview version" }
                    val sampleRate = input.readInt()
                    check(sampleRate == expectedSampleRate) { "Stale voice preview sample rate" }
                    val sampleCount = input.readInt()
                    check(sampleCount in 1..MAX_SAMPLE_COUNT) { "Invalid voice preview length" }
                    check(file.length() == HEADER_BYTES + sampleCount.toLong() * Short.SIZE_BYTES) {
                        "Incomplete voice preview file"
                    }
                    val pcm16 = ShortArray(sampleCount) { input.readShort() }
                    VoicePreviewClip(pcm16, sampleRate)
                }
            }.getOrNull()
        }

    override suspend fun write(
        voice: VoiceDescriptor,
        speed: Float,
        clip: VoicePreviewClip,
    ): Unit = withContext(ioDispatcher) {
        require(clip.pcm16.isNotEmpty()) { "Voice preview audio must not be empty" }
        require(clip.pcm16.size <= MAX_SAMPLE_COUNT) { "Voice preview audio is too long" }
        require(clip.sampleRate == expectedSampleRate) {
            "Voice preview sample rate ${clip.sampleRate} does not match $expectedSampleRate"
        }
        root.mkdirs()
        check(root.isDirectory) { "Could not create the voice preview cache" }

        val target = entryFile(voice, speed)
        val temporary = File(root, ".${target.name}.${UUID.randomUUID()}.tmp")
        try {
            FileOutputStream(temporary).use { stream ->
                DataOutputStream(BufferedOutputStream(stream)).use { output ->
                    output.writeInt(MAGIC)
                    output.writeInt(CACHE_FORMAT_VERSION)
                    output.writeInt(clip.sampleRate)
                    output.writeInt(clip.pcm16.size)
                    clip.pcm16.forEach { sample -> output.writeShort(sample.toInt()) }
                    output.flush()
                    stream.fd.sync()
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
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        } finally {
            temporary.delete()
        }
        Unit
    }

    private fun entryFile(voice: VoiceDescriptor, speed: Float): File {
        require(voice.id.isNotBlank()) { "Voice preview voice id must not be blank" }
        require(speed.isFinite() && speed > 0f) { "Voice preview speed must be positive" }
        val key = "$namespace|${voice.id}|${voice.speakerIndex}|${speed.toBits()}"
        return File(root, "${sha256(key)}.wvp")
    }

    private companion object {
        const val MAGIC = 0x57425056 // WBPV
        const val CACHE_FORMAT_VERSION = 1
        const val SCRIPT_VERSION = 1
        const val HEADER_BYTES = 16L
        const val MAX_SAMPLE_COUNT = 44_100 * 60
    }
}

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte) }
