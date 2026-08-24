package com.whisperbook.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.whisperbook.app.R
import com.whisperbook.app.domain.model.ChapterPlanEntry
import com.whisperbook.app.domain.model.PreparationStage
import com.whisperbook.app.ui.components.PapercraftButton
import com.whisperbook.app.ui.components.PapercraftButtonVariant
import com.whisperbook.app.ui.components.ParchmentPanel
import com.whisperbook.app.ui.components.ProcessingChapter
import com.whisperbook.app.ui.components.ProcessingChapterRow
import com.whisperbook.app.ui.components.ProcessingChapterState
import com.whisperbook.app.ui.components.TheatreFrameOverlay
import com.whisperbook.app.ui.theme.WhisperbookTheme
import kotlinx.coroutines.delay

private const val ProcessingReferenceWidthDp = 400f
private const val ProcessingMaximumScale = 1.8f
private val ProcessingMaximumContentWidth = ProcessingReferenceWidthDp.dp

/**
 * Keeps the illustrated processing composition legible when Android display sizing exposes a
 * phone as a very wide logical viewport. Regular phone widths remain exactly 1:1.
 */
internal fun processingContentScale(maxWidthDp: Float): Float =
    (maxWidthDp / ProcessingReferenceWidthDp).coerceIn(1f, ProcessingMaximumScale)

