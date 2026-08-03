package com.whisperbook.app.domain.model

/** Defines the first chapter that should use a newly selected character voice. */
enum class VoiceRegenerationScope {
    WHOLE_BOOK,
    FROM_NEXT_CHAPTER,
}

data class VoiceRegenerationRequest(
    val bookId: String,
    val characterId: String,
    val assignment: CharacterVoiceAssignment,
    val scope: VoiceRegenerationScope,
    /** Zero-based chapter ordinal at which [assignment] becomes effective. */
    val fromChapterOrdinal: Int,
)

data class RevertibleVoiceChange(
    val generationId: String,
    val bookId: String,
    val characterId: String,
    val previousAssignment: CharacterVoiceAssignment,
    val replacementVoiceId: String,
    val expiresAtEpochMs: Long,
)
