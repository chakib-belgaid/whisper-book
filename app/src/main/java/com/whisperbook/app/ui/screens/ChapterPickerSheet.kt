package com.whisperbook.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.whisperbook.app.ui.theme.WhisperbookTheme
import com.whisperbook.app.ui.components.OrigamiSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChapterPickerSheet(
    chapters: List<ChapterUi>,
    onDismiss: () -> Unit,
    onChapterSelected: (ChapterUi) -> Unit,
) {
    val colors = WhisperbookTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.paper,
        contentColor = colors.ink,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 5.dp)
                    .fillMaxWidth(.16f)
                    .height(4.dp)
                    .background(colors.ornament, RoundedCornerShape(50)),
            )
        },
        modifier = Modifier.testTag("chapter-picker"),
    ) {
        OrigamiSheet(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Choose a chapter",
                    color = colors.ink,
                    style = WhisperbookTheme.typography.title,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp),
                    contentPadding = PaddingValues(bottom = 28.dp),
                ) {
                    items(chapters, key = ChapterUi::id) { chapter ->
                        CompactChapterRow(
                            chapter = chapter,
                            onClick = { onChapterSelected(chapter) },
                            modifier = Modifier.padding(vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }
}
