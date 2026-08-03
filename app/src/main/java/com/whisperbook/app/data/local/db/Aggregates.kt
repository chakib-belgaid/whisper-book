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
