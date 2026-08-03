package com.whisperbook.app.integration

import android.content.Context
import androidx.room.withTransaction
import com.whisperbook.app.data.local.db.WhisperBookDatabase
import com.whisperbook.app.data.local.db.toChapterEntity
import com.whisperbook.app.data.local.db.toDomain
import com.whisperbook.app.data.local.db.toEntity
import com.whisperbook.app.data.local.preferences.whisperBookSettingsDataStore
import com.whisperbook.app.data.repository.DataStoreSettingsRepository
import com.whisperbook.app.data.repository.RoomLibraryRepository
import com.whisperbook.app.domain.model.CharacterVoiceAssignment
import com.whisperbook.app.domain.model.RevertibleVoiceChange
import com.whisperbook.app.domain.model.VoiceRegenerationRequest
import com.whisperbook.app.engine.attribution.HeuristicSpeakerAttributor
import com.whisperbook.app.engine.audio.AppPrivateAudioSegmentStore
import com.whisperbook.app.engine.audio.AppPrivateVoicePreviewCache
import com.whisperbook.app.engine.audio.LocalVoicePreviewPlayer
import com.whisperbook.app.engine.audio.VoicePreviewBootstrap
import com.whisperbook.app.engine.document.OfflinePublicationExtractor
import com.whisperbook.app.engine.document.SafBookImporter
import com.whisperbook.app.engine.preparation.LocalTtsEngineFactory
import com.whisperbook.app.engine.preparation.PreparationDependencies
import com.whisperbook.app.engine.preparation.PreparationRuntime
import com.whisperbook.app.engine.preparation.ProductionPreparationCoordinator
import com.whisperbook.app.engine.tts.SherpaKittenTtsEngine
import com.whisperbook.app.playback.ControllerBackedPlaybackGateway
import com.whisperbook.app.playback.PlaybackRuntime
import java.io.Closeable
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The single process-scoped dependency graph. Nothing here requires or requests network access. */
class WhisperbookAppContainer(context: Context) : WhisperbookServices, Closeable {
    private val appContext = context.applicationContext
    private val maintenanceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val database: WhisperBookDatabase = WhisperBookDatabase.create(appContext)
    private val importer = SafBookImporter(appContext)

    override val libraryRepository = RoomLibraryRepository(database, importer)
    override val settingsRepository = DataStoreSettingsRepository(appContext.whisperBookSettingsDataStore)
    override val audioSegmentStore = AppPrivateAudioSegmentStore(appContext)
    override val availableVoices = SherpaKittenTtsEngine.KITTEN_VOICES
    override val ttsModelVersion: String = SherpaKittenTtsEngine.MODEL_VERSION
    private val voicePreviewCache = AppPrivateVoicePreviewCache(
        context = appContext,
        modelVersion = ttsModelVersion,
        expectedSampleRate = SherpaKittenTtsEngine.EXPECTED_SAMPLE_RATE,
    )
    override val voicePreviewPlayer = LocalVoicePreviewPlayer(
        ttsEngine = SherpaKittenTtsEngine(appContext),
        previewCache = voicePreviewCache,
    )

    val preparationDependencies = PreparationDependencies(
        database = database,
        publicationExtractor = OfflinePublicationExtractor(appContext),
        speakerAttributor = HeuristicSpeakerAttributor(),
        ttsEngineFactory = LocalTtsEngineFactory { SherpaKittenTtsEngine(appContext) },
        audioSegmentStore = audioSegmentStore,
        settingsFlow = settingsRepository.settings,
    )

    override val preparationCoordinator = ProductionPreparationCoordinator(
        context = appContext,
        dependencies = preparationDependencies,
    )

    private val playbackQueueSource = LocalPlaybackQueueSource(
        database = database,
        audioStore = audioSegmentStore,
        ttsEngineFactory = { SherpaKittenTtsEngine(appContext) },
    )
    override val playbackGateway = ControllerBackedPlaybackGateway(appContext, playbackQueueSource)

