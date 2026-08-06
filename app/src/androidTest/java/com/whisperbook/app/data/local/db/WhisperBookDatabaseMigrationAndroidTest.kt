package com.whisperbook.app.data.local.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WhisperBookDatabaseMigrationAndroidTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WhisperBookDatabase::class.java,
    )

    @Test
    fun migration2To3PreservesCharactersWithUnknownProfileDefaults() {
        migrationHelper.createDatabase(DATABASE_NAME, 2).apply {
            execSQL(
                """
                INSERT INTO books (
                    id, title, author, format, source_uri, private_source_path, source_sha256,
                    cover_path, current_chapter_id, current_passage_id, progress_fraction,
                    last_opened_at_epoch_ms
                ) VALUES ('book', 'Story', NULL, 'EPUB', NULL, NULL, NULL, NULL, NULL, NULL, 0, 1)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO characters (id, book_id, display_name, color_role, dialogue_line_count)
                VALUES ('character', 'book', 'Mara', 'BLUE', 4)
                """.trimIndent(),
            )
            close()
        }

        migrationHelper.runMigrationsAndValidate(
            DATABASE_NAME,
            3,
            true,
            WhisperBookDatabase.MIGRATION_2_3,
        ).use { database ->
            database.query(
                """
                SELECT gender, gender_confidence, age_group, age_confidence,
                    narration_perspective, perspective_confidence, narrator_identity
                FROM characters WHERE id = 'character'
                """.trimIndent(),
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals("UNKNOWN", cursor.getString(0))
                assertEquals(0f, cursor.getFloat(1))
                assertEquals("UNKNOWN", cursor.getString(2))
                assertEquals(0f, cursor.getFloat(3))
                assertEquals("UNKNOWN", cursor.getString(4))
                assertEquals(0f, cursor.getFloat(5))
                assertEquals(true, cursor.isNull(6))
            }
        }
    }

    private companion object {
        const val DATABASE_NAME = "profile-migration-test"
    }
}
