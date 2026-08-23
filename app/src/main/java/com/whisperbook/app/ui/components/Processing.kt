package com.whisperbook.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.whisperbook.app.ui.theme.WhisperbookTheme

enum class ProcessingStepState { Complete, Current, Pending, Error }

@Immutable
data class ProcessingStep(
    val label: String,
    val state: ProcessingStepState,
)

/** Chapter-level state shown in the selected listening-order preview. */
enum class ProcessingChapterState(val label: String) {
    Waiting("Waiting"),
    Preparing("Preparing"),
    Ready("Ready"),
    Failed("Failed"),
}

@Immutable
data class ProcessingChapter(
    val id: String,
    val title: String,
    val originalChapterNumber: Int,
    val listeningPosition: Int,
    val state: ProcessingChapterState,
)

/**
 * A compact, color-independent chapter status row. The listening position is intentionally
 * primary while the source chapter number remains visible as book metadata.
 */
@Composable
fun ProcessingChapterRow(
    chapter: ProcessingChapter,
    modifier: Modifier = Modifier,
) {
    val colors = WhisperbookTheme.colors
    val stateColor = when (chapter.state) {
        ProcessingChapterState.Waiting -> colors.inkMuted
        ProcessingChapterState.Preparing -> colors.action
        ProcessingChapterState.Ready -> colors.outline
        ProcessingChapterState.Failed -> colors.error
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.paperHighlight.copy(alpha = 0.62f))
            .border(1.dp, colors.outline.copy(alpha = 0.34f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append("Listening position ")
                    append(chapter.listeningPosition)
                    append(", ")
                    append(chapter.title)
                    append(", original chapter ")
                    append(chapter.originalChapterNumber)
                }
                stateDescription = chapter.state.label
            },
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(colors.stageRaised)
                .border(1.dp, colors.ornament, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = chapter.listeningPosition.toString(),
                color = colors.onStage,
                style = WhisperbookTheme.typography.label,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = chapter.title,
                color = colors.ink,
                style = WhisperbookTheme.typography.body,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Original chapter ${chapter.originalChapterNumber}",
                    color = colors.inkMuted,
                    style = WhisperbookTheme.typography.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when (chapter.state) {
                        ProcessingChapterState.Preparing -> CircularProgressIndicator(
                            color = stateColor,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp),
                        )
                        else -> Icon(
                            imageVector = when (chapter.state) {
                                ProcessingChapterState.Waiting -> Icons.Outlined.HourglassEmpty
                                ProcessingChapterState.Ready -> Icons.Filled.Check
                                ProcessingChapterState.Failed -> Icons.Outlined.ErrorOutline
                                ProcessingChapterState.Preparing -> Icons.Outlined.AutoAwesome
                            },
                            contentDescription = null,
                            tint = stateColor,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                    Text(
                        text = chapter.state.label,
                        color = stateColor,
                        style = WhisperbookTheme.typography.label,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
fun ProcessingStepper(
    steps: List<ProcessingStep>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.semantics {
            collectionInfo = CollectionInfo(rowCount = steps.size, columnCount = 1)
        },
    ) {
        steps.forEachIndexed { index, step ->
            ProcessingStepRow(step = step, index = index, count = steps.size)
        }
    }
}

@Composable
private fun ProcessingStepRow(
    step: ProcessingStep,
    index: Int,
    count: Int,
) {
    val colors = WhisperbookTheme.colors
    val stateText = when (step.state) {
        ProcessingStepState.Complete -> "Complete"
        ProcessingStepState.Current -> "In progress"
        ProcessingStepState.Pending -> "Not started"
        ProcessingStepState.Error -> "Needs attention"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                collectionItemInfo = CollectionItemInfo(index, 1, 0, 1)
                contentDescription = step.label
                stateDescription = stateText
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ProcessingMarker(step.state)
            if (index < count - 1) {
                Spacer(
                    Modifier
                        .width(2.dp)
                        .height(22.dp)
                        .background(
                            if (step.state == ProcessingStepState.Complete) colors.outline
                            else colors.outline.copy(alpha = 0.38f),
                        ),
                )
            }
        }
        Text(
            text = step.label,
            color = when (step.state) {
                ProcessingStepState.Current -> colors.action
                ProcessingStepState.Error -> colors.error
                else -> colors.ink
            },
            style = if (step.state == ProcessingStepState.Current) {
                WhisperbookTheme.typography.title
            } else {
                WhisperbookTheme.typography.body
            },
            modifier = Modifier.padding(start = 14.dp, bottom = if (index < count - 1) 22.dp else 0.dp),
        )
    }
}

@Composable
private fun ProcessingMarker(state: ProcessingStepState) {
    val colors = WhisperbookTheme.colors
    val background = when (state) {
        ProcessingStepState.Complete -> colors.outline
        ProcessingStepState.Current -> colors.stageRaised
        ProcessingStepState.Pending -> colors.paperHighlight
        ProcessingStepState.Error -> colors.error
    }
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(background)
            .border(2.dp, colors.outline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            ProcessingStepState.Complete -> Icon(Icons.Filled.Check, null, tint = colors.onStage, modifier = Modifier.size(23.dp))
            ProcessingStepState.Current -> CircularProgressIndicator(
                color = colors.ornament,
                strokeWidth = 2.dp,
                modifier = Modifier.size(25.dp),
            )
            ProcessingStepState.Pending -> Icon(Icons.Outlined.HourglassEmpty, null, tint = colors.inkMuted, modifier = Modifier.size(19.dp))
            ProcessingStepState.Error -> Icon(Icons.Outlined.ErrorOutline, null, tint = colors.onStage, modifier = Modifier.size(23.dp))
        }
    }
}
