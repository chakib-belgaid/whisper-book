package com.whisperbook.app.engine.preparation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.sqlite.SQLiteException
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.whisperbook.app.MainActivity
import com.whisperbook.app.R
import com.whisperbook.app.data.local.db.AudioSegmentEntity
import com.whisperbook.app.data.local.db.ChapterEntity
import com.whisperbook.app.data.local.db.PassageEntity
import com.whisperbook.app.data.local.db.PreparationJobEntity
import com.whisperbook.app.data.local.db.toAliasEntities
import com.whisperbook.app.data.local.db.toDomain
import com.whisperbook.app.data.local.db.toEntity
import com.whisperbook.app.domain.ExtractedChapter
import com.whisperbook.app.domain.ExtractedPublication
import com.whisperbook.app.domain.ImportedBook
import com.whisperbook.app.domain.PassageTextChunker
import com.whisperbook.app.domain.SynthesisRequest
import com.whisperbook.app.domain.model.AudioSegmentState
import com.whisperbook.app.domain.model.BookFormat
import com.whisperbook.app.domain.model.BuiltInCharacters
import com.whisperbook.app.domain.model.CharacterColorRole
import com.whisperbook.app.domain.model.CharacterVoiceAssignment
import com.whisperbook.app.domain.model.Passage
import com.whisperbook.app.domain.model.PreparationStage
import com.whisperbook.app.domain.model.PreparationState
import com.whisperbook.app.engine.audio.AudioCacheKey
import com.whisperbook.app.engine.audio.LocalAudioGenerationCoordinator
import com.whisperbook.app.engine.document.PdfImportException
import com.whisperbook.app.engine.document.SignatureBookFormatDetector
import com.whisperbook.app.engine.document.UnsupportedPublicationException
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.first

class PreparationWorker @JvmOverloads constructor(
    appContext: Context,
    workerParameters: WorkerParameters,
    private val dependencies: PreparationDependencies = PreparationRuntime.resolve(appContext),
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val bookId = inputData.getString(PreparationWorkPlan.KEY_BOOK_ID)
            ?.takeIf(String::isNotBlank)
            ?: return invalidInput("Missing book id")
        val stage = inputData.getString(PreparationWorkPlan.KEY_STAGE)
            ?.let { encoded -> PreparationStage.entries.firstOrNull { it.name == encoded } }
            ?.takeIf { it in PreparationWorkPlan.stages }
            ?: return invalidInput("Missing or invalid preparation stage")
        val fromChapterOrdinal = inputData
            .getInt(PreparationWorkPlan.KEY_FROM_CHAPTER_ORDINAL, 0)
            .takeIf { it >= 0 }
            ?: return invalidInput("Starting chapter ordinal must not be negative")
        val runner = PreparationStageRunner(dependencies) { state ->
            setForeground(createForegroundInfo(bookId, state))
        }

        return try {
            setForeground(
                createForegroundInfo(
                    bookId,
                    PreparationState(stage, message = stage.notificationMessage()),
                ),
            )
            runner.run(bookId, stage, runAttemptCount, fromChapterOrdinal)
            Result.success()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            val mapped = PreparationErrorMapper.map(failure)
            try {
                runner.markFailed(bookId, mapped, runAttemptCount + 1)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                // The book may have been deleted while this worker was running.
            }
            if (mapped.retryable && runAttemptCount + 1 < MAX_AUTOMATIC_ATTEMPTS) {
                Result.retry()
            } else {
                Result.failure(
                    workDataOf(
                        PreparationWorkPlan.KEY_ERROR_CODE to mapped.code,
                        PreparationWorkPlan.KEY_ERROR_MESSAGE to mapped.message,
                    ),
                )
            }
        }
    }

    private fun invalidInput(message: String): Result = Result.failure(
        workDataOf(
            PreparationWorkPlan.KEY_ERROR_CODE to "invalid-work-input",
            PreparationWorkPlan.KEY_ERROR_MESSAGE to message,
        ),
    )

    private fun createForegroundInfo(bookId: String, state: PreparationState): ForegroundInfo {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                PREPARATION_CHANNEL_ID,
                "Audiobook preparation",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Progress while books are prepared privately on this device"
                setShowBadge(false)
            }
            applicationContext.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
        val hasProgress = state.totalUnits > 0
        val progress = if (hasProgress) {
            (state.progressFraction.coerceIn(0f, 1f) * NOTIFICATION_PROGRESS_MAX).toInt()
        } else {
            0
        }
        val contentText = preparationNotificationText(state)
        val openApp = PendingIntent.getActivity(
            applicationContext,
            bookId.hashCode(),
            Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, PREPARATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Recording your audiobook")
            .setContentText(contentText)
            .setSubText(state.message?.takeIf { it != contentText })
            .setContentIntent(openApp)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(NOTIFICATION_PROGRESS_MAX, progress, !hasProgress)
            .build()
        val notificationId = PREPARATION_NOTIFICATION_ID_BASE + (bookId.hashCode() and 0x0fff)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private companion object {
        const val MAX_AUTOMATIC_ATTEMPTS = 3
        const val NOTIFICATION_PROGRESS_MAX = 1_000
        const val PREPARATION_CHANNEL_ID = "audiobook-preparation"
        const val PREPARATION_NOTIFICATION_ID_BASE = 22_000
    }
}

