package com.whisperbook.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whisperbook.app.domain.ImportedBook
import com.whisperbook.app.engine.document.PublicationMarkdownLoader
import com.whisperbook.app.ui.components.ParchmentPanel
import com.whisperbook.app.ui.theme.WhisperbookTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MarkdownViewerScreen(
    contentPadding: PaddingValues,
    appState: WhisperbookAppState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val book = appState.currentBook
    val sourcePath = book?.privateSourcePath
    val loader = remember(context) { PublicationMarkdownLoader(context) }
    var content by remember(book?.id, sourcePath) { mutableStateOf<MarkdownContent>(MarkdownContent.Loading) }
    LaunchedEffect(book?.id, sourcePath) {
        content = withContext(Dispatchers.IO) {
            val source = sourcePath?.let(::File)
            val imported = if (book != null && source != null) {
                ImportedBook(
                    title = book.title,
                    author = book.author,
                    format = book.format,
                    privateFile = source,
                    sha256 = source.nameWithoutExtension,
                )
            } else {
                null
            }
            val file = imported?.let { loader.load(it).getOrNull() }
            if (file == null) {
                MarkdownContent.Missing
            } else {
                runCatching<MarkdownContent> {
                    MarkdownContent.Ready(
                        fileName = file.name,
                        chunks = file.readText(Charsets.UTF_8).chunked(MARKDOWN_CHUNK_CHARS),
                    )
                }.getOrElse { MarkdownContent.Unreadable }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .testTag("markdown-viewer"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StageTopBar("Markdown file", onBack = onBack)
        when (val state = content) {
            MarkdownContent.Loading -> CircularProgressIndicator(
                color = WhisperbookTheme.colors.action,
                modifier = Modifier.padding(32.dp),
            )
            MarkdownContent.Missing -> MarkdownMessage(
                "Whisperbook could not create a Markdown file from this book.",
            )
            MarkdownContent.Unreadable -> MarkdownMessage(
                "Whisperbook could not read this Markdown file.",
            )
            is MarkdownContent.Ready -> {
                Text(
                    text = state.fileName,
                    color = WhisperbookTheme.colors.onStage,
                    style = WhisperbookTheme.typography.label,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                ParchmentPanel(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().testTag("markdown-content"),
                    ) {
                        itemsIndexed(state.chunks) { index, chunk ->
                            Text(
                                text = chunk,
                                color = WhisperbookTheme.colors.ink,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                modifier = Modifier.fillMaxWidth().testTag("markdown-chunk-$index"),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkdownMessage(message: String) {
    ParchmentPanel(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(24.dp),
    ) {
        Text(
            text = message,
            color = WhisperbookTheme.colors.inkMuted,
            style = WhisperbookTheme.typography.body,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private sealed interface MarkdownContent {
    data object Loading : MarkdownContent
    data object Missing : MarkdownContent
    data object Unreadable : MarkdownContent
    data class Ready(val fileName: String, val chunks: List<String>) : MarkdownContent
}

private const val MARKDOWN_CHUNK_CHARS = 8_192
