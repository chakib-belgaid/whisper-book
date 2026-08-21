package com.whisperbook.app.domain.model

/** Defines which complete custom-chapter voice sets receive a selected character voice. */
enum class VoiceRegenerationScope {
    THIS_CHAPTER,
    FROM_THIS_CHAPTER,
    WHOLE_BOOK,
}

data class VoiceRegenerationRequest(
    val bookId: String,
    val characterId: String,
    val assignment: CharacterVoiceAssignment,
    val scope: VoiceRegenerationScope,
    val selectedChapterId: String,
    /** Zero-based chapter ordinal at which [assignment] becomes effective. */
    val fromChapterOrdinal: Int,
)

data class ChapterVoiceAssignmentSnapshot(
    val chapterId: String,
    val assignment: CharacterVoiceAssignment,
)

data class RevertibleVoiceChange(
    val generationId: String,
    val bookId: String,
    val characterId: String,
    val previousAssignment: CharacterVoiceAssignment,
    val previousChapterAssignments: List<ChapterVoiceAssignmentSnapshot> = emptyList(),
    val scope: VoiceRegenerationScope = VoiceRegenerationScope.WHOLE_BOOK,
    val fromChapterOrdinal: Int = 0,
    val replacementVoiceId: String,
    val expiresAtEpochMs: Long,
)
