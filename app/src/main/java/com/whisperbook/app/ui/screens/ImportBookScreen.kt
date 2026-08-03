package com.whisperbook.app.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whisperbook.app.R
import com.whisperbook.app.ui.components.LeafDivider
import com.whisperbook.app.ui.components.PapercraftButton
import com.whisperbook.app.ui.components.PaperFold
import com.whisperbook.app.ui.components.ParchmentPanel
import com.whisperbook.app.ui.components.paperClickable
import com.whisperbook.app.ui.theme.WhisperbookTheme

private val AcceptedBookTypes = arrayOf(
    "application/pdf",
    "application/epub+zip",
    "application/zip",
    "application/octet-stream",
)

@Composable
fun ImportBookScreen(
    contentPadding: PaddingValues,
    appState: WhisperbookAppState,
    onBack: () -> Unit,
    onChosen: () -> Unit,
    onRecentBook: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        appState.imported(uri)
        onChosen()
    }
    val chooseFile = { picker.launch(AcceptedBookTypes) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(top = 3.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StageTopBar(
            title = "Import a book",
            onBack = onBack,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(5.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 36.dp)
                .padding(top = 8.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            ParchmentPanel(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.scene_import_book),
                    contentDescription = "An open papercraft book revealing a calm moonlit forest",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(166.dp),
                )
                Text(
                    text = "Choose a story to listen to",
                    color = WhisperbookTheme.colors.ink,
                    style = WhisperbookTheme.typography.body.copy(fontSize = 17.sp, lineHeight = 22.sp),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                PapercraftButton(
                    text = "Choose a file",
                    onClick = chooseFile,
                    enabled = !appState.isBusy,
                    isLoading = appState.isBusy,
                    loadingDescription = appState.statusMessage ?: "Opening your book",
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .fillMaxWidth(0.84f),
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                ) {
                    FormatChip("PDF")
                    FormatChip("EPUB")
                }
                Spacer(Modifier.height(8.dp))
                appState.importError?.let { error ->
                    Text(
                        text = error,
                        color = WhisperbookTheme.colors.error,
                        style = WhisperbookTheme.typography.label,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp),
                    )
                }
                LocalProcessingPromise()
            }
            ImportPaperClip(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-13).dp),
            )
        }

        Spacer(Modifier.height(8.dp))
        LeafDivider(modifier = Modifier.padding(horizontal = 24.dp))
        Text(
            text = "Recent files",
            color = WhisperbookTheme.colors.onStage,
            style = WhisperbookTheme.typography.title,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        if (appState.books.isEmpty()) {
            Text(
                text = "No recent imports yet",
                color = WhisperbookTheme.colors.paper,
                style = WhisperbookTheme.typography.body,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
            )
        } else {
            appState.books.take(2).forEachIndexed { index, book ->
                RecentFile(
                    title = book.title,
                    metadata = "${book.author}  ·  On this device",
                    enabled = !appState.isBusy,
                    onClick = { onRecentBook(book.id) },
                )
                if (index < appState.books.take(2).lastIndex) Spacer(Modifier.height(5.dp))
            }
        }
    }
}

@Composable
private fun FormatChip(text: String) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .width(66.dp)
            .height(28.dp)
            .shadow(2.dp, shape)
            .clip(shape)
            .background(WhisperbookTheme.colors.paperHighlight)
            .border(1.dp, WhisperbookTheme.colors.outline.copy(alpha = 0.65f), shape)
            .semantics { contentDescription = "$text files supported" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = WhisperbookTheme.colors.ink,
            style = WhisperbookTheme.typography.body.copy(fontSize = 14.sp, lineHeight = 18.sp),
        )
    }
}

@Composable
private fun LocalProcessingPromise() {
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
    ) {
        Icon(
            imageVector = Icons.Outlined.Security,
            contentDescription = null,
            tint = WhisperbookTheme.colors.outline,
            modifier = Modifier.size(23.dp),
        )
        Text(
            text = "Processed privately on this device",
            color = WhisperbookTheme.colors.ink,
            style = WhisperbookTheme.typography.label.copy(fontSize = 11.sp, lineHeight = 15.sp),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ImportPaperClip(modifier: Modifier = Modifier) {
    val dark = WhisperbookTheme.colors.outline
    val light = WhisperbookTheme.colors.ornament
    Canvas(
        modifier = modifier
            .size(width = 26.dp, height = 43.dp)
            .clearAndSetSemantics { },
    ) {
        val stroke = size.width * 0.18f
        drawArc(
            color = dark,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(stroke, 0f),
            size = androidx.compose.ui.geometry.Size(size.width - stroke * 2f, size.width),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawLine(
            color = dark,
            start = Offset(stroke, size.width * 0.52f),
            end = Offset(stroke, size.height - size.width * 0.40f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = dark,
            start = Offset(size.width - stroke, size.width * 0.52f),
            end = Offset(size.width - stroke, size.height - size.width * 0.40f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawArc(
            color = dark,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(stroke, size.height - size.width * 0.80f),
            size = androidx.compose.ui.geometry.Size(size.width - stroke * 2f, size.width * 0.62f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawLine(
            color = light.copy(alpha = 0.75f),
            start = Offset(size.width * 0.50f, size.width * 0.10f),
            end = Offset(size.width * 0.50f, size.height * 0.76f),
            strokeWidth = stroke * 0.34f,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun RecentFile(
    title: String,
    metadata: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    ParchmentPanel(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 30.dp)
            .heightIn(min = 47.dp)
            .paperClickable(
                onClick = onClick,
                enabled = enabled,
                role = Role.Button,
                fold = PaperFold.Card,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = "$title, $metadata. Choose this file"
            },
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(WhisperbookTheme.colors.paperHighlight)
                    .border(1.dp, WhisperbookTheme.colors.outline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoStories,
                    contentDescription = null,
                    tint = WhisperbookTheme.colors.stageRaised,
                    modifier = Modifier.size(23.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = WhisperbookTheme.colors.ink,
                    style = WhisperbookTheme.typography.body.copy(fontSize = 16.sp, lineHeight = 19.sp),
                    maxLines = 1,
                )
                Text(
                    text = metadata,
                    color = WhisperbookTheme.colors.inkMuted,
                    style = WhisperbookTheme.typography.label.copy(fontSize = 11.sp, lineHeight = 14.sp),
                    maxLines = 1,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = WhisperbookTheme.colors.ink,
                modifier = Modifier.size(27.dp),
            )
        }
    }
}
