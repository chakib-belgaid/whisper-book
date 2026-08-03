package com.whisperbook.app.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whisperbook.app.ui.components.FloatingMiniPlayer
import com.whisperbook.app.ui.components.AssetFittedText
import com.whisperbook.app.ui.components.LeafOrnament
import com.whisperbook.app.ui.components.PaperFold
import com.whisperbook.app.ui.components.ParchmentPanel
import com.whisperbook.app.ui.components.SpeakerPassageCard
import com.whisperbook.app.ui.components.paperClickable
import com.whisperbook.app.ui.components.paperToggleable
import com.whisperbook.app.ui.theme.WhisperbookTheme

@Composable
fun CurrentChapterScreen(
    contentPadding: PaddingValues,
    appState: WhisperbookAppState,
    onBack: () -> Unit,
    onVoiceCast: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val activeIndex = appState.passages.indexOfFirst { it.id == appState.activePassageId }.coerceAtLeast(0)
    var hasObservedInitialPassage by rememberSaveable { mutableStateOf(false) }
    var showChapterPicker by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(activeIndex, appState.autoScroll) {
        if (!hasObservedInitialPassage) {
            hasObservedInitialPassage = true
        } else if (appState.autoScroll && appState.passages.isNotEmpty()) {
            listState.animateScrollToItem(activeIndex)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 8.dp)
            .testTag("current-chapter-screen"),
    ) {
        Column(Modifier.fillMaxSize()) {
            ReaderTopBar(chapterNumber = appState.currentChapterNumber, onBack = onBack, onVoiceCast = onVoiceCast)
            ReaderChapterPlate(
                title = appState.currentChapterTitle,
                chapterNumber = appState.currentChapterNumber,
                totalChapters = appState.totalChapters,
                autoScroll = appState.autoScroll,
                cast = appState.cast,
                onAutoScrollChange = appState::updateAutoScroll,
                onChooseChapter = { showChapterPicker = true },
            )
            if (appState.isBusy && !appState.statusMessage.isNullOrBlank()) {
                Text(
                    text = appState.statusMessage.orEmpty(),
                    color = WhisperbookTheme.colors.inkMuted,
                    style = WhisperbookTheme.typography.label,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .testTag("chapter-loading-status"),
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(top = 7.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (appState.passages.isEmpty()) {
                    item {
                        ParchmentPanel(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
                            Text("This chapter's text is still being prepared on your device.", color = WhisperbookTheme.colors.ink, style = WhisperbookTheme.typography.body)
                        }
                    }
                }
                itemsIndexed(appState.passages, key = { _, passage -> passage.id }) { index, passage ->
                    SpeakerPassageCard(
                        speakerName = passage.speakerName,
                        passage = passage.text,
                        accentColor = speakerColor(passage.speaker),
                        isActive = passage.id == appState.activePassageId,
                        onClick = { appState.selectPassage(passage.id) },
                        progress = if (passage.id == appState.activePassageId && appState.activePassageDurationMs > 0L) {
                            appState.activePassagePositionMs.toFloat()
                                .div(appState.activePassageDurationMs)
                                .coerceIn(0f, 1f)
                        } else {
                            0f
                        },
                        modifier = Modifier.testTag("passage-${index + 1}"),
                    )
                }
            }
        }
        if (appState.passages.isNotEmpty()) {
            val active = appState.passages[activeIndex]
            val cast = appState.cast.firstOrNull { member -> member.role == active.speaker }
            FloatingMiniPlayer(
                speakerName = active.speakerName,
                voiceName = cast?.voice ?: "Automatic",
                positionText = formatPlaybackClock(appState.chapterPositionMs),
                durationText = formatPlaybackClock(appState.chapterDurationMs),
                portraitRes = cast?.portraitRes,
                accentColor = speakerColor(active.speaker),
                isPlaying = appState.isPlaying,
                progress = appState.chapterProgress,
                onPlayPause = appState::togglePlayback,
                onPreviousChapter = appState::playPreviousChapter,
                onNextChapter = appState::playNextChapter,
                hasPreviousChapter = appState.hasPreviousChapter,
                hasNextChapter = appState.hasNextChapter,
                onSeek = appState::seekTo,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().testTag("reader-mini-player"),
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
            },
        )
    }
}

@Composable
private fun ReaderTopBar(chapterNumber: Int, onBack: () -> Unit, onVoiceCast: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(50.dp)) {
        ReaderHeaderButton(
            onClick = onBack,
            description = "Back",
            modifier = Modifier.align(Alignment.CenterStart),
        ) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, modifier = Modifier.size(25.dp)) }
        Text(
            "Chapter $chapterNumber",
            color = WhisperbookTheme.colors.onStage,
            style = WhisperbookTheme.typography.display.copy(fontSize = 29.sp, lineHeight = 33.sp),
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.align(Alignment.Center),
        )
        ReaderHeaderButton(
            onClick = onVoiceCast,
            description = "Open voice cast",
            modifier = Modifier.align(Alignment.CenterEnd),
        ) { Icon(Icons.Outlined.MoreHoriz, contentDescription = null, modifier = Modifier.size(25.dp)) }
    }
}

