package com.whisperbook.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.whisperbook.app.R
import com.whisperbook.app.ui.components.EmbossedCircularButton
import com.whisperbook.app.ui.components.OfflineBadge
import com.whisperbook.app.ui.components.PapercraftButton
import com.whisperbook.app.ui.components.PapercraftButtonVariant
import com.whisperbook.app.ui.components.PaperFold
import com.whisperbook.app.ui.components.ParchmentPanel
import com.whisperbook.app.ui.components.StorySlider
import com.whisperbook.app.ui.components.paperClickable
import com.whisperbook.app.ui.theme.WhisperbookTheme

@Composable
fun LibraryScreen(
    contentPadding: PaddingValues,
    appState: WhisperbookAppState,
    onImport: () -> Unit,
    onBook: (String) -> Unit,
    onResume: (String) -> Unit,
    onRemoveBook: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingRemoveBookId by rememberSaveable { mutableStateOf<String?>(null) }
    appState.books.firstOrNull { it.id == pendingRemoveBookId }?.let { pendingBook ->
        RemoveBookDialog(
            bookTitle = pendingBook.title,
            onConfirm = {
                pendingRemoveBookId = null
                onRemoveBook(pendingBook.id)
            },
            onDismiss = { pendingRemoveBookId = null },
        )
    }
    Column(
        modifier = modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 12.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StageTopBar(title = "Your Library")
        OfflineBadge("Offline", icon = Icons.Outlined.CloudOff)
        PapercraftButton(
            text = "Add a book",
            onClick = onImport,
            variant = PapercraftButtonVariant.Accent,
            modifier = Modifier.fillMaxWidth(.72f),
            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
        )
        if (appState.books.isEmpty()) {
            LibraryEmptyState(onImport = onImport, modifier = Modifier.weight(1f))
        } else {
            val current = appState.books.first()
            ParchmentPanel(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
            ) {
                SectionHeading("Continue Listening")
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TheatreScene(
                        sceneRes = R.drawable.scene_moonlit_wood,
                        contentDescription = "Cover for ${current.title}",
                        modifier = Modifier.width(128.dp),
                        height = 126.dp,
                        showCurtain = true,
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(current.title, color = WhisperbookTheme.colors.ink, style = WhisperbookTheme.typography.title)
                        Text(
                            text = current.libraryProgressLabel(),
                            color = WhisperbookTheme.colors.elara,
                            style = WhisperbookTheme.typography.label,
                        )
                        StorySlider(current.progress, {}, enabled = false, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            EmbossedCircularButton(
                                onClick = { onResume(current.id) },
                                contentDescription = if (current.canListen) {
                                    "Resume ${current.title}"
                                } else if (current.totalChapters > 0) {
                                    "${current.title} is still preparing voices"
                                } else {
                                    "${current.title} is still finding chapters"
                                },
                                enabled = current.canListen,
                                size = 52.dp,
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(30.dp))
                            }
                            EmbossedCircularButton(
                                onClick = { pendingRemoveBookId = current.id },
                                contentDescription = "Remove ${current.title} from library",
                                size = 52.dp,
                            ) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = WhisperbookTheme.colors.error)
                            }
                        }
                    }
                }
            }
            SectionHeading("More stories", dark = false)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                appState.books.drop(1).forEach { book ->
                    ParchmentPanel(
                        modifier = Modifier
                            .width(154.dp)
                            .height(142.dp)
                            .paperClickable(
                                onClick = { onBook(book.id) },
                                role = Role.Button,
                                fold = PaperFold.Card,
                            )
                            .clip(RoundedCornerShape(14.dp))
                            .semantics { contentDescription = "Open ${book.title}" },
                        contentPadding = PaddingValues(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.AutoStories, contentDescription = null, tint = WhisperbookTheme.colors.action, modifier = Modifier.size(34.dp))
                            EmbossedCircularButton(
                                onClick = { pendingRemoveBookId = book.id },
                                contentDescription = "Remove ${book.title} from library",
                                size = 44.dp,
                            ) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = WhisperbookTheme.colors.error)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(book.title, color = WhisperbookTheme.colors.ink, style = WhisperbookTheme.typography.title, maxLines = 2)
                        Text(book.author, color = WhisperbookTheme.colors.inkMuted, style = WhisperbookTheme.typography.label, maxLines = 1)
                    }
                }
            }
        }
    }
}

internal fun LibraryBookUi.libraryProgressLabel(): String = when {
    preparation.stage == com.whisperbook.app.domain.model.PreparationStage.FAILED ->
        "Preparation needs attention"
    preparation.stage == com.whisperbook.app.domain.model.PreparationStage.PREPARING_AUDIO &&
        preparation.totalUnits > 0 ->
        "${preparation.completedUnits.coerceIn(0, preparation.totalUnits)} of ${preparation.totalUnits} chapters recorded"
    totalChapters <= 0 -> "Finding chapters…"
    preparation.stage != com.whisperbook.app.domain.model.PreparationStage.READY ->
        "$totalChapters chapters found · preparing audio"
    else -> "Chapter ${chapter.coerceIn(1, totalChapters)} of $totalChapters"
}

@Composable
private fun LibraryEmptyState(onImport: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        ParchmentPanel(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(24.dp)) {
            Icon(Icons.Outlined.AutoStories, contentDescription = null, tint = WhisperbookTheme.colors.action, modifier = Modifier.size(48.dp).align(Alignment.CenterHorizontally))
            Text("Your shelf is waiting", color = WhisperbookTheme.colors.ink, style = WhisperbookTheme.typography.title, modifier = Modifier.align(Alignment.CenterHorizontally))
            Text("Add a PDF or EPUB to begin. Everything stays on this device.", color = WhisperbookTheme.colors.inkMuted, style = WhisperbookTheme.typography.body)
            Spacer(Modifier.height(12.dp))
            PapercraftButton("Choose a file", onImport, modifier = Modifier.fillMaxWidth())
        }
    }
}