@Composable
fun ProcessingScreen(
    contentPadding: PaddingValues,
    appState: WhisperbookAppState,
    onContinueInBackground: () -> Unit,
    onReady: () -> Unit,
    onRetry: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onBackToImport: () -> Unit,
    modifier: Modifier = Modifier,
    selectedChapterPlan: List<ChapterPlanEntry> = appState.chapterPlan,
    chapterPreparationStates: Map<String, ProcessingChapterState> = emptyMap(),
    isPlayable: Boolean = appState.canListen,
    onEditChapters: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val baseDensity = LocalDensity.current
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(appState.importedUri, appState.isProductionBacked) {
        if (appState.importedUri != null && !appState.isProductionBacked) {
            repeat(3) {
                delay(850)
                appState.advancePreparation()
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        val planSnapshot = selectedChapterPlan.toList()
        val fallbackChapterSnapshot = appState.chapters.toList()
        val preparationStage = appState.preparationStatus?.stage
        val activeChapterId = appState.preparationStatus?.activeChapterId
        val queue = remember(
            planSnapshot,
            fallbackChapterSnapshot,
            chapterPreparationStates,
            preparationStage,
            activeChapterId,
        ) {
            buildProcessingQueue(
                selectedChapterPlan = planSnapshot,
                fallbackChapters = fallbackChapterSnapshot,
                explicitStates = chapterPreparationStates,
                preparationStage = preparationStage,
                activeChapterId = activeChapterId,
            )
        }
        val selectedChapterCount = when {
            planSnapshot.isNotEmpty() -> queue.size
            appState.totalChapters > 0 -> maxOf(queue.size, appState.totalChapters)
            else -> queue.size
        }
        val readyChapterCount = when (preparationStage) {
            PreparationStage.READY -> selectedChapterCount
            else -> queue.count { chapter -> chapter.state == ProcessingChapterState.Ready }
        }
        val contentScale = processingContentScale(maxWidth.value)
        val responsiveDensity = Density(
            density = baseDensity.density * contentScale,
            fontScale = baseDensity.fontScale,
        )
        CompositionLocalProvider(LocalDensity provides responsiveDensity) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = ProcessingMaximumContentWidth)
                    .fillMaxSize()
                    .padding(contentPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .testTag("processing-screen"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Preparing your audiobook",
                    color = WhisperbookTheme.colors.onStage,
                    style = WhisperbookTheme.typography.display.copy(fontSize = 29.sp, lineHeight = 34.sp),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().testTag("processing-header"),
                )
                Spacer(Modifier.height(5.dp))

                if (appState.preparationFailed || appState.importError != null) {
                    PreparationFailure(
                        message = appState.importError
                            ?: "Preparation stopped before the audiobook was ready.",
                        isBusy = appState.isBusy,
                        onRetry = onRetry,
                        onBackToImport = onBackToImport,
                    )
                    return@Column
                }

                ProcessingTheatre(
                    title = appState.currentBookTitle,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = chapterReadinessSummary(
                        readyChapterCount = readyChapterCount,
                        selectedChapterCount = selectedChapterCount,
                    ),
                    color = WhisperbookTheme.colors.onStage,
                    style = WhisperbookTheme.typography.display.copy(fontSize = 29.sp, lineHeight = 34.sp),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = chapterReadinessSummary(readyChapterCount, selectedChapterCount) }
                        .testTag("processing-readiness-summary"),
                )
                Text(
                    text = preparationActivityLabel(
                        stage = appState.preparationStatus?.stage,
                        message = appState.preparationStatus?.message ?: appState.statusMessage,
                        activeChapterTitle = queue.firstOrNull { chapter ->
                            chapter.id == activeChapterId
                        }?.title,
                    ),
                    color = WhisperbookTheme.colors.onStage,
                    style = WhisperbookTheme.typography.body,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .testTag("processing-current-activity"),
                )
                if (appState.isPreparationPaused || appState.isPreparationCancelled) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (appState.isPreparationPaused) {
                            "Paused — your progress is saved on this device."
                        } else {
                            "Cancelled — completed work is still saved on this device."
                        },
                        color = WhisperbookTheme.colors.onStage,
                        style = WhisperbookTheme.typography.label,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                stateDescription = if (appState.isPreparationPaused) {
                                    "Paused"
                                } else {
                                    "Cancelled"
                                }
                            }
                            .testTag("processing-run-state"),
                    )
                }
                Spacer(Modifier.height(8.dp))

                ParchmentPanel(
                    modifier = Modifier.fillMaxWidth().testTag("processing-chapter-queue"),
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                ) {
                    Text(
                        text = "Selected listening order",
                        color = WhisperbookTheme.colors.ink,
                        style = WhisperbookTheme.typography.title,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (queue.isEmpty()) {
                            "Your selected chapters will appear here."
                        } else {
                            "Preparation follows the order you chose."
                        },
                        color = WhisperbookTheme.colors.inkMuted,
                        style = WhisperbookTheme.typography.label,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (queue.isNotEmpty()) {
                        val visibleQueue = visibleProcessingQueue(queue)
                        Spacer(Modifier.height(10.dp))
                        visibleQueue.forEachIndexed { index, chapter ->
                            if (index > 0) Spacer(Modifier.height(7.dp))
                            ProcessingChapterRow(
                                chapter = chapter,
                                modifier = Modifier.testTag("processing-chapter-${chapter.id}"),
                            )
                        }
                        val hiddenCount = queue.size - visibleQueue.size
                        if (hiddenCount > 0) {
                            Spacer(Modifier.height(9.dp))
                            Text(
                                text = "$hiddenCount more selected ${if (hiddenCount == 1) "chapter" else "chapters"}",
                                color = WhisperbookTheme.colors.inkMuted,
                                style = WhisperbookTheme.typography.label,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().testTag("processing-queue-overflow"),
                            )
                        }
                    }
                }
                if (onEditChapters != null) {
                    Spacer(Modifier.height(8.dp))
                    PapercraftButton(
                        text = "Edit chapters",
                        onClick = onEditChapters,
                        enabled = !appState.isBusy,
                        variant = PapercraftButtonVariant.Parchment,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = null,
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("processing-edit-chapters-action"),
                    )
                }
                Spacer(Modifier.height(8.dp))
                val continueInBackground = {
                    if (
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS,
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    onContinueInBackground()
                }
                PapercraftButton(
                    text = when {
                        appState.isPreparationPaused -> "Resume"
                        appState.isPreparationCancelled -> "Start again"
                        isPlayable -> "Listen now"
                        else -> "Continue in background"
                    },
                    onClick = when {
                        appState.isPreparationPaused -> onResume
                        appState.isPreparationCancelled -> onRetry
                        isPlayable -> onReady
                        else -> continueInBackground
                    },
                    variant = PapercraftButtonVariant.Accent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("processing-primary-action"),
                    isLoading = appState.isBusy,
                    loadingDescription = appState.statusMessage ?: "Preparing your audiobook",
                )
                if (
                    isPlayable &&
                    !appState.isPreparationPaused &&
                    !appState.isPreparationCancelled
                ) {
                    Spacer(Modifier.height(8.dp))
                    PapercraftButton(
                        text = "Continue in background",
                        onClick = continueInBackground,
                        enabled = !appState.isBusy,
                        variant = PapercraftButtonVariant.Parchment,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("processing-background-action"),
                    )
                }
                if (!appState.isPreparationCancelled && appState.preparationStatus?.stage != PreparationStage.READY) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("processing-secondary-actions"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (!appState.isPreparationPaused) {
                            PapercraftButton(
                                text = "Pause",
                                onClick = onPause,
                                enabled = !appState.isBusy,
                                variant = PapercraftButtonVariant.Parchment,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("processing-pause-action"),
                            )
                        }
                        PapercraftButton(
                            text = "Cancel",
                            onClick = onCancel,
                            enabled = !appState.isBusy,
                            variant = PapercraftButtonVariant.Parchment,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("processing-cancel-action"),
                        )
                    }
                }
                if (
                    isPlayable &&
                    !appState.isPreparationPaused &&
                    !appState.isPreparationCancelled
                ) {
                    Text(
                        text = "You can listen now while later chapters continue preparing on this device.",
                        color = WhisperbookTheme.colors.onStage,
                        style = WhisperbookTheme.typography.label,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                OnDevicePromise()
            }
        }
    }
}

