package com.whisperbook.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whisperbook.app.R
import com.whisperbook.app.ui.components.EmbossedCircularButton
import com.whisperbook.app.ui.components.PapercraftButton
import com.whisperbook.app.ui.components.ParchmentPanel
import com.whisperbook.app.ui.components.StorySlider
import com.whisperbook.app.ui.theme.WhisperbookTheme

@Composable
fun BookDetailsScreen(
    contentPadding: PaddingValues,
    appState: WhisperbookAppState,
    onBack: () -> Unit,
    onListen: () -> Unit,
    onVoiceCast: () -> Unit,
    onSettings: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showRemoveConfirmation by rememberSaveable { mutableStateOf(false) }
    if (showRemoveConfirmation) {
        RemoveBookDialog(
            bookTitle = appState.currentBookTitle,
            onConfirm = {
                showRemoveConfirmation = false
                onRemove()
            },
            onDismiss = { showRemoveConfirmation = false },
        )
    }
    if (appState.isProductionBacked && appState.books.isEmpty()) {
        Column(
            modifier = modifier.fillMaxSize().padding(contentPadding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StageTopBar("Book details", onBack = onBack)
            ParchmentPanel(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(24.dp)) {
                SectionHeading("No book selected")
                Spacer(Modifier.height(8.dp))
                Text("Choose a book from your Library to see its chapters and voice cast.", color = WhisperbookTheme.colors.inkMuted, style = WhisperbookTheme.typography.body)
            }
        }
        return
    }
    Column(
        modifier = modifier.fillMaxSize().padding(contentPadding).verticalScroll(rememberScrollState()).padding(horizontal = 11.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        StageTopBar("Book details", onBack = onBack, trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                EmbossedCircularButton(
                    onClick = { showRemoveConfirmation = true },
                    contentDescription = "Remove ${appState.currentBookTitle} from library",
                    size = 44.dp,
                ) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = WhisperbookTheme.colors.error)
                }
                EmbossedCircularButton(onClick = onSettings, contentDescription = "Settings", size = 44.dp) {
                    Icon(Icons.Outlined.Settings, null)
                }
            }
        })
        BookTheatreHero(
            title = appState.currentBookTitle,
            sceneRes = R.drawable.scene_moonlit_wood,
            contentDescription = "Papercraft cover illustration for ${appState.currentBookTitle}",
        )
        ParchmentPanel(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp)) {
            Text(appState.currentAuthor, color = WhisperbookTheme.colors.ink, style = WhisperbookTheme.typography.title.copy(fontSize = 18.sp, lineHeight = 22.sp), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text(
                text = if (appState.totalChapters > 0) {
                    "Chapter ${appState.currentChapterNumber.coerceAtLeast(1)} of ${appState.totalChapters}"
                } else {
                    "Finding chapters on this device…"
                },
                color = WhisperbookTheme.colors.action,
                style = WhisperbookTheme.typography.label,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            StorySlider(appState.chapterProgress, {}, enabled = false, modifier = Modifier.fillMaxWidth())
            PapercraftButton(
                text = when {
                    appState.canListen -> "Continue listening"
                    appState.totalChapters > 0 -> "Preparing voices…"
                    else -> "Preparing chapters…"
                },
                onClick = onListen,
                enabled = appState.canListen,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = { Icon(Icons.Filled.PlayArrow, null) },
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DetailTab(
                    text = "Chapters",
                    icon = Icons.Outlined.AutoStories,
                    selected = true,
                    onClick = null,
                    modifier = Modifier.weight(1f),
                )
                DetailTab(
                    text = "Voice cast",
                    icon = Icons.Outlined.Groups,
                    selected = false,
                    onClick = onVoiceCast,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(4.dp))
            SectionHeading("Chapters")
            if (appState.chapters.isEmpty()) {
                Text(
                    text = "Chapters will appear here as soon as the private book scan finishes.",
                    color = WhisperbookTheme.colors.inkMuted,
                    style = WhisperbookTheme.typography.body,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
            }
            appState.chapters.forEach { chapter ->
                CompactChapterRow(
                    chapter = chapter,
                    enabled = appState.canListen && chapter.isAvailable,
                    onClick = {
                        appState.selectChapter(chapter.id)
                        onListen()
                    },
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
