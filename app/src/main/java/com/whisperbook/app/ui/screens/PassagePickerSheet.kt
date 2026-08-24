package com.whisperbook.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whisperbook.app.ui.components.OrigamiSheet
import com.whisperbook.app.ui.components.PaperFold
import com.whisperbook.app.ui.components.paperClickable
import com.whisperbook.app.ui.theme.WhisperbookTheme

internal fun activeReaderPassageNumber(
    passages: List<PassageUi>,
    activePassageId: String,
): Int {
    if (passages.isEmpty()) return 0
    val activeIndex = passages.indexOfFirst { activePassageId in it.playbackPassageIds }
    return activeIndex.coerceAtLeast(0) + 1
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PassagePickerSheet(
    passages: List<PassageUi>,
    activePassageId: String,
    onDismiss: () -> Unit,
    onPassageSelected: (PassageUi) -> Unit,
) {
    val colors = WhisperbookTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.paper,
        contentColor = colors.ink,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 5.dp)
                    .fillMaxWidth(.16f)
                    .height(4.dp)
                    .background(colors.ornament, RoundedCornerShape(50)),
            )
        },
        modifier = Modifier.testTag("passage-picker"),
    ) {
        OrigamiSheet(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Choose a passage",
                    color = colors.ink,
                    style = WhisperbookTheme.typography.title,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp),
                    contentPadding = PaddingValues(bottom = 28.dp),
                ) {
                    itemsIndexed(passages, key = { _, passage -> passage.id }) { index, passage ->
                        val selected = activePassageId in passage.playbackPassageIds
                        PassagePickerRow(
                            number = index + 1,
                            passage = passage,
                            selected = selected,
                            onClick = { onPassageSelected(passage) },
                            modifier = Modifier.padding(vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PassagePickerRow(
    number: Int,
    passage: PassageUi,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WhisperbookTheme.colors
    val accent = speakerColor(passage.speaker)
    val shape = RoundedCornerShape(11.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) accent.copy(alpha = .15f) else colors.paperHighlight)
            .border(if (selected) 1.5.dp else 1.dp, accent.copy(alpha = .72f), shape)
            .paperClickable(onClick = onClick, role = Role.Button, fold = PaperFold.Card)
            .semantics(mergeDescendants = true) {
                contentDescription = "Passage $number, ${passage.speakerName}. ${passage.text}"
                stateDescription = if (selected) "Currently reading" else "Not selected"
            }
            .testTag("passage-picker-item-$number")
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = if (selected) .22f else .1f))
                .border(1.dp, accent.copy(alpha = .7f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number.toString(),
                color = accent,
                style = WhisperbookTheme.typography.label.copy(fontSize = 12.sp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = passage.speakerName,
                color = accent,
                style = WhisperbookTheme.typography.label.copy(fontSize = 11.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = passage.text,
                color = colors.ink,
                style = WhisperbookTheme.typography.body.copy(fontSize = 14.sp, lineHeight = 17.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Outlined.GraphicEq,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
