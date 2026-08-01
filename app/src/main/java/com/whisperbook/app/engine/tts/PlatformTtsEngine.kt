package com.whisperbook.app.engine.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.whisperbook.app.domain.LocalTtsEngine
import com.whisperbook.app.domain.SynthesisRequest
import com.whisperbook.app.domain.SynthesisResult
import com.whisperbook.app.domain.model.VoiceDescriptor
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/** Offline-only fallback for devices on which the bundled native model cannot be loaded. */
class PlatformTtsEngine(context: Context) : LocalTtsEngine {
    private val outputDirectory = File(context.noBackupFilesDir, "whisperbook/tts/platform")
    private val initialization = CompletableDeferred<Result<Unit>>()
    private val synthesisMutex = Mutex()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private val listenerInstalled = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    private val platformTts = TextToSpeech(context.applicationContext) { status ->
        initialization.complete(
            if (status == TextToSpeech.SUCCESS) Result.success(Unit)
            else Result.failure(TtsEngineException("Android's local speech service failed to initialize ($status)")),
        )
    }

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) = Unit

        override fun onDone(utteranceId: String) {
            pending.remove(utteranceId)?.complete(Unit)
        }

        @Deprecated("Required by Android's TTS callback API")
        override fun onError(utteranceId: String) {
            failUtterance(utteranceId, TextToSpeech.ERROR)
        }

        override fun onError(utteranceId: String, errorCode: Int) {
            failUtterance(utteranceId, errorCode)
        }
    }

    override suspend fun warmUp(): Result<Unit> = platformResult {
        requireReady()
        localVoices().ifEmpty {
            throw TtsEngineException("No embedded Android speech voice is installed on this device")
        }
        Unit
    }

    override suspend fun voices(): List<VoiceDescriptor> = try {
        requireReady()
        localVoices().mapIndexed { index, voice -> voice.toDescriptor(index) }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        emptyList()
    }

    override suspend fun synthesize(request: SynthesisRequest): Result<SynthesisResult> = platformResult {
        validateRequest(request)
        synthesisMutex.withLock {
            val tts = requireReady()
            val voices = localVoices()
            val voice = voices.firstOrNull { it.descriptorId() == request.voice.id }
                ?: voices.getOrNull(request.voice.speakerIndex)
                ?: throw TtsEngineException(
                    "The requested local Android voice '${request.voice.displayName}' is unavailable",
                )
            if (voice.isNetworkConnectionRequired) {
                throw TtsEngineException("The selected Android voice requires a network connection")
            }
            if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
                throw TtsEngineException("Cannot create the private Android speech output directory")
            }

            val utteranceId = "whisperbook-${UUID.randomUUID()}"
            val output = File(outputDirectory, "$utteranceId.wav")
            val completion = CompletableDeferred<Unit>()
            pending[utteranceId] = completion
            try {
                if (tts.setVoice(voice) == TextToSpeech.ERROR) {
                    throw TtsEngineException("Android rejected the selected embedded voice")
                }
                if (tts.setSpeechRate(request.speed) == TextToSpeech.ERROR) {
                    throw TtsEngineException("Android rejected the requested speaking speed")
                }
                val status = tts.synthesizeToFile(request.text.trim(), Bundle(), output, utteranceId)
                if (status == TextToSpeech.ERROR) {
                    throw TtsEngineException("Android could not start offline speech synthesis")
                }
                try {
                    withTimeout(SYNTHESIS_TIMEOUT_MS) { completion.await() }
                } catch (failure: Throwable) {
                    tts.stop()
                    throw failure
                }
                readPcm16Wav(output)
            } finally {
                pending.remove(utteranceId)
                output.delete()
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val failure = TtsEngineException("Android's local speech engine has been closed")
        initialization.complete(Result.failure(failure))
        pending.values.forEach { it.completeExceptionally(failure) }
        pending.clear()
        runCatching { platformTts.stop() }
        runCatching { platformTts.shutdown() }
    }

    private suspend fun requireReady(): TextToSpeech {
        if (closed.get()) throw TtsEngineException("Android's local speech engine has been closed")
        withTimeout(INITIALIZATION_TIMEOUT_MS) { initialization.await() }.getOrThrow()
        if (closed.get()) throw TtsEngineException("Android's local speech engine has been closed")
        if (listenerInstalled.compareAndSet(false, true)) {
            if (platformTts.setOnUtteranceProgressListener(progressListener) == TextToSpeech.ERROR) {
                listenerInstalled.set(false)
                throw TtsEngineException("Android's local speech service rejected its completion listener")
            }
        }
        return platformTts
    }

    private fun localVoices(): List<Voice> = platformTts.voices.orEmpty()
        .asSequence()
        .filterNot(Voice::isNetworkConnectionRequired)
        .filterNot { TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED in it.features }
        .sortedWith(compareBy({ it.locale.toLanguageTag() }, Voice::getName))
        .toList()

    private fun validateRequest(request: SynthesisRequest) {
        if (request.text.isBlank()) throw TtsEngineException("Text to synthesize must not be blank")
        if (request.text.length > TextToSpeech.getMaxSpeechInputLength()) {
            throw TtsEngineException(
                "Text exceeds Android's ${TextToSpeech.getMaxSpeechInputLength()} character speech limit",
            )
        }
        if (!request.speed.isFinite() || request.speed !in SherpaKittenTtsEngine.MIN_SPEED..SherpaKittenTtsEngine.MAX_SPEED) {
            throw TtsEngineException(
                "Speaking speed must be between ${SherpaKittenTtsEngine.MIN_SPEED} and " +
                    SherpaKittenTtsEngine.MAX_SPEED,
            )
        }
    }

    private fun failUtterance(utteranceId: String, errorCode: Int) {
        pending.remove(utteranceId)?.completeExceptionally(
            TtsEngineException("Android offline speech synthesis failed ($errorCode)"),
        )
    }

    private fun Voice.descriptorId(): String = "platform:$name"

    private fun Voice.toDescriptor(index: Int): VoiceDescriptor = VoiceDescriptor(
        id = descriptorId(),
        displayName = name.substringAfterLast('.').replace('_', ' ').ifBlank { "Local voice ${index + 1}" },
        speakerIndex = index,
        localeTag = locale.toLanguageTag(),
        embedded = true,
    )

    private companion object {
        const val INITIALIZATION_TIMEOUT_MS = 15_000L
        const val SYNTHESIS_TIMEOUT_MS = 120_000L
    }
}

