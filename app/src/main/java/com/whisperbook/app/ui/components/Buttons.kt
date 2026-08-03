package com.whisperbook.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.whisperbook.app.ui.theme.WhisperbookTheme

enum class PapercraftButtonVariant { Primary, Accent, Parchment }

@Composable
fun PapercraftButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: PapercraftButtonVariant = PapercraftButtonVariant.Primary,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    loadingDescription: String = "Working",
    leadingIcon: (@Composable RowScope.() -> Unit)? = null,
    trailingIcon: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = WhisperbookTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val background = when (variant) {
        PapercraftButtonVariant.Primary -> colors.stageRaised
        PapercraftButtonVariant.Accent -> colors.accent
        PapercraftButtonVariant.Parchment -> colors.paper
    }
    val foreground = when (variant) {
        PapercraftButtonVariant.Parchment -> colors.ink
        else -> colors.onStage
    }
    val shape = WhisperbookTheme.shapes.control
    Button(
        onClick = onClick,
        interactionSource = interactionSource,
        enabled = enabled && !isLoading,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = background,
            contentColor = foreground,
            disabledContainerColor = background.copy(alpha = 0.50f),
            disabledContentColor = foreground.copy(alpha = 0.68f),
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = WhisperbookTheme.elevations.raisedControl,
            pressedElevation = 1.dp,
            focusedElevation = WhisperbookTheme.elevations.raisedControl,
            disabledElevation = 0.dp,
        ),
        border = BorderStroke(1.dp, colors.ornament.copy(alpha = if (enabled) 0.9f else 0.45f)),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
        modifier = modifier
            .paperFold(
                interactionSource = interactionSource,
                enabled = enabled && !isLoading,
                fold = PaperFold.Control,
            )
            .defaultMinSize(
                minWidth = WhisperbookTheme.components.minimumTouchTarget,
                minHeight = WhisperbookTheme.components.buttonHeight,
            )
            .semantics {
                if (isLoading) stateDescription = loadingDescription
            },
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = foreground,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
        } else if (leadingIcon != null) {
            leadingIcon()
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = text,
            style = WhisperbookTheme.typography.title,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
        if (!isLoading && trailingIcon != null) {
            Spacer(Modifier.width(10.dp))
            trailingIcon()
        }
    }
}

@Composable
fun EmbossedCircularButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 64.dp,
    backgroundColor: Color = WhisperbookTheme.colors.stageRaised,
    contentColor: Color = WhisperbookTheme.colors.onStage,
    borderColor: Color = WhisperbookTheme.colors.ornament,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(size.coerceAtLeast(WhisperbookTheme.components.minimumTouchTarget))
            .paperFold(interactionSource, enabled, PaperFold.Control)
            .shadow(
                WhisperbookTheme.elevations.raisedControl,
                CircleShape,
                ambientColor = WhisperbookTheme.colors.shadow,
                spotColor = WhisperbookTheme.colors.shadow,
            )
            .clip(CircleShape)
            .background(if (enabled) backgroundColor else backgroundColor.copy(alpha = 0.5f))
            .border(1.dp, borderColor.copy(alpha = if (enabled) 0.9f else 0.45f), CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                this.contentDescription = contentDescription
                if (!enabled) disabled()
            },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides contentColor,
            content = content,
        )
    }
}

@Composable
fun OfflineBadge(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.CloudOff,
) {
    val colors = WhisperbookTheme.colors
    Row(
        modifier = modifier
            .clip(WhisperbookTheme.shapes.pill)
            .background(colors.stageRaised)
            .border(1.dp, colors.ornament.copy(alpha = 0.7f), WhisperbookTheme.shapes.pill)
            .padding(horizontal = 12.dp, vertical = 7.dp)
            .semantics(mergeDescendants = true) { },
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.paper, modifier = Modifier.size(18.dp))
        Text(text, color = colors.onStage, style = WhisperbookTheme.typography.label)
    }
}
