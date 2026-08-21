package com.whisperbook.app.integration

import android.content.Context
import androidx.room.withTransaction
import com.whisperbook.app.data.local.db.WhisperBookDatabase
import com.whisperbook.app.data.local.db.deleteForPassageIdsBatched
import com.whisperbook.app.data.local.db.toChapterEntity
import com.whisperbook.app.data.local.db.toDomain
import com.whisperbook.app.data.local.db.toEntity
import com.whisperbook.app.data.local.db.updateSpeakerAttributionBatched
import com.whisperbook.app.data.local.preferences.whisperBookSettingsDataStore
import com.whisperbook.app.data.repository.DataStoreSettingsRepository
import com.whisperbook.app.data.repository.RoomLibraryRepository
import com.whisperbook.app.domain.model.CharacterVoiceAssignment
import com.whisperbook.app.domain.model.ChapterVoiceAssignmentSnapshot
import com.whisperbook.app.domain.model.NarrationLanguage
import com.whisperbook.app.domain.model.RevertibleVoiceChange
import com.whisperbook.app.domain.model.SpeakerCorrectionScope
import com.whisperbook.app.domain.model.VoiceRegenerationRequest
import com.whisperbook.app.domain.model.VoiceRegenerationScope
import com.whisperbook.app.domain.model.speakerPhraseMatchKey
import com.whisperbook.app.engine.attribution.HeuristicSpeakerAttributor
import com.whisperbook.app.engine.audio.AppPrivateAudioSegmentStore
import com.whisperbook.app.engine.audio.AppPrivateVoicePreviewCache
import com.whisperbook.app.engine.audio.LocalVoicePreviewPlayer
import com.whisperbook.app.engine.audio.LocalAudioGenerationCoordinator
import com.whisperbook.app.engine.audio.VoicePreviewBootstrap
import com.whisperbook.app.engine.document.OfflinePublicationExtractor
import com.whisperbook.app.engine.document.SafBookImporter
import com.whisperbook.app.engine.export.FfmpegBookMp3Exporter
import com.whisperbook.app.engine.metadata.AppPrivateCharacterMetadataCatalog
import com.whisperbook.app.engine.preparation.LocalTtsEngineFactory
import com.whisperbook.app.engine.preparation.PreparationDependencies
import com.whisperbook.app.engine.preparation.PreparationRuntime
import com.whisperbook.app.engine.preparation.ProductionPreparationCoordinator
import com.whisperbook.app.engine.tts.ProcessScopedLocalTtsEngine
import com.whisperbook.app.engine.tts.SherpaKittenTtsEngine
import com.whisperbook.app.playback.ControllerBackedPlaybackGateway
import com.whisperbook.app.playback.PlaybackRuntime
import java.io.Closeable
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The single process-scoped dependency graph. Nothing here requires or requests network access. */
class WhisperbookAppContainer(context: Context) : WhisperbookServices, Closeable {
    private val appContext = context.applicationContext
    private val maintenanceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val narrationProfilesReady = CompletableDeferred<Unit>()
    val database: WhisperBookDatabase = WhisperBookDatabase.create(appContext)
    private val importer = SafBookImporter(appContext)
    private val characterMetadataCatalog = AppPrivateCharacterMetadataCatalog(appContext)

    override val libraryRepository = RoomLibraryRepository(
        database,
        importer,
        characterMetadataCatalog = characterMetadataCatalog,
    )
    override val settingsRepository = DataStoreSettingsRepository(appContext.whisperBookSettingsDataStore)
    override val audioSegmentStore = AppPrivateAudioSegmentStore(appContext)
    override val availableVoices = SherpaKittenTtsEngine.KITTEN_VOICES
    override val ttsModelVersion: String = SherpaKittenTtsEngine.MODEL_VERSION
    private val sharedTtsEngine = ProcessScopedLocalTtsEngine(SherpaKittenTtsEngine(appContext))
    private val voicePreviewCache = AppPrivateVoicePreviewCache(
        context = appContext,
        modelVersion = ttsModelVersion,
        expectedSampleRate = SherpaKittenTtsEngine.EXPECTED_SAMPLE_RATE,
    )
    override val voicePreviewPlayer = LocalVoicePreviewPlayer(
        ttsEngine = sharedTtsEngine,
        previewCache = voicePreviewCache,
    )

