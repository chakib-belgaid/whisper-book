package com.whisperbook.app.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Relation

data class BookAggregate(
    @Embedded
    val book: BookEntity,
    @Relation(parentColumn = "id", entityColumn = "book_id")
    val preparationJobs: List<PreparationJobEntity>,
    @ColumnInfo(name = "chapter_count")
    val chapterCount: Int = 0,
    @ColumnInfo(name = "current_chapter_ordinal")
    val currentChapterOrdinal: Int? = null,
)

data class ChapterAggregate(
    @Embedded
    val chapter: ChapterEntity,
    @Relation(parentColumn = "id", entityColumn = "chapter_id")
    val passages: List<PassageEntity>,
)

/** Lightweight chapter-plan row used by review UI without loading passage text. */
data class ChapterPlanProjection(
    @ColumnInfo(name = "chapter_id")
    val chapterId: String,
    @ColumnInfo(name = "book_id")
    val bookId: String,
    @ColumnInfo(name = "ordinal")
    val ordinal: Int,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "passage_count")
    val passageCount: Int,
    @ColumnInfo(name = "unattributed_passage_count")
    val unattributedPassageCount: Int,
    @ColumnInfo(name = "is_selected")
    val isSelected: Boolean,
    @ColumnInfo(name = "custom_position")
    val customPosition: Int,
    @ColumnInfo(name = "updated_at")
    val updatedAtEpochMs: Long,
)

data class CharacterAggregate(
    @Embedded
    val character: StoryCharacterEntity,
    @Relation(parentColumn = "id", entityColumn = "character_id")
    val aliases: List<CharacterAliasEntity>,
    @Relation(parentColumn = "id", entityColumn = "character_id")
    val voiceAssignments: List<VoiceAssignmentEntity>,
)

data class ChapterProgressPosition(
    @ColumnInfo(name = "chapter_ordinal")
    val chapterOrdinal: Int?,
    @ColumnInfo(name = "chapter_count")
    val chapterCount: Int,
)
