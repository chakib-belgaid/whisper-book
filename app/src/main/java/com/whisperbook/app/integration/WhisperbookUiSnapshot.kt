package com.whisperbook.app.integration

import androidx.compose.runtime.Immutable
import com.whisperbook.app.domain.model.AppSettings
import com.whisperbook.app.domain.model.Book
import com.whisperbook.app.domain.model.Chapter
import com.whisperbook.app.domain.model.CharacterVoiceAssignment
import com.whisperbook.app.domain.model.PlaybackCursor
import com.whisperbook.app.domain.model.PreparationState
import com.whisperbook.app.domain.model.StoryCharacter
import com.whisperbook.app.domain.model.VoiceDescriptor

/** Complete, immutable screen input derived from the device-local production graph. */
@Immutable
data class WhisperbookUiSnapshot(
    val books: List<Book> = emptyList(),
    val selectedBook: Book? = null,
    val chapters: List<Chapter> = emptyList(),
    val selectedChapter: Chapter? = null,
    val characters: List<StoryCharacter> = emptyList(),
    val voiceAssignments: Map<String, CharacterVoiceAssignment> = emptyMap(),
    val voices: List<VoiceDescriptor> = emptyList(),
    val preparation: PreparationState? = null,
    val settings: AppSettings = AppSettings(),
    val playback: PlaybackCursor? = null,
    val loadingChapterId: String? = null,
    val isBusy: Boolean = false,
    val statusMessage: String? = null,
    val backgroundProgressFraction: Float? = null,
    val errorMessage: String? = null,
    val localStorageBytes: Long = 0L,
    val canRevertVoiceChange: Boolean = false,
) {
    val hasBooks: Boolean get() = books.isNotEmpty()
    val isPlaying: Boolean get() = playback?.isPlaying == true
    val chapterProgress: Float
        get() = playback?.let { cursor ->
            if (cursor.chapterDurationMs <= 0L) 0f
            else cursor.chapterPositionMs.toFloat().div(cursor.chapterDurationMs).coerceIn(0f, 1f)
        } ?: selectedBook?.progressFraction.orZero()

    private fun Float?.orZero(): Float = this?.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
}
