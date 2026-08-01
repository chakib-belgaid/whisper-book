package com.whisperbook.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.whisperbook.app.ui.theme.WhisperbookTheme

@Composable
fun CharacterMedallion(
    speakerName: String,
    portraitRes: Int?,
    accentColor: Color,
    modifier: Modifier = Modifier,
    portraitContentDescription: String = speakerName,
    size: Dp = 112.dp,
) {
    val archShape = RoundedCornerShape(topStart = 48.dp, topEnd = 48.dp, bottomStart = 12.dp, bottomEnd = 12.dp)
    Box(
        modifier = modifier
            .width(size)
            .semantics(mergeDescendants = true) { contentDescription = portraitContentDescription },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .width(size - 12.dp)
                .height(size)
                .clip(archShape)
                .background(WhisperbookTheme.colors.paperHighlight)
                .border(3.dp, accentColor, archShape),
            contentAlignment = Alignment.Center,
        ) {
            if (portraitRes != null) {
                Image(
                    painter = painterResource(portraitRes),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(size * 0.48f),
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(3.dp))
                .background(accentColor)
                .border(1.dp, WhisperbookTheme.colors.outline.copy(alpha = 0.65f), RoundedCornerShape(3.dp))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = speakerName,
                color = WhisperbookTheme.colors.onStage,
                style = WhisperbookTheme.typography.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun VoiceCastCard(
    speakerName: String,
    assignedVoice: String,
    confidencePercent: Int,
    lineCount: Int,
    portraitRes: Int?,
    accentColor: Color,
    onPreviewVoice: () -> Unit,
    onChangeVoice: () -> Unit,
    modifier: Modifier = Modifier,
    previewContentDescription: String = "Preview $assignedVoice for $speakerName",
    changeVoiceText: String = "Change voice",
) {
    val useStackedLayout = LocalDensity.current.fontScale >= 1.5f
    ParchmentPanel(modifier = modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
        if (useStackedLayout) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CharacterMedallion(
                    speakerName = speakerName,
                    portraitRes = portraitRes,
                    accentColor = accentColor,
                    size = 112.dp,
                )
                VoiceDetails(
                    assignedVoice = assignedVoice,
                    confidencePercent = confidencePercent,
                    lineCount = lineCount,
                    accentColor = accentColor,
                    previewContentDescription = previewContentDescription,
                    changeVoiceText = changeVoiceText,
                    onPreviewVoice = onPreviewVoice,
                    onChangeVoice = onChangeVoice,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CharacterMedallion(
                    speakerName = speakerName,
                    portraitRes = portraitRes,
                    accentColor = accentColor,
                    size = 112.dp,
                )
                VoiceDetails(
                    assignedVoice = assignedVoice,
                    confidencePercent = confidencePercent,
                    lineCount = lineCount,
                    accentColor = accentColor,
                    previewContentDescription = previewContentDescription,
                    changeVoiceText = changeVoiceText,
                    onPreviewVoice = onPreviewVoice,
                    onChangeVoice = onChangeVoice,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun VoiceDetails(
    assignedVoice: String,
    confidencePercent: Int,
    lineCount: Int,
    accentColor: Color,
    previewContentDescription: String,
    changeVoiceText: String,
    onPreviewVoice: () -> Unit,
    onChangeVoice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            text = "Assigned voice",
            color = WhisperbookTheme.colors.inkMuted,
            style = WhisperbookTheme.typography.label,
        )
        Text(
            text = assignedVoice,
            color = WhisperbookTheme.colors.ink,
            style = WhisperbookTheme.typography.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${confidencePercent.coerceIn(0, 100)}% confidence · $lineCount lines",
            color = WhisperbookTheme.colors.ink,
            style = WhisperbookTheme.typography.label,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EmbossedCircularButton(
                onClick = onPreviewVoice,
                contentDescription = previewContentDescription,
                size = 52.dp,
                backgroundColor = accentColor,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                )
            }
            PapercraftButton(
                text = changeVoiceText,
                onClick = onChangeVoice,
                variant = PapercraftButtonVariant.Parchment,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp).weight(1f),
            )
        }
    }
}