@Composable
private fun ProcessingTheatre(
    title: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(225.dp)
            .testTag("processing-theatre")
            .semantics(mergeDescendants = true) {
                contentDescription = "$title is being prepared in the papercraft story workshop"
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Image(
            painter = painterResource(R.drawable.scene_book_machine),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 49.dp, end = 49.dp, bottom = 26.dp)
                .fillMaxWidth()
                .height(142.dp)
                .clip(RoundedCornerShape(topStart = 60.dp, topEnd = 60.dp, bottomStart = 5.dp, bottomEnd = 5.dp)),
        )
        TheatreFrameOverlay(
            title = title,
            modifier = Modifier.fillMaxSize(),
            titleStyle = WhisperbookTheme.typography.title.copy(fontSize = 24.sp, lineHeight = 28.sp),
            plaqueModifier = Modifier.testTag("processing-title-plaque"),
            titleModifier = Modifier.testTag("processing-book-title"),
        )
    }
}

private const val ProcessingQueuePreviewLimit = 6

internal fun buildProcessingQueue(
    selectedChapterPlan: List<ChapterPlanEntry>,
    fallbackChapters: List<ChapterUi>,
    explicitStates: Map<String, ProcessingChapterState>,
    preparationStage: PreparationStage?,
    activeChapterId: String?,
): List<ProcessingChapter> {
    val fallbackById = fallbackChapters.associateBy(ChapterUi::id)
    val ordered = if (selectedChapterPlan.isNotEmpty()) {
        selectedChapterPlan
            .asSequence()
            .filter(ChapterPlanEntry::isSelected)
            .sortedWith(compareBy(ChapterPlanEntry::customPosition, { it.chapter.ordinal }))
            .toList()
            .mapIndexed { listeningIndex, entry ->
                ProcessingChapter(
                    id = entry.chapter.id,
                    title = entry.chapter.title,
                    originalChapterNumber = entry.chapter.ordinal + 1,
                    listeningPosition = listeningIndex + 1,
                    state = if (preparationStage == PreparationStage.READY) {
                        ProcessingChapterState.Ready
                    } else {
                        explicitStates[entry.chapter.id] ?: inferredChapterState(
                            chapterId = entry.chapter.id,
                            isFallbackLoading = fallbackById[entry.chapter.id]?.isLoading == true,
                            preparationStage = preparationStage,
                            activeChapterId = activeChapterId,
                        )
                    },
                )
            }
    } else {
        fallbackChapters.mapIndexed { index, chapter ->
            ProcessingChapter(
                id = chapter.id,
                title = chapter.title,
                originalChapterNumber = chapter.number,
                listeningPosition = index + 1,
                state = if (preparationStage == PreparationStage.READY) {
                    ProcessingChapterState.Ready
                } else {
                    explicitStates[chapter.id] ?: inferredChapterState(
                        chapterId = chapter.id,
                        isFallbackLoading = chapter.isLoading,
                        preparationStage = preparationStage,
                        activeChapterId = activeChapterId,
                    )
                },
            )
        }
    }
    if (
        ordered.isNotEmpty() &&
        ordered.none { chapter -> chapter.state == ProcessingChapterState.Preparing } &&
        preparationStage in setOf(
            PreparationStage.FINDING_CHARACTERS,
            PreparationStage.ASSIGNING_VOICES,
            PreparationStage.PREPARING_AUDIO,
        )
    ) {
        val nextIndex = ordered.indexOfFirst { chapter ->
            chapter.state == ProcessingChapterState.Waiting
        }
        if (nextIndex >= 0) {
            return ordered.toMutableList().also { chapters ->
                chapters[nextIndex] = chapters[nextIndex].copy(state = ProcessingChapterState.Preparing)
            }
        }
    }
    return ordered
}