    val preparationDependencies = PreparationDependencies(
        database = database,
        publicationExtractor = OfflinePublicationExtractor(appContext),
        speakerAttributor = HeuristicSpeakerAttributor(),
        ttsEngineFactory = LocalTtsEngineFactory { sharedTtsEngine },
        audioSegmentStore = audioSegmentStore,
        characterMetadataCatalog = characterMetadataCatalog,
        settingsFlow = settingsRepository.settings,
        awaitNarrationProfiles = { narrationProfilesReady.await() },
    )

    override val preparationCoordinator = ProductionPreparationCoordinator(
        context = appContext,
        dependencies = preparationDependencies,
    )

    private val playbackQueueSource = LocalPlaybackQueueSource(
        database = database,
        audioStore = audioSegmentStore,
        ttsEngineFactory = { sharedTtsEngine },
        settingsFlow = settingsRepository.settings,
        awaitNarrationProfiles = { narrationProfilesReady.await() },
    )
    override val bookMp3Exporter = FfmpegBookMp3Exporter(
        context = appContext,
        database = database,
        queueSource = playbackQueueSource,
    )
    override val playbackGateway = ControllerBackedPlaybackGateway(appContext, playbackQueueSource)

    init {
        PreparationRuntime.install(preparationDependencies)
        VoicePreviewBootstrap.enqueue(appContext)
        maintenanceScope.launch {
            try {
                database.bookDao().seedLegacyNarrationProfiles(
                    settingsRepository.legacyNarrationLanguageCode.first(),
                )
                narrationProfilesReady.complete(Unit)
            } catch (failure: Throwable) {
                narrationProfilesReady.completeExceptionally(failure)
            }
        }
        maintenanceScope.launch {
            // Returning listeners should not pay native model initialization after pressing Play.
            // Fresh installs are warmed by the preview bootstrap while the first book is imported.
            if (database.bookDao().count() > 0) {
                runCatching {
                    LocalAudioGenerationCoordinator.runBackground {
                        sharedTtsEngine.warmUp().getOrThrow()
                    }
                }
            }
        }
        maintenanceScope.launch {
            audioSegmentStore.cleanupExpiredRetainedAudio()
            database.bookDao().getBooksWithSourceSha256()
                .groupBy { it.sourceSha256 }
                .values
                .flatMap { matchingBooks -> matchingBooks.drop(1) }
                .forEach { duplicate ->
                    preparationCoordinator.cancel(duplicate.id)
                    libraryRepository.deleteBook(duplicate.id)
                }
        }
        PlaybackRuntime.installCheckpointSink { cursor ->
            val checkpointedAt = System.currentTimeMillis()
            database.playbackCheckpointDao().upsert(cursor.toEntity(checkpointedAt))
            // Chapter generation can be cancelled when the listener switches books. Persist the
            // book-scoped location immediately; only the percentage still waits for a final
            // chapter duration below.
            database.bookDao().updatePlaybackLocation(
                bookId = cursor.bookId,
                chapterId = cursor.chapterId,
                passageId = cursor.passageId,
                openedAtEpochMs = checkpointedAt,
            )
            // The segment checkpoint is valid immediately, but the queued-prefix duration is not
            // a chapter denominator. Wait for the complete timeline before updating shelf progress.
            if (!cursor.chapterDurationIsFinal) return@installCheckpointSink
            val chapterPosition = database.chapterDao().getProgressPosition(cursor.bookId, cursor.chapterId)
            val chapterOrdinal = chapterPosition.chapterOrdinal?.coerceAtLeast(0) ?: 0
            val withinChapter = if (cursor.chapterDurationMs > 0L) {
                cursor.chapterPositionMs.toFloat().div(cursor.chapterDurationMs).coerceIn(0f, 1f)
            } else {
                0f
            }
            val bookProgress = if (chapterPosition.chapterCount <= 0) 0f else {
                (chapterOrdinal + withinChapter).div(chapterPosition.chapterCount).coerceIn(0f, 1f)
            }
            database.bookDao().updateProgress(
                bookId = cursor.bookId,
                chapterId = cursor.chapterId,
                passageId = cursor.passageId,
                progressFraction = bookProgress,
                openedAtEpochMs = checkpointedAt,
            )
        }
    }

