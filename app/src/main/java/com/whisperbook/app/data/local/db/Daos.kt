package com.whisperbook.app.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Transaction
    @Query(
        """
        SELECT books.*,
            (SELECT COUNT(*) FROM chapters WHERE chapters.book_id = books.id) AS chapter_count,
            (SELECT ordinal FROM chapters
                WHERE chapters.id = books.current_chapter_id AND chapters.book_id = books.id
                LIMIT 1) AS current_chapter_ordinal
        FROM books
        ORDER BY last_opened_at_epoch_ms DESC, title COLLATE NOCASE ASC
        """,
    )
    fun observeAll(): Flow<List<BookAggregate>>

    @Transaction
    @Query(
        """
        SELECT books.*,
            (SELECT COUNT(*) FROM chapters WHERE chapters.book_id = books.id) AS chapter_count,
            (SELECT ordinal FROM chapters
                WHERE chapters.id = books.current_chapter_id AND chapters.book_id = books.id
                LIMIT 1) AS current_chapter_ordinal
        FROM books
        WHERE books.id = :bookId
        LIMIT 1
        """,
    )
    fun observeById(bookId: String): Flow<BookAggregate?>

    @Query("SELECT * FROM books WHERE id = :bookId LIMIT 1")
    suspend fun getById(bookId: String): BookEntity?

    @Query(
        """
        UPDATE books
        SET narration_language_code = :languageCode,
            narration_profile_revision = CASE
                WHEN narration_profile_revision < 0 THEN 0
                ELSE narration_profile_revision
            END,
            narration_profile_seeded = 1
        WHERE narration_profile_seeded = 0
        """,
    )
    suspend fun seedLegacyNarrationProfiles(languageCode: String): Int

    @Query(
        """
        UPDATE books
        SET narration_language_code = :languageCode,
            narration_profile_revision = narration_profile_revision + 1
        WHERE id = :bookId
        """,
    )
    suspend fun updateNarrationLanguage(bookId: String, languageCode: String): Int

    @Query(
        """
        UPDATE books
        SET narration_profile_revision = narration_profile_revision + 1
        WHERE id = :bookId
        """,
    )
    suspend fun incrementNarrationProfileRevision(bookId: String): Int

    @Query("SELECT COUNT(*) FROM books")
    suspend fun count(): Int

    @Query(
        """
        SELECT id FROM books
        WHERE source_sha256 = :sourceSha256
        ORDER BY last_opened_at_epoch_ms DESC
        LIMIT 1
        """,
    )
    suspend fun findIdBySourceSha256(sourceSha256: String): String?

    @Query(
        """
        SELECT * FROM books
        WHERE source_sha256 IS NOT NULL AND source_sha256 != ''
        ORDER BY source_sha256 ASC, last_opened_at_epoch_ms DESC, id ASC
        """,
    )
    suspend fun getBooksWithSourceSha256(): List<BookEntity>

    @Query("SELECT COUNT(*) FROM books WHERE private_source_path = :privateSourcePath")
    suspend fun countByPrivateSourcePath(privateSourcePath: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :bookId")
    suspend fun deleteById(bookId: String)

    @Query(
        """
        UPDATE books
        SET current_chapter_id = :chapterId,
            current_passage_id = :passageId,
            progress_fraction = :progressFraction,
            last_opened_at_epoch_ms = :openedAtEpochMs
        WHERE id = :bookId
        """,
    )
    suspend fun updateProgress(
        bookId: String,
        chapterId: String?,
        passageId: String?,
        progressFraction: Float,
        openedAtEpochMs: Long,
    )

    @Query(
        """
        UPDATE books
        SET current_chapter_id = :chapterId,
            current_passage_id = :passageId,
            last_opened_at_epoch_ms = :openedAtEpochMs
        WHERE id = :bookId
        """,
    )
    suspend fun updatePlaybackLocation(
        bookId: String,
        chapterId: String,
        passageId: String,
        openedAtEpochMs: Long,
    )
}

@Dao
interface ChapterDao {
    @Transaction
    @Query("SELECT * FROM chapters WHERE book_id = :bookId ORDER BY ordinal ASC")
    fun observeForBook(bookId: String): Flow<List<ChapterAggregate>>

    @Transaction
    @Query("SELECT * FROM chapters WHERE id = :chapterId LIMIT 1")
    suspend fun getById(chapterId: String): ChapterAggregate?

    @Query("SELECT * FROM chapters WHERE book_id = :bookId ORDER BY ordinal ASC")
    suspend fun getHeadersForBook(bookId: String): List<ChapterEntity>

    @Query(
        """
        SELECT
            (SELECT ordinal FROM chapters WHERE id = :chapterId AND book_id = :bookId LIMIT 1)
                AS chapter_ordinal,
            COUNT(*) AS chapter_count
        FROM chapters
        WHERE book_id = :bookId
        """,
    )
    suspend fun getProgressPosition(bookId: String, chapterId: String): ChapterProgressPosition

    @Upsert
    suspend fun insertAll(chapters: List<ChapterEntity>)

    @Query("DELETE FROM chapters WHERE book_id = :bookId")
    suspend fun deleteForBook(bookId: String)
}

