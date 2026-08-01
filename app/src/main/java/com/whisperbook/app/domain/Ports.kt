package com.whisperbook.app.domain

import android.net.Uri
import com.whisperbook.app.domain.model.AppSettings
import com.whisperbook.app.domain.model.AudioSegment
import com.whisperbook.app.domain.model.Book
import com.whisperbook.app.domain.model.BookFormat
import com.whisperbook.app.domain.model.Chapter
import com.whisperbook.app.domain.model.CharacterVoiceAssignment
import com.whisperbook.app.domain.model.PlaybackCursor
import com.whisperbook.app.domain.model.PreparationState
import com.whisperbook.app.domain.model.StoryCharacter
import com.whisperbook.app.domain.model.VoiceDescriptor
import java.io.File
import kotlinx.coroutines.flow.Flow

data class ImportedBook(
    val title: String,
    val author: String?,
    val format: BookFormat,
    val privateFile: File,
    val sha256: String,
)

data class ExtractedPublication(
    val title: String,
    val author: String?,
    val chapters: List<ExtractedChapter>,
)

data class ExtractedChapter(val title: String, val paragraphs: List<String>)

data class AttributedPublication(
    val chapters: List<Chapter>,
    val characters: List<StoryCharacter>,
)

data class SynthesisRequest(
    val text: String,
    val voice: VoiceDescriptor,
    val speed: Float,
    val cacheKey: String,
)

data class SynthesisResult(
    val pcm16: ShortArray,
    val sampleRate: Int,
    val durationMs: Long,
)

interface BookImporter {
    suspend fun import(uri: Uri): Result<ImportedBook>
}

interface PublicationExtractor {
    suspend fun extract(book: ImportedBook): Result<ExtractedPublication>
}

interface SpeakerAttributor {
    suspend fun attribute(bookId: String, publication: ExtractedPublication): AttributedPublication
}

interface LocalTtsEngine : AutoCloseable {
    suspend fun warmUp(): Result<Unit>
    suspend fun voices(): List<VoiceDescriptor>
    suspend fun synthesize(request: SynthesisRequest): Result<SynthesisResult>
    override fun close()
}

interface AudioSegmentStore {
    suspend fun find(cacheKey: String): AudioSegment?
    suspend fun write(request: SynthesisRequest, result: SynthesisResult): AudioSegment
    suspend fun invalidateForCharacter(characterId: String)
    suspend fun trimTo(limitBytes: Long)
}

interface PreparationCoordinator {
    fun enqueue(bookId: String)
    fun cancel(bookId: String)
    fun observe(bookId: String): Flow<PreparationState>
}

interface LibraryRepository {
    fun observeBooks(): Flow<List<Book>>
    fun observeBook(bookId: String): Flow<Book?>
    fun observeChapters(bookId: String): Flow<List<Chapter>>
    fun observeCharacters(bookId: String): Flow<List<StoryCharacter>>
    suspend fun importBook(uri: Uri): Result<String>
    suspend fun updateVoiceAssignment(assignment: CharacterVoiceAssignment)
    suspend fun deleteBook(bookId: String)
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun update(transform: (AppSettings) -> AppSettings)
}

interface PlaybackGateway {
    val cursor: Flow<PlaybackCursor?>
    suspend fun playBook(bookId: String, chapterId: String? = null)
    suspend fun play()
    suspend fun pause()
    suspend fun seekBy(deltaMs: Long)
    suspend fun seekToPassage(passageId: String)
    suspend fun setSpeed(speed: Float)
    suspend fun setSleepTimer(minutes: Int?)
}
