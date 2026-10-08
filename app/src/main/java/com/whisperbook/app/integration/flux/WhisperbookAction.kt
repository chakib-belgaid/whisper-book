package com.whisperbook.app.integration.flux

import android.net.Uri
import com.whisperbook.app.domain.model.SpeakerCorrectionScope
import com.whisperbook.app.domain.model.VoiceRegenerationScope

/** User and lifecycle intents accepted by the application layer. */
sealed interface WhisperbookAction {
    data class ImportBook(val uri: Uri) : WhisperbookAction
    data class SetChapterSelected(val chapterId: String, val selected: Boolean) : WhisperbookAction
    data class MoveChapter(val chapterId: String, val targetSelectedPosition: Int) : WhisperbookAction
    data object SelectAllChapters : WhisperbookAction
    data object DeselectAllChapters : WhisperbookAction
    data object RestoreOriginalChapterOrder : WhisperbookAction
    data object ResetChapterPlan : WhisperbookAction
    data object ConfirmChapterPlan : WhisperbookAction
    data class ConfirmNarrationSetup(
        val languageCode: String,
        val narratorVoiceId: String,
    ) : WhisperbookAction
    data object ConfirmStoryReview : WhisperbookAction
    data object RetryPreparation : WhisperbookAction
    data object PausePreparation : WhisperbookAction
    data object ResumePreparation : WhisperbookAction
    data object CancelPreparation : WhisperbookAction
    data object DeleteSelectedBook : WhisperbookAction
    data class ExportSelectedBook(val destination: Uri) : WhisperbookAction
    data class SelectBook(val bookId: String) : WhisperbookAction
    data class SelectChapter(val chapterId: String) : WhisperbookAction
    data object PlayPreviousChapter : WhisperbookAction
    data object PlayNextChapter : WhisperbookAction
    data object PlaySelectedChapter : WhisperbookAction
    data object PlayOrPause : WhisperbookAction
    data class SeekBy(val deltaMs: Long) : WhisperbookAction
    data class SeekToFraction(val fraction: Float) : WhisperbookAction
    data class SeekToPassage(val passageId: String) : WhisperbookAction
    data class CorrectPassageSpeaker(
        val passageId: String,
        val speakerId: String,
        val scope: SpeakerCorrectionScope,
    ) : WhisperbookAction
    data class CorrectPassageSpeakers(
        val passageIds: List<String>,
        val speakerId: String,
        val scope: SpeakerCorrectionScope,
    ) : WhisperbookAction
    data object CycleSpeed : WhisperbookAction
    data object CycleNarrationChunkSize : WhisperbookAction
    data class DownloadLanguagePack(val languageCode: String) : WhisperbookAction
    data class SelectNarrationLanguage(val languageCode: String) : WhisperbookAction
    data object CycleSleepTimer : WhisperbookAction
    data class CycleVoice(val characterId: String) : WhisperbookAction
    data class AssignVoice(
        val characterId: String,
        val voiceId: String,
        val regenerationScope: VoiceRegenerationScope,
    ) : WhisperbookAction
    data object RevertVoiceChange : WhisperbookAction
    data class PreviewCharacter(val characterId: String) : WhisperbookAction
    data class PreviewVoice(val voiceId: String, val characterName: String) : WhisperbookAction
    data class PreviewNarrationSetupVoice(
        val voiceId: String,
        val languageCode: String,
    ) : WhisperbookAction
    data class SetAutoScroll(val enabled: Boolean) : WhisperbookAction
    data class SetKeepScreenAwake(val enabled: Boolean) : WhisperbookAction
    data class SetLargerText(val enabled: Boolean) : WhisperbookAction
    data object CompleteOnboarding : WhisperbookAction
    data object ClearMessage : WhisperbookAction
}
