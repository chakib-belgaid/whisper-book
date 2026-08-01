package com.whisperbook.app.data.local.db

import android.net.Uri
import com.whisperbook.app.domain.model.AudioSegment
import com.whisperbook.app.domain.model.AudioSegmentState
import com.whisperbook.app.domain.model.Book
import com.whisperbook.app.domain.model.BookFormat
import com.whisperbook.app.domain.model.Chapter
import com.whisperbook.app.domain.model.CharacterColorRole
import com.whisperbook.app.domain.model.CharacterVoiceAssignment
import com.whisperbook.app.domain.model.Passage
import com.whisperbook.app.domain.model.PlaybackCursor
import com.whisperbook.app.domain.model.PreparationStage
import com.whisperbook.app.domain.model.PreparationState
import com.whisperbook.app.domain.model.StoryCharacter

fun BookAggregate.toDomain(): Book {
    val preparation = preparationJobs.firstOrNull()?.toDomain()
        ?: PreparationState(
            stage = PreparationStage.COPY_AND_VALIDATE,
            message = "Waiting to prepare",
        )
    return Book(
        id = book.id,
        title = book.title,
        author = book.author,
        format = enumValueOrDefault(book.format, BookFormat.EPUB),
        sourceUri = book.sourceUri?.takeIf(String::isNotBlank)?.let(Uri::parse),
        privateSourcePath = book.privateSourcePath,
        coverPath = book.coverPath,
        preparation = preparation,
        currentChapterId = book.currentChapterId,
        currentPassageId = book.currentPassageId,
        progressFraction = book.progressFraction.normalized(default = 0f, minimum = 0f, maximum = 1f),
        lastOpenedAtEpochMs = book.lastOpenedAtEpochMs,
    )
}

fun Book.toEntity(sourceSha256: String? = null): BookEntity = BookEntity(
    id = id,
    title = title,
    author = author,
    format = format.name,
    sourceUri = sourceUri?.toString(),
    privateSourcePath = privateSourcePath,
    sourceSha256 = sourceSha256,
    coverPath = coverPath,
    currentChapterId = currentChapterId,
    currentPassageId = currentPassageId,
    progressFraction = progressFraction.normalized(default = 0f, minimum = 0f, maximum = 1f),
    lastOpenedAtEpochMs = lastOpenedAtEpochMs,
)

fun PreparationJobEntity.toDomain(): PreparationState = PreparationState(
    stage = enumValueOrDefault(stage, PreparationStage.COPY_AND_VALIDATE),
    completedUnits = completedUnits.coerceAtLeast(0),
    totalUnits = totalUnits.coerceAtLeast(0),
    progressFraction = progressFraction.normalized(default = 0f, minimum = 0f, maximum = 1f),
    message = message,
    retryable = retryable,
)

fun PreparationState.toEntity(
    bookId: String,
    attemptCount: Int = 0,
    updatedAtEpochMs: Long,
): PreparationJobEntity = PreparationJobEntity(
    bookId = bookId,
    stage = stage.name,
    completedUnits = completedUnits.coerceAtLeast(0),
    totalUnits = totalUnits.coerceAtLeast(0),
    progressFraction = progressFraction.normalized(default = 0f, minimum = 0f, maximum = 1f),
    message = message,
    retryable = retryable,
    attemptCount = attemptCount.coerceAtLeast(0),
    updatedAtEpochMs = updatedAtEpochMs,
)

fun ChapterAggregate.toDomain(): Chapter = Chapter(
    id = chapter.id,
    bookId = chapter.bookId,
    ordinal = chapter.ordinal,
    title = chapter.title,
    passages = passages.sortedBy(PassageEntity::ordinal).map(PassageEntity::toDomain),
)

fun Chapter.toEntity(): ChapterEntity = ChapterEntity(
    id = id,
    bookId = bookId,
    ordinal = ordinal,
    title = title,
)

fun PassageEntity.toDomain(): Passage = Passage(
    id = id,
    chapterId = chapterId,
    ordinal = ordinal,
    text = text,
    speakerId = speakerId,
    confidence = confidence.normalized(default = 0f, minimum = 0f, maximum = 1f),
    attributionRule = attributionRule,
)

