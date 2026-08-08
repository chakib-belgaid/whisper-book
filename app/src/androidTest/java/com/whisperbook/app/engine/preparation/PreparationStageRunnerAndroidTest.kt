package com.whisperbook.app.engine.preparation

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.whisperbook.app.data.local.db.BookEntity
import com.whisperbook.app.data.local.db.ChapterEntity
import com.whisperbook.app.data.local.db.ChapterVoiceAssignmentEntity
import com.whisperbook.app.data.local.db.CharacterAliasEntity
import com.whisperbook.app.data.local.db.PassageEntity
import com.whisperbook.app.data.local.db.StoryCharacterEntity
import com.whisperbook.app.data.local.db.VoiceAssignmentEntity
import com.whisperbook.app.data.local.db.WhisperBookDatabase
import com.whisperbook.app.domain.AttributedPublication
import com.whisperbook.app.domain.ExtractedChapter
import com.whisperbook.app.domain.ExtractedPublication
import com.whisperbook.app.domain.ImportedBook
import com.whisperbook.app.domain.LocalTtsEngine
import com.whisperbook.app.domain.PublicationExtractor
import com.whisperbook.app.domain.SpeakerAttributor
import com.whisperbook.app.domain.SynthesisRequest
import com.whisperbook.app.domain.SynthesisResult
import com.whisperbook.app.domain.model.AppSettings
import com.whisperbook.app.domain.model.BookFormat
import com.whisperbook.app.domain.model.BuiltInCharacters
import com.whisperbook.app.domain.model.Chapter
import com.whisperbook.app.domain.model.CharacterColorRole
import com.whisperbook.app.domain.model.CharacterGender
import com.whisperbook.app.domain.model.Passage
import com.whisperbook.app.domain.model.PreparationStage
import com.whisperbook.app.domain.model.StoryCharacter
import com.whisperbook.app.domain.model.VoiceDescriptor
import com.whisperbook.app.engine.audio.AppPrivateAudioSegmentStore
import com.whisperbook.app.engine.metadata.AppPrivateCharacterMetadataCatalog
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreparationStageRunnerAndroidTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun findingCharactersAttributesOnlyOpeningChapterAndPreservesLaterState() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, WhisperBookDatabase::class.java).build()
        val metadataRoot = File(context.cacheDir, "preparation-metadata-${System.nanoTime()}")
        val metadataCatalog = AppPrivateCharacterMetadataCatalog(metadataRoot)
        val attributor = RecordingChapterAttributor()
        val existingCharacter = testCharacter()
        val existingVoice = VoiceAssignmentEntity(
            characterId = existingCharacter.id,
            voiceId = "manual-voice",
            modelVersion = "manual-model",
            speed = 0.9f,
        )
        val existingChapterVoice = ChapterVoiceAssignmentEntity(
            chapterId = CHAPTER_TWO_ID,
            characterId = existingCharacter.id,
            voiceId = "chapter-two-manual-voice",
            modelVersion = "manual-model",
            speed = 1.1f,
        )
        val laterProvisionalPassage = PassageEntity(
            id = "$CHAPTER_TWO_ID-passage-1",
            chapterId = CHAPTER_TWO_ID,
            ordinal = 0,
            text = "The later chapter must remain provisional.",
            speakerId = existingCharacter.id,
            confidence = 0f,
            attributionRule = UNATTRIBUTED_RULE,
        )

        try {
            database.bookDao().insert(testBook())
            database.chapterDao().insertAll(
                listOf(
                    ChapterEntity(CHAPTER_ONE_ID, BOOK_ID, 0, "Opening"),
                    ChapterEntity(CHAPTER_TWO_ID, BOOK_ID, 1, "Later"),
                ),
            )
            database.passageDao().insertAll(
                listOf(
                    PassageEntity(
                        id = "$CHAPTER_ONE_ID-passage-1",
                        chapterId = CHAPTER_ONE_ID,
                        ordinal = 0,
                        text = "Alice opened the door.",
                        speakerId = BuiltInCharacters.NARRATOR_ID,
                        confidence = 0f,
                        attributionRule = UNATTRIBUTED_RULE,
                    ),
                    laterProvisionalPassage,
                ),
            )
            database.storyCharacterDao().insertAll(listOf(existingCharacter))
            database.storyCharacterDao().insertAliases(
                listOf(CharacterAliasEntity(existingCharacter.id, "Alicia")),
            )
            database.voiceAssignmentDao().upsert(existingVoice)
            database.chapterVoiceAssignmentDao().upsertAll(listOf(existingChapterVoice))

            val runner = PreparationStageRunner(
                dependencies = PreparationDependencies(
                    database = database,
                    publicationExtractor = NeverUsedPublicationExtractor,
                    speakerAttributor = attributor,
                    ttsEngineFactory = LocalTtsEngineFactory { NeverUsedTtsEngine },
                    audioSegmentStore = AppPrivateAudioSegmentStore(context),
                    characterMetadataCatalog = metadataCatalog,
                    modelVersion = "test-model",
                ),
                nowEpochMs = { 123L },
            )

            runner.run(BOOK_ID, PreparationStage.FINDING_CHARACTERS, attemptCount = 0)

            assertEquals(1, attributor.calls.size)
            with(attributor.calls.single()) {
                assertEquals(CHAPTER_ONE_ID, chapterId)
                assertEquals(0, chapterOrdinal)
                assertEquals("Opening", chapter.title)
                assertEquals(listOf("Alice opened the door."), chapter.paragraphs)
                assertEquals(listOf(existingCharacter.id), knownCharacters.map { it.id })
            }

            val openingPassages = database.passageDao().getForChapter(CHAPTER_ONE_ID)
            assertEquals(1, openingPassages.size)
            assertEquals("direct-speech", openingPassages.single().attributionRule)
            assertEquals(existingCharacter.id, openingPassages.single().speakerId)

            assertEquals(
                listOf(laterProvisionalPassage),
                database.passageDao().getForChapter(CHAPTER_TWO_ID),
            )
            assertEquals(existingVoice, database.voiceAssignmentDao().getForCharacter(existingCharacter.id))
            assertEquals(
                existingChapterVoice,
                database.chapterVoiceAssignmentDao().getForChapterAndCharacter(
                    CHAPTER_TWO_ID,
                    existingCharacter.id,
                ),
            )
            assertTrue(
                database.storyCharacterDao().getEntitiesForBook(BOOK_ID)
                    .any { it.id == existingCharacter.id },
            )
            assertNotNull(
                database.storyCharacterDao().getEntitiesForBook(BOOK_ID)
                    .firstOrNull { it.id == "$BOOK_ID-character-narrator" },
            )

            val checkpoint = database.preparationJobDao().getForBook(BOOK_ID)
            assertEquals(PreparationStage.ASSIGNING_VOICES.name, checkpoint?.stage)
            assertEquals(1, checkpoint?.completedUnits)
            assertEquals(2, checkpoint?.totalUnits)
            val metadata = metadataCatalog.read(BOOK_ID)
            assertNotNull(metadata)
            assertEquals(listOf(CHAPTER_ONE_ID), metadata?.chapters?.map { it.chapterId })
            assertEquals(
                setOf(existingCharacter.id, "$BOOK_ID-character-narrator"),
                metadata?.cumulativeCharacters?.mapTo(linkedSetOf()) { it.id },
            )
            assertEquals(false, metadata?.complete)
        } finally {
            metadataRoot.deleteRecursively()
            database.close()
        }
    }

    @Test
    fun unknownGenderNarratorUsesTheDefaultNarratorVoicePreference() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, WhisperBookDatabase::class.java).build()

        try {
            database.bookDao().insert(testBook())
            database.chapterDao().insertAll(
                listOf(ChapterEntity(CHAPTER_ONE_ID, BOOK_ID, 0, "Opening")),
            )
            database.passageDao().insertAll(
                listOf(
                    provisionalPassage(
                        id = "$CHAPTER_ONE_ID-passage-1",
                        chapterId = CHAPTER_ONE_ID,
                        text = "Morning arrived.",
                    ),
                ),
            )
            val runner = PreparationStageRunner(
                dependencies = PreparationDependencies(
                    database = database,
                    publicationExtractor = NeverUsedPublicationExtractor,
                    speakerAttributor = StreamingChapterAttributor(),
                    ttsEngineFactory = LocalTtsEngineFactory { PreferenceVoiceCatalogEngine },
                    audioSegmentStore = AppPrivateAudioSegmentStore(context),
                    settingsFlow = flowOf(AppSettings(defaultNarratorVoiceId = "jasper")),
                    modelVersion = TEST_MODEL_VERSION,
                    narratorVoiceId = "bella",
                ),
            )

            runner.run(BOOK_ID, PreparationStage.FINDING_CHARACTERS, attemptCount = 0)
            val narrator = database.storyCharacterDao().getEntitiesForBook(BOOK_ID)
                .single { it.colorRole == CharacterColorRole.NARRATOR.name }
            assertEquals(CharacterGender.UNKNOWN.name, narrator.gender)

            runner.run(BOOK_ID, PreparationStage.ASSIGNING_VOICES, attemptCount = 0)

            val assignment = database.voiceAssignmentDao().getForCharacter(narrator.id)
            assertEquals("jasper", assignment?.voiceId)
        } finally {
            database.close()
        }
    }

    @Test
    fun tinyBookStreamsAttributionMetadataVoicesAndDurableAudioChapterByChapter() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, WhisperBookDatabase::class.java).build()
        val testRoot = File(context.cacheDir, "preparation-pipeline-${System.nanoTime()}")
        check(testRoot.mkdirs())
        val audioStore = AppPrivateAudioSegmentStore(File(testRoot, "audio"))
        val metadataCatalog = AppPrivateCharacterMetadataCatalog(
            root = File(testRoot, "metadata"),
            nowEpochMs = { 456L },
        )
        val attributor = StreamingChapterAttributor()
        val tts = RecordingTtsEngine()

        try {
            database.bookDao().insert(testBook())
            database.chapterDao().insertAll(
                listOf(
                    ChapterEntity(CHAPTER_ONE_ID, BOOK_ID, 0, "Opening"),
                    ChapterEntity(CHAPTER_TWO_ID, BOOK_ID, 1, "Meeting Bob"),
                ),
            )
            database.passageDao().insertAll(
                listOf(
                    provisionalPassage(
                        id = "$CHAPTER_ONE_ID-passage-1",
                        chapterId = CHAPTER_ONE_ID,
                        text = "Morning arrived.",
                    ),
                    provisionalPassage(
                        id = "$CHAPTER_TWO_ID-passage-1",
                        chapterId = CHAPTER_TWO_ID,
                        text = "Bob said hello.",
                    ),
                ),
            )
            val runner = PreparationStageRunner(
                dependencies = PreparationDependencies(
                    database = database,
                    publicationExtractor = NeverUsedPublicationExtractor,
                    speakerAttributor = attributor,
                    ttsEngineFactory = LocalTtsEngineFactory { tts },
                    audioSegmentStore = audioStore,
                    characterMetadataCatalog = metadataCatalog,
                    modelVersion = TEST_MODEL_VERSION,
                    expectedSampleRate = TEST_SAMPLE_RATE,
                    narratorVoiceId = "bella",
                ),
                nowEpochMs = { 456L },
            )

            runner.run(BOOK_ID, PreparationStage.FINDING_CHARACTERS, attemptCount = 0)
            runner.run(BOOK_ID, PreparationStage.ASSIGNING_VOICES, attemptCount = 0)
            runner.run(BOOK_ID, PreparationStage.PREPARING_AUDIO, attemptCount = 0)

            assertEquals(
                listOf(CHAPTER_ONE_ID, CHAPTER_TWO_ID),
                attributor.calls.map { it.chapterId },
            )
            assertEquals(listOf(0, 1), attributor.calls.map { it.chapterOrdinal })

            listOf(CHAPTER_ONE_ID, CHAPTER_TWO_ID).forEach { chapterId ->
                val passages = database.passageDao().getForChapter(chapterId)
                assertEquals(1, passages.size)
                assertTrue(passages.none { it.attributionRule == UNATTRIBUTED_RULE })
                val segments = database.audioSegmentDao().observeForPassage(passages.single().id).first()
                assertEquals(1, segments.size)
                assertEquals("READY", segments.single().state)
                assertTrue(segments.single().path?.let(::File)?.isFile == true)
            }

            val characters = database.storyCharacterDao().getEntitiesForBook(BOOK_ID)
            val assignments = database.voiceAssignmentDao().getForCharacters(characters.map { it.id })
            assertEquals(characters.map { it.id }.toSet(), assignments.map { it.characterId }.toSet())
            assertTrue(assignments.all { it.modelVersion == TEST_MODEL_VERSION })

            val metadata = requireNotNull(metadataCatalog.read(BOOK_ID))
            assertEquals(listOf(CHAPTER_ONE_ID, CHAPTER_TWO_ID), metadata.chapters.map { it.chapterId })
            assertTrue(metadata.complete)
            assertTrue(metadataCatalog.metadataFile(BOOK_ID).isFile)

            assertEquals(2, tts.synthesisRequests.size)
            assertEquals(PreparationStage.READY.name, database.preparationJobDao().getForBook(BOOK_ID)?.stage)

            // The JSON file is derived data. A later-ordinal regeneration must repair a missing
            // catalog from every attributed chapter without rescanning or resynthesizing them.
            assertTrue(metadataCatalog.delete(BOOK_ID))
            runner.run(
                BOOK_ID,
                PreparationStage.PREPARING_AUDIO,
                attemptCount = 1,
                fromChapterOrdinal = 1,
            )
            val repairedMetadata = requireNotNull(metadataCatalog.read(BOOK_ID))
            assertEquals(
                listOf(CHAPTER_ONE_ID, CHAPTER_TWO_ID),
                repairedMetadata.chapters.map { it.chapterId },
            )
            assertEquals(
                setOf("$BOOK_ID-character-narrator"),
                repairedMetadata.chapters.single { it.chapterId == CHAPTER_ONE_ID }
                    .contributions.mapTo(linkedSetOf()) { it.characterId },
            )
            assertEquals(
                setOf("$BOOK_ID-character-bob"),
                repairedMetadata.chapters.single { it.chapterId == CHAPTER_TWO_ID }
                    .contributions.mapTo(linkedSetOf()) { it.characterId },
            )
            assertTrue(repairedMetadata.complete)
            assertEquals(2, attributor.calls.size)
            assertEquals(2, tts.synthesisRequests.size)
        } finally {
            database.close()
            testRoot.deleteRecursively()
        }
    }

    private fun testBook() = BookEntity(
        id = BOOK_ID,
        title = "Large book",
        author = "Tester",
        format = BookFormat.EPUB.name,
        sourceUri = null,
        privateSourcePath = "/private/large-book.epub",
        sourceSha256 = "large-book-hash",
        coverPath = null,
        currentChapterId = CHAPTER_ONE_ID,
        currentPassageId = null,
        progressFraction = 0f,
        lastOpenedAtEpochMs = 1L,
    )

    private fun testCharacter() = StoryCharacterEntity(
        id = "$BOOK_ID-character-alice",
        bookId = BOOK_ID,
        displayName = "Alice",
        colorRole = CharacterColorRole.BLUE.name,
        dialogueLineCount = 1,
    )

    private fun provisionalPassage(
        id: String,
        chapterId: String,
        text: String,
    ) = PassageEntity(
        id = id,
        chapterId = chapterId,
        ordinal = 0,
        text = text,
        speakerId = BuiltInCharacters.NARRATOR_ID,
        confidence = 0f,
        attributionRule = UNATTRIBUTED_RULE,
    )

    private class RecordingChapterAttributor : SpeakerAttributor {
        val calls = mutableListOf<ChapterCall>()

        override suspend fun attribute(
            bookId: String,
            publication: ExtractedPublication,
        ): AttributedPublication = error("Whole-publication attribution must not run")

        override suspend fun attributeChapter(
            bookId: String,
            chapterId: String,
            chapterOrdinal: Int,
            chapter: ExtractedChapter,
            knownCharacters: List<StoryCharacter>,
        ): AttributedPublication {
            calls += ChapterCall(chapterId, chapterOrdinal, chapter, knownCharacters)
            val alice = knownCharacters.single { it.displayName == "Alice" }
            return AttributedPublication(
                chapters = listOf(
                    Chapter(
                        id = chapterId,
                        bookId = bookId,
                        ordinal = chapterOrdinal,
                        title = chapter.title,
                        passages = listOf(
                            Passage(
                                id = "$chapterId-attributed-passage-1",
                                chapterId = chapterId,
                                ordinal = 0,
                                text = chapter.paragraphs.single(),
                                speakerId = alice.id,
                                confidence = 0.95f,
                                attributionRule = "direct-speech",
                            ),
                        ),
                    ),
                ),
                characters = listOf(
                    alice,
                    StoryCharacter(
                        id = BuiltInCharacters.NARRATOR_ID,
                        bookId = bookId,
                        displayName = "Narrator",
                        aliases = emptySet(),
                        colorRole = CharacterColorRole.NARRATOR,
                        dialogueLineCount = 0,
                    ),
                ),
            )
        }
    }

    private class StreamingChapterAttributor : SpeakerAttributor {
        val calls = mutableListOf<ChapterCall>()

        override suspend fun attribute(
            bookId: String,
            publication: ExtractedPublication,
        ): AttributedPublication = error("Whole-publication attribution must not run")

        override suspend fun attributeChapter(
            bookId: String,
            chapterId: String,
            chapterOrdinal: Int,
            chapter: ExtractedChapter,
            knownCharacters: List<StoryCharacter>,
        ): AttributedPublication {
            calls += ChapterCall(chapterId, chapterOrdinal, chapter, knownCharacters)
            val speaker = if (chapterOrdinal == 0) {
                StoryCharacter(
                    id = BuiltInCharacters.NARRATOR_ID,
                    bookId = bookId,
                    displayName = "Narrator",
                    aliases = setOf("Narrator"),
                    colorRole = CharacterColorRole.NARRATOR,
                    dialogueLineCount = 0,
                )
            } else {
                StoryCharacter(
                    id = "$bookId-character-bob",
                    bookId = bookId,
                    displayName = "Bob",
                    aliases = setOf("Bob"),
                    colorRole = CharacterColorRole.BLUE,
                    dialogueLineCount = 1,
                )
            }
            return AttributedPublication(
                chapters = listOf(
                    Chapter(
                        id = chapterId,
                        bookId = bookId,
                        ordinal = chapterOrdinal,
                        title = chapter.title,
                        passages = listOf(
                            Passage(
                                id = "$chapterId-attributed-passage-1",
                                chapterId = chapterId,
                                ordinal = 0,
                                text = chapter.paragraphs.single(),
                                speakerId = speaker.id,
                                confidence = 0.95f,
                                attributionRule = if (chapterOrdinal == 0) "narration" else "direct-speech",
                            ),
                        ),
                    ),
                ),
                characters = knownCharacters + speaker,
            )
        }
    }

    private data class ChapterCall(
        val chapterId: String,
        val chapterOrdinal: Int,
        val chapter: ExtractedChapter,
        val knownCharacters: List<StoryCharacter>,
    )

    private object NeverUsedPublicationExtractor : PublicationExtractor {
        override suspend fun extract(book: ImportedBook): Result<ExtractedPublication> =
            error("Publication extraction must not run")
    }

    private object NeverUsedTtsEngine : LocalTtsEngine {
        override suspend fun warmUp(): Result<Unit> = error("TTS must not run")
        override suspend fun voices(): List<VoiceDescriptor> = error("TTS must not run")
        override suspend fun synthesize(request: SynthesisRequest): Result<SynthesisResult> =
            error("TTS must not run")

        override fun close() = Unit
    }

    private object PreferenceVoiceCatalogEngine : LocalTtsEngine {
        override suspend fun warmUp(): Result<Unit> = error("TTS warm-up must not run")

        override suspend fun voices(): List<VoiceDescriptor> = listOf(
            VoiceDescriptor(
                id = "bella",
                displayName = "Bella",
                speakerIndex = 0,
                gender = CharacterGender.FEMALE,
            ),
            VoiceDescriptor(
                id = "jasper",
                displayName = "Jasper",
                speakerIndex = 1,
                gender = CharacterGender.MALE,
            ),
        )

        override suspend fun synthesize(request: SynthesisRequest): Result<SynthesisResult> =
            error("TTS synthesis must not run")

        override fun close() = Unit
    }

    private class RecordingTtsEngine : LocalTtsEngine {
        val synthesisRequests = mutableListOf<SynthesisRequest>()

        override suspend fun warmUp(): Result<Unit> = Result.success(Unit)

        override suspend fun voices(): List<VoiceDescriptor> = listOf(
            VoiceDescriptor(id = "bella", displayName = "Bella", speakerIndex = 0),
            VoiceDescriptor(id = "leo", displayName = "Leo", speakerIndex = 1),
        )

        override suspend fun synthesize(request: SynthesisRequest): Result<SynthesisResult> {
            synthesisRequests += request
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
        const val BOOK_ID = "large-book"
        const val CHAPTER_ONE_ID = "$BOOK_ID-chapter-1"
        const val CHAPTER_TWO_ID = "$BOOK_ID-chapter-2"
        const val UNATTRIBUTED_RULE = "preparation-unattributed"
        const val TEST_MODEL_VERSION = "test-model"
        const val TEST_SAMPLE_RATE = 24_000
    }
}
