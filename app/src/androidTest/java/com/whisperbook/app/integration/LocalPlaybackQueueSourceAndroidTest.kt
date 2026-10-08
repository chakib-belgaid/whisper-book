package com.whisperbook.app.integration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.whisperbook.app.data.local.db.BookEntity
import com.whisperbook.app.data.local.db.ChapterVoiceAssignmentEntity
import com.whisperbook.app.data.local.db.ChapterEntity
import com.whisperbook.app.data.local.db.PassageEntity
import com.whisperbook.app.data.local.db.PlaybackCheckpointEntity
import com.whisperbook.app.data.local.db.StoryCharacterEntity
import com.whisperbook.app.data.local.db.VoiceAssignmentEntity
import com.whisperbook.app.data.local.db.WhisperBookDatabase
import com.whisperbook.app.domain.LocalTtsEngine
import com.whisperbook.app.domain.SynthesisRequest
import com.whisperbook.app.domain.SynthesisResult
import com.whisperbook.app.domain.model.AppSettings
import com.whisperbook.app.domain.model.AudioSegmentState
import com.whisperbook.app.domain.model.BookFormat
import com.whisperbook.app.domain.model.CharacterColorRole
import com.whisperbook.app.domain.model.CharacterGender
import com.whisperbook.app.domain.model.VocalAge
import com.whisperbook.app.domain.model.VoiceDescriptor
import com.whisperbook.app.engine.audio.AppPrivateAudioSegmentStore
import com.whisperbook.app.engine.audio.LocalAudioGenerationCoordinator
import java.io.File
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalPlaybackQueueSourceAndroidTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun incompatibleChapterVoiceIsRejectedBeforeSynthesis() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, WhisperBookDatabase::class.java).build()
        val audioRoot = File(context.cacheDir, "incompatible-voice-audio-${System.nanoTime()}")
        val tts = RecordingTtsEngine()
        try {
            database.bookDao().insert(bookEntity(BOOK_A, CHAPTER_A1, "en"))
            database.chapterDao().insertAll(listOf(ChapterEntity(CHAPTER_A1, BOOK_A, 0, "Opening")))
            insertChapterPlan(database, listOf(PlannedChapter(CHAPTER_A1, BOOK_A, true, 0)))
            database.storyCharacterDao().insertAll(listOf(narrator(BOOK_A, NARRATOR_A)))
            database.voiceAssignmentDao().upsert(
                VoiceAssignmentEntity(NARRATOR_A, "english", TEST_MODEL_VERSION, 1f),
            )
            database.passageDao().insertAll(
                listOf(passage(PASSAGE_A1, CHAPTER_A1, NARRATOR_A, "An English paragraph")),
            )
            database.chapterVoiceAssignmentDao().upsertAll(
                listOf(chapterVoice(BOOK_A, CHAPTER_A1, NARRATOR_A, "french")),
            )
            val source = LocalPlaybackQueueSource(
                database = database,
                audioStore = AppPrivateAudioSegmentStore(audioRoot),
                ttsEngineFactory = { tts },
                voices = listOf(
                    VoiceDescriptor("english", "English voice", 0, supportedLanguageCodes = setOf("en")),
                    VoiceDescriptor("french", "French voice", 1, supportedLanguageCodes = setOf("fr")),
                ),
                modelVersion = TEST_MODEL_VERSION,
                expectedSampleRate = TEST_SAMPLE_RATE,
            )

            val result = source.load(BOOK_A, CHAPTER_A1)

            assertTrue(result.isFailure)
            assertTrue(
                result.exceptionOrNull()?.message.orEmpty()
                    .contains("unavailable for this book's language"),
            )
            assertTrue(tts.requests.isEmpty())
        } finally {
            database.close()
            audioRoot.deleteRecursively()
        }
    }

    @Test
    fun chapterSnapshotsKeepBookLanguagesAndCastsIsolatedAcrossRestart() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, WhisperBookDatabase::class.java).build()
        val audioRoot = File(context.cacheDir, "queue-source-audio-${System.nanoTime()}")
        val tts = RecordingTtsEngine()

        try {
            database.bookDao().insert(bookEntity(BOOK_A, CHAPTER_A1, "en"))
            database.bookDao().insert(bookEntity(BOOK_B, CHAPTER_B1, "fr"))
            database.chapterDao().insertAll(
                listOf(
                    ChapterEntity(CHAPTER_A1, BOOK_A, 0, "A One"),
                    ChapterEntity(CHAPTER_A2, BOOK_A, 1, "A Two"),
                    ChapterEntity(CHAPTER_B1, BOOK_B, 0, "B One"),
                ),
            )
            insertChapterPlan(
                database,
                listOf(
                    PlannedChapter(CHAPTER_A1, BOOK_A, true, 0),
                    PlannedChapter(CHAPTER_A2, BOOK_A, true, 1),
                    PlannedChapter(CHAPTER_B1, BOOK_B, true, 0),
                ),
            )
            database.storyCharacterDao().insertAll(
                listOf(
                    narrator(BOOK_A, NARRATOR_A),
                    narrator(BOOK_B, NARRATOR_B),
                ),
            )
            database.voiceAssignmentDao().upsert(
                VoiceAssignmentEntity(NARRATOR_A, "bella", TEST_MODEL_VERSION, 1f),
            )
            database.voiceAssignmentDao().upsert(
                VoiceAssignmentEntity(NARRATOR_B, "jasper", TEST_MODEL_VERSION, 1f),
            )
            database.passageDao().insertAll(
                listOf(
                    passage(PASSAGE_A1, CHAPTER_A1, NARRATOR_A, "A one"),
                    passage(PASSAGE_A2, CHAPTER_A2, NARRATOR_A, "A two"),
                    passage(PASSAGE_B1, CHAPTER_B1, NARRATOR_B, "B one"),
                ),
            )
            database.chapterVoiceAssignmentDao().upsertAll(
                listOf(
                    chapterVoice(BOOK_A, CHAPTER_A1, NARRATOR_A, "jasper"),
                    chapterVoice(BOOK_A, CHAPTER_A2, NARRATOR_A, "bella"),
                    chapterVoice(BOOK_B, CHAPTER_B1, NARRATOR_B, "bella"),
                ),
            )

            val source = LocalPlaybackQueueSource(
                database = database,
                audioStore = AppPrivateAudioSegmentStore(audioRoot),
                ttsEngineFactory = { tts },
                voices = TEST_VOICES,
                modelVersion = TEST_MODEL_VERSION,
                expectedSampleRate = TEST_SAMPLE_RATE,
                settingsFlow = flowOf(AppSettings()),
            )

            assertEquals(CHAPTER_A1, source.load(BOOK_A, CHAPTER_A1).getOrThrow().chapterId)
            assertEquals(CHAPTER_A2, source.load(BOOK_A, CHAPTER_A2).getOrThrow().chapterId)
            assertEquals(CHAPTER_B1, source.load(BOOK_B, CHAPTER_B1).getOrThrow().chapterId)

            assertEquals(listOf("jasper", "bella", "bella"), tts.requests.map { it.voice.id })
            assertEquals(listOf("en", "en", "fr"), tts.requests.map { it.languageCode })

            audioRoot.listFiles().orEmpty().forEach { it.delete() }
            val restartedTts = RecordingTtsEngine()
            val restartedSource = LocalPlaybackQueueSource(
                database = database,
                audioStore = AppPrivateAudioSegmentStore(audioRoot),
                ttsEngineFactory = { restartedTts },
                voices = TEST_VOICES,
                modelVersion = TEST_MODEL_VERSION,
                expectedSampleRate = TEST_SAMPLE_RATE,
                settingsFlow = flowOf(AppSettings()),
            )
            val returnedToA = restartedSource.load(BOOK_A, CHAPTER_A1).getOrThrow()

            assertTrue(returnedToA.segments.isNotEmpty())
            assertEquals(listOf("jasper"), restartedTts.requests.map { it.voice.id })
            assertEquals(listOf("en"), restartedTts.requests.map { it.languageCode })
            assertEquals("bella", database.voiceAssignmentDao().getForCharacter(NARRATOR_A)?.voiceId)
        } finally {
            database.close()
            audioRoot.deleteRecursively()
        }
    }

    @Test
    fun largeCustomChapterUsesOneFrozenCastForEveryParagraphAndMicrochunk() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, WhisperBookDatabase::class.java).build()
        val audioRoot = File(context.cacheDir, "large-chapter-audio-${System.nanoTime()}")
        val tts = RecordingTtsEngine()
        try {
            database.bookDao().insert(bookEntity(BOOK_A, CHAPTER_A1, "en"))
            database.chapterDao().insertAll(listOf(ChapterEntity(CHAPTER_A1, BOOK_A, 0, "Large")))
            insertChapterPlan(database, listOf(PlannedChapter(CHAPTER_A1, BOOK_A, true, 0)))
            database.storyCharacterDao().insertAll(listOf(narrator(BOOK_A, NARRATOR_A)))
            database.voiceAssignmentDao().upsert(
                VoiceAssignmentEntity(NARRATOR_A, "bella", TEST_MODEL_VERSION, 1f),
            )
            database.passageDao().insertAll(
                (0 until 251).map { ordinal ->
                    PassageEntity(
                        id = "$CHAPTER_A1-passage-$ordinal",
                        chapterId = CHAPTER_A1,
                        ordinal = ordinal,
                        text = "Paragraph $ordinal stays in the same custom chapter voice set.",
                        speakerId = NARRATOR_A,
                        confidence = 1f,
                        attributionRule = "narration-outside-dialogue:prose",
                    )
                },
            )
            database.chapterVoiceAssignmentDao().upsertAll(
                listOf(chapterVoice(BOOK_A, CHAPTER_A1, NARRATOR_A, "jasper")),
            )
            val source = LocalPlaybackQueueSource(
                database = database,
                audioStore = AppPrivateAudioSegmentStore(audioRoot),
                ttsEngineFactory = { tts },
                voices = TEST_VOICES,
                modelVersion = TEST_MODEL_VERSION,
                expectedSampleRate = TEST_SAMPLE_RATE,
                settingsFlow = flowOf(AppSettings(narrationChunkChars = 80)),
            )

            val queue = source.load(BOOK_A, CHAPTER_A1).getOrThrow()

            assertEquals(251, queue.segments.size)
            assertEquals(251, tts.requests.size)
            assertTrue(tts.requests.all { it.voice.id == "jasper" && it.languageCode == "en" })
        } finally {
            database.close()
            audioRoot.deleteRecursively()
        }
    }

    @Test
    fun progressiveGenerationLetsBackgroundPreparationRunBetweenSegments() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, WhisperBookDatabase::class.java).build()
        val audioRoot = File(context.cacheDir, "progressive-fairness-audio-${System.nanoTime()}")
        var backgroundRanBetweenSegments = false
        var backgroundJob: Job? = null
        val tts = RecordingTtsEngine { requestNumber ->
            if (requestNumber == 1) {
                // Register background preparation while the first playback segment still owns the
                // model. The second segment is requested immediately after this synthesis returns.
                backgroundJob = launch(start = CoroutineStart.UNDISPATCHED) {
                    LocalAudioGenerationCoordinator.runBackground {
                        backgroundRanBetweenSegments = true
                    }
                }
            } else if (requestNumber == 2) {
                assertTrue(backgroundRanBetweenSegments)
            }
        }
        try {
            database.bookDao().insert(bookEntity(BOOK_A, CHAPTER_A1, "en"))
            database.chapterDao().insertAll(listOf(ChapterEntity(CHAPTER_A1, BOOK_A, 0, "Opening")))
            insertChapterPlan(database, listOf(PlannedChapter(CHAPTER_A1, BOOK_A, true, 0)))
            database.storyCharacterDao().insertAll(listOf(narrator(BOOK_A, NARRATOR_A)))
            database.voiceAssignmentDao().upsert(
                VoiceAssignmentEntity(NARRATOR_A, "bella", TEST_MODEL_VERSION, 1f),
            )
            database.passageDao().insertAll(
                listOf(
                    passage(PASSAGE_A1, CHAPTER_A1, NARRATOR_A, "First paragraph"),
                    passage("$CHAPTER_A1-passage-2", CHAPTER_A1, NARRATOR_A, "Second paragraph")
                        .copy(ordinal = 1),
                ),
            )
            database.chapterVoiceAssignmentDao().upsertAll(
                listOf(chapterVoice(BOOK_A, CHAPTER_A1, NARRATOR_A, "jasper")),
            )
            val source = LocalPlaybackQueueSource(
                database = database,
                audioStore = AppPrivateAudioSegmentStore(audioRoot),
                ttsEngineFactory = { tts },
                voices = TEST_VOICES,
                modelVersion = TEST_MODEL_VERSION,
                expectedSampleRate = TEST_SAMPLE_RATE,
            )
            val queue = withTimeout(5_000L) {
                source.loadProgressively(BOOK_A, CHAPTER_A1) { _, _, _ -> }.getOrThrow()
            }
            backgroundJob?.join()

            assertTrue(backgroundRanBetweenSegments)
            assertEquals(2, queue.segments.size)
            assertEquals(2, tts.requests.size)
        } finally {
            database.close()
            audioRoot.deleteRecursively()
        }
    }

    @Test
    fun staleGenerationCannotPersistAfterNarrationProfileRevisionChanges() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, WhisperBookDatabase::class.java).build()
        val audioRoot = File(context.cacheDir, "stale-generation-audio-${System.nanoTime()}")
        val tts = DelayedTtsEngine()
        try {
            database.bookDao().insert(bookEntity(BOOK_A, CHAPTER_A1, "en"))
            database.chapterDao().insertAll(listOf(ChapterEntity(CHAPTER_A1, BOOK_A, 0, "Opening")))
            insertChapterPlan(database, listOf(PlannedChapter(CHAPTER_A1, BOOK_A, true, 0)))
            database.storyCharacterDao().insertAll(listOf(narrator(BOOK_A, NARRATOR_A)))
            database.voiceAssignmentDao().upsert(
                VoiceAssignmentEntity(NARRATOR_A, "bella", TEST_MODEL_VERSION, 1f),
            )
            database.passageDao().insertAll(
                listOf(passage(PASSAGE_A1, CHAPTER_A1, NARRATOR_A, "A delayed paragraph")),
            )
            database.chapterVoiceAssignmentDao().upsertAll(
                listOf(chapterVoice(BOOK_A, CHAPTER_A1, NARRATOR_A, "jasper")),
            )
            val source = LocalPlaybackQueueSource(
                database = database,
                audioStore = AppPrivateAudioSegmentStore(audioRoot),
                ttsEngineFactory = { tts },
                voices = TEST_VOICES,
                modelVersion = TEST_MODEL_VERSION,
                expectedSampleRate = TEST_SAMPLE_RATE,
            )
            val loading = async(Dispatchers.Default) { source.load(BOOK_A, CHAPTER_A1) }
            val staleRequest = tts.started.await()

            assertEquals(1, database.bookDao().updateNarrationLanguage(BOOK_A, "fr"))
            tts.result.complete(testSynthesisResult())

            var cancelled = false
            try {
                loading.await()
            } catch (_: CancellationException) {
                cancelled = true
            }
            assertTrue("A stale profile generation must be cancelled", cancelled)
            val staleRow = database.audioSegmentDao().findByCacheKey(staleRequest.cacheKey)
            assertEquals(AudioSegmentState.PENDING.name, staleRow?.state)
            assertTrue(audioRoot.listFiles().orEmpty().none { it.extension == "wav" })
        } finally {
            database.close()
            audioRoot.deleteRecursively()
        }
    }

    @Test
    fun selectedCustomOrderControlsRequestedChapterAndContinuation() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, WhisperBookDatabase::class.java).build()
        val audioRoot = File(context.cacheDir, "selected-order-audio-${System.nanoTime()}")
        try {
            setupFiveChapterBook(database, currentChapterId = chapterId(2))
            val source = queueSource(database, audioRoot)

            // Chapter 2 is skipped. Initial and explicit requests fall forward to the next
            // selected chapter in the user's retained custom order.
            assertEquals(chapterId(1), source.load(BOOK_A, null).getOrThrow().chapterId)
            assertEquals(chapterId(1), source.load(BOOK_A, chapterId(2)).getOrThrow().chapterId)

            database.bookDao().updateProgress(
                bookId = BOOK_A,
                chapterId = null,
                passageId = null,
                progressFraction = 0f,
                openedAtEpochMs = 2L,
            )
            assertEquals(chapterId(5), source.load(BOOK_A, null).getOrThrow().chapterId)

            // The authoritative selected order is 5, 1, 3; chapters 2 and 4 never enter it.
            assertEquals(chapterId(1), source.loadNext(BOOK_A, chapterId(5)).getOrThrow()?.chapterId)
            assertEquals(chapterId(3), source.loadNext(BOOK_A, chapterId(1)).getOrThrow()?.chapterId)
            assertNull(source.loadNext(BOOK_A, chapterId(3)).getOrThrow())
            assertEquals(chapterId(1), source.loadNext(BOOK_A, chapterId(2)).getOrThrow()?.chapterId)
            assertNull(source.loadNext(BOOK_A, chapterId(4)).getOrThrow())
        } finally {
            database.close()
            audioRoot.deleteRecursively()
        }
    }

    @Test
    fun skippedCheckpointFallsForwardAndAllSkippedFailsClearly() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, WhisperBookDatabase::class.java).build()
        val audioRoot = File(context.cacheDir, "checkpoint-fallback-audio-${System.nanoTime()}")
        try {
            setupFiveChapterBook(database, currentChapterId = null)
            database.playbackCheckpointDao().upsert(
                PlaybackCheckpointEntity(
                    bookId = BOOK_A,
                    chapterId = chapterId(4),
                    passageId = passageId(4),
                    segmentId = "obsolete-segment",
                    segmentPositionMs = 400L,
                    chapterPositionMs = 400L,
                    chapterDurationMs = 1_000L,
                    isPlaying = false,
                    speed = 1f,
                    updatedAtEpochMs = 1L,
                ),
            )
            val source = queueSource(database, audioRoot)

            // Chapter 4 is skipped at the end of the custom plan, so resume wraps to the
            // first selected chapter rather than restoring an excluded checkpoint.
            assertEquals(chapterId(5), source.load(BOOK_A, null).getOrThrow().chapterId)

            database.openHelper.writableDatabase.execSQL(
                "UPDATE chapter_plan_entries SET is_selected = 0 WHERE book_id = ?",
                arrayOf(BOOK_A),
            )
            val failure = source.load(BOOK_A, null).exceptionOrNull()
            assertEquals("Select at least one chapter before listening", failure?.message)
        } finally {
            database.close()
            audioRoot.deleteRecursively()
        }
    }

    private suspend fun setupFiveChapterBook(
        database: WhisperBookDatabase,
        currentChapterId: String?,
    ) {
        database.bookDao().insert(bookEntity(BOOK_A, currentChapterId, "en"))
        val chapters = (1..5).map { number ->
            ChapterEntity(chapterId(number), BOOK_A, number - 1, "Chapter $number")
        }
        database.chapterDao().insertAll(chapters)
        database.storyCharacterDao().insertAll(listOf(narrator(BOOK_A, NARRATOR_A)))
        database.voiceAssignmentDao().upsert(
            VoiceAssignmentEntity(NARRATOR_A, "bella", TEST_MODEL_VERSION, 1f),
        )
        database.passageDao().insertAll(
            (1..5).map { number ->
                passage(passageId(number), chapterId(number), NARRATOR_A, "Chapter $number text")
            },
        )
        database.chapterVoiceAssignmentDao().upsertAll(
            (1..5).map { number -> chapterVoice(BOOK_A, chapterId(number), NARRATOR_A, "bella") },
        )
        insertChapterPlan(
            database,
            listOf(
                PlannedChapter(chapterId(5), BOOK_A, true, 0),
                PlannedChapter(chapterId(2), BOOK_A, false, 1),
                PlannedChapter(chapterId(1), BOOK_A, true, 2),
                PlannedChapter(chapterId(3), BOOK_A, true, 3),
                PlannedChapter(chapterId(4), BOOK_A, false, 4),
            ),
        )
    }

    private fun queueSource(
        database: WhisperBookDatabase,
        audioRoot: File,
    ) = LocalPlaybackQueueSource(
        database = database,
        audioStore = AppPrivateAudioSegmentStore(audioRoot),
        ttsEngineFactory = { RecordingTtsEngine() },
        voices = TEST_VOICES,
        modelVersion = TEST_MODEL_VERSION,
        expectedSampleRate = TEST_SAMPLE_RATE,
    )

    private fun insertChapterPlan(
        database: WhisperBookDatabase,
        chapters: List<PlannedChapter>,
    ) {
        chapters.forEach { chapter ->
            database.openHelper.writableDatabase.execSQL(
                """
                INSERT INTO chapter_plan_entries (
                    book_id, chapter_id, is_selected, custom_position, updated_at
                ) VALUES (?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    chapter.bookId,
                    chapter.chapterId,
                    if (chapter.selected) 1 else 0,
                    chapter.position,
                    1L,
                ),
            )
        }
    }

    private fun chapterId(number: Int) = "$BOOK_A-chapter-$number"

    private fun passageId(number: Int) = "${chapterId(number)}-passage"

    private fun bookEntity(id: String, chapterId: String?, language: String) = BookEntity(
        id = id,
        title = "Book $id",
        author = "Tester",
        format = BookFormat.EPUB.name,
        sourceUri = null,
        privateSourcePath = null,
        sourceSha256 = null,
        coverPath = null,
        currentChapterId = chapterId,
        currentPassageId = null,
        progressFraction = 0f,
        lastOpenedAtEpochMs = 1L,
        narrationLanguageCode = language,
    )

    private fun narrator(bookId: String, characterId: String) = StoryCharacterEntity(
        id = characterId,
        bookId = bookId,
        displayName = "Narrator",
        colorRole = CharacterColorRole.NARRATOR.name,
        dialogueLineCount = 0,
        gender = CharacterGender.UNKNOWN.name,
    )

    private fun passage(id: String, chapterId: String, speakerId: String, text: String) =
        PassageEntity(
            id = id,
            chapterId = chapterId,
            ordinal = 0,
            text = text,
            speakerId = speakerId,
            confidence = 1f,
            attributionRule = "narration-outside-dialogue:prose",
        )

    private fun chapterVoice(bookId: String, chapterId: String, characterId: String, voiceId: String) =
        ChapterVoiceAssignmentEntity(
            bookId = bookId,
            chapterId = chapterId,
            characterId = characterId,
            voiceId = voiceId,
            modelVersion = TEST_MODEL_VERSION,
            speed = 1f,
        )

    private class RecordingTtsEngine(
        private val onSynthesize: suspend (requestNumber: Int) -> Unit = {},
    ) : LocalTtsEngine {
        val requests = mutableListOf<SynthesisRequest>()

        override suspend fun warmUp(): Result<Unit> = Result.success(Unit)

        override suspend fun voices(): List<VoiceDescriptor> = TEST_VOICES

        override suspend fun synthesize(request: SynthesisRequest): Result<SynthesisResult> {
            requests += request
            onSynthesize(requests.size)
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

    private class DelayedTtsEngine : LocalTtsEngine {
        val started = CompletableDeferred<SynthesisRequest>()
        val result = CompletableDeferred<SynthesisResult>()

        override suspend fun warmUp(): Result<Unit> = Result.success(Unit)
        override suspend fun voices(): List<VoiceDescriptor> = TEST_VOICES
        override suspend fun synthesize(request: SynthesisRequest): Result<SynthesisResult> {
            started.complete(request)
            return Result.success(result.await())
        }
        override fun close() = Unit
    }

    private fun testSynthesisResult() = SynthesisResult(
        pcm16 = shortArrayOf(0, 100, 0, -100),
        sampleRate = TEST_SAMPLE_RATE,
        durationMs = 1L,
    )

    private data class PlannedChapter(
        val chapterId: String,
        val bookId: String,
        val selected: Boolean,
        val position: Int,
    )

    private companion object {
        const val BOOK_A = "queue-book-a"
        const val BOOK_B = "queue-book-b"
        const val CHAPTER_A1 = "$BOOK_A-chapter-1"
        const val CHAPTER_A2 = "$BOOK_A-chapter-2"
        const val CHAPTER_B1 = "$BOOK_B-chapter-1"
        const val NARRATOR_A = "$BOOK_A-character-narrator"
        const val NARRATOR_B = "$BOOK_B-character-narrator"
        const val PASSAGE_A1 = "$CHAPTER_A1-passage-1"
        const val PASSAGE_A2 = "$CHAPTER_A2-passage-1"
        const val PASSAGE_B1 = "$CHAPTER_B1-passage-1"
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
