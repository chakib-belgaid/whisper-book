package com.whisperbook.app.domain.model

import android.net.Uri
import androidx.compose.runtime.Immutable

@Immutable
data class Book(
    val id: String,
    val title: String,
    val author: String?,
    val format: BookFormat,
    val sourceUri: Uri?,
    val privateSourcePath: String?,
    val coverPath: String?,
    val preparation: PreparationState,
    val currentChapterId: String?,
    val currentPassageId: String?,
    val progressFraction: Float,
    val lastOpenedAtEpochMs: Long,
    val chapterCount: Int = 0,
    val currentChapterOrdinal: Int? = null,
)

enum class BookFormat { PDF, EPUB }

@Immutable
data class Chapter(
    val id: String,
    val bookId: String,
    val ordinal: Int,
    val title: String,
    val passages: List<Passage> = emptyList(),
)

@Immutable
data class Passage(
    val id: String,
    val chapterId: String,
    val ordinal: Int,
    val text: String,
    val speakerId: String,
    val confidence: Float,
    val attributionRule: String,
)

@Immutable
data class StoryCharacter(
    val id: String,
    val bookId: String,
    val displayName: String,
    val aliases: Set<String>,
    val colorRole: CharacterColorRole,
    val dialogueLineCount: Int,
    val gender: CharacterGender = CharacterGender.UNKNOWN,
    val genderConfidence: Float = 0f,
    val ageGroup: CharacterAgeGroup = CharacterAgeGroup.UNKNOWN,
    val ageConfidence: Float = 0f,
    val narrationPerspective: NarrationPerspective = NarrationPerspective.UNKNOWN,
    val perspectiveConfidence: Float = 0f,
    val narratorIdentity: String? = null,
)

enum class CharacterColorRole { NARRATOR, ELARA_BURGUNDY, FOX_ORANGE, BLUE, BURGUNDY, ORANGE }

/** Textual identity evidence used only to improve automatic casting. */
enum class CharacterGender { FEMALE, MALE, NON_BINARY, UNKNOWN }

/** Broad story-age bands; exact ages are deliberately not persisted. */
enum class CharacterAgeGroup { CHILD, TEEN, YOUNG_ADULT, ADULT, OLDER_ADULT, UNKNOWN }

enum class NarrationPerspective { FIRST_PERSON, THIRD_PERSON, UNKNOWN }

/** Vocal timbre category exposed by an embedded voice preset. */
enum class VocalAge { YOUTHFUL, ADULT, MATURE, UNKNOWN }

@Immutable
data class VoiceDescriptor(
    val id: String,
    val displayName: String,
    val speakerIndex: Int,
    val localeTag: String = "en-US",
    val embedded: Boolean = true,
    val gender: CharacterGender = CharacterGender.UNKNOWN,
    val vocalAge: VocalAge = VocalAge.UNKNOWN,
)

@Immutable
data class CharacterVoiceAssignment(
    val characterId: String,
    val voiceId: String,
    val modelVersion: String,
    val speed: Float = 1f,
)

enum class PreparationStage {
    COPY_AND_VALIDATE,
    READING_CHAPTERS,
    FINDING_CHARACTERS,
    ASSIGNING_VOICES,
    PREPARING_AUDIO,
    READY,
    FAILED,
}

@Immutable
data class PreparationState(
    val stage: PreparationStage,
    val completedUnits: Int = 0,
    val totalUnits: Int = 0,
    val progressFraction: Float = 0f,
    val message: String? = null,
    val retryable: Boolean = false,
) {
    companion object {
        val Ready = PreparationState(PreparationStage.READY, progressFraction = 1f)
    }
}

enum class AudioSegmentState { PENDING, GENERATING, READY, FAILED }

@Immutable
data class AudioSegment(
    val id: String,
    val passageId: String,
    val cacheKey: String,
    val state: AudioSegmentState,
    val path: String?,
    val durationMs: Long,
    val sampleRate: Int = 24_000,
)

@Immutable
data class PlaybackCursor(
    val bookId: String,
    val chapterId: String,
    val passageId: String,
    val segmentId: String,
    val segmentPositionMs: Long,
    val chapterPositionMs: Long,
    val chapterDurationMs: Long,
    val isPlaying: Boolean,
    val speed: Float,
    val segmentDurationMs: Long = 0L,
    /** False while [chapterDurationMs] describes only the currently playable queue prefix. */
    val chapterDurationIsFinal: Boolean = true,
)

@Immutable
data class PlaybackPreparationProgress(
    val bookId: String,
    val chapterId: String,
    val completedSegments: Int,
    val totalSegments: Int,
) {
    init {
        require(bookId.isNotBlank())
        require(chapterId.isNotBlank())
        require(totalSegments > 0)
        require(completedSegments in 0..totalSegments)
    }

    val progressFraction: Float
        get() = completedSegments.toFloat() / totalSegments
}

@Immutable
data class AppSettings(
    val onboardingComplete: Boolean = false,
    val defaultNarratorVoiceId: String = "bella",
    val narrationLanguageCode: String = NarrationLanguage.ENGLISH.code,
    val installedLanguagePackCodes: Set<String> = setOf(NarrationLanguage.ENGLISH.code),
    val speakingSpeed: Float = 1f,
    val sleepTimerMinutes: Int = 30,
    val keepScreenAwake: Boolean = false,
    val largerText: Boolean = false,
    val autoScroll: Boolean = true,
    val audioCacheLimitBytes: Long = 2L * 1024 * 1024 * 1024,
)

/** Languages which Whisperbook exposes from the shared embedded Supertonic 3 model. */
enum class NarrationLanguage(
    val code: String,
    val displayName: String,
    val nativeName: String,
) {
    ENGLISH("en", "English", "English"),
    FRENCH("fr", "French", "Français"),
    ARABIC("ar", "Arabic", "العربية"),
    ;

    companion object {
        val supportedCodes: Set<String> = entries.mapTo(linkedSetOf()) { it.code }

        fun fromCode(code: String): NarrationLanguage? = entries.firstOrNull { it.code == code }
    }
}

object BuiltInCharacters {
    const val NARRATOR_ID = "narrator"
}
