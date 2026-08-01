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
    @Query("SELECT * FROM books ORDER BY last_opened_at_epoch_ms DESC, title COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<BookAggregate>>

    @Transaction
    @Query("SELECT * FROM books WHERE id = :bookId LIMIT 1")
    fun observeById(bookId: String): Flow<BookAggregate?>

    @Query("SELECT COUNT(*) FROM books")
    suspend fun count(): Int

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
}

@Dao
interface ChapterDao {
    @Transaction
    @Query("SELECT * FROM chapters WHERE book_id = :bookId ORDER BY ordinal ASC")
    fun observeForBook(bookId: String): Flow<List<ChapterAggregate>>

    @Transaction
    @Query("SELECT * FROM chapters WHERE id = :chapterId LIMIT 1")
    suspend fun getById(chapterId: String): ChapterAggregate?

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
}

@Dao
interface StoryCharacterDao {
    @Transaction
    @Query("SELECT * FROM characters WHERE book_id = :bookId ORDER BY display_name COLLATE NOCASE ASC")
    fun observeForBook(bookId: String): Flow<List<CharacterAggregate>>

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

    @Query("SELECT * FROM voice_assignments WHERE character_id = :characterId LIMIT 1")
    suspend fun getForCharacter(characterId: String): VoiceAssignmentEntity?
}

@Dao
interface AudioSegmentDao {
    @Query("SELECT * FROM audio_segments WHERE cache_key = :cacheKey LIMIT 1")
    suspend fun findByCacheKey(cacheKey: String): AudioSegmentEntity?

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
        DELETE FROM audio_segments
        WHERE passage_id IN (
            SELECT id FROM passages WHERE speaker_id = :characterId
        )
        """,
    )
    suspend fun deleteForCharacter(characterId: String)
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
