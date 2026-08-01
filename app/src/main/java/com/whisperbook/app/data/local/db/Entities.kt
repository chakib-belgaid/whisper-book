package com.whisperbook.app.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "books",
    indices = [
        Index(value = ["last_opened_at_epoch_ms"]),
        Index(value = ["source_sha256"]),
    ],
)
data class BookEntity(
    @androidx.room.PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "author")
    val author: String?,
    @ColumnInfo(name = "format")
    val format: String,
    @ColumnInfo(name = "source_uri")
    val sourceUri: String?,
    @ColumnInfo(name = "private_source_path")
    val privateSourcePath: String?,
    @ColumnInfo(name = "source_sha256")
    val sourceSha256: String?,
    @ColumnInfo(name = "cover_path")
    val coverPath: String?,
    @ColumnInfo(name = "current_chapter_id")
    val currentChapterId: String?,
    @ColumnInfo(name = "current_passage_id")
    val currentPassageId: String?,
    @ColumnInfo(name = "progress_fraction")
    val progressFraction: Float,
    @ColumnInfo(name = "last_opened_at_epoch_ms")
    val lastOpenedAtEpochMs: Long,
)

@Entity(
    tableName = "chapters",
    primaryKeys = ["id"],
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["book_id"]),
        Index(value = ["book_id", "ordinal"], unique = true),
    ],
)
data class ChapterEntity(
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "book_id")
    val bookId: String,
    @ColumnInfo(name = "ordinal")
    val ordinal: Int,
    @ColumnInfo(name = "title")
    val title: String,
)

@Entity(
    tableName = "characters",
    primaryKeys = ["id"],
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["book_id"]),
        Index(value = ["book_id", "display_name"]),
    ],
)
data class StoryCharacterEntity(
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "book_id")
    val bookId: String,
    @ColumnInfo(name = "display_name")
    val displayName: String,
    @ColumnInfo(name = "color_role")
    val colorRole: String,
    @ColumnInfo(name = "dialogue_line_count")
    val dialogueLineCount: Int,
)

@Entity(
    tableName = "character_aliases",
    primaryKeys = ["character_id", "alias"],
    foreignKeys = [
        ForeignKey(
            entity = StoryCharacterEntity::class,
            parentColumns = ["id"],
            childColumns = ["character_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["character_id"])],
)
data class CharacterAliasEntity(
    @ColumnInfo(name = "character_id")
    val characterId: String,
    @ColumnInfo(name = "alias")
    val alias: String,
)

@Entity(
    tableName = "passages",
    primaryKeys = ["id"],
    foreignKeys = [
        ForeignKey(
            entity = ChapterEntity::class,
            parentColumns = ["id"],
            childColumns = ["chapter_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["chapter_id"]),
        Index(value = ["chapter_id", "ordinal"], unique = true),
        Index(value = ["speaker_id"]),
    ],
)
data class PassageEntity(
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "chapter_id")
    val chapterId: String,
    @ColumnInfo(name = "ordinal")
    val ordinal: Int,
    @ColumnInfo(name = "text")
    val text: String,
    @ColumnInfo(name = "speaker_id")
    val speakerId: String,
    @ColumnInfo(name = "confidence")
    val confidence: Float,
    @ColumnInfo(name = "attribution_rule")
    val attributionRule: String,
)

@Entity(
    tableName = "voice_assignments",
    primaryKeys = ["character_id"],
    foreignKeys = [
        ForeignKey(
            entity = StoryCharacterEntity::class,
            parentColumns = ["id"],
            childColumns = ["character_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["voice_id"])],
)
data class VoiceAssignmentEntity(
    @ColumnInfo(name = "character_id")
    val characterId: String,
    @ColumnInfo(name = "voice_id")
    val voiceId: String,
    @ColumnInfo(name = "model_version")
    val modelVersion: String,
    @ColumnInfo(name = "speed")
    val speed: Float,
)

@Entity(
    tableName = "audio_segments",
    primaryKeys = ["id"],
    foreignKeys = [
        ForeignKey(
            entity = PassageEntity::class,
            parentColumns = ["id"],
            childColumns = ["passage_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["passage_id"]),
        Index(value = ["cache_key"], unique = true),
        Index(value = ["state"]),
    ],
)
data class AudioSegmentEntity(
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "passage_id")
    val passageId: String,
    @ColumnInfo(name = "cache_key")
    val cacheKey: String,
    @ColumnInfo(name = "state")
    val state: String,
    @ColumnInfo(name = "path")
    val path: String?,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,
    @ColumnInfo(name = "sample_rate")
    val sampleRate: Int,
)

@Entity(
    tableName = "preparation_jobs",
    primaryKeys = ["book_id"],
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["stage"])],
)
data class PreparationJobEntity(
    @ColumnInfo(name = "book_id")
    val bookId: String,
    @ColumnInfo(name = "stage")
    val stage: String,
    @ColumnInfo(name = "completed_units")
    val completedUnits: Int,
    @ColumnInfo(name = "total_units")
    val totalUnits: Int,
    @ColumnInfo(name = "progress_fraction")
    val progressFraction: Float,
    @ColumnInfo(name = "message")
    val message: String?,
    @ColumnInfo(name = "retryable")
    val retryable: Boolean,
    @ColumnInfo(name = "attempt_count")
    val attemptCount: Int,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long,
)

@Entity(
    tableName = "playback_checkpoints",
    primaryKeys = ["book_id"],
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["chapter_id"]),
        Index(value = ["passage_id"]),
        Index(value = ["segment_id"]),
    ],
)
data class PlaybackCheckpointEntity(
    @ColumnInfo(name = "book_id")
    val bookId: String,
    @ColumnInfo(name = "chapter_id")
    val chapterId: String,
    @ColumnInfo(name = "passage_id")
    val passageId: String,
    @ColumnInfo(name = "segment_id")
    val segmentId: String,
    @ColumnInfo(name = "segment_position_ms")
    val segmentPositionMs: Long,
    @ColumnInfo(name = "chapter_position_ms")
    val chapterPositionMs: Long,
    @ColumnInfo(name = "chapter_duration_ms")
    val chapterDurationMs: Long,
    @ColumnInfo(name = "is_playing")
    val isPlaying: Boolean,
    @ColumnInfo(name = "speed")
    val speed: Float,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long,
)
