package com.whisperbook.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whisperbook.app.R
import com.whisperbook.app.ui.components.AssetFittedText
import com.whisperbook.app.ui.components.ChapterNavigationButton
import com.whisperbook.app.ui.components.EmbossedCircularButton
import com.whisperbook.app.ui.components.LeafOrnament
import com.whisperbook.app.ui.components.PaperFold
import com.whisperbook.app.ui.components.PaperProgressBar
import com.whisperbook.app.ui.components.ParchmentPanel
import com.whisperbook.app.ui.components.TheatreTitleSafeWidthFraction
import com.whisperbook.app.ui.components.paperClickable
import com.whisperbook.app.ui.theme.WhisperbookTheme

@Composable
fun NowPlayingScreen(
    contentPadding: PaddingValues,
    appState: WhisperbookAppState,
    onBookDetails: () -> Unit,
    onVoiceCast: () -> Unit,
    onCurrentChapter: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (appState.isProductionBacked && appState.books.isEmpty()) {
        Column(
            modifier = modifier.fillMaxSize().padding(contentPadding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StageTopBar("Whisperbook")
            ParchmentPanel(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(24.dp)) {
                SectionHeading("Nothing to play yet")
                Spacer(Modifier.height(8.dp))
                Text("Import a PDF or EPUB from the Library. Your audiobook will be prepared entirely on this device.", color = WhisperbookTheme.colors.inkMuted, style = WhisperbookTheme.typography.body)
            }
        }
        return
    }

    var showChapterPicker by rememberSaveable { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize().padding(contentPadding).testTag("now-playing-screen"),
    ) {
        val theatreHeight = ((maxHeight - 49.dp) * .47f).coerceIn(205.dp, 245.dp)
        val passageInTheatre = maxHeight < 646.dp
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            PlayerHeader(onSettings = onSettings)
            PlayerTheatre(
                bookTitle = appState.currentBookTitle,
                chapterNumber = appState.currentChapterNumber,
                currentPassage = appState.currentPassage.takeIf { passageInTheatre },
                onBookDetails = onBookDetails,
                onCurrentChapter = onCurrentChapter,
                modifier = Modifier.height(theatreHeight),
            )
            PlayerControlDeck(
                appState = appState,
                currentPassage = appState.currentPassage.takeUnless { passageInTheatre },
                onVoiceCast = onVoiceCast,
                onCurrentChapter = onCurrentChapter,
                onChooseChapter = { showChapterPicker = true },
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (showChapterPicker) {
        ChapterPickerSheet(
            chapters = appState.chapters,
            onDismiss = { showChapterPicker = false },
            onChapterSelected = { chapter ->
                showChapterPicker = false
                appState.selectChapter(chapter.id)
                onCurrentChapter()
            },
        )
    }
}

@Composable
private fun PlayerHeader(onSettings: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(49.dp).padding(horizontal = 10.dp)) {
        CompactOfflineBadge(Modifier.align(Alignment.CenterStart))
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Whisperbook",
                color = WhisperbookTheme.colors.onStage,
                style = WhisperbookTheme.typography.display.copy(fontSize = 23.sp, lineHeight = 23.sp),
                maxLines = 1,
            )
            Text(
                "Stories, beautifully heard. Offline.",
                color = WhisperbookTheme.colors.paper,
                style = WhisperbookTheme.typography.body.copy(fontSize = 8.sp, lineHeight = 10.sp),
                maxLines = 1,
            )
        }
        CompactHeaderButton(onClick = onSettings, description = "Settings", modifier = Modifier.align(Alignment.CenterEnd)) {
            Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(21.dp))
        }
    }
}

