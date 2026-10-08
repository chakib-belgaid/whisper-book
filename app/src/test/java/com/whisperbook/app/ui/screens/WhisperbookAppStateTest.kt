package com.whisperbook.app.ui.screens

import com.whisperbook.app.R
import com.whisperbook.app.domain.NarrationTextChunker
import com.whisperbook.app.domain.model.BuiltInCharacters
import com.whisperbook.app.domain.model.AppSettings
import com.whisperbook.app.domain.model.Book
import com.whisperbook.app.domain.model.BookFormat
import com.whisperbook.app.domain.model.Chapter
import com.whisperbook.app.domain.model.ChapterPlanEntry
import com.whisperbook.app.domain.model.CharacterColorRole
import com.whisperbook.app.domain.model.CharacterVoiceAssignment
import com.whisperbook.app.domain.model.NarrationLanguage
import com.whisperbook.app.domain.model.Passage
import com.whisperbook.app.domain.model.PlaybackCursor
import com.whisperbook.app.domain.model.PreparationRunState
import com.whisperbook.app.domain.model.PreparationStage
import com.whisperbook.app.domain.model.PreparationState
import com.whisperbook.app.domain.model.StoryCharacter
import com.whisperbook.app.domain.model.VoiceDescriptor
import com.whisperbook.app.integration.WhisperbookUiSnapshot
import com.whisperbook.app.engine.tts.SherpaKittenTtsEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WhisperbookAppStateTest {
    @Test
    fun `language changes expose and select only compatible voices`() {
        val book = Book(
            id = "book-1",
            title = "Bilingual Book",
            author = "A. Reader",
            format = BookFormat.EPUB,
            sourceUri = null,
            privateSourcePath = null,
            coverPath = null,
            preparation = PreparationState(
                stage = PreparationStage.AWAITING_NARRATION_SETUP,
                chapterPlanConfirmed = true,
            ),
            currentChapterId = null,
            currentPassageId = null,
            progressFraction = 0f,
            lastOpenedAtEpochMs = 1L,
            narrationLanguageCode = NarrationLanguage.ENGLISH.code,
            preferredNarratorVoiceId = "english",
            narrationSetupConfirmed = false,
        )
        val state = WhisperbookAppState()
        state.synchronize(
            WhisperbookUiSnapshot(
                books = listOf(book),
                selectedBook = book,
                voices = listOf(
                    VoiceDescriptor(
                        "english",
                        "English voice",
                        0,
                        supportedLanguageCodes = setOf("en"),
                    ),
                    VoiceDescriptor(
                        "french",
                        "French voice",
                        1,
                        supportedLanguageCodes = setOf("fr"),
                    ),
                ),
                preparation = book.preparation,
            ),
        )

        assertEquals(listOf("english"), state.narrationSetupVoiceOptions.map(VoiceOptionUi::id))
        state.chooseNarrationSetupLanguage("fr")
        assertEquals(listOf("french"), state.narrationSetupVoiceOptions.map(VoiceOptionUi::id))
        assertEquals("french", state.narrationSetupNarratorVoiceId)

        state.chooseNarrationSetupNarrator("english")
        assertEquals("french", state.narrationSetupNarratorVoiceId)
        assertEquals(listOf("english"), state.bookVoiceOptions.map(VoiceOptionUi::id))
    }

    @Test
    fun `library keeps the imported books own chapter count during background preparation`() {
        val preparation = PreparationState(
            stage = PreparationStage.PREPARING_AUDIO,
            completedUnits = 2,
            totalUnits = 37,
            progressFraction = 2f / 37f,
        )
        val book = Book(
            id = "large-book",
            title = "A Large Book",
            author = "A. Reader",
            format = BookFormat.EPUB,
            sourceUri = null,
            privateSourcePath = "/private/large.epub",
            coverPath = null,
            preparation = preparation,
            currentChapterId = "chapter-1",
            currentPassageId = null,
            progressFraction = 0f,
            lastOpenedAtEpochMs = 1L,
            chapterCount = 37,
            currentChapterOrdinal = 0,
        )
        val state = WhisperbookAppState()

        state.synchronize(
            WhisperbookUiSnapshot(
                books = listOf(book),
                selectedBook = book,
                chapters = emptyList(),
                preparation = preparation,
            ),
        )

        assertEquals(37, state.totalChapters)
        assertEquals(37, state.books.single().totalChapters)
        assertEquals("2 of 37 chapters prepared", state.books.single().libraryProgressLabel())
        assertTrue(state.isBookPreparing)
    }

    @Test
    fun `early background continuation says chapters are being found instead of showing zero`() {
        val item = LibraryBookUi(
            id = "large-book",
            title = "A Large Book",
            author = "A. Reader",
            chapter = 1,
            totalChapters = 0,
            progress = 0f,
            preparation = PreparationState(
                stage = PreparationStage.READING_CHAPTERS,
                message = "Reading chapters on this device",
            ),
        )

        assertEquals("Finding chapters…", item.libraryProgressLabel())
    }

    @Test
    fun `paused and cancelled preparation remain visible without looking active`() {
        val paused = PreparationState(
            stage = PreparationStage.READING_CHAPTERS,
            progressFraction = 0.4f,
            runState = PreparationRunState.PAUSED,
        )
        val state = WhisperbookAppState()

        state.synchronize(WhisperbookUiSnapshot(preparation = paused))

        assertTrue(state.isPreparationPaused)
        assertFalse(state.isPreparationCancelled)
        assertFalse(state.isBookPreparing)
        assertEquals(
            "Preparation paused · progress saved",
            LibraryBookUi(
                id = "book",
                title = "Story",
                author = "Author",
                chapter = 1,
                totalChapters = 10,
                progress = 0.2f,
                preparation = paused,
            ).libraryProgressLabel(),
        )

        state.synchronize(
            WhisperbookUiSnapshot(
                preparation = paused.copy(runState = PreparationRunState.CANCELLED),
            ),
        )

        assertFalse(state.isPreparationPaused)
        assertTrue(state.isPreparationCancelled)
        assertFalse(state.isBookPreparing)
    }

    @Test
    fun `unconfirmed book needs setup and is not active preparation`() {
        val preparation = PreparationState(
            stage = PreparationStage.COPY_AND_VALIDATE,
            message = "Waiting to prepare",
        )
        val book = Book(
            id = "unconfirmed-book",
            title = "Unconfirmed Book",
            author = "A. Reader",
            format = BookFormat.EPUB,
            sourceUri = null,
            privateSourcePath = "/private/unconfirmed.epub",
            coverPath = null,
            preparation = preparation,
            currentChapterId = null,
            currentPassageId = null,
            progressFraction = 0f,
            lastOpenedAtEpochMs = 1L,
            narrationSetupConfirmed = false,
        )
        val state = WhisperbookAppState()

        state.synchronize(
            WhisperbookUiSnapshot(
                books = listOf(book),
                selectedBook = book,
                preparation = preparation,
            ),
        )

        assertTrue(state.narrationSetupRequired)
        assertTrue(state.requiresNarrationSetup(book.id))
        assertFalse(state.isBookPreparing)
        assertFalse(state.canListen)
        assertEquals("Narration setup needed", state.books.single().libraryProgressLabel())
    }

    @Test
    fun `audio preparation is openable before the first chapter finishes`() {
        val state = WhisperbookAppState()

        state.synchronize(
            WhisperbookUiSnapshot(
                preparation = PreparationState(
                    stage = PreparationStage.PREPARING_AUDIO,
                    completedUnits = 0,
                    totalUnits = 3,
                ),
            ),
        )
        assertEquals(3, state.preparationStage)

        state.synchronize(
            WhisperbookUiSnapshot(
                preparation = PreparationState(
                    stage = PreparationStage.PREPARING_AUDIO,
                    completedUnits = 1,
                    totalUnits = 3,
                    progressFraction = 1f / 3f,
                ),
            ),
        )
        assertEquals(3, state.preparationStage)
    }

    @Test
    fun `listening opens once voice assignment is durable without waiting for a chapter`() {
        val preparation = PreparationState(
            stage = PreparationStage.PREPARING_AUDIO,
            completedUnits = 0,
            totalUnits = 1,
        )
        val chapter = Chapter(
            id = "chapter-1",
            bookId = "book-1",
            ordinal = 0,
            title = "Chapter 1",
            passages = emptyList(),
        )
        val book = Book(
            id = "book-1",
            title = "Book",
            author = "Author",
            format = BookFormat.EPUB,
            sourceUri = null,
            privateSourcePath = "/private/book.epub",
            coverPath = null,
            preparation = preparation,
            currentChapterId = chapter.id,
            currentPassageId = null,
            progressFraction = 0f,
            lastOpenedAtEpochMs = 1L,
            chapterCount = 1,
            currentChapterOrdinal = 0,
        )
        val state = WhisperbookAppState()

        state.synchronize(
            WhisperbookUiSnapshot(
                books = listOf(book),
                selectedBook = book,
                chapters = listOf(chapter),
                selectedChapter = chapter,
                preparation = preparation,
            ),
        )

        assertTrue(state.canListen)
        assertTrue(state.books.single().canListen)
    }

    @Test
    fun `selected chapter availability requires its authoritative speaker voices`() {
        val ready = chapterWithPassage("ready", 0, BuiltInCharacters.NARRATOR_ID, "narration")
        val unattributed = chapterWithPassage(
            "unattributed",
            1,
            BuiltInCharacters.NARRATOR_ID,
            "preparation-unattributed",
        )
        val missingVoice = chapterWithPassage("missing-voice", 2, "new-character", "dialogue")
        val empty = Chapter("empty", "book-1", 3, "Chapter 4")
        val attributedHeader = Chapter(
            id = "attributed-header",
            bookId = "book-1",
            ordinal = 4,
            title = "Chapter 5",
            passageCount = 8,
            unattributedPassageCount = 0,
        )
        val unattributedHeader = Chapter(
            id = "unattributed-header",
            bookId = "book-1",
            ordinal = 5,
            title = "Chapter 6",
            passageCount = 8,
            unattributedPassageCount = 1,
        )
        val state = WhisperbookAppState()

        state.synchronize(
            WhisperbookUiSnapshot(
                chapters = listOf(
                    ready,
                    unattributed,
                    missingVoice,
                    empty,
                    attributedHeader,
                    unattributedHeader,
                ),
                selectedChapter = ready,
                preparation = PreparationState(stage = PreparationStage.PREPARING_AUDIO),
                voiceAssignments = mapOf(
                    BuiltInCharacters.NARRATOR_ID to CharacterVoiceAssignment(
                        characterId = BuiltInCharacters.NARRATOR_ID,
                        voiceId = "bella",
                        modelVersion = "test-model",
                    ),
                ),
            ),
        )

        // Only the selected chapter's cast is observed. Persisted non-selected chapters are
        // projected from their persisted attribution summary instead of loading every passage.
        assertEquals(
            listOf(true, false, true, false, true, false),
            state.chapters.map(ChapterUi::isAvailable),
        )
        assertTrue(state.canListen)
        assertFalse(state.hasNextChapter)
    }

    @Test
    fun `voice assignment arrival unlocks chapter without replacing chapter list`() {
        val chapters = listOf(chapterWithPassage("chapter-1", 0, "new-character", "dialogue"))
        val state = WhisperbookAppState()
        val initial = WhisperbookUiSnapshot(
            chapters = chapters,
            selectedChapter = chapters.single(),
            preparation = PreparationState(stage = PreparationStage.PREPARING_AUDIO),
        )

        state.synchronize(initial)
        assertFalse(state.chapters.single().isAvailable)

        state.synchronize(
            initial.copy(
                voiceAssignments = mapOf(
                    "new-character" to CharacterVoiceAssignment(
                        characterId = "new-character",
                        voiceId = "jasper",
                        modelVersion = "test-model",
                    ),
                ),
            ),
        )

        assertTrue(state.chapters.single().isAvailable)
    }

    @Test
    fun `cast selection preserves stable voice id when display names match`() {
        val chapter = chapterWithPassage("chapter-1", 0, BuiltInCharacters.NARRATOR_ID, "narration")
        val state = WhisperbookAppState()

        state.synchronize(
            WhisperbookUiSnapshot(
                chapters = listOf(chapter),
                selectedChapter = chapter,
                characters = listOf(
                    StoryCharacter(
                        id = BuiltInCharacters.NARRATOR_ID,
                        bookId = "book-1",
                        displayName = "Narrator",
                        aliases = emptySet(),
                        colorRole = CharacterColorRole.NARRATOR,
                        dialogueLineCount = 1,
                    ),
                ),
                voices = listOf(
                    VoiceDescriptor("voice-a", "Same name", 0),
                    VoiceDescriptor("voice-b", "Same name", 1),
                ),
                voiceAssignments = mapOf(
                    BuiltInCharacters.NARRATOR_ID to CharacterVoiceAssignment(
                        BuiltInCharacters.NARRATOR_ID,
                        "voice-b",
                        "test-model",
                    ),
                ),
            ),
        )

        assertEquals("voice-b", state.cast.single().voiceId)
    }

    @Test
    fun `cast follows selected chapter across books and returns to its frozen snapshot`() {
        val state = WhisperbookAppState()
        val voices = listOf(
            VoiceDescriptor("bella", "Bella", 0),
            VoiceDescriptor("jasper", "Jasper", 1),
            VoiceDescriptor("bruno", "Bruno", 2),
        )

        fun synchronize(bookId: String, chapterId: String, voiceId: String) {
            val chapter = Chapter(
                id = chapterId,
                bookId = bookId,
                ordinal = if (chapterId.endsWith("2")) 1 else 0,
                title = chapterId,
                passages = listOf(
                    Passage("$chapterId-p1", chapterId, 0, "Text", "$bookId-narrator", 1f, "rule"),
                ),
            )
            val narrator = StoryCharacter(
                id = "$bookId-narrator",
                bookId = bookId,
                displayName = "Narrator",
                aliases = emptySet(),
                colorRole = CharacterColorRole.NARRATOR,
                dialogueLineCount = 1,
            )
            state.synchronize(
                WhisperbookUiSnapshot(
                    chapters = listOf(chapter),
                    selectedChapter = chapter,
                    characters = listOf(narrator),
                    voices = voices,
                    voiceAssignments = mapOf(
                        narrator.id to CharacterVoiceAssignment(narrator.id, voiceId, "model"),
                    ),
                ),
            )
        }

        synchronize("book-a", "a1", "bella")
        assertEquals("bella", state.cast.single().voiceId)
        synchronize("book-a", "a2", "jasper")
        assertEquals("jasper", state.cast.single().voiceId)
        synchronize("book-b", "b1", "bruno")
        assertEquals("bruno", state.cast.single().voiceId)
        synchronize("book-a", "a1", "bella")
        assertEquals("bella", state.cast.single().voiceId)
    }

    @Test
    fun `chapter discovery alone does not expose playback before voices exist`() {
        val item = LibraryBookUi(
            id = "book-1",
            title = "Book",
            author = "Author",
            chapter = 1,
            totalChapters = 1,
            progress = 0f,
            preparation = PreparationState(stage = PreparationStage.FINDING_CHARACTERS),
        )

        assertFalse(item.canListen)
    }

    @Test
    fun `chapter controls move through adjacent demo chapters`() {
        val state = WhisperbookAppState()

        assertFalse(state.hasPreviousChapter)
        assertTrue(state.hasNextChapter)

        state.playNextChapter()
        assertEquals(8, state.currentChapterNumber)
        assertTrue(state.hasPreviousChapter)

        state.playPreviousChapter()
        assertEquals(7, state.currentChapterNumber)
        assertFalse(state.hasPreviousChapter)
    }

    @Test
    fun `every embedded voice has a distinct avatar`() {
        val avatarResources = SherpaKittenTtsEngine.KITTEN_VOICES.map { voice ->
            voiceAvatarRes(voice.id)
        }

        assertEquals(SherpaKittenTtsEngine.KITTEN_VOICES.size, avatarResources.distinct().size)
    }

    @Test
    fun `assigning a voice updates its avatar immediately`() {
        val state = WhisperbookAppState()

        state.assignVoice("narrator", "hugo")

        val narrator = state.cast.first { it.id == "narrator" }
        assertEquals("Hugo", narrator.voice)
        assertEquals(R.drawable.voice_hugo, narrator.portraitRes)
    }

    @Test
    fun `legacy oversized passage is chunked before the reader renders it`() {
        val text = "A very large selectable PDF sentence. ".repeat(50_000)
        val chapter = Chapter(
            id = "chapter-1",
            bookId = "book-1",
            ordinal = 0,
            title = "Chapter 1",
            passages = listOf(
                Passage(
                    id = "legacy-passage",
                    chapterId = "chapter-1",
                    ordinal = 0,
                    text = text,
                    speakerId = BuiltInCharacters.NARRATOR_ID,
                    confidence = 1f,
                    attributionRule = "narration",
                ),
            ),
        )
        val state = WhisperbookAppState()

        state.synchronize(
            WhisperbookUiSnapshot(
                chapters = listOf(chapter),
                selectedChapter = chapter,
            ),
        )

        assertTrue(state.passages.size > 1)
        assertTrue(state.passages.all { it.text.length <= NarrationTextChunker.MAX_CHARS })
        assertEquals(state.passages.size, state.passages.map { it.id }.distinct().size)
        assertEquals(
            NarrationTextChunker.chunks("legacy-passage", text).map { it.id },
            state.passages.map { it.id },
        )
        assertEquals(text.trim(), state.passages.joinToString(" ") { it.text })
    }

    @Test
    fun `reader keeps consecutive sections for one voice in one card`() {
        val chapter = Chapter(
            id = "chapter-1",
            bookId = "book-1",
            ordinal = 0,
            title = "Chapter 1",
            passages = listOf(
                Passage("p1", "chapter-1", 0, "First narrator paragraph.", "narrator", 1f, "narration"),
                Passage("p2", "chapter-1", 1, "Second narrator paragraph.", "narrator", 1f, "narration"),
                Passage("p3", "chapter-1", 2, "A spoken reply.", "elara", 1f, "dialogue"),
                Passage("p4", "chapter-1", 3, "Narration resumes.", "narrator", 1f, "narration"),
            ),
        )
        val state = WhisperbookAppState()

        state.synchronize(
            WhisperbookUiSnapshot(
                chapters = listOf(chapter),
                selectedChapter = chapter,
                characters = listOf(
                    StoryCharacter("narrator", "book-1", "Narrator", emptySet(), CharacterColorRole.NARRATOR, 0),
                    StoryCharacter("elara", "book-1", "Elara", emptySet(), CharacterColorRole.ELARA_BURGUNDY, 1),
                ),
            ),
        )

        assertEquals(3, state.readerPassages.size)
        assertEquals("First narrator paragraph.\n\nSecond narrator paragraph.", state.readerPassages[0].text)
        assertEquals(listOf("p1", "p2"), state.readerPassages[0].sourcePassageIds)
        assertEquals(listOf("narrator", "elara", "narrator"), state.readerPassages.map(PassageUi::speakerId))
    }

    @Test
    fun `reader card tracks every playback chunk it contains`() {
        val text = List(30) { index -> "Sentence $index ends cleanly." }.joinToString(" ")
        val chapter = chapterWithPassage(
            id = "chapter-1",
            ordinal = 0,
            speakerId = BuiltInCharacters.NARRATOR_ID,
            attributionRule = "narration",
            text = text,
        )
        val state = WhisperbookAppState()

        state.synchronize(
            WhisperbookUiSnapshot(
                chapters = listOf(chapter),
                selectedChapter = chapter,
                settings = AppSettings(narrationChunkChars = 80),
            ),
        )

        assertTrue(state.passages.size > 1)
        assertEquals(1, state.readerPassages.size)
        assertEquals(state.passages.map(PassageUi::id), state.readerPassages.single().playbackPassageIds)
        assertEquals(text, state.readerPassages.single().text)
    }

    @Test
    fun `reader preserves paragraph breaks from the source section`() {
        val text = "First paragraph remains visible.\n\nSecond paragraph keeps its spacing."
        val chapter = chapterWithPassage(
            id = "chapter-1",
            ordinal = 0,
            speakerId = BuiltInCharacters.NARRATOR_ID,
            attributionRule = "narration",
            text = text,
        )
        val state = WhisperbookAppState()

        state.synchronize(
            WhisperbookUiSnapshot(chapters = listOf(chapter), selectedChapter = chapter),
        )

        assertEquals(text, state.readerPassages.single().text)
    }

    @Test
    fun `changing narration chunk size reprojects reader passages`() {
        val text = List(20) { index -> "Sentence $index ends cleanly." }.joinToString(" ")
        val chapter = chapterWithPassage(
            id = "chapter-1",
            ordinal = 0,
            speakerId = BuiltInCharacters.NARRATOR_ID,
            attributionRule = "narration",
            text = text,
        )
        val state = WhisperbookAppState()

        state.synchronize(
            WhisperbookUiSnapshot(
                chapters = listOf(chapter),
                selectedChapter = chapter,
                settings = AppSettings(narrationChunkChars = 80),
            ),
        )
        val smallChunkCount = state.passages.size
        assertTrue(state.passages.any { it.text.length > 80 })
        assertTrue(state.passages.all { it.text.length <= NarrationTextChunker.MAX_CHARS })

        state.synchronize(
            WhisperbookUiSnapshot(
                chapters = listOf(chapter),
                selectedChapter = chapter,
                settings = AppSettings(narrationChunkChars = 240),
            ),
        )

        assertTrue(state.passages.size < smallChunkCount)
        assertTrue(state.passages.all { it.text.length <= 240 })
    }

    @Test
    fun `playback ticks preserve structural ui items`() {
        val chapter = Chapter(
            id = "chapter-1",
            bookId = "book-1",
            ordinal = 0,
            title = "Chapter 1",
            passages = listOf(
                Passage(
                    id = "passage-1",
                    chapterId = "chapter-1",
                    ordinal = 0,
                    text = "The unchanged passage text.",
                    speakerId = BuiltInCharacters.NARRATOR_ID,
                    confidence = 1f,
                    attributionRule = "narration",
                ),
            ),
        )
        val chapters = listOf(chapter)
        val state = WhisperbookAppState()
        val initial = WhisperbookUiSnapshot(chapters = chapters, selectedChapter = chapter)
        state.synchronize(initial)
        val chapterItem = state.chapters.single()
        val passageItem = state.passages.single()

        state.synchronize(
            initial.copy(
                playback = PlaybackCursor(
                    bookId = "book-1",
                    chapterId = "chapter-1",
                    passageId = "passage-1",
                    segmentId = "segment-1",
                    segmentPositionMs = 250L,
                    chapterPositionMs = 250L,
                    chapterDurationMs = 10_000L,
                    isPlaying = true,
                    speed = 1f,
                ),
            ),
        )

        assertSame(chapterItem, state.chapters.single())
        assertSame(passageItem, state.passages.single())
        assertEquals(0.025f, state.chapterProgress)
    }

    @Test
    fun `current passage follows the playback cursor`() {
        val chapter = Chapter(
            id = "chapter-1",
            bookId = "book-1",
            ordinal = 0,
            title = "Chapter 1",
            passages = listOf(
                Passage(
                    id = "passage-1",
                    chapterId = "chapter-1",
                    ordinal = 0,
                    text = "The first passage.",
                    speakerId = BuiltInCharacters.NARRATOR_ID,
                    confidence = 1f,
                    attributionRule = "narration",
                ),
                Passage(
                    id = "passage-2",
                    chapterId = "chapter-1",
                    ordinal = 1,
                    text = "The passage being read now.",
                    speakerId = BuiltInCharacters.NARRATOR_ID,
                    confidence = 1f,
                    attributionRule = "narration",
                ),
            ),
        )
        val state = WhisperbookAppState()

        state.synchronize(
            WhisperbookUiSnapshot(
                chapters = listOf(chapter),
                selectedChapter = chapter,
                playback = PlaybackCursor(
                    bookId = "book-1",
                    chapterId = "chapter-1",
                    passageId = "passage-2",
                    segmentId = "segment-2",
                    segmentPositionMs = 1_000L,
                    chapterPositionMs = 2_000L,
                    chapterDurationMs = 8_000L,
                    isPlaying = true,
                    speed = 1f,
                ),
            ),
        )

        assertEquals("passage-2", state.currentPassage?.id)
        assertEquals("The passage being read now.", state.currentPassage?.text)
    }

    @Test
    fun `foreign playback cursor does not overwrite the selected books ui state`() {
        val selectedBook = Book(
            id = "book-b",
            title = "Book B",
            author = "Author B",
            format = BookFormat.EPUB,
            sourceUri = null,
            privateSourcePath = null,
            coverPath = null,
            preparation = PreparationState.Ready,
            currentChapterId = "b-1",
            currentPassageId = null,
            progressFraction = 0.65f,
            lastOpenedAtEpochMs = 2L,
        )
        val selectedChapter = Chapter("b-1", "book-b", 0, "Book B Chapter")
        val state = WhisperbookAppState()

        state.synchronize(
            WhisperbookUiSnapshot(
                books = listOf(selectedBook),
                selectedBook = selectedBook,
                chapters = listOf(selectedChapter),
                selectedChapter = selectedChapter,
                playback = PlaybackCursor(
                    bookId = "book-a",
                    chapterId = "a-4",
                    passageId = "a-passage",
                    segmentId = "a-segment",
                    segmentPositionMs = 8_000L,
                    chapterPositionMs = 40_000L,
                    chapterDurationMs = 50_000L,
                    isPlaying = true,
                    speed = 1f,
                ),
            ),
        )

        assertEquals("Book B", state.currentBookTitle)
        assertEquals("Book B Chapter", state.currentChapterTitle)
        assertEquals(0.65f, state.chapterProgress)
        assertFalse(state.isPlaying)
        assertEquals(0L, state.chapterPositionMs)
    }

    @Test
    fun `chapter review state exposes complete plan while playback chapters stay selected and ordered`() {
        val source = listOf(
            Chapter("chapter-1", "book-1", 0, "One"),
            Chapter("chapter-2", "book-1", 1, "Two"),
            Chapter("chapter-3", "book-1", 2, "Three"),
        )
        val preparation = PreparationState(
            stage = PreparationStage.AWAITING_CHAPTER_SELECTION,
            chapterPlanConfirmed = false,
        )
        val book = Book(
            id = "book-1",
            title = "Plan Book",
            author = "Author",
            format = BookFormat.EPUB,
            sourceUri = null,
            privateSourcePath = null,
            coverPath = null,
            preparation = preparation,
            currentChapterId = null,
            currentPassageId = null,
            progressFraction = 0f,
            lastOpenedAtEpochMs = 1L,
            chapterCount = 3,
            narrationSetupConfirmed = false,
        )
        val plan = listOf(
            ChapterPlanEntry(source[0], isSelected = true, customPosition = 1),
            ChapterPlanEntry(source[1], isSelected = false, customPosition = 2),
            ChapterPlanEntry(source[2], isSelected = true, customPosition = 0),
        )
        val state = WhisperbookAppState()

        state.synchronize(
            WhisperbookUiSnapshot(
                books = listOf(book),
                selectedBook = book,
                chapterPlan = plan,
                chapters = listOf(source[2], source[0]),
                selectedChapter = source[2],
                preparation = preparation,
            ),
        )

        assertEquals(book, state.currentBook)
        assertTrue(state.requiresChapterReview)
        assertFalse(state.narrationSetupRequired)
        assertFalse(state.isBookParsing)
        assertFalse(state.isBookPreparing)
        assertEquals(2, state.selectedChapterCount)
        assertEquals(listOf("chapter-3", "chapter-1", "chapter-2"), state.chapterPlan.map { it.chapter.id })
        assertEquals(listOf("chapter-3", "chapter-1"), state.chapters.map(ChapterUi::id))
    }

    @Test
    fun `parsing is active before narration setup becomes required`() {
        val preparation = PreparationState(
            stage = PreparationStage.READING_CHAPTERS,
            progressFraction = 0.4f,
            chapterPlanConfirmed = false,
        )
        val book = Book(
            id = "book-1",
            title = "Parsing Book",
            author = null,
            format = BookFormat.PDF,
            sourceUri = null,
            privateSourcePath = null,
            coverPath = null,
            preparation = preparation,
            currentChapterId = null,
            currentPassageId = null,
            progressFraction = 0f,
            lastOpenedAtEpochMs = 1L,
            narrationSetupConfirmed = false,
        )
        val state = WhisperbookAppState()

        state.synchronize(
            WhisperbookUiSnapshot(
                books = listOf(book),
                selectedBook = book,
                preparation = preparation,
            ),
        )

        assertTrue(state.isBookParsing)
        assertFalse(state.isBookPreparing)
        assertFalse(state.requiresChapterReview)
        assertFalse(state.narrationSetupRequired)
        assertFalse(state.requiresNarrationSetup(book.id))
    }
}

private fun chapterWithPassage(
    id: String,
    ordinal: Int,
    speakerId: String,
    attributionRule: String,
    text: String = "A chapter passage.",
): Chapter = Chapter(
    id = id,
    bookId = "book-1",
    ordinal = ordinal,
    title = "Chapter ${ordinal + 1}",
    passages = listOf(
        Passage(
            id = "$id-passage",
            chapterId = id,
            ordinal = 0,
            text = text,
            speakerId = speakerId,
            confidence = 1f,
            attributionRule = attributionRule,
        ),
    ),
)
