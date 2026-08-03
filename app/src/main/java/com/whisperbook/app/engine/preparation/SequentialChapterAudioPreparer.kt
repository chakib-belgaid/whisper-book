package com.whisperbook.app.engine.preparation

import com.whisperbook.app.data.local.db.ChapterAggregate
import com.whisperbook.app.data.local.db.PassageEntity

internal data class ChapterAudioBatch<T>(
    val chapterId: String,
    val chapterOrdinal: Int,
    val chapterTitle: String,
    val passages: List<T>,
)

internal fun orderedChapterAudioBatches(
    chapters: List<ChapterAggregate>,
    fromChapterOrdinal: Int,
): List<ChapterAudioBatch<PassageEntity>> {
    require(fromChapterOrdinal >= 0) { "fromChapterOrdinal must not be negative" }
    return chapters
        .asSequence()
        .filter { it.chapter.ordinal >= fromChapterOrdinal }
        .sortedBy { it.chapter.ordinal }
        .map { chapter ->
            ChapterAudioBatch(
                chapterId = chapter.chapter.id,
                chapterOrdinal = chapter.chapter.ordinal,
                chapterTitle = chapter.chapter.title,
                passages = chapter.passages.sortedBy(PassageEntity::ordinal),
            )
        }
        .filter { it.passages.isNotEmpty() }
        .toList()
}

/**
 * Completes and durably verifies one chapter before allowing work on the next chapter to start.
 *
 * [isPassageReady] makes retries resume at the first missing passage while still tolerating a
 * later passage which was already cached by on-demand playback.
 */
internal class SequentialChapterAudioPreparer<T>(
    private val isPassageReady: suspend (T) -> Boolean,
    private val preparePassage: suspend (T) -> Unit,
) {
    suspend fun prepare(
        chapters: List<ChapterAudioBatch<T>>,
        onChapterStarted: suspend (index: Int, chapter: ChapterAudioBatch<T>) -> Unit = { _, _ -> },
        onChapterReady: suspend (index: Int, chapter: ChapterAudioBatch<T>) -> Unit = { _, _ -> },
    ) {
        chapters.forEachIndexed { chapterIndex, chapter ->
            onChapterStarted(chapterIndex, chapter)
            chapter.passages.forEach { passage ->
                if (!isPassageReady(passage)) preparePassage(passage)
                check(isPassageReady(passage)) {
                    "Passage audio was not durable after local generation"
                }
            }
            onChapterReady(chapterIndex, chapter)
        }
    }
}