@Composable
private fun PlayerTheatre(
    bookTitle: String,
    chapterNumber: Int,
    currentPassage: PassageUi?,
    onBookDetails: () -> Unit,
    onCurrentChapter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .paperClickable(onClick = onBookDetails, role = Role.Button, fold = PaperFold.Card)
            .semantics { contentDescription = "$bookTitle, chapter $chapterNumber. Open book details" }
            .testTag("player-theatre"),
    ) {
        Image(
            painter = painterResource(R.drawable.theatre_frame),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Image(
            painter = painterResource(R.drawable.scene_moonlit_wood),
            contentDescription = "$bookTitle chapter artwork",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 43.dp, end = 43.dp, top = 63.dp, bottom = 8.dp)
                .clip(RoundedCornerShape(topStart = 23.dp, topEnd = 23.dp)),
        )
        Image(
            painter = painterResource(R.drawable.curtain_top),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 42.dp).align(Alignment.TopCenter).padding(top = 55.dp).height(44.dp),
        )
        AssetFittedText(
            text = bookTitle,
            color = WhisperbookTheme.colors.ink,
            style = WhisperbookTheme.typography.display.copy(fontSize = 21.sp, lineHeight = 24.sp),
            minFontSize = 10.sp,
            maxLines = 2,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(TheatreTitleSafeWidthFraction)
                .height(38.dp)
                .padding(top = 8.dp),
        )
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 39.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(WhisperbookTheme.colors.elara)
                .border(1.dp, WhisperbookTheme.colors.ornament, RoundedCornerShape(6.dp))
                .padding(horizontal = 17.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LeafOrnament(Modifier.size(width = 18.dp, height = 9.dp), WhisperbookTheme.colors.ornament)
            Spacer(Modifier.width(6.dp))
            Text("Chapter $chapterNumber", color = WhisperbookTheme.colors.onStage, style = WhisperbookTheme.typography.body.copy(fontSize = 13.sp, lineHeight = 16.sp))
            Spacer(Modifier.width(6.dp))
            LeafOrnament(Modifier.size(width = 18.dp, height = 9.dp), WhisperbookTheme.colors.ornament)
        }
        currentPassage?.let { passage ->
            CurrentPassageExcerpt(
                passage = passage,
                onClick = onCurrentChapter,
                maxLines = 2,
                overArtwork = true,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 56.dp, vertical = 14.dp),
            )
        }
    }
}

@Composable
private fun CompactOfflineBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(WhisperbookTheme.colors.stageRaised)
            .border(1.dp, WhisperbookTheme.colors.ornament, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 5.dp)
            .semantics(mergeDescendants = true) { contentDescription = "Offline" },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⌁", color = WhisperbookTheme.colors.paper, style = WhisperbookTheme.typography.body.copy(fontSize = 14.sp, lineHeight = 14.sp))
        Text("Offline", color = WhisperbookTheme.colors.onStage, style = WhisperbookTheme.typography.body.copy(fontSize = 11.sp, lineHeight = 13.sp), maxLines = 1)
    }
}

