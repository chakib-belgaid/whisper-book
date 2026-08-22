package com.whisperbook.app.engine.preparation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.sqlite.SQLiteException
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.whisperbook.app.MainActivity
import com.whisperbook.app.R
import com.whisperbook.app.data.local.db.AudioSegmentEntity
import com.whisperbook.app.data.local.db.ChapterAggregate
import com.whisperbook.app.data.local.db.ChapterEntity
import com.whisperbook.app.data.local.db.PassageEntity
import com.whisperbook.app.data.local.db.PreparationJobEntity
import com.whisperbook.app.data.local.db.toAliasEntities
import com.whisperbook.app.data.local.db.toChapterEntity
import com.whisperbook.app.data.local.db.toDomain
import com.whisperbook.app.data.local.db.toEntity
import com.whisperbook.app.domain.ExtractedChapter
import com.whisperbook.app.domain.ImportedBook
import com.whisperbook.app.domain.PassageTextChunker
import com.whisperbook.app.domain.SynthesisRequest
import com.whisperbook.app.domain.model.AudioSegmentState
import com.whisperbook.app.domain.model.AppSettings
import com.whisperbook.app.domain.model.BookFormat
import com.whisperbook.app.domain.model.BuiltInCharacters
import com.whisperbook.app.domain.model.CharacterColorRole
import com.whisperbook.app.domain.model.CharacterVoiceAssignment
import com.whisperbook.app.domain.model.Passage
import com.whisperbook.app.domain.model.PreparationStage
import com.whisperbook.app.domain.model.PreparationState
import com.whisperbook.app.domain.model.StoryCharacter
import com.whisperbook.app.domain.model.VoiceDescriptor
import com.whisperbook.app.diagnostics.BetaDiagnostics
import com.whisperbook.app.engine.audio.LocalAudioGenerationCoordinator
import com.whisperbook.app.engine.audio.NarrationSynthesisPlanner
import com.whisperbook.app.engine.document.PdfImportException
import com.whisperbook.app.engine.document.SignatureBookFormatDetector
import com.whisperbook.app.engine.document.UnsupportedPublicationException
import com.whisperbook.app.engine.metadata.ChapterCharacterMetadata
import com.whisperbook.app.engine.metadata.CharacterDialogueContribution
import com.whisperbook.app.engine.metadata.CharacterMetadataChapterUpdate
import com.whisperbook.app.engine.metadata.CharacterMetadataFingerprint
import com.whisperbook.app.engine.metadata.CharacterMetadataRecord
import com.whisperbook.app.engine.tts.CharacterVoiceCaster
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
        val startedAtMs = SystemClock.elapsedRealtime()
        BetaDiagnostics.info(
            "preparation_stage_started",
            mapOf("stage" to stage.name, "attempt" to runAttemptCount),
        )

        return try {
            dependencies.awaitNarrationProfiles()
            setForeground(
                createForegroundInfo(
                    bookId,
                    PreparationState(stage, message = stage.notificationMessage()),
                ),
            )
            runner.run(bookId, stage, runAttemptCount, fromChapterOrdinal)
            BetaDiagnostics.performance(
                "preparation_stage_completed",
                mapOf(
                    "stage" to stage.name,
                    "attempt" to runAttemptCount,
                    "elapsed_ms" to (SystemClock.elapsedRealtime() - startedAtMs),
                ),
            )
            Result.success()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            val mapped = PreparationErrorMapper.map(failure)
            BetaDiagnostics.error(
                "preparation_stage_failed",
                failure,
                mapOf(
                    "stage" to stage.name,
                    "attempt" to runAttemptCount,
                    "elapsed_ms" to (SystemClock.elapsedRealtime() - startedAtMs),
                    "error_code" to mapped.code,
                    "retryable" to mapped.retryable,
                ),
            )
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
            .setContentTitle("Preparing your audiobook")
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
        val chapters = database.chapterDao().getHeadersForBook(bookId)
        if (chapters.isEmpty()) {
            throw PreparationPipelineException("chapters-missing", "Chapters must be read before attribution", false)
        }

        checkpoint(
            bookId,
            PreparationState(
                PreparationStage.FINDING_CHARACTERS,
                completedUnits = 0,
                totalUnits = chapters.size,
                message = "Finding voices in the opening chapter",
            ),
            attemptCount,
        )
        ensureChapterAttributed(
            bookId = bookId,
            chapterId = chapters.first().id,
            isFinalChapter = chapters.size == 1,
        )
        val characterCount = database.storyCharacterDao().getEntitiesForBook(bookId).size
        checkpoint(
            bookId,
            PreparationState(
                stage = PreparationStage.ASSIGNING_VOICES,
                completedUnits = 1,
                totalUnits = chapters.size,
                progressFraction = 1f / chapters.size,
                message = "Found $characterCount voices in the opening chapter",
            ),
            attemptCount,
        )
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
            checkpoint(
                bookId,
                PreparationState(
                    PreparationStage.ASSIGNING_VOICES,
                    totalUnits = characters.size,
                    message = "Casting local voices",
                ),
                attemptCount,
            )
            assignMissingVoices(bookId, voices, settings)
            database.chapterDao().getHeadersForBook(bookId).firstOrNull()?.let { firstChapter ->
                database.chapterDao().getById(firstChapter.id)?.let { chapter ->
                    materializeChapterVoiceSet(
                        bookId = bookId,
                        chapter = chapter,
                        expectedProfileRevision = requireBook(bookId).narrationProfileRevision,
                    )
                }
            }
            checkpoint(
                bookId,
                PreparationState(
                    stage = PreparationStage.PREPARING_AUDIO,
                    completedUnits = 0,
                    totalUnits = database.chapterDao().getHeadersForBook(bookId).size,
                    message = "The opening cast is ready",
                ),
                attemptCount,
            )
        } finally {
            engine.close()
        }
    }

    private suspend fun prepareAudio(
        bookId: String,
        attemptCount: Int,
        fromChapterOrdinal: Int,
    ) {
        val narrationProfile = requireBook(bookId)
        val expectedProfileRevision = narrationProfile.narrationProfileRevision
        if (!narrationProfile.narrationProfileSeeded || expectedProfileRevision < 0) {
            throw PreparationPipelineException(
                "narration-profile-migration-pending",
                "The book narration profile is still being migrated",
                true,
            )
        }
        val languageCode = narrationProfile.narrationLanguageCode
            .takeIf { it in com.whisperbook.app.domain.model.NarrationLanguage.supportedCodes }
            ?: com.whisperbook.app.domain.model.NarrationLanguage.ENGLISH.code
        val chapters = database.chapterDao().getHeadersForBook(bookId)
        if (chapters.isEmpty()) {
            throw PreparationPipelineException("chapters-missing", "Chapters must exist before audio is prepared", false)
        }
        val priorStage = database.preparationJobDao().getForBook(bookId).stage()
        val requestedStartOrdinal = if (priorStage == PreparationStage.READY) {
            fromChapterOrdinal
        } else {
            0
        }
        // Voice regeneration replaces the unique WorkManager chain. If it happens while a large
        // book is still being prepared, resume at the first chapter whose catalog is incomplete so
        // the remainder of the book is not stranded behind the replacement request.
        val attributionStartOrdinal = if (requestedStartOrdinal == 0) {
            null
        } else {
            val characterIds = database.storyCharacterDao().getEntitiesForBook(bookId)
                .mapTo(hashSetOf()) { it.id }
            chapters.firstOrNull { chapter ->
                val passages = database.passageDao().getForChapter(chapter.id)
                passages.isEmpty() ||
                    passages.any { it.attributionRule == UNATTRIBUTED_RULE || it.speakerId !in characterIds }
            }?.ordinal
        }
        val metadataStartOrdinal = firstMissingMetadataChapterOrdinal(bookId, chapters)
        val effectiveStartOrdinal = listOfNotNull(
            requestedStartOrdinal,
            attributionStartOrdinal,
            metadataStartOrdinal,
        ).minOrNull() ?: 0
        val targets = chapters.filter { it.ordinal >= effectiveStartOrdinal }
        if (targets.isEmpty()) {
            checkpoint(bookId, PreparationState.Ready, attemptCount)
            return
        }

        val completedBeforeStart = chapters.size - targets.size
        checkpoint(
            bookId,
            PreparationState(
                PreparationStage.PREPARING_AUDIO,
                completedUnits = completedBeforeStart,
                totalUnits = chapters.size,
                progressFraction = completedBeforeStart.toFloat() / chapters.size,
                message = chapterPreparationMessage(
                    targets.first().ordinal,
                    targets.first().title,
                    chapters.size,
                ),
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
            val settings = dependencies.settingsFlow.first()
            val preparer = SequentialChapterAudioPreparer(
                isPassageReady = ::isPassageAudioDurable,
                preparePassage = { task ->
                    synthesizePassage(bookId, expectedProfileRevision, engine, task)
                },
            )
            var openingChunkPrepared = false
            targets.forEachIndexed { targetIndex, chapterHeader ->
                requireNarrationProfileRevision(bookId, expectedProfileRevision)
                val completedChapters = completedBeforeStart + targetIndex
                checkpoint(
                    bookId,
                    PreparationState(
                        stage = PreparationStage.PREPARING_AUDIO,
                        completedUnits = completedChapters,
                        totalUnits = chapters.size,
                        progressFraction = completedChapters.toFloat() / chapters.size,
                        message = chapterPreparationMessage(
                            chapterHeader.ordinal,
                            chapterHeader.title,
                            chapters.size,
                        ),
                    ),
                    attemptCount,
                )

                val chapter = ensureChapterAttributed(
                    bookId = bookId,
                    chapterId = chapterHeader.id,
                    isFinalChapter = chapterHeader.ordinal == chapters.last().ordinal,
                )
                assignMissingVoices(bookId, voices, settings)
                materializeChapterVoiceSet(bookId, chapter, expectedProfileRevision)
                val chapterBatch = ChapterAudioBatch(
                    chapterId = chapter.chapter.id,
                    chapterOrdinal = chapter.chapter.ordinal,
                    chapterTitle = chapter.chapter.title,
                    passages = chapter.passages.sortedBy { it.ordinal },
                )

                // Background preparation records only the first narration chunk. The remaining
                // chunks are generated progressively when the listener starts that chapter. We
                // still attribute every chapter here so later chapters remain playable on demand.
                if (!openingChunkPrepared) {
                    val openingPassage = chapterBatch.passages.first()
                    val openingTask = synthesisTasks(
                        bookId = bookId,
                        passage = openingPassage,
                        voices = voices,
                        languageCode = languageCode,
                        maxChars = settings.narrationChunkChars,
                    ).first()
                    val openingBatch = ChapterAudioBatch(
                        chapterId = chapterBatch.chapterId,
                        chapterOrdinal = chapterBatch.chapterOrdinal,
                        chapterTitle = chapterBatch.chapterTitle,
                        passages = listOf(openingTask),
                    )
                    preparer.prepare(listOf(openingBatch))
                    openingChunkPrepared = true
                }

                val readyChapters = completedChapters + 1
                checkpoint(
                    bookId,
                    PreparationState(
                        stage = PreparationStage.PREPARING_AUDIO,
                        completedUnits = readyChapters,
                        totalUnits = chapters.size,
                        progressFraction = readyChapters.toFloat() / chapters.size,
                        message = "${chapterBatch.chapterTitle} is ready to listen",
                    ),
                    attemptCount,
                )
            }
        } finally {
            engine.close()
        }
        reconcileCharacterMetadata(bookId, chapters)
        checkpoint(bookId, PreparationState.Ready, attemptCount)
    }

    private suspend fun firstMissingMetadataChapterOrdinal(
        bookId: String,
        chapters: List<ChapterEntity>,
    ): Int? {
        val catalog = dependencies.characterMetadataCatalog ?: return null
        val sourceSha256 = requireBook(bookId).sourceSha256?.trim()?.takeIf(String::isNotBlank)
        val snapshot = catalog.read(bookId)?.takeIf { current ->
            current.sourceSha256 == sourceSha256 &&
                current.analysisVersion == CHARACTER_ANALYSIS_VERSION
        } ?: return chapters.firstOrNull()?.ordinal
        val recordedChapterIds = snapshot.chapters.mapTo(hashSetOf()) { it.chapterId }
        return chapters.firstOrNull { it.id !in recordedChapterIds }?.ordinal
    }

    private suspend fun ensureChapterAttributed(
        bookId: String,
        chapterId: String,
        isFinalChapter: Boolean,
    ): ChapterAggregate {
        val source = database.chapterDao().getById(chapterId)
            ?: throw PreparationPipelineException(
                "chapter-missing",
                "A chapter disappeared while the book was being prepared",
                false,
            )
        if (source.passages.isEmpty()) {
            throw PreparationPipelineException("passages-missing", "A chapter has no readable passages", false)
        }
        val existingCharacters = database.storyCharacterDao().observeForBook(bookId).first()
        val existingCharacterIds = existingCharacters.mapTo(hashSetOf()) { it.character.id }
        val alreadyAttributed = source.passages.all { it.attributionRule != UNATTRIBUTED_RULE }
        if (alreadyAttributed) {
            if (source.passages.any { it.speakerId !in existingCharacterIds }) {
                throw PreparationPipelineException(
                    "character-catalog-incomplete",
                    "A prepared chapter references a missing character",
                    false,
                )
            }
            recordChapterMetadata(bookId, source, isFinalChapter)
            return source
        }

        val attributed = dependencies.speakerAttributor.attributeChapter(
            bookId = bookId,
            chapterId = source.chapter.id,
            chapterOrdinal = source.chapter.ordinal,
            chapter = ExtractedChapter(
                title = source.chapter.title,
                paragraphs = source.passages.sortedBy { it.ordinal }.map { it.text },
            ),
            knownCharacters = existingCharacters.map { it.toDomain() },
        )
        val attributedChapter = attributed.chapters.singleOrNull()
            ?: throw PreparationPipelineException(
                "attribution-empty",
                "No narratable story passages were found in this chapter",
                false,
            )
        if (
            attributedChapter.id != source.chapter.id ||
            attributedChapter.ordinal != source.chapter.ordinal
        ) {
            throw PreparationPipelineException(
                "attribution-chapter-mismatch",
                "Chapter attribution returned the wrong chapter identity",
                false,
            )
        }

        // The legacy first call uses the built-in narrator ID. Scope it before persistence so
        // assignments and audio invalidation remain isolated to this book. Incremental calls are
        // seeded with this scoped character and retain the ID directly.
        val narratorId = "$bookId-character-narrator"
        val persistedCharacters = attributed.characters.map { character ->
            if (character.id == BuiltInCharacters.NARRATOR_ID) character.copy(id = narratorId) else character
        }.distinctBy(StoryCharacter::id)
        val persistedPassages = attributedChapter.passages.map { passage ->
            passage.copy(
                chapterId = source.chapter.id,
                speakerId = if (passage.speakerId == BuiltInCharacters.NARRATOR_ID) {
                    narratorId
                } else {
                    passage.speakerId
                },
            )
        }
        val persistedCharacterIds = persistedCharacters.mapTo(hashSetOf(), StoryCharacter::id)
        if (persistedCharacters.isEmpty() || persistedPassages.isEmpty()) {
            throw PreparationPipelineException(
                "attribution-empty",
                "No narratable story passages were found in this chapter",
                false,
            )
        }
        if (persistedPassages.any { it.speakerId !in persistedCharacterIds }) {
            throw PreparationPipelineException(
                "attribution-character-missing",
                "Chapter attribution returned a speaker without character metadata",
                false,
            )
        }

        database.withTransaction {
            // This chapter has not produced audio yet. Replacing only its provisional passages
            // leaves completed chapters, manual voice choices, and their cached audio untouched.
            database.passageDao().deleteForChapter(source.chapter.id)
            database.storyCharacterDao().insertAll(persistedCharacters.map { it.toEntity() })
            database.storyCharacterDao().insertAliases(
                persistedCharacters.flatMap { it.toAliasEntities() },
            )
            database.passageDao().insertAll(persistedPassages.map { it.toEntity() })
        }
        val stored = database.chapterDao().getById(source.chapter.id)
            ?: throw PreparationPipelineException(
                "chapter-missing",
                "A chapter disappeared after character attribution",
                false,
            )
        recordChapterMetadata(bookId, stored, isFinalChapter)
        return stored
    }

    private suspend fun recordChapterMetadata(
        bookId: String,
        chapter: ChapterAggregate,
        complete: Boolean,
    ) {
        val catalog = dependencies.characterMetadataCatalog ?: return
        val book = requireBook(bookId)
        val sourceSha256 = book.sourceSha256?.trim()?.takeIf(String::isNotBlank)
        val current = catalog.read(bookId)?.takeIf { snapshot ->
            snapshot.sourceSha256 == sourceSha256 &&
                snapshot.analysisVersion == CHARACTER_ANALYSIS_VERSION
        }
        val expectedChapterIds = database.chapterDao().getHeadersForBook(bookId)
            .mapTo(linkedSetOf()) { it.id }
        val recordedChapterIds = current?.chapters.orEmpty().mapTo(linkedSetOf()) { it.chapterId }
        val existingChapter = current?.chapters?.firstOrNull { it.chapterId == chapter.chapter.id }
        val alreadyComplete = current?.complete == true && recordedChapterIds.containsAll(expectedChapterIds)
        if (existingChapter != null && (!complete || alreadyComplete)) return

        val characters = database.storyCharacterDao().observeForBook(bookId).first().map { it.toDomain() }
        val charactersById = characters.associateBy(StoryCharacter::id)
        val chapterDialogueCounts = chapter.passages
            .asSequence()
            .filter { passage ->
                charactersById[passage.speakerId]?.colorRole != CharacterColorRole.NARRATOR
            }
            .groupBy(PassageEntity::speakerId)
            .mapValues { (_, passages) ->
                passages.mapTo(linkedSetOf(), PassageEntity::dialogueUnitKey).size
            }
        val chapterSpeakerIds = chapter.passages.mapTo(linkedSetOf()) { it.speakerId }
        val contributions = chapterSpeakerIds.sorted().map { characterId ->
            if (characterId !in charactersById) {
                throw PreparationPipelineException(
                    "character-catalog-incomplete",
                    "Character metadata is missing while writing the chapter catalog",
                    false,
                )
            }
            CharacterDialogueContribution(
                characterId = characterId,
                dialogueLineCount = chapterDialogueCounts[characterId] ?: 0,
            )
        }
        val chapterMetadata = existingChapter ?: ChapterCharacterMetadata(
            chapterId = chapter.chapter.id,
            ordinal = chapter.chapter.ordinal,
            textSha256 = CharacterMetadataFingerprint.sha256Utf8(
                chapter.passages.sortedBy { it.ordinal }.joinToString("\n") { it.text.trim() },
            ),
            contributions = contributions,
        )
        catalog.recordChapter(
            CharacterMetadataChapterUpdate(
                bookId = bookId,
                sourceSha256 = sourceSha256,
                analysisVersion = CHARACTER_ANALYSIS_VERSION,
                chapter = chapterMetadata,
                characters = characters.map(StoryCharacter::toMetadataRecord),
                complete = complete && (recordedChapterIds + chapter.chapter.id).containsAll(expectedChapterIds),
            ),
        )
    }

    private suspend fun reconcileCharacterMetadata(
        bookId: String,
        chapters: List<ChapterEntity>,
    ) {
        val catalog = dependencies.characterMetadataCatalog ?: return
        val sourceSha256 = requireBook(bookId).sourceSha256?.trim()?.takeIf(String::isNotBlank)
        val expectedIds = chapters.mapTo(linkedSetOf()) { it.id }
        val current = catalog.read(bookId)?.takeIf { snapshot ->
            snapshot.sourceSha256 == sourceSha256 &&
                snapshot.analysisVersion == CHARACTER_ANALYSIS_VERSION
        }
        if (current?.complete == true && current.chapters.mapTo(hashSetOf()) { it.chapterId }.containsAll(expectedIds)) {
            return
        }
        chapters.forEachIndexed { index, header ->
            val chapter = database.chapterDao().getById(header.id)
                ?: throw PreparationPipelineException(
                    "chapter-missing",
                    "A chapter disappeared while rebuilding character metadata",
                    false,
                )
            if (
                chapter.passages.isEmpty() ||
                chapter.passages.any { it.attributionRule == UNATTRIBUTED_RULE }
            ) {
                throw PreparationPipelineException(
                    "character-metadata-incomplete",
                    "Character metadata cannot finish before every chapter is attributed",
                    false,
                )
            }
            recordChapterMetadata(bookId, chapter, complete = index == chapters.lastIndex)
        }
        val repaired = catalog.read(bookId)
        if (
            repaired?.complete != true ||
            !repaired.chapters.mapTo(hashSetOf()) { it.chapterId }.containsAll(expectedIds)
        ) {
            throw IOException("Character metadata did not finish writing")
        }
    }

    private suspend fun assignMissingVoices(
        bookId: String,
        voices: List<VoiceDescriptor>,
        settings: AppSettings,
    ) {
        val characters = database.storyCharacterDao().observeForBook(bookId).first()
        if (characters.isEmpty()) {
            throw PreparationPipelineException("characters-missing", "No story characters are available", false)
        }
        val voiceIds = voices.mapTo(hashSetOf(), VoiceDescriptor::id)
        val assignments = database.voiceAssignmentDao()
            .getForCharacters(characters.map { it.character.id })
            .associateBy { it.characterId }
        val narratorVoice = voices.firstOrNull { it.id == dependencies.narratorVoiceId }
            ?: voices.first()
        val orderedCharacters = characters.sortedWith(
            compareBy(
                { it.character.colorRole != CharacterColorRole.NARRATOR.name },
                { it.character.id },
            ),
        )
        val usedVoiceIds = assignments.values.mapNotNullTo(linkedSetOf()) { assignment ->
            assignment.voiceId.takeIf { voiceId ->
                voiceId in voiceIds && assignment.modelVersion == dependencies.modelVersion
            }
        }
        database.withTransaction {
            orderedCharacters.forEach { character ->
                val existing = assignments[character.character.id]
                if (
                    existing != null &&
                    existing.voiceId in voiceIds &&
                    existing.modelVersion == dependencies.modelVersion
                ) {
                    usedVoiceIds += existing.voiceId
                    return@forEach
                }
                val voice = existing?.voiceId?.let { id -> voices.firstOrNull { it.id == id } }
                    ?: CharacterVoiceCaster.select(
                        character = character.toDomain(),
                        voices = voices,
                        preferredNarrator = narratorVoice,
                        alreadyUsedVoiceIds = usedVoiceIds,
                    )
                usedVoiceIds += voice.id
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
        }
    }

    private suspend fun synthesisTasks(
        bookId: String,
        passage: PassageEntity,
        voices: List<com.whisperbook.app.domain.model.VoiceDescriptor>,
        languageCode: String,
        maxChars: Int,
    ): List<PassageSynthesisTask> {
        val assignment = database.chapterVoiceAssignmentDao()
            .getForChapterAndCharacter(bookId, passage.chapterId, passage.speakerId)
            ?.toDomain()
            ?: throw PreparationPipelineException(
                "voice-assignment-missing",
                "This chapter's complete voice set is still being prepared",
                false,
            )
        val voice = voices.firstOrNull { it.id == assignment.voiceId }
            ?: throw PreparationPipelineException(
                "assigned-voice-unavailable",
                "An assigned local voice is unavailable",
                false,
            )
        return NarrationSynthesisPlanner.plan(
            passageId = passage.id,
            text = passage.text,
            voice = voice,
            speed = assignment.speed,
            modelVersion = assignment.modelVersion,
            sampleRate = dependencies.expectedSampleRate,
            languageCode = languageCode,
            maxChars = maxChars,
        ).map { unit ->
            PassageSynthesisTask(
                passage = passage,
                request = unit.request,
            )
        }
    }

    private suspend fun materializeChapterVoiceSet(
        bookId: String,
        chapter: ChapterAggregate,
        expectedProfileRevision: Long,
    ) = database.withTransaction {
        // The active worker may have passed its loop-level check just before a narrator change.
        // Keep this check and the insert in one Room transaction: either the old row commits first
        // and regeneration replaces it, or the new revision commits first and this work cancels.
        requireNarrationProfileRevision(bookId, expectedProfileRevision)
        val speakerIds = chapter.passages.mapTo(linkedSetOf()) { it.speakerId }
        if (speakerIds.isEmpty()) {
            throw PreparationPipelineException("voice-set-empty", "A chapter has no speakers to cast", false)
        }
        val ownedCharacterIds = database.storyCharacterDao().getEntitiesForBook(bookId)
            .mapTo(hashSetOf()) { it.id }
        if (!ownedCharacterIds.containsAll(speakerIds)) {
            throw PreparationPipelineException(
                "voice-set-cross-book",
                "A chapter references a character outside this book",
                false,
            )
        }
        val templates = database.voiceAssignmentDao().getForCharacters(speakerIds.toList())
            .associateBy { it.characterId }
        val existing = database.chapterVoiceAssignmentDao()
            .getForChapter(bookId, chapter.chapter.id)
            .associateBy { it.characterId }
        val missing = speakerIds.filterNot(existing::containsKey).map { characterId ->
            templates[characterId]?.toDomain()?.toChapterEntity(bookId, chapter.chapter.id)
                ?: throw PreparationPipelineException(
                    "voice-template-missing",
                    "A character is missing its book voice template",
                    false,
                )
        }
        if (missing.isNotEmpty()) {
            database.chapterVoiceAssignmentDao().upsertAll(missing)
        }
        val completed = database.chapterVoiceAssignmentDao()
            .getForChapter(bookId, chapter.chapter.id)
            .mapTo(hashSetOf()) { it.characterId }
        if (!completed.containsAll(speakerIds)) {
            throw PreparationPipelineException(
                "voice-set-incomplete",
                "The chapter voice set could not be completed",
                true,
            )
        }
    }

    private suspend fun synthesizePassage(
        bookId: String,
        expectedProfileRevision: Long,
        engine: com.whisperbook.app.domain.LocalTtsEngine,
        task: PassageSynthesisTask,
    ) {
        requireNarrationProfileRevision(bookId, expectedProfileRevision)
        val passage = task.passage
        val request = task.request
        val segment = LocalAudioGenerationCoordinator.runBackground {
            requireNarrationProfileRevision(bookId, expectedProfileRevision)
            durablePassageAudio(task) ?: run {
                requireNarrationProfileRevision(bookId, expectedProfileRevision)
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
                    requireNarrationProfileRevision(bookId, expectedProfileRevision)
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
        requireNarrationProfileRevision(bookId, expectedProfileRevision)
        database.audioSegmentDao().upsert(segment.toEntity())
    }

    private suspend fun requireNarrationProfileRevision(bookId: String, expectedRevision: Long) {
        val actualRevision = database.bookDao().getById(bookId)?.narrationProfileRevision
        if (actualRevision != expectedRevision) {
            throw CancellationException("The book narration profile changed during audio generation")
        }
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
        chapterOrdinal: Int,
        chapterTitle: String,
        totalChapters: Int,
    ): String = "Preparing chapter ${chapterOrdinal + 1} of $totalChapters: $chapterTitle"

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
        const val CHARACTER_ANALYSIS_VERSION = "heuristic-attribution-chapter-v1"
        const val UNATTRIBUTED_RULE = "preparation-unattributed"

        fun chapterId(bookId: String, index: Int): String = "$bookId-chapter-${index + 1}"
        fun passageId(chapterId: String, index: Int): String = "$chapterId-passage-${index + 1}"
    }
}

private fun StoryCharacter.toMetadataRecord(): CharacterMetadataRecord = CharacterMetadataRecord(
    id = id,
    displayName = displayName,
    aliases = aliases,
    colorRole = colorRole,
    dialogueLineCount = dialogueLineCount,
    gender = gender,
    genderConfidence = genderConfidence,
    ageGroup = ageGroup,
    ageConfidence = ageConfidence,
    narrationPerspective = narrationPerspective,
    perspectiveConfidence = perspectiveConfidence,
    narratorIdentity = narratorIdentity,
)

private fun PassageEntity.dialogueUnitKey(): String =
    DIALOGUE_UNIT_PATTERN.find(attributionRule)?.value ?: id

private val DIALOGUE_UNIT_PATTERN = Regex("(?:^|;)dialogue-unit-\\d+-\\d+(?:$|;)")

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
        "Prepared $completed of ${state.totalUnits} chapters"
    }
    else -> state.message ?: state.stage.notificationMessage()
}
