package com.whisperbook.app.ui.screens

import com.whisperbook.app.R
import com.whisperbook.app.domain.NarrationTextChunker
import com.whisperbook.app.domain.model.BuiltInCharacters
import com.whisperbook.app.domain.model.Book
import com.whisperbook.app.domain.model.BookFormat
import com.whisperbook.app.domain.model.Chapter
import com.whisperbook.app.domain.model.CharacterVoiceAssignment
import com.whisperbook.app.domain.model.Passage
import com.whisperbook.app.domain.model.PlaybackCursor
import com.whisperbook.app.domain.model.PreparationStage
import com.whisperbook.app.domain.model.PreparationState
import com.whisperbook.app.integration.WhisperbookUiSnapshot
import com.whisperbook.app.engine.tts.SherpaKittenTtsEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WhisperbookAppStateTest {
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
        assertEquals("2 of 37 chapters recorded", state.books.single().libraryProgressLabel())
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
    fun `chapter availability requires attribution and every speaker voice`() {
        val ready = chapterWithPassage("ready", 0, BuiltInCharacters.NARRATOR_ID, "narration")
        val unattributed = chapterWithPassage(
            "unattributed",
            1,
            BuiltInCharacters.NARRATOR_ID,
            "preparation-unattributed",
        )
        val missingVoice = chapterWithPassage("missing-voice", 2, "new-character", "dialogue")
        val empty = Chapter("empty", "book-1", 3, "Chapter 4")
        val state = WhisperbookAppState()

        state.synchronize(
            WhisperbookUiSnapshot(
                chapters = listOf(ready, unattributed, missingVoice, empty),
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

        assertEquals(listOf(true, false, false, false), state.chapters.map(ChapterUi::isAvailable))
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
}

private fun chapterWithPassage(
    id: String,
    ordinal: Int,
    speakerId: String,
    attributionRule: String,
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
            text = "A chapter passage.",
            speakerId = speakerId,
            confidence = 1f,
            attributionRule = attributionRule,
        ),
    ),
)
