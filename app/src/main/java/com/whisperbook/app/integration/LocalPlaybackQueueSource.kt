package com.whisperbook.app.integration

import com.whisperbook.app.data.local.db.AudioSegmentEntity
import com.whisperbook.app.data.local.db.WhisperBookDatabase
import com.whisperbook.app.data.local.db.toDomain
import com.whisperbook.app.data.local.db.toEntity
import com.whisperbook.app.domain.LocalTtsEngine
import com.whisperbook.app.domain.SynthesisRequest
import com.whisperbook.app.domain.model.AudioSegment
import com.whisperbook.app.domain.model.AudioSegmentState
import com.whisperbook.app.domain.model.BuiltInCharacters
import com.whisperbook.app.domain.model.CharacterVoiceAssignment
import com.whisperbook.app.domain.model.VoiceDescriptor
import com.whisperbook.app.engine.audio.AppPrivateAudioSegmentStore
import com.whisperbook.app.engine.audio.AudioCacheKey
import com.whisperbook.app.engine.tts.SherpaKittenTtsEngine
import com.whisperbook.app.playback.PlayableSegment
import com.whisperbook.app.playback.PlaybackChapterQueue
import com.whisperbook.app.playback.PlaybackQueueSource
import java.io.File
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Resolves a Media3 queue entirely from local storage and fills any missing chapter audio on demand.
 * This makes every prepared chapter playable even when WorkManager only prefetched its opening.
 */
