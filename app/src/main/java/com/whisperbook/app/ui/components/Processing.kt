package com.whisperbook.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.unit.dp
import com.whisperbook.app.ui.theme.WhisperbookTheme

enum class ProcessingStepState { Complete, Current, Pending, Error }

@Immutable
data class ProcessingStep(
    val label: String,
    val state: ProcessingStepState,
)

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