fun Passage.toEntity(): PassageEntity = PassageEntity(
    id = id,
    chapterId = chapterId,
    ordinal = ordinal,
    text = text,
    speakerId = speakerId,
    confidence = confidence.normalized(default = 0f, minimum = 0f, maximum = 1f),
    attributionRule = attributionRule,
)

fun CharacterAggregate.toDomain(): StoryCharacter = StoryCharacter(
    id = character.id,
    bookId = character.bookId,
    displayName = character.displayName,
    aliases = aliases.map(CharacterAliasEntity::alias).sorted().toSet(),
    colorRole = enumValueOrDefault(character.colorRole, CharacterColorRole.BLUE),
    dialogueLineCount = character.dialogueLineCount.coerceAtLeast(0),
)

fun StoryCharacter.toEntity(): StoryCharacterEntity = StoryCharacterEntity(
    id = id,
    bookId = bookId,
    displayName = displayName,
    colorRole = colorRole.name,
    dialogueLineCount = dialogueLineCount.coerceAtLeast(0),
)

fun StoryCharacter.toAliasEntities(): List<CharacterAliasEntity> = aliases
    .filter(String::isNotBlank)
    .distinct()
    .sorted()
    .map { alias -> CharacterAliasEntity(characterId = id, alias = alias) }

fun VoiceAssignmentEntity.toDomain(): CharacterVoiceAssignment = CharacterVoiceAssignment(
    characterId = characterId,
    voiceId = voiceId,
    modelVersion = modelVersion,
    speed = speed.normalized(default = 1f, minimum = MIN_SPEAKING_SPEED, maximum = MAX_SPEAKING_SPEED),
)

fun CharacterVoiceAssignment.toEntity(): VoiceAssignmentEntity = VoiceAssignmentEntity(
    characterId = characterId,
    voiceId = voiceId,
    modelVersion = modelVersion,
    speed = speed.normalized(default = 1f, minimum = MIN_SPEAKING_SPEED, maximum = MAX_SPEAKING_SPEED),
)

fun AudioSegmentEntity.toDomain(): AudioSegment = AudioSegment(
    id = id,
    passageId = passageId,
    cacheKey = cacheKey,
    state = enumValueOrDefault(state, AudioSegmentState.PENDING),
    path = path,
    durationMs = durationMs.coerceAtLeast(0L),
    sampleRate = sampleRate.coerceAtLeast(1),
)

fun AudioSegment.toEntity(): AudioSegmentEntity = AudioSegmentEntity(
    id = id,
    passageId = passageId,
    cacheKey = cacheKey,
    state = state.name,
    path = path,
    durationMs = durationMs.coerceAtLeast(0L),
    sampleRate = sampleRate.coerceAtLeast(1),
)

fun PlaybackCheckpointEntity.toDomain(): PlaybackCursor = PlaybackCursor(
    bookId = bookId,
    chapterId = chapterId,
    passageId = passageId,
    segmentId = segmentId,
    segmentPositionMs = segmentPositionMs.coerceAtLeast(0L),
    chapterPositionMs = chapterPositionMs.coerceAtLeast(0L),
    chapterDurationMs = chapterDurationMs.coerceAtLeast(0L),
    isPlaying = isPlaying,
    speed = speed.normalized(default = 1f, minimum = MIN_SPEAKING_SPEED, maximum = MAX_SPEAKING_SPEED),
)

fun PlaybackCursor.toEntity(updatedAtEpochMs: Long): PlaybackCheckpointEntity =
    PlaybackCheckpointEntity(
        bookId = bookId,
        chapterId = chapterId,
        passageId = passageId,
        segmentId = segmentId,
        segmentPositionMs = segmentPositionMs.coerceAtLeast(0L),
        chapterPositionMs = chapterPositionMs.coerceAtLeast(0L),
        chapterDurationMs = chapterDurationMs.coerceAtLeast(0L),
        isPlaying = isPlaying,
        speed = speed.normalized(default = 1f, minimum = MIN_SPEAKING_SPEED, maximum = MAX_SPEAKING_SPEED),
        updatedAtEpochMs = updatedAtEpochMs,
    )

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T =
    enumValues<T>().firstOrNull { candidate -> candidate.name == value } ?: default

private fun Float.normalized(default: Float, minimum: Float, maximum: Float): Float =
    if (isFinite()) coerceIn(minimum, maximum) else default

private const val MIN_SPEAKING_SPEED = 0.5f
private const val MAX_SPEAKING_SPEED = 2f
