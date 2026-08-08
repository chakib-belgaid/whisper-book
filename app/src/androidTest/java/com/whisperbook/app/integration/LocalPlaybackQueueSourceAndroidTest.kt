package com.whisperbook.app.integration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.whisperbook.app.data.local.db.BookEntity
import com.whisperbook.app.data.local.db.ChapterEntity
import com.whisperbook.app.data.local.db.PassageEntity
import com.whisperbook.app.data.local.db.StoryCharacterEntity
import com.whisperbook.app.data.local.db.WhisperBookDatabase
import com.whisperbook.app.domain.LocalTtsEngine
import com.whisperbook.app.domain.SynthesisRequest
import com.whisperbook.app.domain.SynthesisResult
import com.whisperbook.app.domain.model.AppSettings
import com.whisperbook.app.domain.model.BookFormat
import com.whisperbook.app.domain.model.CharacterColorRole
import com.whisperbook.app.domain.model.CharacterGender
import com.whisperbook.app.domain.model.VocalAge
import com.whisperbook.app.domain.model.VoiceDescriptor
import com.whisperbook.app.engine.audio.AppPrivateAudioSegmentStore
import java.io.File
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalPlaybackQueueSourceAndroidTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun missingNarratorAssignmentUsesThePreferredVoiceFromSettings() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, WhisperBookDatabase::class.java).build()
        val audioRoot = File(context.cacheDir, "queue-source-audio-${System.nanoTime()}")
        val tts = RecordingTtsEngine()

        try {
            database.bookDao().insert(
                BookEntity(
                    id = BOOK_ID,
                    title = "Preference test",
                    author = "Tester",
                    format = BookFormat.EPUB.name,
                    sourceUri = null,
                    privateSourcePath = null,
                    sourceSha256 = null,
                    coverPath = null,
                    currentChapterId = CHAPTER_ID,
                    currentPassageId = null,
                    progressFraction = 0f,
                    lastOpenedAtEpochMs = 1L,
                ),
            )
            database.chapterDao().insertAll(
                listOf(ChapterEntity(CHAPTER_ID, BOOK_ID, 0, "Opening")),
            )
            database.storyCharacterDao().insertAll(
                listOf(
                    StoryCharacterEntity(
                        id = NARRATOR_ID,
                        bookId = BOOK_ID,
                        displayName = "Narrator",
                        colorRole = CharacterColorRole.NARRATOR.name,
                        dialogueLineCount = 0,
                        gender = CharacterGender.UNKNOWN.name,
                    ),
                ),
            )
            database.passageDao().insertAll(
                listOf(
                    PassageEntity(
                        id = PASSAGE_ID,
                        chapterId = CHAPTER_ID,
                        ordinal = 0,
                        text = "Morning arrived over the quiet valley.",
                        speakerId = NARRATOR_ID,
                        confidence = 1f,
                        attributionRule = "narration-outside-dialogue:prose",
                    ),
                ),
            )
            assertNull(database.voiceAssignmentDao().getForCharacter(NARRATOR_ID))

            val source = LocalPlaybackQueueSource(
                database = database,
                audioStore = AppPrivateAudioSegmentStore(audioRoot),
                ttsEngineFactory = { tts },
                voices = TEST_VOICES,
                modelVersion = TEST_MODEL_VERSION,
                expectedSampleRate = TEST_SAMPLE_RATE,
                settingsFlow = flowOf(AppSettings(defaultNarratorVoiceId = "jasper")),
            )

            val queue = source.load(BOOK_ID, CHAPTER_ID).getOrThrow()

            assertEquals(CHAPTER_ID, queue.chapterId)
            assertTrue(queue.segments.isNotEmpty())
            assertEquals("jasper", database.voiceAssignmentDao().getForCharacter(NARRATOR_ID)?.voiceId)
            assertEquals(listOf("jasper"), tts.requests.map { request -> request.voice.id })
        } finally {
            database.close()
            audioRoot.deleteRecursively()
        }
    }

    private class RecordingTtsEngine : LocalTtsEngine {
        val requests = mutableListOf<SynthesisRequest>()

        override suspend fun warmUp(): Result<Unit> = Result.success(Unit)

        override suspend fun voices(): List<VoiceDescriptor> = TEST_VOICES

        override suspend fun synthesize(request: SynthesisRequest): Result<SynthesisResult> {
            requests += request
            return Result.success(
                SynthesisResult(
                    pcm16 = shortArrayOf(0, 100, 0, -100),
                    sampleRate = TEST_SAMPLE_RATE,
                    durationMs = 1L,
                ),
            )
        }

        override fun close() = Unit
    }

    private companion object {
        const val BOOK_ID = "queue-preference-book"
        const val CHAPTER_ID = "$BOOK_ID-chapter-1"
        const val NARRATOR_ID = "$BOOK_ID-character-narrator"
        const val PASSAGE_ID = "$CHAPTER_ID-passage-1"
        const val TEST_MODEL_VERSION = "queue-preference-test-v1"
        const val TEST_SAMPLE_RATE = 24_000

        val TEST_VOICES = listOf(
            VoiceDescriptor(
                id = "bella",
                displayName = "Bella",
                speakerIndex = 0,
                gender = CharacterGender.FEMALE,
                vocalAge = VocalAge.ADULT,
            ),
            VoiceDescriptor(
                id = "jasper",
                displayName = "Jasper",
                speakerIndex = 1,
                gender = CharacterGender.MALE,
                vocalAge = VocalAge.MATURE,
            ),
        )
    }
}
