package com.whisperbook.app.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.whisperbook.app.domain.model.Chapter
import com.whisperbook.app.domain.model.ChapterPlanEntry
import com.whisperbook.app.ui.theme.WhisperbookTheme

@Preview(name = "Chapter review", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
private fun ChapterReviewPreview() {
    WhisperbookTheme {
        ChapterReviewScreen(
            contentPadding = PaddingValues(),
            chapters = previewChapters(),
            onToggleChapter = { _, _ -> },
            onMoveChapter = { _, _ -> },
            onSelectAll = {},
            onDeselectAll = {},
            onRestoreOriginalOrder = {},
            onReset = {},
            onContinue = {},
            onBack = {},
        )
    }
}

@Preview(
    name = "Chapter review 200 percent text",
    widthDp = 360,
    heightDp = 800,
    fontScale = 2f,
    showBackground = true,
)
@Composable
private fun ChapterReviewLargeTextPreview() {
    ChapterReviewPreview()
}

private fun previewChapters(): List<ChapterPlanEntry> = listOf(
    "The Door in the Moonlit Wood",
    "Elara and the Fox",
    "A Very Long Chapter Title That Wraps Gracefully on a Small Phone",
    "The Road Home",
).mapIndexed { index, title ->
    ChapterPlanEntry(
        chapter = Chapter(
            id = "preview-$index",
            bookId = "preview-book",
            ordinal = index,
            title = title,
        ),
        isSelected = index != 2,
        customPosition = index,
    )
}
