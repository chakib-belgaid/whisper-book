package com.whisperbook.app.integration

import com.whisperbook.app.domain.AudioSegmentStore
import com.whisperbook.app.domain.LibraryRepository
import com.whisperbook.app.domain.PlaybackGateway
import com.whisperbook.app.domain.PreparationCoordinator
import com.whisperbook.app.domain.SettingsRepository
import com.whisperbook.app.domain.VoicePreviewPlayer
import com.whisperbook.app.domain.model.CharacterVoiceAssignment
import com.whisperbook.app.domain.model.RevertibleVoiceChange
import com.whisperbook.app.domain.model.VoiceDescriptor
import com.whisperbook.app.domain.model.VoiceRegenerationRequest
import kotlinx.coroutines.flow.Flow

/** Testable boundary between the lifecycle layer and application-scoped Android services. */
interface WhisperbookServices {
    val libraryRepository: LibraryRepository
    val settingsRepository: SettingsRepository
    val preparationCoordinator: PreparationCoordinator
    val playbackGateway: PlaybackGateway
    val audioSegmentStore: AudioSegmentStore
    val voicePreviewPlayer: VoicePreviewPlayer
    val availableVoices: List<VoiceDescriptor>
    val ttsModelVersion: String

    fun observeVoiceAssignments(characterIds: List<String>): Flow<Map<String, CharacterVoiceAssignment>>
    suspend fun applyVoiceRegeneration(request: VoiceRegenerationRequest): RevertibleVoiceChange?
    suspend fun retainedVoiceChanges(bookId: String, characterIds: List<String>): List<RevertibleVoiceChange>
    suspend fun revertVoiceChange(change: RevertibleVoiceChange): Boolean
    suspend fun deletePersistedAudioForCharacter(characterId: String)
    suspend fun localStorageBytes(): Long
}
