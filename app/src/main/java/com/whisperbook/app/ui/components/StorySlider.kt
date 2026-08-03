package com.whisperbook.app.ui.components

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.whisperbook.app.ui.theme.WhisperbookTheme

@Composable
fun StorySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    enabled: Boolean = true,
    valueDescription: (Float) -> String = { "${(it * 100).toInt()} percent" },
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val colors = WhisperbookTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    Slider(
        value = value.coerceIn(valueRange),
        onValueChange = onValueChange,
        modifier = modifier
            .paperFold(interactionSource, enabled, PaperFold.Toggle)
            .defaultMinSize(minHeight = WhisperbookTheme.components.minimumTouchTarget)
            .semantics { stateDescription = valueDescription(value) },
        enabled = enabled,
        valueRange = valueRange,
        onValueChangeFinished = onValueChangeFinished,
        interactionSource = interactionSource,
        colors = SliderDefaults.colors(
            thumbColor = colors.ornament,
            activeTrackColor = colors.action,
            inactiveTrackColor = colors.outline.copy(alpha = 0.45f),
            disabledThumbColor = colors.ornament.copy(alpha = 0.5f),
            disabledActiveTrackColor = colors.action.copy(alpha = 0.5f),
            disabledInactiveTrackColor = colors.outline.copy(alpha = 0.25f),
        ),
    )
}