    override fun observeChapterVoiceAssignments(
        bookId: String,
        chapterId: String,
    ): Flow<Map<String, CharacterVoiceAssignment>> {
        if (bookId.isBlank() || chapterId.isBlank()) return flowOf(emptyMap())
        return database.chapterVoiceAssignmentDao().observeForChapter(bookId, chapterId)
            .map { rows -> rows.associate { row -> row.characterId to row.toDomain() } }
            .distinctUntilChanged()
    }

    override suspend fun applyVoiceRegeneration(
        request: VoiceRegenerationRequest,
    ): RevertibleVoiceChange? = withContext(Dispatchers.IO) {
        narrationProfilesReady.await()
        require(request.assignment.characterId == request.characterId)
        require(request.fromChapterOrdinal >= 0)
        val chapters = database.chapterDao().getHeadersForBook(request.bookId)
        require(request.fromChapterOrdinal < chapters.size) { "There is no chapter at that regeneration boundary" }
        val selectedChapter = chapters.firstOrNull { it.id == request.selectedChapterId }
            ?: error("The selected chapter does not belong to this book")
        require(selectedChapter.ordinal == request.fromChapterOrdinal) {
            "The selected chapter ordinal changed before the voice update"
        }
        val character = database.storyCharacterDao().getEntitiesForBook(request.bookId)
            .firstOrNull { it.id == request.characterId }
            ?: error("The selected character does not belong to this book")
        val affectedChapters = when (request.scope) {
            VoiceRegenerationScope.THIS_CHAPTER -> listOf(selectedChapter)
            VoiceRegenerationScope.FROM_THIS_CHAPTER -> chapters.filter { it.ordinal >= selectedChapter.ordinal }
            VoiceRegenerationScope.WHOLE_BOOK -> chapters
        }
        val affectedChapterIds = affectedChapters.mapTo(linkedSetOf()) { it.id }
        val previous = database.voiceAssignmentDao().getForCharacter(request.characterId)?.toDomain()
            ?: error("The current voice assignment is unavailable")
        val previousChapterAssignments = database.chapterVoiceAssignmentDao()
            .getForCharacter(request.bookId, request.characterId)
            .filter { it.chapterId in affectedChapterIds }
            .map { row -> ChapterVoiceAssignmentSnapshot(row.chapterId, row.toDomain()) }
        val targetPassageIds = when (request.scope) {
            VoiceRegenerationScope.THIS_CHAPTER ->
                database.audioSegmentDao().getPassageIdsForCharacterInChapter(
                    request.characterId,
                    request.bookId,
                    selectedChapter.id,
                )
            VoiceRegenerationScope.FROM_THIS_CHAPTER,
            VoiceRegenerationScope.WHOLE_BOOK,
            -> database.audioSegmentDao().getPassageIdsForCharacterFromChapterOrdinal(
                request.characterId,
                request.bookId,
                affectedChapters.minOf { it.ordinal },
            )
        }.toSet()
        val retained = targetPassageIds.takeIf(Collection<String>::isNotEmpty)?.let { passageIds ->
            audioSegmentStore.retainForCharacter(
                characterId = request.characterId,
                previousAssignment = previous,
                passageIds = passageIds,
                previousChapterAssignments = previousChapterAssignments,
                scope = request.scope,
                fromChapterOrdinal = request.fromChapterOrdinal,
                bookId = request.bookId,
            )
        }

        database.withTransaction {
            if (request.scope != VoiceRegenerationScope.THIS_CHAPTER) {
                database.voiceAssignmentDao().upsert(request.assignment.toEntity())
            }
            // Preparation can finish another chapter while retained files are being staged. Read
            // the current rows again inside this transaction so every affected row committed by
            // the old profile is replaced before the revision changes.
            val replacements = database.chapterVoiceAssignmentDao()
                .getForCharacter(request.bookId, request.characterId)
                .filter { it.chapterId in affectedChapterIds }
                .map { row -> request.assignment.toChapterEntity(request.bookId, row.chapterId) }
            if (replacements.isNotEmpty()) database.chapterVoiceAssignmentDao().upsertAll(replacements)
            if (targetPassageIds.isNotEmpty()) {
                database.audioSegmentDao().deleteForPassageIdsBatched(targetPassageIds)
            }
            check(database.bookDao().incrementNarrationProfileRevision(request.bookId) == 1) {
                "The book disappeared during the voice update"
            }
        }
        preparationCoordinator.regenerateAudio(request.bookId, affectedChapters.minOf { it.ordinal })
        // Do not clear a playable queue until all retention and database work has succeeded. The
        // previous generation remains valid during this short handoff and is protected above.
        val playbackReload = playbackGateway.invalidateNarrationProfile(
            request.bookId,
            affectedChapterIds,
        )
        playbackReload?.let { playbackGateway.reloadNarrationProfile(it) }
        retained?.let { generation ->
            RevertibleVoiceChange(
                generationId = generation.id,
                bookId = request.bookId,
                characterId = request.characterId,
                previousAssignment = previous,
                previousChapterAssignments = previousChapterAssignments,
                scope = request.scope,
                fromChapterOrdinal = request.fromChapterOrdinal,
                replacementVoiceId = request.assignment.voiceId,
                expiresAtEpochMs = generation.expiresAtEpochMs,
            )
        }
    }

