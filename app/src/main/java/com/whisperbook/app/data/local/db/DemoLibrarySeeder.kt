package com.whisperbook.app.data.local.db

import androidx.room.withTransaction
import com.whisperbook.app.domain.model.AudioSegmentState
import com.whisperbook.app.domain.model.BookFormat
import com.whisperbook.app.domain.model.CharacterColorRole
import com.whisperbook.app.domain.model.PreparationStage

/**
 * Explicitly invoked demo content for previews and manual QA.
 *
 * Production startup must never call this helper. It is deterministic so screenshot tests and
 * developer previews can address stable rows without making demo insertion an application side
 * effect.
 */
object DemoLibrarySeeder {
    suspend fun seedIfEmpty(database: WhisperBookDatabase, nowEpochMs: Long = 1_700_000_000_000L): Boolean =
        database.withTransaction {
            if (database.bookDao().count() != 0) return@withTransaction false

            database.bookDao().insert(
                BookEntity(
                    id = BOOK_ID,
                    title = "The Moonlit Wood",
                    author = "Whisperbook",
                    format = BookFormat.EPUB.name,
                    sourceUri = null,
                    privateSourcePath = null,
                    sourceSha256 = null,
                    coverPath = null,
                    currentChapterId = CHAPTER_ID,
                    currentPassageId = PASSAGE_NARRATOR_ID,
                    progressFraction = 0.08f,
                    lastOpenedAtEpochMs = nowEpochMs,
                ),
            )
            database.preparationJobDao().upsert(
                PreparationJobEntity(
                    bookId = BOOK_ID,
                    stage = PreparationStage.READY.name,
                    completedUnits = 3,
                    totalUnits = 3,
                    progressFraction = 1f,
                    message = null,
                    retryable = false,
                    attemptCount = 1,
                    updatedAtEpochMs = nowEpochMs,
                ),
            )
            database.chapterDao().insertAll(
                listOf(ChapterEntity(CHAPTER_ID, BOOK_ID, 0, "The Lantern in the Wood")),
            )
            database.storyCharacterDao().insertAll(
                listOf(
                    StoryCharacterEntity(
                        id = NARRATOR_ID,
                        bookId = BOOK_ID,
                        displayName = "Narrator",
                        colorRole = CharacterColorRole.NARRATOR.name,
                        dialogueLineCount = 1,
                    ),
                    StoryCharacterEntity(
                        id = ELARA_ID,
                        bookId = BOOK_ID,
                        displayName = "Elara",
                        colorRole = CharacterColorRole.ELARA_BURGUNDY.name,
                        dialogueLineCount = 1,
                    ),
                    StoryCharacterEntity(
                        id = FOX_ID,
                        bookId = BOOK_ID,
                        displayName = "Fox",
                        colorRole = CharacterColorRole.FOX_ORANGE.name,
                        dialogueLineCount = 1,
                    ),
                ),
            )
            database.storyCharacterDao().insertAliases(
                listOf(
                    CharacterAliasEntity(ELARA_ID, "the girl"),
                    CharacterAliasEntity(FOX_ID, "the silver fox"),
                ),
            )
            database.passageDao().insertAll(
                listOf(
                    PassageEntity(
                        id = PASSAGE_NARRATOR_ID,
                        chapterId = CHAPTER_ID,
                        ordinal = 0,
                        text = "The moon hung low above the paper trees.",
                        speakerId = NARRATOR_ID,
                        confidence = 1f,
                        attributionRule = "narration-default",
                    ),
                    PassageEntity(
                        id = PASSAGE_ELARA_ID,
                        chapterId = CHAPTER_ID,
                        ordinal = 1,
                        text = "I think the lantern is trying to lead us somewhere.",
                        speakerId = ELARA_ID,
                        confidence = 0.91f,
                        attributionRule = "quoted-speech-nearest-name",
                    ),
                    PassageEntity(
                        id = PASSAGE_FOX_ID,
                        chapterId = CHAPTER_ID,
                        ordinal = 2,
                        text = "Then we should tread softly.",
                        speakerId = FOX_ID,
                        confidence = 0.88f,
                        attributionRule = "dialogue-turn-alternation",
                    ),
                ),
            )
            database.voiceAssignmentDao().upsert(
                VoiceAssignmentEntity(NARRATOR_ID, "bella", "demo-v1", 1f),
            )
            database.voiceAssignmentDao().upsert(
                VoiceAssignmentEntity(ELARA_ID, "clara", "demo-v1", 1f),
            )
            database.voiceAssignmentDao().upsert(
                VoiceAssignmentEntity(FOX_ID, "george", "demo-v1", 0.95f),
            )
            database.audioSegmentDao().upsert(
                AudioSegmentEntity(
                    id = SEGMENT_ID,
                    passageId = PASSAGE_NARRATOR_ID,
                    cacheKey = "demo-segment-not-synthesized",
                    state = AudioSegmentState.PENDING.name,
                    path = null,
                    durationMs = 0L,
                    sampleRate = 24_000,
                ),
            )
            database.playbackCheckpointDao().upsert(
                PlaybackCheckpointEntity(
                    bookId = BOOK_ID,
                    chapterId = CHAPTER_ID,
                    passageId = PASSAGE_NARRATOR_ID,
                    segmentId = SEGMENT_ID,
                    segmentPositionMs = 0L,
                    chapterPositionMs = 0L,
                    chapterDurationMs = 0L,
                    isPlaying = false,
                    speed = 1f,
                    updatedAtEpochMs = nowEpochMs,
                ),
            )
            true
        }

    const val BOOK_ID = "demo-book-moonlit-wood"
    const val CHAPTER_ID = "demo-chapter-lantern"
    const val NARRATOR_ID = "demo-character-narrator"
    const val ELARA_ID = "demo-character-elara"
    const val FOX_ID = "demo-character-fox"
    const val PASSAGE_NARRATOR_ID = "demo-passage-001"
    const val PASSAGE_ELARA_ID = "demo-passage-002"
    const val PASSAGE_FOX_ID = "demo-passage-003"
    const val SEGMENT_ID = "demo-segment-001"
}
