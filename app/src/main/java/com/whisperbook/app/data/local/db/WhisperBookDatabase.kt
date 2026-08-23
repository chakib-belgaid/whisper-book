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
        ChapterPlanEntryEntity::class,
        PassageEntity::class,
        StoryCharacterEntity::class,
        CharacterAliasEntity::class,
        VoiceAssignmentEntity::class,
        ChapterVoiceAssignmentEntity::class,
        AudioSegmentEntity::class,
        PreparationJobEntity::class,
        PlaybackCheckpointEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class WhisperBookDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun chapterDao(): ChapterDao
    abstract fun chapterPlanDao(): ChapterPlanDao
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
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                )
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

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `characters` ADD COLUMN `gender` TEXT NOT NULL DEFAULT 'UNKNOWN'")
                database.execSQL("ALTER TABLE `characters` ADD COLUMN `gender_confidence` REAL NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE `characters` ADD COLUMN `age_group` TEXT NOT NULL DEFAULT 'UNKNOWN'")
                database.execSQL("ALTER TABLE `characters` ADD COLUMN `age_confidence` REAL NOT NULL DEFAULT 0")
                database.execSQL(
                    "ALTER TABLE `characters` ADD COLUMN `narration_perspective` TEXT NOT NULL DEFAULT 'UNKNOWN'",
                )
                database.execSQL(
                    "ALTER TABLE `characters` ADD COLUMN `perspective_confidence` REAL NOT NULL DEFAULT 0",
                )
                database.execSQL("ALTER TABLE `characters` ADD COLUMN `narrator_identity` TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE `books` ADD COLUMN `narration_language_code` " +
                        "TEXT NOT NULL DEFAULT 'en'",
                )
                database.execSQL(
                    "ALTER TABLE `books` ADD COLUMN `narration_profile_revision` " +
                        "INTEGER NOT NULL DEFAULT -1",
                )
                database.execSQL(
                    "ALTER TABLE `books` ADD COLUMN `narration_profile_seeded` " +
                        "INTEGER NOT NULL DEFAULT 0",
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_chapters_id_book_id` " +
                        "ON `chapters` (`id`, `book_id`)",
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_characters_id_book_id` " +
                        "ON `characters` (`id`, `book_id`)",
                )
                database.execSQL("ALTER TABLE `chapter_voice_assignments` RENAME TO `chapter_voice_assignments_legacy`")
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `chapter_voice_assignments` (
                        `book_id` TEXT NOT NULL,
                        `chapter_id` TEXT NOT NULL,
                        `character_id` TEXT NOT NULL,
                        `voice_id` TEXT NOT NULL,
                        `model_version` TEXT NOT NULL,
                        `speed` REAL NOT NULL,
                        PRIMARY KEY(`chapter_id`, `character_id`),
                        FOREIGN KEY(`book_id`) REFERENCES `books`(`id`)
                            ON UPDATE CASCADE ON DELETE CASCADE,
                        FOREIGN KEY(`chapter_id`, `book_id`) REFERENCES `chapters`(`id`, `book_id`)
                            ON UPDATE CASCADE ON DELETE CASCADE,
                        FOREIGN KEY(`character_id`, `book_id`) REFERENCES `characters`(`id`, `book_id`)
                            ON UPDATE CASCADE ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                // Preserve valid explicit chapter overrides. Any historical cross-book row is
                // deliberately omitted because it cannot represent a legal chapter voice set.
                database.execSQL(
                    """
                    INSERT OR IGNORE INTO `chapter_voice_assignments` (
                        `book_id`, `chapter_id`, `character_id`, `voice_id`, `model_version`, `speed`
                    )
                    SELECT chapters.book_id, legacy.chapter_id, legacy.character_id,
                        legacy.voice_id, legacy.model_version, legacy.speed
                    FROM `chapter_voice_assignments_legacy` AS legacy
                    INNER JOIN `chapters` ON chapters.id = legacy.chapter_id
                    INNER JOIN `characters` ON characters.id = legacy.character_id
                        AND characters.book_id = chapters.book_id
                    """.trimIndent(),
                )
                // Complete each already-attributed chapter set from the existing book-character
                // template. Explicit rows above win because this insert is OR IGNORE.
                database.execSQL(
                    """
                    INSERT OR IGNORE INTO `chapter_voice_assignments` (
                        `book_id`, `chapter_id`, `character_id`, `voice_id`, `model_version`, `speed`
                    )
                    SELECT DISTINCT chapters.book_id, passages.chapter_id, passages.speaker_id,
                        voice_assignments.voice_id, voice_assignments.model_version, voice_assignments.speed
                    FROM `passages`
                    INNER JOIN `chapters` ON chapters.id = passages.chapter_id
                    INNER JOIN `characters` ON characters.id = passages.speaker_id
                        AND characters.book_id = chapters.book_id
                    INNER JOIN `voice_assignments` ON voice_assignments.character_id = passages.speaker_id
                    """.trimIndent(),
                )
                database.execSQL("DROP TABLE `chapter_voice_assignments_legacy`")
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_chapter_voice_assignments_book_id` " +
                        "ON `chapter_voice_assignments` (`book_id`)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_chapter_voice_assignments_chapter_id_book_id` " +
                        "ON `chapter_voice_assignments` (`chapter_id`, `book_id`)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_chapter_voice_assignments_character_id_book_id` " +
                        "ON `chapter_voice_assignments` (`character_id`, `book_id`)",
                )
            }
        }

        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE `books` ADD COLUMN `preferred_narrator_voice_id` TEXT DEFAULT NULL",
                )
                // Existing books have already crossed the old implicit setup boundary. Keeping
                // them confirmed avoids interrupting playback after an app update, while every
                // newly imported book is inserted as unconfirmed.
                database.execSQL(
                    "ALTER TABLE `books` ADD COLUMN `narration_setup_confirmed` " +
                        "INTEGER NOT NULL DEFAULT 1",
                )
            }
        }

        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE `preparation_jobs` ADD COLUMN `run_state` " +
                        "TEXT NOT NULL DEFAULT 'RUNNING'",
                )
            }
        }

        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE `preparation_jobs` ADD COLUMN `chapter_plan_confirmed` " +
                        "INTEGER NOT NULL DEFAULT 1",
                )
                database.execSQL(
                    "ALTER TABLE `preparation_jobs` ADD COLUMN `plan_revision` " +
                        "INTEGER NOT NULL DEFAULT 0",
                )
                database.execSQL(
                    "ALTER TABLE `preparation_jobs` ADD COLUMN `active_chapter_id` TEXT DEFAULT NULL",
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `chapter_plan_entries` (
                        `book_id` TEXT NOT NULL,
                        `chapter_id` TEXT NOT NULL,
                        `is_selected` INTEGER NOT NULL,
                        `custom_position` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        PRIMARY KEY(`book_id`, `chapter_id`),
                        FOREIGN KEY(`book_id`) REFERENCES `books`(`id`)
                            ON UPDATE CASCADE ON DELETE CASCADE,
                        FOREIGN KEY(`chapter_id`, `book_id`) REFERENCES `chapters`(`id`, `book_id`)
                            ON UPDATE CASCADE ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_chapter_plan_entries_book_id` " +
                        "ON `chapter_plan_entries` (`book_id`)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_chapter_plan_entries_chapter_id_book_id` " +
                        "ON `chapter_plan_entries` (`chapter_id`, `book_id`)",
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_chapter_plan_entries_book_id_custom_position` " +
                        "ON `chapter_plan_entries` (`book_id`, `custom_position`)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_chapter_plan_entries_book_id_is_selected_custom_position` " +
                        "ON `chapter_plan_entries` (`book_id`, `is_selected`, `custom_position`)",
                )
                database.execSQL(
                    """
                    INSERT OR IGNORE INTO `chapter_plan_entries` (
                        `book_id`, `chapter_id`, `is_selected`, `custom_position`, `updated_at`
                    )
                    SELECT chapters.book_id, chapters.id, 1, chapters.ordinal, 0
                    FROM `chapters`
                    """.trimIndent(),
                )
                database.execSQL(
                    "UPDATE `preparation_jobs` SET `chapter_plan_confirmed` = 1",
                )
            }
        }
    }
}
