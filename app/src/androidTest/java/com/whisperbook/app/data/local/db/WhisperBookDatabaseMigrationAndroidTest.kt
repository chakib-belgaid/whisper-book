package com.whisperbook.app.data.local.db

import android.database.sqlite.SQLiteConstraintException
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun migration3To4SeedsLanguageAndCompletesSameBookChapterVoiceSets() {
        migrationHelper.createDatabase(DATABASE_NAME, 3).apply {
            execSQL(
                """
                INSERT INTO books (
                    id, title, author, format, source_uri, private_source_path, source_sha256,
                    cover_path, current_chapter_id, current_passage_id, progress_fraction,
                    last_opened_at_epoch_ms
                ) VALUES
                    ('book-a', 'Story A', NULL, 'EPUB', NULL, NULL, NULL, NULL, NULL, NULL, 0, 1),
                    ('book-b', 'Story B', NULL, 'EPUB', NULL, NULL, NULL, NULL, NULL, NULL, 0, 1)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO chapters (id, book_id, ordinal, title) VALUES
                    ('a-1', 'book-a', 0, 'A One'),
                    ('a-2', 'book-a', 1, 'A Two'),
                    ('b-1', 'book-b', 0, 'B One')
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO characters (id, book_id, display_name, color_role, dialogue_line_count) VALUES
                    ('a-narrator', 'book-a', 'Narrator', 'NARRATOR', 2),
                    ('a-guest', 'book-a', 'Guest', 'BLUE', 1),
                    ('b-narrator', 'book-b', 'Narrator', 'NARRATOR', 1)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO voice_assignments (character_id, voice_id, model_version, speed) VALUES
                    ('a-narrator', 'a-template', 'model', 1),
                    ('a-guest', 'guest-template', 'model', 1),
                    ('b-narrator', 'b-template', 'model', 1)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO passages (
                    id, chapter_id, ordinal, text, speaker_id, confidence, attribution_rule
                ) VALUES
                    ('a-1-p1', 'a-1', 0, 'A narrator', 'a-narrator', 1, 'rule'),
                    ('a-1-p2', 'a-1', 1, 'A guest', 'a-guest', 1, 'rule'),
                    ('a-2-p1', 'a-2', 0, 'A narrator later', 'a-narrator', 1, 'rule'),
                    ('b-1-p1', 'b-1', 0, 'B narrator', 'b-narrator', 1, 'rule')
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO chapter_voice_assignments (
                    chapter_id, character_id, voice_id, model_version, speed
                ) VALUES
                    ('a-1', 'a-narrator', 'a-explicit', 'model', 0.9),
                    ('a-1', 'b-narrator', 'illegal-cross-book', 'model', 1)
                """.trimIndent(),
            )
            close()
        }

        migrationHelper.runMigrationsAndValidate(
            DATABASE_NAME,
            4,
            true,
            WhisperBookDatabase.MIGRATION_3_4,
        ).use { database ->
            database.query(
                "SELECT narration_language_code, narration_profile_revision, " +
                    "narration_profile_seeded FROM books WHERE id = 'book-a'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("en", cursor.getString(0))
                assertEquals(-1L, cursor.getLong(1))
                assertEquals(0, cursor.getInt(2))
            }
            database.query(
                """
                SELECT chapter_id, character_id, voice_id
                FROM chapter_voice_assignments
                ORDER BY chapter_id, character_id
                """.trimIndent(),
            ).use { cursor ->
                val assignments = buildList {
                    while (cursor.moveToNext()) {
                        add("${cursor.getString(0)}:${cursor.getString(1)}:${cursor.getString(2)}")
                    }
                }
                assertEquals(
                    listOf(
                        "a-1:a-guest:guest-template",
                        "a-1:a-narrator:a-explicit",
                        "a-2:a-narrator:a-template",
                        "b-1:b-narrator:b-template",
                    ),
                    assignments,
                )
            }
        }

        val roomDatabase = WhisperBookDatabase.create(
            ApplicationProvider.getApplicationContext(),
            DATABASE_NAME,
        )
        try {
            runBlocking {
                assertEquals(2, roomDatabase.bookDao().seedLegacyNarrationProfiles("fr"))
                val seeded = roomDatabase.bookDao().getById("book-a")!!
                assertEquals("fr", seeded.narrationLanguageCode)
                assertEquals(0L, seeded.narrationProfileRevision)
                assertTrue(seeded.narrationProfileSeeded)
                assertEquals(0, roomDatabase.bookDao().seedLegacyNarrationProfiles("ar"))
                assertEquals("fr", roomDatabase.bookDao().getById("book-a")!!.narrationLanguageCode)
                var rejected = false
                try {
                    roomDatabase.chapterVoiceAssignmentDao().upsertAll(
                        listOf(
                            ChapterVoiceAssignmentEntity(
                                bookId = "book-a",
                                chapterId = "a-2",
                                characterId = "b-narrator",
                                voiceId = "illegal",
                                modelVersion = "model",
                                speed = 1f,
                            ),
                        ),
                    )
                } catch (_: SQLiteConstraintException) {
                    rejected = true
                }
                assertTrue("Cross-book chapter casts must be rejected", rejected)
            }
        } finally {
            roomDatabase.close()
        }
    }

    private companion object {
        const val DATABASE_NAME = "profile-migration-test"
    }
}