@Dao
interface PassageDao {
    @Upsert
    suspend fun insertAll(passages: List<PassageEntity>)

    @Query("SELECT * FROM passages WHERE id = :passageId LIMIT 1")
    suspend fun getById(passageId: String): PassageEntity?

    @Query("SELECT * FROM passages WHERE chapter_id = :chapterId ORDER BY ordinal ASC")
    suspend fun getForChapter(chapterId: String): List<PassageEntity>

    @Query(
        """
        SELECT passages.* FROM passages
        INNER JOIN chapters ON chapters.id = passages.chapter_id
        WHERE chapters.book_id = :bookId
        ORDER BY chapters.ordinal ASC, passages.ordinal ASC
        """,
    )
    suspend fun getForBook(bookId: String): List<PassageEntity>

    @Query(
        """
        UPDATE passages
        SET speaker_id = :speakerId,
            confidence = 1.0,
            attribution_rule = :attributionRule
        WHERE id IN (:passageIds)
        """,
    )
    suspend fun updateSpeakerAttribution(
        passageIds: List<String>,
        speakerId: String,
        attributionRule: String,
    ): Int

    @Query("DELETE FROM passages WHERE chapter_id = :chapterId")
    suspend fun deleteForChapter(chapterId: String)
}

@Dao
interface StoryCharacterDao {
    @Transaction
    @Query("SELECT * FROM characters WHERE book_id = :bookId ORDER BY display_name COLLATE NOCASE ASC")
    fun observeForBook(bookId: String): Flow<List<CharacterAggregate>>

    @Query("SELECT * FROM characters WHERE book_id = :bookId ORDER BY display_name COLLATE NOCASE ASC")
    suspend fun getEntitiesForBook(bookId: String): List<StoryCharacterEntity>

    @Upsert
    suspend fun insertAll(characters: List<StoryCharacterEntity>)

    @Upsert
    suspend fun insertAliases(aliases: List<CharacterAliasEntity>)

    @Query("DELETE FROM characters WHERE book_id = :bookId")
    suspend fun deleteForBook(bookId: String)
}

@Dao
interface VoiceAssignmentDao {
    @Upsert
    suspend fun upsert(assignment: VoiceAssignmentEntity)

    @Query("SELECT * FROM voice_assignments WHERE character_id = :characterId LIMIT 1")
    fun observeForCharacter(characterId: String): Flow<VoiceAssignmentEntity?>

    @Query("SELECT * FROM voice_assignments WHERE character_id IN (:characterIds)")
    fun observeForCharacters(characterIds: List<String>): Flow<List<VoiceAssignmentEntity>>

    @Query("SELECT * FROM voice_assignments WHERE character_id = :characterId LIMIT 1")
    suspend fun getForCharacter(characterId: String): VoiceAssignmentEntity?

    @Query("SELECT * FROM voice_assignments WHERE character_id IN (:characterIds)")
    suspend fun getForCharacters(characterIds: List<String>): List<VoiceAssignmentEntity>
}

@Dao
interface ChapterVoiceAssignmentDao {
    @Query(
        """
        SELECT * FROM chapter_voice_assignments
        WHERE book_id = :bookId AND chapter_id = :chapterId
        ORDER BY character_id ASC
        """,
    )
    fun observeForChapter(bookId: String, chapterId: String): Flow<List<ChapterVoiceAssignmentEntity>>

    @Query(
        """
        SELECT * FROM chapter_voice_assignments
        WHERE book_id = :bookId AND chapter_id = :chapterId
        ORDER BY character_id ASC
        """,
    )
    suspend fun getForChapter(bookId: String, chapterId: String): List<ChapterVoiceAssignmentEntity>

    @Query(
        """
        SELECT * FROM chapter_voice_assignments
        WHERE book_id = :bookId AND chapter_id = :chapterId AND character_id = :characterId
        LIMIT 1
        """,
    )
    suspend fun getForChapterAndCharacter(
        bookId: String,
        chapterId: String,
        characterId: String,
    ): ChapterVoiceAssignmentEntity?

    @Query(
        """
        SELECT * FROM chapter_voice_assignments
        WHERE book_id = :bookId AND character_id = :characterId
        ORDER BY chapter_id ASC
        """,
    )
    suspend fun getForCharacter(bookId: String, characterId: String): List<ChapterVoiceAssignmentEntity>

    @Upsert
    suspend fun upsertAll(assignments: List<ChapterVoiceAssignmentEntity>)

    @Query("DELETE FROM chapter_voice_assignments WHERE book_id = :bookId AND character_id = :characterId")
    suspend fun deleteForCharacter(bookId: String, characterId: String)

    @Query(
        """
        DELETE FROM chapter_voice_assignments
        WHERE book_id = :bookId AND character_id = :characterId
          AND chapter_id IN (
              SELECT id FROM chapters
              WHERE book_id = :bookId AND ordinal >= :fromChapterOrdinal
          )
        """,
    )
    suspend fun deleteForCharacterFromChapterOrdinal(
        characterId: String,
        bookId: String,
        fromChapterOrdinal: Int,
    )

