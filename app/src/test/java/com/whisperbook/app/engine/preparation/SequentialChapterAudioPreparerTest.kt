package com.whisperbook.app.engine.preparation

import com.whisperbook.app.data.local.db.ChapterAggregate
import com.whisperbook.app.data.local.db.ChapterEntity
import com.whisperbook.app.data.local.db.PassageEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SequentialChapterAudioPreparerTest {
    @Test
    fun `planner orders chapters and their passages from requested ordinal`() {
        val batches = orderedChapterAudioBatches(
            chapters = listOf(
                chapter(2, passageOrdinals = listOf(2, 0, 1)),
                chapter(0, passageOrdinals = listOf(1, 0)),
                chapter(1, passageOrdinals = listOf(1, 0)),
            ),
            fromChapterOrdinal = 1,
        )

        assertEquals(listOf(1, 2), batches.map { it.chapterOrdinal })
        assertEquals(listOf(0, 1), batches[0].passages.map(PassageEntity::ordinal))
        assertEquals(listOf(0, 1, 2), batches[1].passages.map(PassageEntity::ordinal))
    }

    @Test
    fun `chapter is ready before the next chapter starts`() = runTest {
        val ready = mutableSetOf<String>()
        val events = mutableListOf<String>()
        val chapters = listOf(
            batch(0, "p0", "p1"),
            batch(1, "p2", "p3"),
        )
        val preparer = SequentialChapterAudioPreparer<String>(
            isPassageReady = ready::contains,
            preparePassage = { passage ->
                events += "generate:$passage"
                ready += passage
            },
        )

        preparer.prepare(
            chapters = chapters,
            onChapterStarted = { _, chapter -> events += "start:${chapter.chapterOrdinal}" },
            onChapterReady = { _, chapter ->
                assertTrue(chapter.passages.all(ready::contains))
                events += "ready:${chapter.chapterOrdinal}"
            },
        )

        assertEquals(
            listOf(
                "start:0", "generate:p0", "generate:p1", "ready:0",
                "start:1", "generate:p2", "generate:p3", "ready:1",
            ),
            events,
        )
        assertTrue(events.indexOf("ready:0") < events.indexOf("generate:p2"))
    }

    @Test
    fun `retry skips durable passages and resumes at first missing passage`() = runTest {
        val ready = mutableSetOf("p0", "p1", "p3")
        val generated = mutableListOf<String>()
        val preparer = SequentialChapterAudioPreparer<String>(
            isPassageReady = ready::contains,
            preparePassage = { passage ->
                generated += passage
                ready += passage
            },
        )

        preparer.prepare(listOf(batch(0, "p0", "p1"), batch(1, "p2", "p3")))

        assertEquals(listOf("p2"), generated)
        assertTrue(ready.containsAll(listOf("p0", "p1", "p2", "p3")))
    }

    @Test
    fun `failed passage prevents the next chapter from starting`() = runTest {
        val ready = mutableSetOf<String>()
        var nextChapterStarted = false
        val preparer = SequentialChapterAudioPreparer<String>(
            isPassageReady = ready::contains,
            preparePassage = { passage ->
                if (passage == "p1") error("synthesis failed")
                ready += passage
            },
        )

        val result = runCatching {
            preparer.prepare(
                listOf(batch(0, "p0", "p1"), batch(1, "p2")),
                onChapterStarted = { _, chapter ->
                    if (chapter.chapterOrdinal == 1) nextChapterStarted = true
                },
            )
        }

        assertTrue(result.isFailure)
        assertFalse(nextChapterStarted)
    }

    private fun chapter(ordinal: Int, passageOrdinals: List<Int>) = ChapterAggregate(
        chapter = ChapterEntity(
            id = "chapter-$ordinal",
            bookId = "book",
            ordinal = ordinal,
            title = "Chapter $ordinal",
        ),
        passages = passageOrdinals.map { passageOrdinal ->
            PassageEntity(
                id = "chapter-$ordinal-passage-$passageOrdinal",
                chapterId = "chapter-$ordinal",
                ordinal = passageOrdinal,
                text = "Passage $passageOrdinal",
                speakerId = "narrator",
                confidence = 1f,
                attributionRule = "test",
            )
        },
    )

    private fun batch(ordinal: Int, vararg passages: String) = ChapterAudioBatch(
        chapterId = "chapter-$ordinal",
        chapterOrdinal = ordinal,
        chapterTitle = "Chapter $ordinal",
        passages = passages.toList(),
    )
}
