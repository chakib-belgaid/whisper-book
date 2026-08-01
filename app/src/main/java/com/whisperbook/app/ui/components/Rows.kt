package com.whisperbook.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.whisperbook.app.ui.theme.WhisperbookTheme

@Composable
fun ChapterRow(
    chapterNumber: Int,
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    chapterLabel: String = "Chapter $chapterNumber",
) {
    val colors = WhisperbookTheme.colors
    val shape = WhisperbookTheme.shapes.card
    val background = if (selected) colors.stageRaised else colors.paper
    val foreground = if (selected) colors.onStage else colors.ink
    Column(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 72.dp)
            .clip(shape)
            .background(background)
            .border(1.dp, if (selected) colors.ornament else colors.outline.copy(alpha = 0.7f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (selected) colors.action else colors.paperHighlight)
                    .border(1.dp, colors.outline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(chapterNumber.toString(), color = foreground, style = WhisperbookTheme.typography.title)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(chapterLabel, color = foreground, style = WhisperbookTheme.typography.label)
                Text(
                    title,
                    color = foreground,
                    style = WhisperbookTheme.typography.body,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Outlined.Headphones,
                    contentDescription = "Current chapter",
                    tint = colors.ornament,
                    modifier = Modifier.size(28.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.ink,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        if (progress != null) {
            StorySlider(
                value = progress.coerceIn(0f, 1f),
                onValueChange = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.padding(top = 17.dp)) {
        ParchmentPanel(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(top = 26.dp, start = 16.dp, end = 16.dp, bottom = 8.dp),
            content = { content() },
        )
        RibbonTitle(
            title = title,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = WhisperbookTheme.colors
    val interactionModifier = if (onClick != null) {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 58.dp)
            .then(interactionModifier)
            .padding(horizontal = 4.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = colors.action, modifier = Modifier.size(28.dp))
        }
        Text(
            title,
            color = colors.ink,
            style = WhisperbookTheme.typography.body,
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Text(value, color = colors.action, style = WhisperbookTheme.typography.label)
        }
        if (trailingContent != null) {
            trailingContent()
        } else if (onClick != null) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.action,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}
