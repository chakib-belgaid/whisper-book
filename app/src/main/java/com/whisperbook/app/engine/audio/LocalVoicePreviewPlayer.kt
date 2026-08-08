package com.whisperbook.app.engine.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.whisperbook.app.domain.LocalTtsEngine
import com.whisperbook.app.domain.SynthesisRequest
import com.whisperbook.app.domain.VoicePreviewPlayer
import com.whisperbook.app.domain.model.VoiceDescriptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LocalVoicePreviewPlayer internal constructor(
    private val ttsEngine: LocalTtsEngine,
    private val audioSink: PcmAudioSink,
    private val previewCache: VoicePreviewClipCache? = null,
) : VoicePreviewPlayer {
    constructor(ttsEngine: LocalTtsEngine) : this(ttsEngine, AndroidPcmAudioSink())
    internal constructor(
        ttsEngine: LocalTtsEngine,
        previewCache: VoicePreviewClipCache,
    ) : this(ttsEngine, AndroidPcmAudioSink(), previewCache)

    private val playbackMutex = Mutex()

    override suspend fun play(
        text: String,
        voice: VoiceDescriptor,
        speed: Float,
        languageCode: String,
    ): Result<Unit> = resultOf {
        require(text.isNotBlank()) { "Voice preview text must not be blank" }
        playbackMutex.withLock {
            audioSink.stop()
            // Installation previews are currently pre-generated in English. Other language
            // packs synthesize their own localized sample so an English clip is never replayed.
            previewCache?.takeIf { languageCode == "en" }?.read(voice, speed)?.let { cached ->
                audioSink.play(cached.pcm16, cached.sampleRate)
                return@withLock
            }
            val result = LocalAudioGenerationCoordinator.run {
                // Keep the preview responsive by avoiding CPU contention with WorkManager or
                // on-demand chapter synthesis. Waiting for the current passage remains cancellable.
                ttsEngine.warmUp().getOrThrow()
                ttsEngine.synthesize(
                    SynthesisRequest(
                        text = text,
                        voice = voice,
                        speed = speed,
                        cacheKey = "voice-preview",
                        languageCode = languageCode,
                    ),
                ).getOrThrow()
            }
            audioSink.play(result.pcm16, result.sampleRate)
        }
    }

    override fun stop() = audioSink.stop()

    override fun close() {
        audioSink.stop()
        ttsEngine.close()
    }
}

internal interface PcmAudioSink {
    suspend fun play(pcm16: ShortArray, sampleRate: Int)
    fun stop()
}

private class AndroidPcmAudioSink(
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PcmAudioSink {
    private val trackLock = Any()
    private var activeTrack: AudioTrack? = null

    override suspend fun play(pcm16: ShortArray, sampleRate: Int) {
        require(pcm16.isNotEmpty()) { "Voice preview audio must not be empty" }
        require(sampleRate > 0) { "Voice preview sample rate must be positive" }
        withContext(workerDispatcher) {
            val encoding = AudioFormat.ENCODING_PCM_16BIT
            val channelMask = AudioFormat.CHANNEL_OUT_MONO
            val minimumBytes = AudioTrack.getMinBufferSize(sampleRate, channelMask, encoding)
            check(minimumBytes > 0) { "Android could not allocate a voice preview buffer" }
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(encoding)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelMask)
                        .build(),
                )
                .setBufferSizeInBytes(minimumBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            check(track.state == AudioTrack.STATE_INITIALIZED) {
                track.release()
                "Android could not initialize voice preview playback"
            }

            synchronized(trackLock) {
                releaseActiveTrackLocked()
                activeTrack = track
            }
            try {
                track.play()
                var offset = 0
                while (offset < pcm16.size) {
                    val written = track.write(
                        pcm16,
                        offset,
                        pcm16.size - offset,
                        AudioTrack.WRITE_BLOCKING,
                    )
                    check(written > 0) { "Android stopped while loading the voice preview" }
                    offset += written
                }
                delay(PLAYBACK_TAIL_MS)
            } finally {
                synchronized(trackLock) {
                    if (activeTrack === track) {
                        activeTrack = null
                        releaseTrack(track)
                    }
                }
            }
        }
    }

    override fun stop() {
        synchronized(trackLock) { releaseActiveTrackLocked() }
    }

    private fun releaseActiveTrackLocked() {
        activeTrack?.let(::releaseTrack)
        activeTrack = null
    }

    private fun releaseTrack(track: AudioTrack) {
        runCatching { track.pause() }
        runCatching { track.flush() }
        runCatching { track.stop() }
        runCatching { track.release() }
    }

    private companion object {
        const val PLAYBACK_TAIL_MS = 120L
    }
}

private suspend inline fun <T> resultOf(crossinline block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (failure: Throwable) {
    Result.failure(failure)
}
