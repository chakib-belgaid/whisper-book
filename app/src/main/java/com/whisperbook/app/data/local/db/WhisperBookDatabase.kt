package com.whisperbook.app.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        BookEntity::class,
        ChapterEntity::class,
        PassageEntity::class,
        StoryCharacterEntity::class,
        CharacterAliasEntity::class,
        VoiceAssignmentEntity::class,
        AudioSegmentEntity::class,
        PreparationJobEntity::class,
        PlaybackCheckpointEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class WhisperBookDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun chapterDao(): ChapterDao
    abstract fun passageDao(): PassageDao
    abstract fun storyCharacterDao(): StoryCharacterDao
    abstract fun voiceAssignmentDao(): VoiceAssignmentDao
    abstract fun audioSegmentDao(): AudioSegmentDao
    abstract fun preparationJobDao(): PreparationJobDao
    abstract fun playbackCheckpointDao(): PlaybackCheckpointDao

    companion object {
        const val DEFAULT_NAME = "whisperbook.db"

        fun create(context: Context, name: String = DEFAULT_NAME): WhisperBookDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                WhisperBookDatabase::class.java,
                name,
            ).build()
    }
}
