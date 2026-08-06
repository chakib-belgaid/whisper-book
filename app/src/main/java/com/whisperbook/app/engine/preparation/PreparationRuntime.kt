package com.whisperbook.app.engine.preparation

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.whisperbook.app.data.local.preferences.whisperBookSettingsDataStore
import com.whisperbook.app.data.repository.DataStoreSettingsRepository
import com.whisperbook.app.data.local.db.WhisperBookDatabase
import com.whisperbook.app.domain.LocalTtsEngine
import com.whisperbook.app.domain.PublicationExtractor
import com.whisperbook.app.domain.SpeakerAttributor
import com.whisperbook.app.domain.model.AppSettings
import com.whisperbook.app.engine.attribution.HeuristicSpeakerAttributor
import com.whisperbook.app.engine.audio.AppPrivateAudioSegmentStore
import com.whisperbook.app.engine.document.OfflinePublicationExtractor
import com.whisperbook.app.engine.metadata.AppPrivateCharacterMetadataCatalog
import com.whisperbook.app.engine.metadata.CharacterMetadataCatalog
import com.whisperbook.app.engine.tts.SherpaKittenTtsEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

fun interface LocalTtsEngineFactory {
    fun create(): LocalTtsEngine
}

/** Process-scoped dependencies used by WorkManager after the importing process has gone away. */
data class PreparationDependencies(
    val database: WhisperBookDatabase,
    val publicationExtractor: PublicationExtractor,
    val speakerAttributor: SpeakerAttributor,
    val ttsEngineFactory: LocalTtsEngineFactory,
    val audioSegmentStore: AppPrivateAudioSegmentStore,
    val characterMetadataCatalog: CharacterMetadataCatalog? = null,
    val settingsFlow: Flow<AppSettings> = flowOf(AppSettings()),
    val modelVersion: String = SherpaKittenTtsEngine.MODEL_VERSION,
    val expectedSampleRate: Int = SherpaKittenTtsEngine.EXPECTED_SAMPLE_RATE,
    val narratorVoiceId: String = "bella",
    val speakingSpeed: Float = 1f,
    val audioPrefetchPassageCount: Int = DEFAULT_AUDIO_PREFETCH_PASSAGE_COUNT,
) {
    init {
        require(modelVersion.isNotBlank())
        require(expectedSampleRate > 0)
        require(narratorVoiceId.isNotBlank())
        require(speakingSpeed.isFinite() && speakingSpeed > 0f)
        require(audioPrefetchPassageCount >= 1)
    }
}

/**
 * Small service locator used only at WorkManager's process-restart boundary.
 *
 * Apps that already provide a custom WorkManager configuration should install
 * [PreparationWorkerFactory]. Otherwise the standard two-argument worker constructor resolves the
 * same production dependencies here. Tests can inject a complete dependency graph without Hilt.
 */
object PreparationRuntime {
    @Volatile
    private var installed: PreparationDependencies? = null

    fun install(dependencies: PreparationDependencies) {
        synchronized(this) { installed = dependencies }
    }

    fun resolve(context: Context): PreparationDependencies {
        installed?.let { return it }
        return synchronized(this) {
            installed ?: createProductionDependencies(context.applicationContext).also { installed = it }
        }
    }

    internal fun clearForTests() {
        synchronized(this) {
            installed?.database?.close()
            installed = null
        }
    }

    private fun createProductionDependencies(context: Context): PreparationDependencies =
        PreparationDependencies(
            database = WhisperBookDatabase.create(context),
            publicationExtractor = OfflinePublicationExtractor(context),
            speakerAttributor = HeuristicSpeakerAttributor(),
            ttsEngineFactory = LocalTtsEngineFactory { SherpaKittenTtsEngine(context) },
            audioSegmentStore = AppPrivateAudioSegmentStore(context),
            characterMetadataCatalog = AppPrivateCharacterMetadataCatalog(context),
            settingsFlow = DataStoreSettingsRepository(context.whisperBookSettingsDataStore).settings,
        )
}

/** WorkerFactory option for applications that want constructor injection without Hilt. */
class PreparationWorkerFactory(
    private val dependencies: PreparationDependencies,
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = if (workerClassName == PreparationWorker::class.java.name) {
        PreparationWorker(appContext, workerParameters, dependencies)
    } else {
        null
    }
}

const val DEFAULT_AUDIO_PREFETCH_PASSAGE_COUNT = 8
