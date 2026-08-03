package com.whisperbook.app.ui.components

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.whisperbook.app.ui.theme.WhisperbookTheme

@Composable
fun StorybookToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = WhisperbookTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier
            .paperFold(interactionSource, enabled, PaperFold.Toggle)
            .defaultMinSize(
                minWidth = WhisperbookTheme.components.minimumTouchTarget,
                minHeight = WhisperbookTheme.components.minimumTouchTarget,
            )
            .semantics { this.contentDescription = contentDescription },
        enabled = enabled,
        interactionSource = interactionSource,
        colors = SwitchDefaults.colors(
            checkedThumbColor = colors.paperHighlight,
            checkedTrackColor = colors.action,
            checkedBorderColor = colors.ornament,
            uncheckedThumbColor = colors.paperHighlight,
            uncheckedTrackColor = colors.paper.copy(alpha = 0.72f),
            uncheckedBorderColor = colors.outline,
            disabledCheckedThumbColor = colors.paperHighlight.copy(alpha = 0.6f),
            disabledCheckedTrackColor = colors.action.copy(alpha = 0.4f),
            disabledUncheckedThumbColor = colors.paperHighlight.copy(alpha = 0.6f),
            disabledUncheckedTrackColor = colors.paper.copy(alpha = 0.35f),
        ),
    )
}

@Composable
fun SettingsToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    SettingsRow(
        title = title,
        icon = icon,
        modifier = modifier,
        trailingContent = {
            StorybookToggle(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                contentDescription = title,
            )
        },
    )
}
