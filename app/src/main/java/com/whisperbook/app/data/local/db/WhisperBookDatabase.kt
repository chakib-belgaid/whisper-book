package com.whisperbook.app.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        BookEntity::class,
        ChapterEntity::class,
        PassageEntity::class,
        StoryCharacterEntity::class,
        CharacterAliasEntity::class,
        VoiceAssignmentEntity::class,
        ChapterVoiceAssignmentEntity::class,
        AudioSegmentEntity::class,
        PreparationJobEntity::class,
        PlaybackCheckpointEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class WhisperBookDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun chapterDao(): ChapterDao
    abstract fun passageDao(): PassageDao
    abstract fun storyCharacterDao(): StoryCharacterDao
    abstract fun voiceAssignmentDao(): VoiceAssignmentDao
    abstract fun chapterVoiceAssignmentDao(): ChapterVoiceAssignmentDao
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
            )
                .addMigrations(MIGRATION_1_2)
                .build()

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `chapter_voice_assignments` (
                        `chapter_id` TEXT NOT NULL,
                        `character_id` TEXT NOT NULL,
                        `voice_id` TEXT NOT NULL,
                        `model_version` TEXT NOT NULL,
                        `speed` REAL NOT NULL,
                        PRIMARY KEY(`chapter_id`, `character_id`),
                        FOREIGN KEY(`chapter_id`) REFERENCES `chapters`(`id`)
                            ON UPDATE CASCADE ON DELETE CASCADE,
                        FOREIGN KEY(`character_id`) REFERENCES `characters`(`id`)
                            ON UPDATE CASCADE ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_chapter_voice_assignments_chapter_id` " +
                        "ON `chapter_voice_assignments` (`chapter_id`)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_chapter_voice_assignments_character_id` " +
                        "ON `chapter_voice_assignments` (`character_id`)",
                )
            }
        }
    }
}