    override suspend fun applyNarrationLanguageToBook(bookId: String, languageCode: String) =
        withContext(Dispatchers.IO) {
            narrationProfilesReady.await()
            require(languageCode in NarrationLanguage.supportedCodes) { "Unsupported narration language" }
            val book = database.bookDao().getById(bookId) ?: error("This book is no longer in the library")
            if (book.narrationLanguageCode == languageCode) return@withContext
            val chapterIds = database.chapterDao().getHeadersForBook(bookId).mapTo(linkedSetOf()) { it.id }
            val playbackReload = playbackGateway.invalidateNarrationProfile(bookId, chapterIds)
            database.withTransaction {
                check(database.bookDao().updateNarrationLanguage(bookId, languageCode) == 1) {
                    "This book is no longer in the library"
                }
                database.audioSegmentDao().deleteForBook(bookId)
            }
            preparationCoordinator.regenerateAudio(bookId, 0)
            playbackReload?.let { playbackGateway.reloadNarrationProfile(it) }
            Unit
        }

    override suspend fun applySpeakerCorrection(
        bookId: String,
        passageId: String,
        speakerId: String,
        scope: SpeakerCorrectionScope,
    ): Int = withContext(Dispatchers.IO) {
        narrationProfilesReady.await()
        require(bookId.isNotBlank() && passageId.isNotBlank() && speakerId.isNotBlank())
        database.bookDao().getById(bookId) ?: error("This book is no longer in the library")
        val targetCharacter = database.storyCharacterDao().getEntitiesForBook(bookId)
            .firstOrNull { it.id == speakerId }
            ?: error("The selected voice does not belong to this book")
        val passages = database.passageDao().getForBook(bookId)
        val source = passages.firstOrNull { it.id == passageId }
            ?: error("The selected phrase does not belong to this book")
        if (source.speakerId == targetCharacter.id) return@withContext 0

        val sourceMatchKey = speakerPhraseMatchKey(source.text)
        val affectedPassages = when (scope) {
            SpeakerCorrectionScope.THIS_PASSAGE -> listOf(source)
            SpeakerCorrectionScope.MATCHING_PHRASES -> passages.filter { candidate ->
                candidate.speakerId == source.speakerId &&
                    sourceMatchKey.isNotBlank() &&
                    speakerPhraseMatchKey(candidate.text) == sourceMatchKey
            }
        }
        if (affectedPassages.isEmpty()) return@withContext 0

        val chapterHeaders = database.chapterDao().getHeadersForBook(bookId)
        val affectedChapterIds = affectedPassages.mapTo(linkedSetOf()) { it.chapterId }
        val affectedChapters = chapterHeaders.filter { it.id in affectedChapterIds }
        check(affectedChapters.size == affectedChapterIds.size) {
            "A corrected phrase belongs to a missing chapter"
        }
        val targetVoice = database.voiceAssignmentDao().getForCharacter(targetCharacter.id)?.toDomain()
            ?: error("The selected character's voice is not ready yet")
        val affectedPassageIds = affectedPassages.mapTo(linkedSetOf()) { it.id }

        // Keep the current queue's files alive during the profile reload. No previous assignment
        // is attached, so this safety retention is not exposed as a reversible cast change.
        audioSegmentStore.retainForCharacter(
            characterId = source.speakerId,
            passageIds = affectedPassageIds,
            bookId = bookId,
        )

        database.withTransaction {
            database.chapterVoiceAssignmentDao().upsertAll(
                affectedChapterIds.map { chapterId -> targetVoice.toChapterEntity(bookId, chapterId) },
            )
            val updated = database.passageDao().updateSpeakerAttributionBatched(
                passageIds = affectedPassageIds,
                speakerId = targetCharacter.id,
                attributionRule = when (scope) {
                    SpeakerCorrectionScope.THIS_PASSAGE -> "manual-speaker:this-passage"
                    SpeakerCorrectionScope.MATCHING_PHRASES -> "manual-speaker:matching-phrases"
                },
            )
            check(updated == affectedPassageIds.size) { "A phrase disappeared during the correction" }
            database.audioSegmentDao().deleteForPassageIdsBatched(affectedPassageIds)
            check(database.bookDao().incrementNarrationProfileRevision(bookId) == 1) {
                "This book is no longer in the library"
            }
        }

        preparationCoordinator.regenerateAudio(bookId, affectedChapters.minOf { it.ordinal })
        val playbackReload = playbackGateway.invalidateNarrationProfile(bookId, affectedChapterIds)
        playbackReload?.let { playbackGateway.reloadNarrationProfile(it) }
        affectedPassageIds.size
    }