@Composable
private fun ReaderHeaderButton(
    onClick: () -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .paperClickable(onClick = onClick, role = Role.Button, fold = PaperFold.Control)
            .clip(CircleShape)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(WhisperbookTheme.colors.stageRaised).border(1.dp, WhisperbookTheme.colors.ornament, CircleShape),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

@Composable
private fun ReaderChapterPlate(
    title: String,
    chapterNumber: Int,
    totalChapters: Int,
    autoScroll: Boolean,
    cast: List<CastMemberUi>,
    onAutoScrollChange: (Boolean) -> Unit,
    onChooseChapter: () -> Unit,
) {
    ParchmentPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 54.dp, topEnd = 54.dp, bottomStart = 3.dp, bottomEnd = 3.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
    ) {
        AssetFittedText(
            text = title,
            color = WhisperbookTheme.colors.ink,
            style = WhisperbookTheme.typography.display.copy(fontSize = 27.sp, lineHeight = 29.sp),
            minFontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .paperClickable(onClick = onChooseChapter, role = Role.Button, fold = PaperFold.Card)
                .clip(RoundedCornerShape(50))
                .semantics {
                    contentDescription = "Choose chapter, currently $chapterNumber of $totalChapters"
                }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            LeafOrnament(Modifier.size(width = 25.dp, height = 11.dp), WhisperbookTheme.colors.outline)
            Text(
                "$chapterNumber of $totalChapters",
                color = WhisperbookTheme.colors.inkMuted,
                style = WhisperbookTheme.typography.body.copy(fontSize = 12.sp, lineHeight = 14.sp),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 10.dp),
            )
            LeafOrnament(Modifier.size(width = 25.dp, height = 11.dp), WhisperbookTheme.colors.outline)
            Icon(
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = WhisperbookTheme.colors.inkMuted,
                modifier = Modifier.size(18.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            cast.take(3).forEach { member ->
                SpeakerLegend(member.character, speakerColor(member.role))
            }
            Spacer(Modifier.weight(1f))
            Text("Auto-scroll", color = WhisperbookTheme.colors.ink, style = WhisperbookTheme.typography.body.copy(fontSize = 11.sp), maxLines = 1)
            CompactReaderToggle(autoScroll, onAutoScrollChange)
        }
    }
}

@Composable
private fun CompactReaderToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 48.dp, height = 36.dp)
            .paperToggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
                fold = PaperFold.Toggle,
            )
            .semantics {
                contentDescription = "Auto-scroll current passage"
                stateDescription = if (checked) "On" else "Off"
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 36.dp, height = 22.dp)
                .clip(RoundedCornerShape(50))
                .background(if (checked) WhisperbookTheme.colors.action else WhisperbookTheme.colors.paper)
                .border(1.dp, WhisperbookTheme.colors.ornament, RoundedCornerShape(50)),
        )
        Spacer(
            Modifier
                .align(Alignment.Center)
                .padding(start = if (checked) 14.dp else 0.dp, end = if (checked) 0.dp else 14.dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(WhisperbookTheme.colors.paperHighlight),
        )
    }
}

@Composable
private fun SpeakerLegend(name: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Spacer(Modifier.size(9.dp).clip(CircleShape).background(color))
        Text(
            name,
            color = WhisperbookTheme.colors.ink,
            style = WhisperbookTheme.typography.body.copy(fontSize = 10.sp, lineHeight = 11.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 55.dp),
        )
    }
}