internal class PreparationStageRunner(
    private val dependencies: PreparationDependencies,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
    private val onStateCheckpoint: suspend (PreparationState) -> Unit = {},
) {
    private val database get() = dependencies.database

    suspend fun run(
        bookId: String,
        stage: PreparationStage,
        attemptCount: Int,
        fromChapterOrdinal: Int = 0,
    ) {
        when (stage) {
            PreparationStage.COPY_AND_VALIDATE -> validatePrivateCopy(bookId, attemptCount)
            PreparationStage.READING_CHAPTERS -> extractChapters(bookId, attemptCount)
            PreparationStage.FINDING_CHARACTERS -> attributeSpeakers(bookId, attemptCount)
            PreparationStage.ASSIGNING_VOICES -> assignVoices(bookId, attemptCount)
            PreparationStage.PREPARING_AUDIO -> prepareAudio(bookId, attemptCount, fromChapterOrdinal)
            PreparationStage.READY,
            PreparationStage.FAILED,
            -> throw PreparationPipelineException(
                code = "invalid-stage",
                message = "${stage.name} is not an executable preparation stage",
                retryable = false,
            )
        }
    }

    suspend fun markFailed(bookId: String, failure: MappedPreparationError, attemptCount: Int) {
        checkpoint(
            bookId = bookId,
            state = PreparationState(
                stage = PreparationStage.FAILED,
                message = failure.message,
                retryable = failure.retryable,
            ),
            attemptCount = attemptCount,
        )
    }

    private suspend fun validatePrivateCopy(bookId: String, attemptCount: Int) {
        val current = database.preparationJobDao().getForBook(bookId)
        val book = requireBook(bookId)
        val source = book.privateSourcePath?.let(::File)
            ?: throw PreparationPipelineException(
                code = "private-copy-missing",
                message = "The private book copy is missing",
                retryable = false,
            )
        if (!source.isFile || source.length() <= 0L) {
            throw PreparationPipelineException(
                code = "private-copy-unreadable",
                message = "The private book copy is unavailable or empty",
                retryable = false,
            )
        }
        val detected = SignatureBookFormatDetector.detect(source)
        val expected = enumValueOrNull<BookFormat>(book.format)
            ?: throw PreparationPipelineException("invalid-format", "The stored book format is invalid", false)
        if (detected != expected) {
            throw PreparationPipelineException(
                code = "format-mismatch",
                message = "The private copy no longer matches its imported format",
                retryable = false,
            )
        }
        book.sourceSha256?.takeIf(String::isNotBlank)?.let { expectedHash ->
            if (!source.sha256().equals(expectedHash, ignoreCase = true)) {
                throw PreparationPipelineException(
                    code = "hash-mismatch",
                    message = "The private book copy failed its integrity check",
                    retryable = false,
                )
            }
        }

        if (current == null || current.stage in setOf(PreparationStage.COPY_AND_VALIDATE.name, PreparationStage.FAILED.name)) {
            checkpoint(
                bookId,
                PreparationState(
                    stage = PreparationStage.READING_CHAPTERS,
                    completedUnits = 1,
                    totalUnits = 1,
                    progressFraction = 1f,
                    message = "Private copy verified",
                ),
                attemptCount,
            )
        }
    }

    private suspend fun extractChapters(bookId: String, attemptCount: Int) {
        val existing = database.chapterDao().observeForBook(bookId).first()
        if (existing.isNotEmpty() && existing.any { it.passages.isNotEmpty() }) return

        checkpoint(
            bookId,
            PreparationState(PreparationStage.READING_CHAPTERS, message = "Reading chapters on this device"),
            attemptCount,
        )
        val book = requireBook(bookId)
        val file = book.privateSourcePath?.let(::File)
            ?: throw PreparationPipelineException("private-copy-missing", "The private book copy is missing", false)
        val imported = ImportedBook(
            title = book.title,
            author = book.author,
            format = enumValueOrNull<BookFormat>(book.format)
                ?: throw PreparationPipelineException("invalid-format", "The stored book format is invalid", false),
            privateFile = file,
            sha256 = book.sourceSha256.orEmpty().ifBlank { file.sha256() },
        )
        val publication = dependencies.publicationExtractor.extract(imported) { completed, total ->
            val safeTotal = total.coerceAtLeast(1)
            val safeCompleted = completed.coerceIn(0, safeTotal)
            checkpoint(
                bookId,
                PreparationState(
                    stage = PreparationStage.READING_CHAPTERS,
                    completedUnits = safeCompleted,
                    totalUnits = safeTotal,
                    progressFraction = safeCompleted.toFloat() / safeTotal,
                    message = "Reading page $safeCompleted of $safeTotal",
                ),
                attemptCount,
            )
        }.getOrThrow()
        if (publication.chapters.isEmpty()) {
            throw PreparationPipelineException("empty-publication", "No readable chapters were found", false)
        }

        val chapters = publication.chapters.mapIndexed { index, chapter ->
            ChapterEntity(
                id = chapterId(bookId, index),
                bookId = bookId,
                ordinal = index,
                title = chapter.title.ifBlank { "Chapter ${index + 1}" },
            )
        }
        val passages = publication.chapters.flatMapIndexed { chapterIndex, chapter ->
            val chapterId = chapterId(bookId, chapterIndex)
            val boundedParagraphs = chapter.paragraphs.flatMap(PassageTextChunker::split)
            boundedParagraphs.mapIndexed { passageIndex, text ->
                PassageEntity(
                    id = passageId(chapterId, passageIndex),
                    chapterId = chapterId,
                    ordinal = passageIndex,
                    text = text.trim(),
                    speakerId = BuiltInCharacters.NARRATOR_ID,
                    confidence = 0f,
                    attributionRule = UNATTRIBUTED_RULE,
                )
            }
        }
        if (passages.isEmpty()) {
            throw PreparationPipelineException("empty-publication", "No readable passages were found", false)
        }

        database.withTransaction {
            database.chapterDao().deleteForBook(bookId)
            database.chapterDao().insertAll(chapters)
            database.passageDao().insertAll(passages)
            database.preparationJobDao().upsert(
                stateEntity(
                    bookId,
                    PreparationState(
                        stage = PreparationStage.FINDING_CHARACTERS,
                        completedUnits = chapters.size,
                        totalUnits = chapters.size,
                        progressFraction = 1f,
                        message = "Found ${chapters.size} chapters",
                    ),
                    attemptCount,
                ),
            )
        }
    }

    private suspend fun attributeSpeakers(bookId: String, attemptCount: Int) {
        val source = database.chapterDao().observeForBook(bookId).first()
        if (source.isEmpty()) {
            throw PreparationPipelineException("chapters-missing", "Chapters must be read before attribution", false)
        }
        val storedPassages = source.flatMap { it.passages }
        val alreadyAttributed = storedPassages.isNotEmpty() &&
            storedPassages.all { it.attributionRule != UNATTRIBUTED_RULE }
        val existingCharacters = database.storyCharacterDao().observeForBook(bookId).first()
        val characterIds = existingCharacters.mapTo(hashSetOf()) { it.character.id }
        val allSpeakersPersisted = storedPassages.all { it.speakerId in characterIds }
        if (alreadyAttributed && existingCharacters.isNotEmpty() && allSpeakersPersisted) {
            return
        }

        checkpoint(
            bookId,
            PreparationState(
                PreparationStage.FINDING_CHARACTERS,
                totalUnits = source.sumOf { it.passages.size },
                message = "Finding the voices in this story",
            ),
            attemptCount,
        )
        val publication = ExtractedPublication(
            title = requireBook(bookId).title,
            author = requireBook(bookId).author,
            chapters = source.map { aggregate ->
                ExtractedChapter(
                    title = aggregate.chapter.title,
                    paragraphs = aggregate.passages.sortedBy { it.ordinal }.map { it.text },
                )
            },
        )
        val attributed = dependencies.speakerAttributor.attribute(bookId, publication)
        if (attributed.chapters.isEmpty() || attributed.characters.isEmpty()) {
            throw PreparationPipelineException("attribution-empty", "No narratable story passages were found", false)
        }
        // Character IDs are database primary keys, so the built-in narrator must be book-scoped
        // just like every discovered character. This also keeps per-character cache invalidation
        // isolated when several books are in the library.
        val narratorId = "$bookId-character-narrator"
        val persistedChapters = attributed.chapters.map { chapter ->
            chapter.copy(
                passages = chapter.passages.map { passage ->
                    if (passage.speakerId == BuiltInCharacters.NARRATOR_ID) {
                        passage.copy(speakerId = narratorId)
                    } else {
                        passage
                    }
                },
            )
        }
        val persistedCharacters = attributed.characters.map { character ->
            if (character.id == BuiltInCharacters.NARRATOR_ID) character.copy(id = narratorId) else character
        }
        val attributedPassages = persistedChapters.flatMap { it.passages }
        if (attributedPassages.isEmpty()) {
            throw PreparationPipelineException("attribution-empty", "No narratable story passages were found", false)
        }

        database.withTransaction {
            database.chapterDao().deleteForBook(bookId)
            database.storyCharacterDao().deleteForBook(bookId)
            database.chapterDao().insertAll(persistedChapters.map { it.toEntity() })
            database.storyCharacterDao().insertAll(persistedCharacters.map { it.toEntity() })
            database.storyCharacterDao().insertAliases(persistedCharacters.flatMap { it.toAliasEntities() })
            database.passageDao().insertAll(attributedPassages.map { it.toEntity() })
            database.preparationJobDao().upsert(
                stateEntity(
                    bookId,
                    PreparationState(
                        stage = PreparationStage.ASSIGNING_VOICES,
                        completedUnits = persistedCharacters.size,
                        totalUnits = persistedCharacters.size,
                        progressFraction = 1f,
                        message = "Found ${persistedCharacters.size} story voices",
                    ),
                    attemptCount,
                ),
            )
        }
    }

    private suspend fun assignVoices(bookId: String, attemptCount: Int) {
        val characters = database.storyCharacterDao().observeForBook(bookId).first()
        if (characters.isEmpty()) {
            throw PreparationPipelineException("characters-missing", "Characters must be found before voices are assigned", false)
        }
        val engine = dependencies.ttsEngineFactory.create()
        try {
            val settings = dependencies.settingsFlow.first()
            val voices = engine.voices()
            if (voices.isEmpty()) {
                throw PreparationPipelineException("voices-unavailable", "No local story voices are available", false)
            }
            val voiceIds = voices.mapTo(hashSetOf()) { it.id }
            val assignments = characters.associate { character ->
                character.character.id to database.voiceAssignmentDao().getForCharacter(character.character.id)
            }
            val complete = assignments.values.all {
                it != null &&
                    it.voiceId in voiceIds &&
                    it.modelVersion == dependencies.modelVersion
            }
            if (complete) return

            checkpoint(
                bookId,
                PreparationState(
                    PreparationStage.ASSIGNING_VOICES,
                    totalUnits = characters.size,
                    message = "Casting local voices",
                ),
                attemptCount,
            )
            val narratorVoice = voices.firstOrNull { it.id == settings.defaultNarratorVoiceId }
                ?: voices.firstOrNull { it.id == dependencies.narratorVoiceId }
                ?: voices.first()
            val orderedCharacters = characters.sortedWith(
                compareBy(
                    { it.character.colorRole != CharacterColorRole.NARRATOR.name },
                    { it.character.id },
                ),
            )
            database.withTransaction {
                orderedCharacters.forEachIndexed { index, character ->
                    val existing = assignments[character.character.id]
                    if (
                        existing != null &&
                        existing.voiceId in voiceIds &&
                        existing.modelVersion == dependencies.modelVersion
                    ) {
                        return@forEachIndexed
                    }
                    val voice = if (character.character.colorRole == CharacterColorRole.NARRATOR.name) {
                        existing?.voiceId?.let { id -> voices.firstOrNull { it.id == id } } ?: narratorVoice
                    } else {
                        existing?.voiceId?.let { id -> voices.firstOrNull { it.id == id } }
                            ?: voices[index % voices.size]
                    }
                    database.voiceAssignmentDao().upsert(
                        CharacterVoiceAssignment(
                            characterId = character.character.id,
                            voiceId = voice.id,
                            modelVersion = dependencies.modelVersion,
                            speed = existing?.speed ?: settings.speakingSpeed
                                .takeIf { it.isFinite() && it in 0.5f..2f }
                                ?: dependencies.speakingSpeed,
                        ).toEntity(),
                    )
                }
                database.preparationJobDao().upsert(
                    stateEntity(
                        bookId,
                        PreparationState(
                            stage = PreparationStage.PREPARING_AUDIO,
                            completedUnits = characters.size,
                            totalUnits = characters.size,
                            progressFraction = 1f,
                            message = "The cast is ready",
                        ),
                        attemptCount,
                    ),
                )
            }
        } finally {
            engine.close()
        }
    }

    private suspend fun prepareAudio(
        bookId: String,
        attemptCount: Int,
        fromChapterOrdinal: Int,
    ) {
        val chapters = database.chapterDao().observeForBook(bookId).first()
        val allBatches = orderedChapterAudioBatches(chapters, fromChapterOrdinal = 0)
        if (allBatches.isEmpty()) {
            throw PreparationPipelineException("passages-missing", "Passages must exist before audio is prepared", false)
        }
        val targets = allBatches.filter { it.chapterOrdinal >= fromChapterOrdinal }
        if (targets.isEmpty()) {
            checkpoint(bookId, PreparationState.Ready, attemptCount)
            return
        }

        val completedBeforeStart = allBatches.size - targets.size
        checkpoint(
            bookId,
            PreparationState(
                PreparationStage.PREPARING_AUDIO,
                completedUnits = completedBeforeStart,
                totalUnits = allBatches.size,
                progressFraction = completedBeforeStart.toFloat() / allBatches.size,
                message = chapterPreparationMessage(targets.first(), allBatches.size),
            ),
            attemptCount,
        )
        val engine = dependencies.ttsEngineFactory.create()
        try {
            LocalAudioGenerationCoordinator.runBackground { engine.warmUp().getOrThrow() }
            val voices = engine.voices()
            if (voices.isEmpty()) {
                throw PreparationPipelineException("voices-unavailable", "No local story voices are available", false)
            }
            val synthesisBatches = targets.map { chapter ->
                ChapterAudioBatch(
                    chapterId = chapter.chapterId,
                    chapterOrdinal = chapter.chapterOrdinal,
                    chapterTitle = chapter.chapterTitle,
                    passages = chapter.passages.map { passage -> synthesisTask(passage, voices) },
                )
            }
            SequentialChapterAudioPreparer(
                isPassageReady = ::isPassageAudioDurable,
                preparePassage = { task -> synthesizePassage(engine, task) },
            ).prepare(
                chapters = synthesisBatches,
                onChapterStarted = { targetIndex, chapter ->
                    val completedChapters = completedBeforeStart + targetIndex
                    checkpoint(
                        bookId,
                        PreparationState(
                            stage = PreparationStage.PREPARING_AUDIO,
                            completedUnits = completedChapters,
                            totalUnits = allBatches.size,
                            progressFraction = completedChapters.toFloat() / allBatches.size,
                            message = chapterPreparationMessage(chapter, allBatches.size),
                        ),
                        attemptCount,
                    )
                },
                onChapterReady = { targetIndex, chapter ->
                    val completedChapters = completedBeforeStart + targetIndex + 1
                    checkpoint(
                        bookId,
                        PreparationState(
                            stage = PreparationStage.PREPARING_AUDIO,
                            completedUnits = completedChapters,
                            totalUnits = allBatches.size,
                            progressFraction = completedChapters.toFloat() / allBatches.size,
                            message = "${chapter.chapterTitle} is ready to listen",
                        ),
                        attemptCount,
                    )
                },
            )
        } finally {
            engine.close()
        }
        checkpoint(bookId, PreparationState.Ready, attemptCount)
    }

    private suspend fun synthesisTask(
        passage: PassageEntity,
        voices: List<com.whisperbook.app.domain.model.VoiceDescriptor>,
    ): PassageSynthesisTask {
        val assignment = database.chapterVoiceAssignmentDao()
            .getForChapterAndCharacter(passage.chapterId, passage.speakerId)
            ?.toDomain()
            ?: database.voiceAssignmentDao().getForCharacter(passage.speakerId)?.toDomain()
            ?: throw PreparationPipelineException(
                "voice-assignment-missing",
                "A character is missing its local voice assignment",
                false,
            )
        val voice = voices.firstOrNull { it.id == assignment.voiceId }
            ?: throw PreparationPipelineException(
                "assigned-voice-unavailable",
                "An assigned local voice is unavailable",
                false,
            )
        val baseRequest = SynthesisRequest(
            text = passage.text,
            voice = voice,
            speed = assignment.speed,
            cacheKey = "pending",
        )
        val cacheKey = AudioCacheKey.forPassage(
            passageId = passage.id,
            request = baseRequest,
            modelVersion = assignment.modelVersion,
            sampleRate = dependencies.expectedSampleRate,
        )
        return PassageSynthesisTask(
            passage = passage,
            request = baseRequest.copy(cacheKey = cacheKey),
        )
    }

    private suspend fun synthesizePassage(
        engine: com.whisperbook.app.domain.LocalTtsEngine,
        task: PassageSynthesisTask,
    ) {
        val passage = task.passage
        val request = task.request
        val segment = LocalAudioGenerationCoordinator.runBackground {
            durablePassageAudio(task) ?: run {
                database.audioSegmentDao().upsert(
                    AudioSegmentEntity(
                        id = request.cacheKey,
                        passageId = passage.id,
                        cacheKey = request.cacheKey,
                        state = AudioSegmentState.GENERATING.name,
                        path = null,
                        durationMs = 0L,
                        sampleRate = dependencies.expectedSampleRate,
                    ),
                )
                try {
                    val result = engine.synthesize(request).getOrThrow()
                    if (result.sampleRate != dependencies.expectedSampleRate) {
                        throw PreparationPipelineException(
                            "sample-rate-mismatch",
                            "The local model returned an unexpected sample rate",
                            false,
                        )
                    }
                    dependencies.audioSegmentStore.writeForPassage(
                        passage.id,
                        passage.speakerId,
                        request,
                        result,
                    )
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (failure: Throwable) {
                    database.audioSegmentDao().updateState(
                        request.cacheKey,
                        AudioSegmentState.FAILED.name,
                        null,
                    )
                    throw failure
                }
            }
        }
        database.audioSegmentDao().upsert(segment.toEntity())
    }

    private suspend fun isPassageAudioDurable(task: PassageSynthesisTask): Boolean {
        val segment = durablePassageAudio(task) ?: return false
        database.audioSegmentDao().upsert(segment.toEntity())
        return true
    }

    private suspend fun durablePassageAudio(task: PassageSynthesisTask) =
        database.audioSegmentDao().findByCacheKey(task.request.cacheKey)
            ?.takeIf { it.state == AudioSegmentState.READY.name }
            ?.toDomain()
            ?.takeIf { it.path?.let(::File)?.isFile == true }
            ?: dependencies.audioSegmentStore.find(task.request.cacheKey)
                ?.takeIf { it.path?.let(::File)?.isFile == true }

    private fun chapterPreparationMessage(
        chapter: ChapterAudioBatch<*>,
        totalChapters: Int,
    ): String = "Preparing chapter ${chapter.chapterOrdinal + 1} of $totalChapters: ${chapter.chapterTitle}"

    private data class PassageSynthesisTask(
        val passage: PassageEntity,
        val request: SynthesisRequest,
    )

    private suspend fun requireBook(bookId: String) =
        database.bookDao().observeById(bookId).first()?.book
            ?: throw PreparationPipelineException("book-not-found", "The imported book no longer exists", false)

    private suspend fun checkpoint(bookId: String, state: PreparationState, attemptCount: Int) {
        database.preparationJobDao().upsert(stateEntity(bookId, state, attemptCount))
        onStateCheckpoint(state)
    }

    private fun stateEntity(
        bookId: String,
        state: PreparationState,
        attemptCount: Int,
    ): PreparationJobEntity = state.toEntity(
        bookId = bookId,
        attemptCount = attemptCount.coerceAtLeast(0),
        updatedAtEpochMs = nowEpochMs(),
    )

    private companion object {
        const val UNATTRIBUTED_RULE = "preparation-unattributed"

        fun chapterId(bookId: String, index: Int): String = "$bookId-chapter-${index + 1}"
        fun passageId(chapterId: String, index: Int): String = "$chapterId-passage-${index + 1}"
    }
}

internal data class MappedPreparationError(
    val code: String,
    val message: String,
    val retryable: Boolean,
)

internal object PreparationErrorMapper {
    fun map(failure: Throwable): MappedPreparationError {
        val specific = generateSequence(failure) { it.cause }
            .filterIsInstance<PreparationPipelineException>()
            .firstOrNull()
        if (specific != null) return MappedPreparationError(
            code = specific.code,
            message = specific.message.safeMessage(),
            retryable = specific.retryable,
        )
        return when (failure) {
            is PdfImportException -> MappedPreparationError(
                "pdf-processing-error",
                failure.message.safeMessage("This PDF could not be processed offline"),
                false,
            )
            is UnsupportedPublicationException -> MappedPreparationError(
                "unsupported-publication",
                failure.message.safeMessage(),
                false,
            )
            is IOException, is SQLiteException -> MappedPreparationError(
                "temporary-storage-error",
                "Preparation was interrupted by a temporary storage problem",
                true,
            )
            is IllegalArgumentException -> MappedPreparationError(
                "invalid-publication",
                failure.message.safeMessage(),
                false,
            )
            else -> MappedPreparationError(
                "local-processing-error",
                failure.message.safeMessage("Local preparation could not finish"),
                true,
            )
        }
    }
}

internal class PreparationPipelineException(
    val code: String,
    override val message: String,
    val retryable: Boolean,
) : IllegalStateException(message)

private fun PreparationJobEntity?.stage(): PreparationStage? =
    this?.stage?.let { name -> PreparationStage.entries.firstOrNull { it.name == name } }

private inline fun <reified T : Enum<T>> enumValueOrNull(name: String): T? =
    enumValues<T>().firstOrNull { it.name == name }

private fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().buffered().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read > 0) digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

private fun String?.safeMessage(fallback: String = "Preparation could not finish"): String =
    this?.trim()?.takeIf(String::isNotBlank)?.take(300) ?: fallback

private fun PreparationStage.notificationMessage(): String = when (this) {
    PreparationStage.COPY_AND_VALIDATE -> "Checking the private book copy"
    PreparationStage.READING_CHAPTERS -> "Reading chapters on this device"
    PreparationStage.FINDING_CHARACTERS -> "Finding the voices in this story"
    PreparationStage.ASSIGNING_VOICES -> "Casting local voices"
    PreparationStage.PREPARING_AUDIO -> "Preparing the opening passages"
    PreparationStage.READY -> "Ready to listen"
    PreparationStage.FAILED -> "Preparation needs attention"
}

internal fun preparationNotificationText(state: PreparationState): String = when {
    state.stage == PreparationStage.PREPARING_AUDIO && state.totalUnits > 0 -> {
        val completed = state.completedUnits.coerceIn(0, state.totalUnits)
        "Recorded $completed of ${state.totalUnits} chapters"
    }
    else -> state.message ?: state.stage.notificationMessage()
}