    init {
        PreparationRuntime.install(preparationDependencies)
        VoicePreviewBootstrap.enqueue(appContext)
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
            database.playbackCheckpointDao().upsert(cursor.toEntity(System.currentTimeMillis()))
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
                openedAtEpochMs = System.currentTimeMillis(),
            )
        }
    }

    override fun observeVoiceAssignments(
        characterIds: List<String>,
    ): Flow<Map<String, CharacterVoiceAssignment>> {
        val ids = characterIds.distinct()
        if (ids.isEmpty()) return flowOf(emptyMap())
        return database.voiceAssignmentDao().observeForCharacters(ids)
            .map { rows -> rows.associate { row -> row.characterId to row.toDomain() } }
            .distinctUntilChanged()
    }

    override suspend fun applyVoiceRegeneration(
        request: VoiceRegenerationRequest,
    ): RevertibleVoiceChange? = withContext(Dispatchers.IO) {
        require(request.assignment.characterId == request.characterId)
        require(request.fromChapterOrdinal >= 0)
        val chapters = database.chapterDao().getHeadersForBook(request.bookId)
        require(request.fromChapterOrdinal < chapters.size) { "There is no chapter at that regeneration boundary" }
        val affectedChapterIds = chapters
            .asSequence()
            .filter { it.ordinal >= request.fromChapterOrdinal }
            .mapTo(mutableSetOf()) { it.id }
        playbackGateway.invalidateQueuedChapters(request.bookId, affectedChapterIds)
        val previous = database.voiceAssignmentDao().getForCharacter(request.characterId)?.toDomain()
            ?: error("The current voice assignment is unavailable")
        val targetPassageIds = database.audioSegmentDao()
            .getPassageIdsForCharacterFromChapterOrdinal(
                request.characterId,
                request.bookId,
                request.fromChapterOrdinal,
            )
            .toSet()
        val retained = audioSegmentStore.retainForCharacter(
            characterId = request.characterId,
            previousAssignment = previous,
            passageIds = targetPassageIds,
        )

        database.withTransaction {
            val chapterAssignments = database.chapterVoiceAssignmentDao()
            if (request.fromChapterOrdinal == 0) {
                chapterAssignments.deleteForCharacter(request.characterId)
            } else {
                val existingChapterIds = chapterAssignments.getForCharacter(request.characterId)
                    .mapTo(mutableSetOf()) { it.chapterId }
                val preserved = chapters
                    .asSequence()
                    .filter { it.ordinal < request.fromChapterOrdinal && it.id !in existingChapterIds }
                    .map { previous.toChapterEntity(it.id) }
                    .toList()
                if (preserved.isNotEmpty()) chapterAssignments.upsertAll(preserved)
                chapterAssignments.deleteForCharacterFromChapterOrdinal(
                    request.characterId,
                    request.bookId,
                    request.fromChapterOrdinal,
                )
            }
            database.voiceAssignmentDao().upsert(request.assignment.toEntity())
            database.audioSegmentDao().deleteForCharacterFromChapterOrdinal(
                request.characterId,
                request.bookId,
                request.fromChapterOrdinal,
            )
        }
        preparationCoordinator.regenerateAudio(request.bookId, request.fromChapterOrdinal)
        retained?.let { generation ->
            RevertibleVoiceChange(
                generationId = generation.id,
                bookId = request.bookId,
                characterId = request.characterId,
                previousAssignment = previous,
                replacementVoiceId = request.assignment.voiceId,
                expiresAtEpochMs = generation.expiresAtEpochMs,
            )
        }
    }

    override suspend fun retainedVoiceChanges(
        bookId: String,
        characterIds: List<String>,
    ): List<RevertibleVoiceChange> = withContext(Dispatchers.IO) {
        val ids = characterIds.toSet()
        audioSegmentStore.retainedAudioGenerations()
            .filter { it.characterId in ids }
            .mapNotNull { generation ->
                val previous = generation.previousAssignment ?: return@mapNotNull null
                val replacement = database.voiceAssignmentDao().getForCharacter(generation.characterId)
                    ?: return@mapNotNull null
                RevertibleVoiceChange(
                    generationId = generation.id,
                    bookId = bookId,
                    characterId = generation.characterId,
                    previousAssignment = previous,
                    replacementVoiceId = replacement.voiceId,
                    expiresAtEpochMs = generation.expiresAtEpochMs,
                )
            }
    }

    override suspend fun revertVoiceChange(change: RevertibleVoiceChange): Boolean = withContext(Dispatchers.IO) {
        val restored = audioSegmentStore.restoreRetainedGeneration(change.generationId)
            ?: return@withContext false
        val previous = restored.previousAssignment ?: change.previousAssignment
        val chapterIds = database.chapterDao().getHeadersForBook(change.bookId)
            .mapTo(mutableSetOf()) { it.id }
        playbackGateway.invalidateQueuedChapters(change.bookId, chapterIds)
        preparationCoordinator.cancel(change.bookId)
        database.withTransaction {
            database.chapterVoiceAssignmentDao().deleteForCharacter(change.characterId)
            database.voiceAssignmentDao().upsert(previous.toEntity())
        }
        preparationCoordinator.regenerateAudio(change.bookId, 0)
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
        database.close()
    }
}

private fun File.recursiveByteCount(): Long = when {
    isFile -> length().coerceAtLeast(0L)
    isDirectory -> listFiles().orEmpty().sumOf(File::recursiveByteCount)
    else -> 0L
}
