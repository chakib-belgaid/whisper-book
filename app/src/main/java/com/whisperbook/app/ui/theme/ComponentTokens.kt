package com.whisperbook.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Layer 3: reusable component decisions derived from semantic tokens. */
@Immutable
data class WhisperbookShapes(
    val small: Shape = RoundedCornerShape(WhisperPrimitives.Radius.Small),
    val control: Shape = RoundedCornerShape(WhisperPrimitives.Radius.Medium),
    val card: Shape = RoundedCornerShape(WhisperPrimitives.Radius.Large),
    val panel: Shape = RoundedCornerShape(WhisperPrimitives.Radius.Panel),
    val pill: Shape = RoundedCornerShape(WhisperPrimitives.Radius.Full),
    val selectedNavigation: Shape = RoundedCornerShape(
        topStart = WhisperPrimitives.Radius.Full,
        topEnd = WhisperPrimitives.Radius.Full,
        bottomStart = WhisperPrimitives.Radius.Medium,
        bottomEnd = WhisperPrimitives.Radius.Medium,
    ),
)

@Immutable
data class WhisperbookComponentTokens(
    val minimumTouchTarget: Dp = WhisperPrimitives.Size.MinimumTouchTarget,
    val buttonHeight: Dp = WhisperPrimitives.Size.ButtonHeight,
    val bottomBarHeight: Dp = WhisperPrimitives.Size.BottomBarHeight,
    val passageRailWidth: Dp = WhisperPrimitives.Size.PassageRailWidth,
    val panelPadding: Dp = WhisperPrimitives.Space.Lg,
    val focusRingWidth: Dp = 2.dp,
    val outlineWidth: Dp = 1.dp,
)

internal val LocalWhisperbookShapes = staticCompositionLocalOf { WhisperbookShapes() }
internal val LocalWhisperbookComponents = staticCompositionLocalOf { WhisperbookComponentTokens() }
