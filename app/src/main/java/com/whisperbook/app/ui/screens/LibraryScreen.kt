package com.whisperbook.app.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.whisperbook.app.ui.components.ParchmentPanel
import com.whisperbook.app.ui.components.StorySlider
import com.whisperbook.app.ui.theme.WhisperbookTheme

@Composable
fun LibraryScreen(
    contentPadding: PaddingValues,
    appState: WhisperbookAppState,
    onImport: () -> Unit,
    onBook: (String) -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                        Text("Chapter ${current.chapter}", color = WhisperbookTheme.colors.elara, style = WhisperbookTheme.typography.label)
                        StorySlider(current.progress, {}, enabled = false, modifier = Modifier.fillMaxWidth())
                        EmbossedCircularButton(onClick = onResume, contentDescription = "Resume ${current.title}", size = 52.dp) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(30.dp))
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
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(role = Role.Button) { onBook(book.id) }
                            .semantics { contentDescription = "Open ${book.title}" },
                        contentPadding = PaddingValues(12.dp),
                    ) {
                        Icon(Icons.Outlined.AutoStories, contentDescription = null, tint = WhisperbookTheme.colors.action, modifier = Modifier.size(34.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(book.title, color = WhisperbookTheme.colors.ink, style = WhisperbookTheme.typography.title, maxLines = 2)
                        Text(book.author, color = WhisperbookTheme.colors.inkMuted, style = WhisperbookTheme.typography.label, maxLines = 1)
                    }
                }
            }
        }
    }
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