    @Query(
        """
        DELETE FROM chapter_voice_assignments
        WHERE book_id = :bookId AND character_id = :characterId AND chapter_id = :chapterId
        """,
    )
    suspend fun deleteForChapterAndCharacter(bookId: String, chapterId: String, characterId: String)
}

@Dao
interface AudioSegmentDao {
    @Query("SELECT * FROM audio_segments WHERE cache_key = :cacheKey LIMIT 1")
    suspend fun findByCacheKey(cacheKey: String): AudioSegmentEntity?

    @Query("SELECT * FROM audio_segments WHERE cache_key IN (:cacheKeys)")
    suspend fun findByCacheKeys(cacheKeys: List<String>): List<AudioSegmentEntity>

    @Query("SELECT * FROM audio_segments WHERE passage_id = :passageId ORDER BY id ASC")
    fun observeForPassage(passageId: String): Flow<List<AudioSegmentEntity>>

    @Upsert
    suspend fun upsert(segment: AudioSegmentEntity)

    @Query("UPDATE audio_segments SET state = :state, path = :path WHERE id = :segmentId")
    suspend fun updateState(segmentId: String, state: String, path: String?)

    @Query("DELETE FROM audio_segments WHERE id = :segmentId")
    suspend fun deleteById(segmentId: String)

    @Query(
        """
        SELECT audio_segments.path FROM audio_segments
        INNER JOIN passages ON passages.id = audio_segments.passage_id
        INNER JOIN chapters ON chapters.id = passages.chapter_id
        WHERE chapters.book_id = :bookId AND audio_segments.path IS NOT NULL
        """,
    )
    suspend fun getPathsForBook(bookId: String): List<String>

    @Query("SELECT COUNT(*) FROM audio_segments WHERE path = :path")
    suspend fun countByPath(path: String): Int

    @Query(
        """
        DELETE FROM audio_segments
        WHERE passage_id IN (
            SELECT id FROM passages WHERE speaker_id = :characterId
        )
        """,
    )
    suspend fun deleteForCharacter(characterId: String)

    @Query(
        """
        SELECT passages.id FROM passages
        INNER JOIN chapters ON chapters.id = passages.chapter_id
        WHERE passages.speaker_id = :characterId
          AND chapters.book_id = :bookId
          AND chapters.ordinal >= :fromChapterOrdinal
        """,
    )
    suspend fun getPassageIdsForCharacterFromChapterOrdinal(
        characterId: String,
        bookId: String,
        fromChapterOrdinal: Int,
    ): List<String>

    @Query(
        """
        SELECT passages.id FROM passages
        INNER JOIN chapters ON chapters.id = passages.chapter_id
        WHERE passages.speaker_id = :characterId
          AND chapters.book_id = :bookId
          AND chapters.id = :chapterId
        """,
    )
    suspend fun getPassageIdsForCharacterInChapter(
        characterId: String,
        bookId: String,
        chapterId: String,
    ): List<String>

    @Query("DELETE FROM audio_segments WHERE passage_id IN (:passageIds)")
    suspend fun deleteForPassageIdsBatch(passageIds: List<String>)

    @Query(
        """
        DELETE FROM audio_segments
        WHERE passage_id IN (
            SELECT passages.id FROM passages
            INNER JOIN chapters ON chapters.id = passages.chapter_id
            WHERE chapters.book_id = :bookId
        )
        """,
    )
    suspend fun deleteForBook(bookId: String)

    @Query(
        """
        DELETE FROM audio_segments
        WHERE passage_id IN (
            SELECT passages.id FROM passages
            INNER JOIN chapters ON chapters.id = passages.chapter_id
            WHERE passages.speaker_id = :characterId
              AND chapters.book_id = :bookId
              AND chapters.ordinal >= :fromChapterOrdinal
        )
        """,
    )
    suspend fun deleteForCharacterFromChapterOrdinal(
        characterId: String,
        bookId: String,
        fromChapterOrdinal: Int,
    )
}

@Dao
interface PreparationJobDao {
    @Query("SELECT * FROM preparation_jobs WHERE book_id = :bookId LIMIT 1")
    fun observeForBook(bookId: String): Flow<PreparationJobEntity?>

    @Query("SELECT * FROM preparation_jobs WHERE book_id = :bookId LIMIT 1")
    suspend fun getForBook(bookId: String): PreparationJobEntity?

    @Upsert
    suspend fun upsert(job: PreparationJobEntity)
}

@Dao
interface PlaybackCheckpointDao {
    @Query("SELECT * FROM playback_checkpoints WHERE book_id = :bookId LIMIT 1")
    fun observeForBook(bookId: String): Flow<PlaybackCheckpointEntity?>

    @Query("SELECT * FROM playback_checkpoints WHERE book_id = :bookId LIMIT 1")
    suspend fun getForBook(bookId: String): PlaybackCheckpointEntity?

    @Upsert
    suspend fun upsert(checkpoint: PlaybackCheckpointEntity)

    @Query("DELETE FROM playback_checkpoints WHERE book_id = :bookId")
    suspend fun deleteForBook(bookId: String)
}