internal fun readPcm16Wav(file: File): SynthesisResult {
    val bytes = file.readBytes()
    if (bytes.size < 44 || bytes.asAscii(0, 4) != "RIFF" || bytes.asAscii(8, 4) != "WAVE") {
        throw TtsEngineException("Android produced an invalid WAV file")
    }

    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    var position = 12
    var format = -1
    var channels = -1
    var sampleRate = -1
    var bitsPerSample = -1
    var dataStart = -1
    var dataSize = -1
    while (position + 8 <= bytes.size) {
        val chunkId = bytes.asAscii(position, 4)
        val size = buffer.getInt(position + 4).toLong() and 0xffff_ffffL
        val chunkStart = position + 8
        val chunkEnd = chunkStart.toLong() + size
        if (chunkEnd > bytes.size) throw TtsEngineException("Android produced a truncated WAV file")
        when (chunkId) {
            "fmt " -> {
                if (size < 16) throw TtsEngineException("Android produced an invalid WAV format chunk")
                format = buffer.getShort(chunkStart).toInt() and 0xffff
                channels = buffer.getShort(chunkStart + 2).toInt() and 0xffff
                sampleRate = buffer.getInt(chunkStart + 4)
                bitsPerSample = buffer.getShort(chunkStart + 14).toInt() and 0xffff
            }
            "data" -> {
                dataStart = chunkStart
                dataSize = size.toInt()
            }
        }
        position = (chunkEnd + (size and 1L)).toInt()
    }
    if (format != 1 || channels <= 0 || sampleRate <= 0 || bitsPerSample != 16 || dataStart < 0) {
        throw TtsEngineException("Android produced an unsupported WAV encoding")
    }

    val bytesPerFrame = channels * 2
    val frameCount = dataSize / bytesPerFrame
    val pcm = ShortArray(frameCount)
    repeat(frameCount) { frame ->
        var sum = 0L
        repeat(channels) { channel ->
            sum += buffer.getShort(dataStart + frame * bytesPerFrame + channel * 2).toLong()
        }
        pcm[frame] = (sum / channels).toShort()
    }
    if (pcm.isEmpty()) throw TtsEngineException("Android produced empty speech audio")
    return SynthesisResult(pcm, sampleRate, pcmDurationMs(pcm.size, sampleRate))
}

private fun ByteArray.asAscii(offset: Int, length: Int): String =
    String(this, offset, length, Charsets.US_ASCII)

private suspend inline fun <T> platformResult(crossinline block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (failure: Throwable) {
    Result.failure(failure)
}