    override suspend fun retainedVoiceChanges(
        bookId: String,
        characterIds: List<String>,
    ): List<RevertibleVoiceChange> = withContext(Dispatchers.IO) {
        val ids = characterIds.toSet()
        audioSegmentStore.retainedAudioGenerations()
            .filter { generation ->
                generation.characterId in ids &&
                    (generation.bookId == null || generation.bookId == bookId)
            }
            .mapNotNull { generation ->
                val previous = generation.previousAssignment ?: return@mapNotNull null
                val boundaryChapter = database.chapterDao().getHeadersForBook(bookId)
                    .firstOrNull { it.ordinal == generation.fromChapterOrdinal }
                val replacementVoiceId = boundaryChapter?.let { chapter ->
                    database.chapterVoiceAssignmentDao().getForChapterAndCharacter(
                        bookId,
                        chapter.id,
                        generation.characterId,
                    )?.voiceId
                } ?: database.voiceAssignmentDao().getForCharacter(generation.characterId)?.voiceId
                    ?: return@mapNotNull null
                RevertibleVoiceChange(
                    generationId = generation.id,
                    bookId = bookId,
                    characterId = generation.characterId,
                    previousAssignment = previous,
                    previousChapterAssignments = generation.previousChapterAssignments,
                    scope = generation.voiceRegenerationScope,
                    fromChapterOrdinal = generation.fromChapterOrdinal,
                    replacementVoiceId = replacementVoiceId,
                    expiresAtEpochMs = generation.expiresAtEpochMs,
                )
            }
    }