private fun inferredChapterState(
    chapterId: String,
    isFallbackLoading: Boolean,
    preparationStage: PreparationStage?,
    activeChapterId: String?,
): ProcessingChapterState = when {
    preparationStage == PreparationStage.READY -> ProcessingChapterState.Ready
    chapterId == activeChapterId || isFallbackLoading -> ProcessingChapterState.Preparing
    else -> ProcessingChapterState.Waiting
}

internal fun visibleProcessingQueue(
    chapters: List<ProcessingChapter>,
): List<ProcessingChapter> = chapters.take(ProcessingQueuePreviewLimit)

internal fun chapterReadinessSummary(
    readyChapterCount: Int,
    selectedChapterCount: Int,
): String {
    if (selectedChapterCount <= 0) return "Preparing selected chapters"
    val safeReadyCount = readyChapterCount.coerceIn(0, selectedChapterCount)
    return "$safeReadyCount of $selectedChapterCount ${if (selectedChapterCount == 1) "chapter" else "chapters"} ready"
}

internal fun preparationActivityLabel(
    stage: PreparationStage?,
    message: String?,
    activeChapterTitle: String?,
): String {
    val activity = message?.takeIf(String::isNotBlank) ?: when (stage) {
        PreparationStage.COPY_AND_VALIDATE -> "Validating your book"
        PreparationStage.READING_CHAPTERS -> "Reading chapter structure"
        PreparationStage.AWAITING_CHAPTER_SELECTION -> "Waiting for chapter selection"
        PreparationStage.AWAITING_NARRATION_SETUP -> "Waiting for narration setup"
        PreparationStage.FINDING_CHARACTERS -> "Finding characters"
        PreparationStage.ASSIGNING_VOICES -> "Assigning voices"
        PreparationStage.PREPARING_AUDIO -> "Preparing audio"
        PreparationStage.READY -> "Selected chapters are ready"
        PreparationStage.FAILED -> "Preparation needs attention"
        null -> "Preparing on this device"
    }
    return activeChapterTitle?.takeIf(String::isNotBlank)?.let { title ->
        "$activity. Current chapter: $title"
    } ?: activity
}

@Composable
private fun OnDevicePromise() {
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Security,
            contentDescription = null,
            tint = WhisperbookTheme.colors.ornament,
            modifier = Modifier.size(25.dp),
        )
        Text(
            text = "Everything stays on this device",
            color = WhisperbookTheme.colors.onStage,
            style = WhisperbookTheme.typography.label.copy(fontSize = 12.sp, lineHeight = 16.sp),
        )
    }
}

@Composable
private fun PreparationFailure(
    message: String,
    isBusy: Boolean,
    onRetry: () -> Unit,
    onBackToImport: () -> Unit,
) {
    Spacer(Modifier.height(18.dp))
    ParchmentPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(22.dp),
    ) {
        Text(
            text = "This book needs your attention",
            color = WhisperbookTheme.colors.ink,
            style = WhisperbookTheme.typography.title,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = message,
            color = WhisperbookTheme.colors.error,
            style = WhisperbookTheme.typography.body,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        PapercraftButton(
            text = "Try again",
            onClick = onRetry,
            enabled = !isBusy,
            isLoading = isBusy,
            loadingDescription = "Retrying preparation",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        PapercraftButton(
            text = "Choose another file",
            onClick = onBackToImport,
            enabled = !isBusy,
            variant = PapercraftButtonVariant.Parchment,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "No upload is needed. You can retry fully offline.",
            color = WhisperbookTheme.colors.inkMuted,
            style = WhisperbookTheme.typography.label,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