@Composable
private fun CompactHeaderButton(
    onClick: () -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .paperClickable(onClick = onClick, role = Role.Button, fold = PaperFold.Control)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(50)).background(WhisperbookTheme.colors.stageRaised).border(1.dp, WhisperbookTheme.colors.ornament, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

@Composable
private fun PlayerControlDeck(
    appState: WhisperbookAppState,
    currentPassage: PassageUi?,
    onVoiceCast: () -> Unit,
    onCurrentChapter: () -> Unit,
    onChooseChapter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ParchmentPanel(
        modifier = modifier.fillMaxWidth().testTag("player-control-deck"),
        shape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp),
        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 4.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(34.dp)) {
            PaperProgressBar(
                value = appState.chapterProgress,
                onValueChange = appState::seekTo,
                modifier = Modifier.fillMaxWidth().height(15.dp).align(Alignment.TopCenter),
            )
            Text(formatPlaybackClock(appState.chapterPositionMs), color = WhisperbookTheme.colors.ink, style = WhisperbookTheme.typography.body.copy(fontSize = 11.sp), modifier = Modifier.align(Alignment.BottomStart))
            Text("−${formatPlaybackClock((appState.chapterDurationMs - appState.chapterPositionMs).coerceAtLeast(0L))}", color = WhisperbookTheme.colors.ink, style = WhisperbookTheme.typography.body.copy(fontSize = 11.sp), modifier = Modifier.align(Alignment.BottomEnd))
        }
        Text(
            formatPlaybackRemaining((appState.chapterDurationMs - appState.chapterPositionMs).coerceAtLeast(0L)),
            color = WhisperbookTheme.colors.action,
            style = WhisperbookTheme.typography.body.copy(fontSize = 12.sp, lineHeight = 14.sp),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        currentPassage?.let { passage ->
            CurrentPassageExcerpt(
                passage = passage,
                onClick = onCurrentChapter,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            )
        }
        Row(Modifier.fillMaxWidth().height(62.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
            PlayerTransport(
                description = "Previous chapter",
                forward = false,
                enabled = appState.hasPreviousChapter,
                onClick = appState::playPreviousChapter,
            )
            EmbossedCircularButton(
                onClick = appState::togglePlayback,
                contentDescription = when {
                    appState.isChapterLoading -> "Preparing chapter audio"
                    appState.isPlaying -> "Pause"
                    else -> "Play"
                },
                enabled = !appState.isChapterLoading,
                size = 59.dp,
            ) {
                if (appState.isChapterLoading) {
                    CircularProgressIndicator(
                        color = WhisperbookTheme.colors.onStage,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(25.dp),
                    )
                } else {
                    Icon(if (appState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(30.dp))
                }
            }
            PlayerTransport(
                description = "Next chapter",
                forward = true,
                enabled = appState.hasNextChapter,
                onClick = appState::playNextChapter,
            )
        }
        Row(
            Modifier.fillMaxWidth().height(38.dp).border(width = 1.dp, color = WhisperbookTheme.colors.outline.copy(alpha = .28f), shape = RoundedCornerShape(1.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DeckSetting(
                primary = "${appState.speed}×",
                secondary = "Speed",
                onClick = appState::cycleSpeed,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(1.dp).height(27.dp).background(WhisperbookTheme.colors.outline.copy(alpha = .38f)))
            DeckSetting(
                primary = if (appState.sleepMinutes == 0) "Off" else "${appState.sleepMinutes} min",
                secondary = "Sleep timer",
                onClick = appState::cycleSleepTimer,
                leading = { Icon(Icons.Outlined.DarkMode, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(Modifier.fillMaxWidth().height(18.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            LeafOrnament(Modifier.size(width = 22.dp, height = 10.dp), WhisperbookTheme.colors.outline)
            Text("Cast for this chapter", color = WhisperbookTheme.colors.ink, style = WhisperbookTheme.typography.title.copy(fontSize = 13.sp, lineHeight = 16.sp), textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 8.dp))
            LeafOrnament(Modifier.size(width = 22.dp, height = 10.dp), WhisperbookTheme.colors.outline)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .paperClickable(onClick = onVoiceCast, role = Role.Button, fold = PaperFold.Card)
                .semantics { contentDescription = "Open voice cast" },
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            appState.cast.take(3).forEach { member ->
                PlayerCastMedallion(member.character, member.portraitRes, speakerColor(member.role))
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .paperClickable(onClick = onChooseChapter, role = Role.Button, fold = PaperFold.Card)
                .clip(RoundedCornerShape(9.dp))
                .border(1.dp, WhisperbookTheme.colors.outline.copy(alpha = .65f), RoundedCornerShape(9.dp))
                .semantics { contentDescription = "Choose chapter, currently ${appState.currentChapterNumber} of ${appState.totalChapters}" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Outlined.AutoStories, contentDescription = null, tint = WhisperbookTheme.colors.ink, modifier = Modifier.size(21.dp))
            Spacer(Modifier.width(7.dp))
            Text("Chapters", color = WhisperbookTheme.colors.ink, style = WhisperbookTheme.typography.body.copy(fontSize = 13.sp))
            Spacer(Modifier.width(24.dp))
            Text("${appState.currentChapterNumber} of ${appState.totalChapters}", color = WhisperbookTheme.colors.ink, style = WhisperbookTheme.typography.body.copy(fontSize = 13.sp))
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, tint = WhisperbookTheme.colors.inkMuted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun CurrentPassageExcerpt(
    passage: PassageUi,
    onClick: () -> Unit,
    maxLines: Int = 3,
    overArtwork: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val accent = speakerColor(passage.speaker)
    val backgroundColor = if (overArtwork) {
        WhisperbookTheme.colors.paper.copy(alpha = .96f)
    } else {
        accent.copy(alpha = .08f)
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .border(1.dp, accent.copy(alpha = .42f), RoundedCornerShape(8.dp))
            .paperClickable(onClick = onClick, role = Role.Button, fold = PaperFold.Card)
            .semantics(mergeDescendants = true) {
                contentDescription = "Currently reading by ${passage.speakerName}. ${passage.text}. Open current chapter"
                liveRegion = LiveRegionMode.Polite
            }
            .testTag("current-passage-excerpt")
            .padding(horizontal = 12.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = passage.speakerName.uppercase(),
            color = accent,
            style = WhisperbookTheme.typography.label.copy(fontSize = 9.sp, lineHeight = 11.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = passage.text,
            color = WhisperbookTheme.colors.ink,
            style = WhisperbookTheme.typography.reader.copy(fontSize = 14.sp, lineHeight = 18.sp),
            textAlign = TextAlign.Center,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PlayerCastMedallion(name: String, portraitRes: Int, accent: Color) {
    Box(Modifier.width(70.dp).height(64.dp).semantics { contentDescription = name }, contentAlignment = Alignment.BottomCenter) {
        Image(
            painter = painterResource(portraitRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(width = 55.dp, height = 57.dp)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 6.dp, bottomEnd = 6.dp))
                .background(WhisperbookTheme.colors.paperHighlight)
                .border(2.dp, accent, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 6.dp, bottomEnd = 6.dp)),
        )
        Box(
            Modifier.fillMaxWidth().height(19.dp).clip(RoundedCornerShape(2.dp)).background(accent).border(1.dp, WhisperbookTheme.colors.outline, RoundedCornerShape(2.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(name, color = WhisperbookTheme.colors.onStage, style = WhisperbookTheme.typography.body.copy(fontSize = 10.sp, lineHeight = 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

internal fun formatPlaybackClock(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}

internal fun formatPlaybackRemaining(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
    return "${totalSeconds / 60L} min ${totalSeconds % 60L} sec left in chapter"
}

@Composable
private fun PlayerTransport(
    description: String,
    forward: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ChapterNavigationButton(
            onClick = onClick,
            description = description,
            forward = forward,
            enabled = enabled,
        )
        Text(
            if (forward) "Next chapter" else "Previous chapter",
            color = if (enabled) WhisperbookTheme.colors.ink else WhisperbookTheme.colors.inkMuted,
            style = WhisperbookTheme.typography.body.copy(fontSize = 10.sp, lineHeight = 11.sp),
        )
    }
}

@Composable
private fun DeckSetting(
    primary: String,
    secondary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .paperClickable(onClick = onClick, role = Role.Button, fold = PaperFold.Control)
            .semantics { contentDescription = "$secondary, $primary" },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        if (leading != null) Spacer(Modifier.width(4.dp))
        Column(horizontalAlignment = Alignment.Start) {
            Text(primary, color = WhisperbookTheme.colors.ink, style = WhisperbookTheme.typography.body.copy(fontSize = 13.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold))
            Text(secondary, color = WhisperbookTheme.colors.ink, style = WhisperbookTheme.typography.body.copy(fontSize = 9.sp, lineHeight = 10.sp))
        }
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, tint = WhisperbookTheme.colors.inkMuted, modifier = Modifier.size(16.dp))
    }
}
