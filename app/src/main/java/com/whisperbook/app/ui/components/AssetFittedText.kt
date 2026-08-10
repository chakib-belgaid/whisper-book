package com.whisperbook.app.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Keeps dynamic copy inside decorative artwork by sizing the type against the
 * actual layout constraints. Text only truncates after reaching [minFontSize].
 */
@Composable
internal fun AssetFittedText(
    text: String,
    color: Color,
    style: TextStyle,
    minFontSize: TextUnit,
    modifier: Modifier = Modifier,
    maxFontSize: TextUnit = style.fontSize,
    maxLines: Int = 1,
    textAlign: TextAlign = TextAlign.Center,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(
            color = color,
            fontSize = maxFontSize,
            lineHeight = TextUnit.Unspecified,
            textAlign = textAlign,
        ),
        onTextLayout = onTextLayout,
        overflow = TextOverflow.Ellipsis,
        softWrap = maxLines > 1,
        maxLines = maxLines,
        autoSize = TextAutoSize.StepBased(
            minFontSize = minFontSize,
            maxFontSize = maxFontSize,
            stepSize = 0.5.sp,
        ),
    )
}
