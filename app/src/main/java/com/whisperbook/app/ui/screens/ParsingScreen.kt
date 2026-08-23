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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.PauseCircleOutline
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whisperbook.app.domain.model.Book
import com.whisperbook.app.domain.model.BookFormat
import com.whisperbook.app.domain.model.PreparationRunState
import com.whisperbook.app.domain.model.PreparationStage
import com.whisperbook.app.domain.model.PreparationState
import com.whisperbook.app.ui.components.PapercraftButton
import com.whisperbook.app.ui.components.PapercraftButtonVariant
import com.whisperbook.app.ui.components.ParchmentPanel
import com.whisperbook.app.ui.theme.WhisperbookTheme

private const val ParsingChapterPreviewLimit = 5

/** Lightweight, read-only chapter metadata discovered while parsing a book. */
@Immutable
data class ParsingChapterHeader(
    val id: String,
    val ordinal: Int,
    val title: String,
)

/**
 * Stateless structural-parsing surface. This screen deliberately exposes no chapter-selection
 * affordance because chapter boundaries are not stable until parsing has completed.
 */
@Composable
fun ParsingScreen(
    contentPadding: PaddingValues,
    book: Book,
    preparation: PreparationState,
    discoveredChapters: List<ParsingChapterHeader>,
    onBack: () -> Unit,
    onContinueInBackground: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = parsingProgressDisplay(book.format, preparation)
    val status = parsingStatusDisplay(preparation)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .testTag("parsing-screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StageTopBar(
            title = "Parsing book",
            onBack = onBack,
            modifier = Modifier.testTag("parsing-top-bar"),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = book.title,
                color = WhisperbookTheme.colors.onStage,
                style = WhisperbookTheme.typography.display.copy(fontSize = 27.sp, lineHeight = 32.sp),
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().testTag("parsing-book-title"),
            )
            Text(
                text = "Reading this ${book.format.name} on your device",
                color = WhisperbookTheme.colors.onStage.copy(alpha = 0.82f),
                style = WhisperbookTheme.typography.label,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        ParchmentPanel(
            modifier = Modifier.fillMaxWidth().testTag("parsing-progress-card"),
            shape = RoundedCornerShape(18.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(
                text = progress.phase,
                color = WhisperbookTheme.colors.ink,
                style = WhisperbookTheme.typography.title,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { heading() }
                    .testTag("parsing-phase"),
            )
            Spacer(Modifier.height(10.dp))
            if (progress.fraction != null) {
                LinearProgressIndicator(
                    progress = { progress.fraction },
                    color = WhisperbookTheme.colors.action,
                    trackColor = WhisperbookTheme.colors.inkMuted.copy(alpha = 0.22f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .semantics {
                            progressBarRangeInfo = ProgressBarRangeInfo(
                                current = progress.fraction,
                                range = 0f..1f,
                                steps = 0,
                            )
                            contentDescription = progress.semanticDescription
                        }
                        .testTag("parsing-progress"),
                )
            } else {
                LinearProgressIndicator(
                    color = WhisperbookTheme.colors.action,
                    trackColor = WhisperbookTheme.colors.inkMuted.copy(alpha = 0.22f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .semantics {
                            progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                            contentDescription = progress.semanticDescription
                        }
                        .testTag("parsing-progress"),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = progress.detail,
                color = WhisperbookTheme.colors.inkMuted,
                style = WhisperbookTheme.typography.body,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().testTag("parsing-progress-detail"),
            )
            Text(
                text = "You can choose and reorder chapters after their boundaries are finalized.",
                color = WhisperbookTheme.colors.inkMuted,
                style = WhisperbookTheme.typography.label,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }

        if (status != null) {
            ParsingRunStatus(status)
        }

        ParsingActions(
            preparation = preparation,
            onContinueInBackground = onContinueInBackground,
            onPause = onPause,
            onResume = onResume,
            onCancel = onCancel,
            onRetry = onRetry,
        )

        ParsingChapterPreview(discoveredChapters)

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ParsingChapterPreview(chapters: List<ParsingChapterHeader>) {
    ParchmentPanel(
        modifier = Modifier.fillMaxWidth().testTag("parsing-chapter-preview"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = chapterCountLabel(chapters.size),
            color = WhisperbookTheme.colors.ink,
            style = WhisperbookTheme.typography.title,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { heading() }
                .testTag("parsing-chapters-found"),
        )
        if (chapters.isEmpty()) {
            Text(
                text = "Chapter titles will appear here as the book is read.",
                color = WhisperbookTheme.colors.inkMuted,
                style = WhisperbookTheme.typography.body,
                modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
            )
        } else {
            Spacer(Modifier.height(4.dp))
            chapters.take(ParsingChapterPreviewLimit).forEachIndexed { index, chapter ->
                if (index > 0) {
                    HorizontalDivider(color = WhisperbookTheme.colors.outline.copy(alpha = 0.35f))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .semantics(mergeDescendants = true) {
                            contentDescription = "Chapter ${chapter.ordinal + 1}, ${chapter.title}"
                        }
                        .testTag("parsing-chapter-${chapter.id}"),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoStories,
                        contentDescription = null,
                        tint = WhisperbookTheme.colors.action,
                    )
                    Text(
                        text = chapter.title,
                        color = WhisperbookTheme.colors.ink,
                        style = WhisperbookTheme.typography.body,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${chapter.ordinal + 1}",
                        color = WhisperbookTheme.colors.inkMuted,
                        style = WhisperbookTheme.typography.label,
                    )
                }
            }
            val remaining = chapters.size - ParsingChapterPreviewLimit
            if (remaining > 0) {
                Text(
                    text = "+ $remaining more found",
                    color = WhisperbookTheme.colors.inkMuted,
                    style = WhisperbookTheme.typography.label,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun ParsingRunStatus(status: ParsingStatusDisplay) {
    ParchmentPanel(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { stateDescription = status.stateDescription }
            .testTag("parsing-run-state"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = status.icon,
                contentDescription = null,
                tint = status.iconTint(),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = status.title,
                    color = WhisperbookTheme.colors.ink,
                    style = WhisperbookTheme.typography.title,
                )
                Text(
                    text = status.message,
                    color = WhisperbookTheme.colors.inkMuted,
                    style = WhisperbookTheme.typography.body,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun ParsingStatusDisplay.iconTint() = when (kind) {
    ParsingStatusKind.Failed -> WhisperbookTheme.colors.error
    ParsingStatusKind.Paused -> WhisperbookTheme.colors.action
    ParsingStatusKind.Cancelled -> WhisperbookTheme.colors.inkMuted
    ParsingStatusKind.Complete -> WhisperbookTheme.colors.action
}

@Composable
private fun ParsingActions(
    preparation: PreparationState,
    onContinueInBackground: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    val failed = preparation.stage == PreparationStage.FAILED
    val paused = preparation.runState == PreparationRunState.PAUSED && !failed
    val cancelled = preparation.runState == PreparationRunState.CANCELLED && !failed
    val parsingComplete = preparation.stage == PreparationStage.AWAITING_CHAPTER_SELECTION ||
        preparation.stage == PreparationStage.AWAITING_NARRATION_SETUP ||
        preparation.stage == PreparationStage.READY

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            parsingComplete -> Unit

            failed -> {
                if (preparation.retryable) {
                    PapercraftButton(
                        text = "Try again",
                        onClick = onRetry,
                        variant = PapercraftButtonVariant.Accent,
                        modifier = Modifier.fillMaxWidth().testTag("parsing-retry-action"),
                    )
                }
            }

            cancelled -> PapercraftButton(
                text = "Start again",
                onClick = onRetry,
                variant = PapercraftButtonVariant.Accent,
                modifier = Modifier.fillMaxWidth().testTag("parsing-retry-action"),
            )

            paused -> {
                PapercraftButton(
                    text = "Resume parsing",
                    onClick = onResume,
                    variant = PapercraftButtonVariant.Accent,
                    modifier = Modifier.fillMaxWidth().testTag("parsing-resume-action"),
                )
                PapercraftButton(
                    text = "Cancel parsing",
                    onClick = onCancel,
                    variant = PapercraftButtonVariant.Parchment,
                    modifier = Modifier.fillMaxWidth().testTag("parsing-cancel-action"),
                )
            }

            else -> {
                PapercraftButton(
                    text = "Continue in background",
                    onClick = onContinueInBackground,
                    variant = PapercraftButtonVariant.Accent,
                    modifier = Modifier.fillMaxWidth().testTag("parsing-background-action"),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PapercraftButton(
                        text = "Pause",
                        onClick = onPause,
                        variant = PapercraftButtonVariant.Parchment,
                        modifier = Modifier.weight(1f).testTag("parsing-pause-action"),
                    )
                    PapercraftButton(
                        text = "Cancel",
                        onClick = onCancel,
                        variant = PapercraftButtonVariant.Parchment,
                        modifier = Modifier.weight(1f).testTag("parsing-cancel-action"),
                    )
                }
            }
        }
    }
}

@Immutable
internal data class ParsingProgressDisplay(
    val phase: String,
    val detail: String,
    val semanticDescription: String,
    val fraction: Float?,
)

internal fun parsingProgressDisplay(
    format: BookFormat,
    preparation: PreparationState,
): ParsingProgressDisplay {
    val completed = preparation.completedUnits.coerceAtLeast(0)
    val total = preparation.totalUnits.coerceAtLeast(0)
    val unit = when (format) {
        BookFormat.PDF -> "page"
        BookFormat.EPUB -> "section"
    }
    val unitPlural = if (unit == "page") "pages" else "sections"
    val phase = when (preparation.stage) {
        PreparationStage.FAILED -> "Parsing needs attention"
        PreparationStage.AWAITING_CHAPTER_SELECTION,
        PreparationStage.AWAITING_NARRATION_SETUP,
        PreparationStage.READY -> "Chapter structure ready"
        else -> preparation.message?.takeIf(String::isNotBlank)
            ?: when (preparation.stage) {
                PreparationStage.COPY_AND_VALIDATE -> "Validating file"
                PreparationStage.READING_CHAPTERS -> if (total > 0 && completed > 0) {
                    "Reading $unit ${completed.coerceAtMost(total)} of $total"
                } else {
                    "Reading $unitPlural"
                }
                else -> "Organizing chapters"
            }
    }

    return if (total > 0) {
        val boundedCompleted = completed.coerceAtMost(total)
        val fraction = boundedCompleted.toFloat() / total.toFloat()
        val unitLabel = if (total == 1) unit else unitPlural
        ParsingProgressDisplay(
            phase = phase,
            detail = "$boundedCompleted of $total $unitLabel processed",
            semanticDescription = "Parsing progress, $boundedCompleted of $total $unitLabel processed",
            fraction = fraction,
        )
    } else {
        ParsingProgressDisplay(
            phase = phase,
            detail = "Progress will appear when the book reports a total.",
            semanticDescription = "Parsing progress, total amount unknown",
            fraction = null,
        )
    }
}

private enum class ParsingStatusKind { Paused, Cancelled, Failed, Complete }

@Immutable
private data class ParsingStatusDisplay(
    val kind: ParsingStatusKind,
    val title: String,
    val message: String,
    val stateDescription: String,
    val icon: ImageVector,
)

private fun parsingStatusDisplay(preparation: PreparationState): ParsingStatusDisplay? = when {
    preparation.stage == PreparationStage.FAILED -> ParsingStatusDisplay(
        kind = ParsingStatusKind.Failed,
        title = "Parsing stopped",
        message = preparation.message ?: "Whisperbook could not finish reading this book.",
        stateDescription = "Failed",
        icon = Icons.Outlined.ErrorOutline,
    )

    preparation.runState == PreparationRunState.PAUSED -> ParsingStatusDisplay(
        kind = ParsingStatusKind.Paused,
        title = "Parsing paused",
        message = "Your progress is saved on this device.",
        stateDescription = "Paused",
        icon = Icons.Outlined.PauseCircleOutline,
    )

    preparation.runState == PreparationRunState.CANCELLED -> ParsingStatusDisplay(
        kind = ParsingStatusKind.Cancelled,
        title = "Parsing cancelled",
        message = "Completed work is still saved. Start again whenever you are ready.",
        stateDescription = "Cancelled",
        icon = Icons.Outlined.StopCircle,
    )

    preparation.stage == PreparationStage.AWAITING_CHAPTER_SELECTION ||
        preparation.stage == PreparationStage.AWAITING_NARRATION_SETUP ||
        preparation.stage == PreparationStage.READY -> ParsingStatusDisplay(
        kind = ParsingStatusKind.Complete,
        title = "Chapter structure ready",
        message = "Your chapters are ready to review.",
        stateDescription = "Complete",
        icon = Icons.Outlined.CheckCircle,
    )

    else -> null
}

internal fun chapterCountLabel(count: Int): String = when (count) {
    0 -> "Looking for chapters"
    1 -> "1 chapter found"
    else -> "$count chapters found"
}
