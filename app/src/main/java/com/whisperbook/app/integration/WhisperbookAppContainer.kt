package com.whisperbook.app.integration

import android.content.Context
import com.whisperbook.app.data.local.db.WhisperBookDatabase
import com.whisperbook.app.data.local.db.toDomain
import com.whisperbook.app.data.local.db.toEntity
import com.whisperbook.app.data.local.preferences.whisperBookSettingsDataStore
import com.whisperbook.app.data.repository.DataStoreSettingsRepository
import com.whisperbook.app.data.repository.RoomLibraryRepository
import com.whisperbook.app.domain.model.CharacterVoiceAssignment
import com.whisperbook.app.engine.attribution.HeuristicSpeakerAttributor
import com.whisperbook.app.engine.audio.AppPrivateAudioSegmentStore
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

/** The single process-scoped dependency graph. Nothing here requires or requests network access. */
class WhisperbookAppContainer(context: Context) : WhisperbookServices, Closeable {
    private val appContext = context.applicationContext
    val database: WhisperBookDatabase = WhisperBookDatabase.create(appContext)
    private val importer = SafBookImporter(appContext)

    override val libraryRepository = RoomLibraryRepository(database, importer)
    override val settingsRepository = DataStoreSettingsRepository(appContext.whisperBookSettingsDataStore)
    override val audioSegmentStore = AppPrivateAudioSegmentStore(appContext)
    override val availableVoices = SherpaKittenTtsEngine.KITTEN_VOICES
    override val ttsModelVersion: String = SherpaKittenTtsEngine.MODEL_VERSION

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
        PlaybackRuntime.installCheckpointSink { cursor ->
            database.playbackCheckpointDao().upsert(cursor.toEntity(System.currentTimeMillis()))
            val chapters = database.chapterDao().observeForBook(cursor.bookId).first()
            val chapterOrdinal = chapters.indexOfFirst { it.chapter.id == cursor.chapterId }.coerceAtLeast(0)
            val withinChapter = if (cursor.chapterDurationMs > 0L) {
                cursor.chapterPositionMs.toFloat().div(cursor.chapterDurationMs).coerceIn(0f, 1f)
            } else {
                0f
            }
            val bookProgress = if (chapters.isEmpty()) 0f else {
                (chapterOrdinal + withinChapter).div(chapters.size).coerceIn(0f, 1f)
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
        return combine(ids.map { id -> database.voiceAssignmentDao().observeForCharacter(id) }) { rows ->
            rows.filterNotNull().associate { row -> row.characterId to row.toDomain() }
        }
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
        PlaybackRuntime.installCheckpointSink(null)
        (playbackGateway as ControllerBackedPlaybackGateway).close()
        database.close()
    }
}

private fun File.recursiveByteCount(): Long = when {
    isFile -> length().coerceAtLeast(0L)
    isDirectory -> listFiles().orEmpty().sumOf(File::recursiveByteCount)
    else -> 0L
}