class LocalPlaybackQueueSource(
    private val database: WhisperBookDatabase,
    private val audioStore: AppPrivateAudioSegmentStore,
    private val ttsEngineFactory: () -> LocalTtsEngine,
    private val voices: List<VoiceDescriptor> = SherpaKittenTtsEngine.KITTEN_VOICES,
    private val modelVersion: String = SherpaKittenTtsEngine.MODEL_VERSION,
    private val expectedSampleRate: Int = SherpaKittenTtsEngine.EXPECTED_SAMPLE_RATE,
) : PlaybackQueueSource {
    override suspend fun load(bookId: String, chapterId: String?): Result<PlaybackChapterQueue> =
        withContext(Dispatchers.IO) {
            try {
                Result.success(buildQueue(bookId, chapterId))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                Result.failure(failure)
            }
        }

    override suspend fun loadNext(
        bookId: String,
        chapterId: String,
    ): Result<PlaybackChapterQueue?> = withContext(Dispatchers.IO) {
        try {
            val chapters = database.chapterDao().observeForBook(bookId).first()
                .sortedBy { it.chapter.ordinal }
            val currentIndex = chapters.indexOfFirst { it.chapter.id == chapterId }
            val nextChapterId = chapters.getOrNull(currentIndex + 1)?.chapter?.id
                ?.takeIf { currentIndex >= 0 }
            Result.success(nextChapterId?.let { buildQueue(bookId, it) })
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            Result.failure(failure)
        }
    }

    private suspend fun buildQueue(bookId: String, requestedChapterId: String?): PlaybackChapterQueue {
        val book = database.bookDao().observeById(bookId).first()
            ?: error("This book is no longer in the library")
        val chapters = database.chapterDao().observeForBook(bookId).first().sortedBy { it.chapter.ordinal }
        check(chapters.isNotEmpty()) { "This book has no prepared chapters yet" }
        val chapter = requestedChapterId
            ?.let { id -> chapters.firstOrNull { it.chapter.id == id } }
            ?: book.book.currentChapterId?.let { id -> chapters.firstOrNull { it.chapter.id == id } }
            ?: chapters.first()
        val passages = chapter.passages.sortedBy { it.ordinal }
        check(passages.isNotEmpty()) { "This chapter has no readable passages" }

        val characterNames = database.storyCharacterDao().observeForBook(bookId).first()
            .associate { it.character.id to it.character.displayName }
        var engine: LocalTtsEngine? = null
        try {
            val segments = passages.map { passage ->
                val ready = database.audioSegmentDao().observeForPassage(passage.id).first()
                    .asSequence()
                    .filter { it.state == AudioSegmentState.READY.name }
                    .map { it.toDomain() }
                    .firstOrNull { it.path?.let(::File)?.isFile == true }
                    ?: run {
                        val activeEngine = engine ?: ttsEngineFactory().also {
                            it.warmUp().getOrThrow()
                            engine = it
                        }
                        synthesizeMissingPassage(passage.id, passage.text, passage.speakerId, activeEngine)
                    }
                PlayableSegment(
                    passageId = passage.id,
                    passageOrdinal = passage.ordinal,
                    speakerName = characterNames[passage.speakerId] ?: "Narrator",
                    audioSegment = ready,
                )
            }
            val checkpoint = database.playbackCheckpointDao().getForBook(bookId)
                ?.takeIf { it.chapterId == chapter.chapter.id }
            return PlaybackChapterQueue(
                bookId = bookId,
                chapterId = chapter.chapter.id,
                bookTitle = book.book.title,
                chapterTitle = chapter.chapter.title,
                segments = segments,
                startPassageId = checkpoint?.passageId ?: book.book.currentPassageId,
                startSegmentPositionMs = checkpoint?.segmentPositionMs ?: 0L,
            )
        } finally {
            engine?.close()
        }
    }

    private suspend fun synthesizeMissingPassage(
        passageId: String,
        text: String,
        speakerId: String,
        engine: LocalTtsEngine,
    ): AudioSegment {
        check(voices.isNotEmpty()) { "No embedded voices are available" }
        val stored = database.voiceAssignmentDao().getForCharacter(speakerId)
        val voice = stored?.let { assignment -> voices.firstOrNull { it.id == assignment.voiceId } }
            ?: defaultVoiceFor(speakerId)
        val assignment = stored?.takeIf { it.voiceId == voice.id }
            ?: CharacterVoiceAssignment(
                characterId = speakerId,
                voiceId = voice.id,
                modelVersion = modelVersion,
                speed = 1f,
            ).also { database.voiceAssignmentDao().upsert(it.toEntity()) }.toEntity()
        val provisional = SynthesisRequest(text.trim(), voice, assignment.speed, cacheKey = "pending")
        // Passage ownership is included so repeated prose in one book has independent DB rows while
        // the generated waveform remains stable and private.
        val waveformKey = AudioCacheKey.fromRequest(provisional, assignment.modelVersion, expectedSampleRate)
        val cacheKey = AudioCacheKey.create(
            text = "$passageId\u0000$waveformKey",
            voiceId = voice.id,
            speakerIndex = voice.speakerIndex,
            modelVersion = assignment.modelVersion,
            speed = assignment.speed,
            sampleRate = expectedSampleRate,
        )
        val request = provisional.copy(cacheKey = cacheKey)
        database.audioSegmentDao().upsert(
            AudioSegmentEntity(
                id = cacheKey,
                passageId = passageId,
                cacheKey = cacheKey,
                state = AudioSegmentState.GENERATING.name,
                path = null,
                durationMs = 0L,
                sampleRate = expectedSampleRate,
            ),
        )
        return try {
            val result = engine.synthesize(request).getOrThrow()
            check(result.sampleRate == expectedSampleRate) {
                "The embedded voice returned an unexpected sample rate"
            }
            audioStore.writeForPassage(passageId, speakerId, request, result)
                .also { database.audioSegmentDao().upsert(it.toEntity()) }
        } catch (failure: Throwable) {
            database.audioSegmentDao().updateState(cacheKey, AudioSegmentState.FAILED.name, null)
            throw failure
        }
    }

    private fun defaultVoiceFor(speakerId: String): VoiceDescriptor = if (speakerId == BuiltInCharacters.NARRATOR_ID) {
        voices.firstOrNull { it.id == "bella" } ?: voices.first()
    } else {
        voices[Math.floorMod(speakerId.hashCode(), voices.size)]
    }
}