    override suspend fun revertVoiceChange(change: RevertibleVoiceChange): Boolean = withContext(Dispatchers.IO) {
        narrationProfilesReady.await()
        val restored = audioSegmentStore.restoreRetainedGeneration(change.generationId)
            ?: return@withContext false
        val previous = restored.previousAssignment ?: change.previousAssignment
        val chapters = database.chapterDao().getHeadersForBook(change.bookId)
        val affectedChapters = when (change.scope) {
            VoiceRegenerationScope.THIS_CHAPTER ->
                chapters.filter { it.ordinal == change.fromChapterOrdinal }
            VoiceRegenerationScope.FROM_THIS_CHAPTER ->
                chapters.filter { it.ordinal >= change.fromChapterOrdinal }
            VoiceRegenerationScope.WHOLE_BOOK -> chapters
        }
        val affectedChapterIds = affectedChapters.mapTo(linkedSetOf()) { it.id }
        val playbackReload = playbackGateway.invalidateNarrationProfile(
            change.bookId,
            affectedChapterIds,
        )
        preparationCoordinator.cancel(change.bookId)
        database.withTransaction {
            if (change.scope != VoiceRegenerationScope.THIS_CHAPTER) {
                database.voiceAssignmentDao().upsert(previous.toEntity())
            }
            val restoredRows = restored.previousChapterAssignments.map { snapshot ->
                snapshot.assignment.toChapterEntity(change.bookId, snapshot.chapterId)
            }
            if (restoredRows.isNotEmpty()) database.chapterVoiceAssignmentDao().upsertAll(restoredRows)
            val restoredChapterIds = restored.previousChapterAssignments.mapTo(hashSetOf()) { it.chapterId }
            val laterMaterializedRows = database.chapterVoiceAssignmentDao()
                .getForCharacter(change.bookId, change.characterId)
                .filter { row ->
                    row.chapterId in affectedChapterIds && row.chapterId !in restoredChapterIds
                }
                .map { row -> previous.toChapterEntity(change.bookId, row.chapterId) }
            if (laterMaterializedRows.isNotEmpty()) {
                database.chapterVoiceAssignmentDao().upsertAll(laterMaterializedRows)
            }
            check(database.bookDao().incrementNarrationProfileRevision(change.bookId) == 1)
        }
        preparationCoordinator.regenerateAudio(
            change.bookId,
            affectedChapters.minOfOrNull { it.ordinal } ?: 0,
        )
        playbackReload?.let { playbackGateway.reloadNarrationProfile(it) }
        true
    }

    override suspend fun deletePersistedAudioForCharacter(characterId: String) {
        database.audioSegmentDao().deleteForCharacter(characterId)
    }

    override suspend fun localStorageBytes(): Long = withContext(Dispatchers.IO) {
        listOf(
            File(appContext.filesDir, "publications"),
            File(appContext.filesDir, "audio"),
            File(appContext.noBackupFilesDir, "whisperbook/tts"),
        ).sumOf(File::recursiveByteCount)
    }

    override fun close() {
        maintenanceScope.cancel()
        PlaybackRuntime.installCheckpointSink(null)
        voicePreviewPlayer.close()
        (playbackGateway as ControllerBackedPlaybackGateway).close()
        sharedTtsEngine.shutdown()
        database.close()
    }
}

private fun File.recursiveByteCount(): Long = when {
    isFile -> length().coerceAtLeast(0L)
    isDirectory -> listFiles().orEmpty().sumOf(File::recursiveByteCount)
    else -> 0L
}
