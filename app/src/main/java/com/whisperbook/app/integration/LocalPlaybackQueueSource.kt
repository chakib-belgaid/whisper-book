package com.whisperbook.app.integration

import com.whisperbook.app.data.local.db.AudioSegmentEntity
import com.whisperbook.app.data.local.db.StoryCharacterEntity
import com.whisperbook.app.data.local.db.WhisperBookDatabase
import com.whisperbook.app.data.local.db.toDomain
import com.whisperbook.app.data.local.db.toEntity
import com.whisperbook.app.domain.LocalTtsEngine
import com.whisperbook.app.domain.SynthesisRequest
import com.whisperbook.app.domain.model.AudioSegment
import com.whisperbook.app.domain.model.AudioSegmentState
import com.whisperbook.app.domain.model.AppSettings
import com.whisperbook.app.domain.model.CharacterVoiceAssignment
import com.whisperbook.app.domain.model.VoiceDescriptor
import com.whisperbook.app.engine.audio.AppPrivateAudioSegmentStore
import com.whisperbook.app.engine.audio.LocalAudioGenerationCoordinator
import com.whisperbook.app.engine.audio.NarrationSynthesisPlanner
import com.whisperbook.app.engine.tts.CharacterVoiceCaster
import com.whisperbook.app.engine.tts.SherpaKittenTtsEngine
import com.whisperbook.app.playback.PlayableSegment
import com.whisperbook.app.playback.PlaybackChapterQueue
import com.whisperbook.app.playback.PlaybackQueueSource
import java.io.File
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
    private val settingsFlow: Flow<AppSettings> = flowOf(AppSettings()),
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

    override suspend fun loadProgressively(
        bookId: String,
        chapterId: String?,
        onProgress: suspend (
            readyQueue: PlaybackChapterQueue?,
            completedSegments: Int,
            totalSegments: Int,
        ) -> Unit,
    ): Result<PlaybackChapterQueue> = withContext(Dispatchers.IO) {
        try {
            Result.success(
                LocalAudioGenerationCoordinator.withOnDemandPriority {
                    buildQueue(bookId, chapterId, onProgress)
                },
            )
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
            val chapters = database.chapterDao().getHeadersForBook(bookId)
            val currentIndex = chapters.indexOfFirst { it.id == chapterId }
            val nextChapterId = chapters.getOrNull(currentIndex + 1)?.id
                ?.takeIf { currentIndex >= 0 }
            Result.success(nextChapterId?.let { buildQueue(bookId, it) })
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            Result.failure(failure)
        }
    }

    private suspend fun buildQueue(
        bookId: String,
        requestedChapterId: String?,
        onProgress: (suspend (PlaybackChapterQueue?, Int, Int) -> Unit)? = null,
    ): PlaybackChapterQueue {
        val book = database.bookDao().getById(bookId)
            ?: error("This book is no longer in the library")
        val chapters = database.chapterDao().getHeadersForBook(bookId)
        check(chapters.isNotEmpty()) { "This book has no prepared chapters yet" }
        val chapterHeader = requestedChapterId
            ?.let { id -> chapters.firstOrNull { it.id == id } }
            ?: book.currentChapterId?.let { id -> chapters.firstOrNull { it.id == id } }
            ?: chapters.first()
        val chapter = database.chapterDao().getById(chapterHeader.id)
            ?: error("This chapter is no longer available")
        val passages = chapter.passages.sortedBy { it.ordinal }
        check(passages.isNotEmpty()) { "This chapter has no readable passages" }
        check(passages.all { it.attributionRule != UNATTRIBUTED_RULE }) {
            "This chapter's voices are still being prepared"
        }

        val characters = database.storyCharacterDao().getEntitiesForBook(bookId).associateBy { it.id }
        check(passages.all { it.speakerId in characters }) {
            "This chapter's character catalog is still being prepared"
        }
        val characterNames = characters.mapValues { it.value.displayName }
        val sourcePassages = passages.map { passage ->
            SourcePassage(
                passageId = passage.id,
                text = passage.text,
                speakerId = passage.speakerId,
            )
        }
        check(sourcePassages.isNotEmpty()) { "This chapter has no narratable passages" }
        val settings = settingsFlow.first()
        val resolvedVoices = resolveVoices(
            chapterId = chapterHeader.id,
            speakerIds = sourcePassages.map(SourcePassage::speakerId).distinct(),
            characters = characters,
        )
        val resolvedPassages = sourcePassages.flatMap { passage ->
            val resolvedVoice = resolvedVoices.getValue(passage.speakerId)
            NarrationSynthesisPlanner.plan(
                passageId = passage.passageId,
                text = passage.text,
                voice = resolvedVoice.voice,
                speed = resolvedVoice.assignment.speed,
                modelVersion = resolvedVoice.assignment.modelVersion,
                sampleRate = expectedSampleRate,
                languageCode = settings.narrationLanguageCode,
            ).map { unit ->
                ResolvedPassage(
                    queued = QueuedPassage(
                        sourcePassageId = passage.passageId,
                        passageId = unit.passageId,
                        speakerId = passage.speakerId,
                    ),
                    request = unit.request,
                )
            }
        }
        check(resolvedPassages.isNotEmpty()) { "This chapter has no narratable passages" }
        val persistedSegments = resolvedPassages
            .chunked(SQL_QUERY_BATCH_SIZE)
            .flatMap { batch -> database.audioSegmentDao().findByCacheKeys(batch.map { it.request.cacheKey }) }
            .associateBy(AudioSegmentEntity::cacheKey)
        val checkpoint = database.playbackCheckpointDao().getForBook(bookId)
            ?.takeIf { it.chapterId == chapterHeader.id }
        val resumeTarget = resolvePlaybackResumeTarget(
            checkpoint = checkpoint?.let { saved ->
                SavedPlaybackResume(
                    passageId = saved.passageId,
                    segmentId = saved.segmentId,
                    segmentPositionMs = saved.segmentPositionMs,
                )
            },
            currentPassageId = book.currentPassageId,
            plannedSegments = resolvedPassages.map { passage ->
                PlannedPlaybackSegment(
                    passageId = passage.queued.passageId,
                    sourcePassageId = passage.queued.sourcePassageId,
                    segmentId = passage.request.cacheKey,
                )
            },
        )
        val queueSnapshot: (List<PlayableSegment>) -> PlaybackChapterQueue = { readySegments ->
            val resumeSegment = readySegments.firstOrNull { segment ->
                segment.passageId == resumeTarget.passageId &&
                    segment.audioSegment.id == resumeTarget.segmentId
            }
            PlaybackChapterQueue(
                bookId = bookId,
                chapterId = chapterHeader.id,
                bookTitle = book.title,
                chapterTitle = chapterHeader.title,
                segments = readySegments,
                startPassageId = resumeTarget.passageId,
                // Reuse an offset only for the exact cached segment it was recorded against.
                // Older checkpoints used larger chunks and must restart at the mapped microsegment.
                startSegmentPositionMs = resumeSegment?.let { resumeTarget.segmentPositionMs } ?: 0L,
            )
        }
        onProgress?.invoke(null, 0, resolvedPassages.size)
        var engine: LocalTtsEngine? = null
        try {
            val segments = ArrayList<PlayableSegment>(resolvedPassages.size)
            resolvedPassages.forEachIndexed { passageOrdinal, passage ->
                val ready = resolvePassageAudio(
                    passage = passage,
                    persisted = persistedSegments[passage.request.cacheKey],
                    engineProvider = {
                        engine ?: ttsEngineFactory().also {
                            it.warmUp().getOrThrow()
                            engine = it
                        }
                    },
                )
                segments += PlayableSegment(
                    passageId = passage.queued.passageId,
                    passageOrdinal = passageOrdinal,
                    speakerName = characterNames[passage.queued.speakerId] ?: "Narrator",
                    audioSegment = ready,
                )
                val startPassageIsReady = resumeTarget.passageId == null ||
                    segments.any { it.passageId == resumeTarget.passageId }
                onProgress?.invoke(
                    queueSnapshot(segments.toList()).takeIf { startPassageIsReady },
                    segments.size,
                    resolvedPassages.size,
                )
            }
            return queueSnapshot(segments)
        } finally {
            engine?.close()
        }
    }

    private suspend fun resolveVoices(
        chapterId: String,
        speakerIds: List<String>,
        characters: Map<String, StoryCharacterEntity>,
    ): Map<String, ResolvedVoice> {
        check(voices.isNotEmpty()) { "No embedded voices are available" }
        val storedAssignments = database.voiceAssignmentDao().getForCharacters(speakerIds)
            .associate { assignment -> assignment.characterId to assignment.toDomain() }
        val chapterAssignments = speakerIds.mapNotNull { speakerId ->
            database.chapterVoiceAssignmentDao()
                .getForChapterAndCharacter(chapterId, speakerId)
        }.associate { assignment -> assignment.characterId to assignment.toDomain() }
        val preferredNarrator = settingsFlow.first().defaultNarratorVoiceId.let { preferredId ->
            voices.firstOrNull { voice -> voice.id == preferredId }
        }
        return speakerIds.associateWith { speakerId ->
            val stored = chapterAssignments[speakerId] ?: storedAssignments[speakerId]
            val voice = stored?.let { assignment -> voices.firstOrNull { it.id == assignment.voiceId } }
                ?: defaultVoiceFor(speakerId, characters[speakerId], preferredNarrator)
            val assignment = stored?.takeIf { it.voiceId == voice.id }
                ?: CharacterVoiceAssignment(
                    characterId = speakerId,
                    voiceId = voice.id,
                    modelVersion = modelVersion,
                    speed = 1f,
                ).also { database.voiceAssignmentDao().upsert(it.toEntity()) }
            ResolvedVoice(voice, assignment)
        }
    }

    private suspend fun resolvePassageAudio(
        passage: ResolvedPassage,
        persisted: AudioSegmentEntity?,
        engineProvider: suspend () -> LocalTtsEngine,
    ): AudioSegment {
        val cacheKey = passage.request.cacheKey
        persisted
            ?.takeIf { it.state == AudioSegmentState.READY.name }
            ?.toDomain()
            ?.takeIf { it.path?.let(::File)?.isFile == true }
            ?.let { return it }
        return LocalAudioGenerationCoordinator.run {
            val completed = database.audioSegmentDao().findByCacheKey(cacheKey)
                ?.takeIf { it.state == AudioSegmentState.READY.name }
                ?.toDomain()
                ?.takeIf { it.path?.let(::File)?.isFile == true }
                ?: audioStore.find(cacheKey)
                    ?.takeIf { it.path?.let(::File)?.isFile == true }
            if (completed != null) {
                database.audioSegmentDao().upsert(completed.toEntity())
                return@run completed
            }
            database.audioSegmentDao().upsert(
                AudioSegmentEntity(
                    id = cacheKey,
                    passageId = passage.queued.sourcePassageId,
                    cacheKey = cacheKey,
                    state = AudioSegmentState.GENERATING.name,
                    path = null,
                    durationMs = 0L,
                    sampleRate = expectedSampleRate,
                ),
            )
            try {
                val engine = engineProvider()
                val result = engine.synthesize(passage.request).getOrThrow()
                check(result.sampleRate == expectedSampleRate) {
                    "The embedded voice returned an unexpected sample rate"
                }
                audioStore.writeForPassage(
                    passage.queued.sourcePassageId,
                    passage.queued.speakerId,
                    passage.request,
                    result,
                )
                    .also { database.audioSegmentDao().upsert(it.toEntity()) }
            } catch (cancellation: CancellationException) {
                // A newer chapter selection canceled this request. Do not present that intentional
                // interruption as a synthesis failure; a later request can resume the passage.
                throw cancellation
            } catch (failure: Throwable) {
                database.audioSegmentDao().updateState(cacheKey, AudioSegmentState.FAILED.name, null)
                throw failure
            }
        }
    }

    private data class QueuedPassage(
        val sourcePassageId: String,
        val passageId: String,
        val speakerId: String,
    )

    private data class SourcePassage(
        val passageId: String,
        val text: String,
        val speakerId: String,
    )

    private data class ResolvedVoice(
        val voice: VoiceDescriptor,
        val assignment: CharacterVoiceAssignment,
    )

    private data class ResolvedPassage(
        val queued: QueuedPassage,
        val request: SynthesisRequest,
    )

    private fun defaultVoiceFor(
        speakerId: String,
        character: StoryCharacterEntity?,
        preferredNarrator: VoiceDescriptor?,
    ): VoiceDescriptor =
        character?.let { storedCharacter ->
            CharacterVoiceCaster.select(
                character = storedCharacter.toDomain(),
                voices = voices,
                preferredNarrator = preferredNarrator,
            )
        } ?: voices[Math.floorMod(speakerId.hashCode(), voices.size)]

    private companion object {
        const val SQL_QUERY_BATCH_SIZE = 900
        const val UNATTRIBUTED_RULE = "preparation-unattributed"
    }
}
