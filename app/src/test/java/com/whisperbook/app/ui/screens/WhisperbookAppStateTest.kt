package com.whisperbook.app.ui.screens

import com.whisperbook.app.R
import com.whisperbook.app.domain.PassageTextChunker
import com.whisperbook.app.domain.model.BuiltInCharacters
import com.whisperbook.app.domain.model.Book
import com.whisperbook.app.domain.model.BookFormat
import com.whisperbook.app.domain.model.Chapter
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
    fun `first completed chapter makes the audiobook openable while later chapters prepare`() {
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
        assertEquals(2, state.preparationStage)

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
        assertTrue(state.passages.all { it.text.length <= PassageTextChunker.MAX_CHARS })
        assertEquals(state.passages.size, state.passages.map { it.id }.distinct().size)
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
}
