package com.whisperbook.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/** Layer 2: purpose-based aliases used by screen layouts and custom drawing. */
@Immutable
data class WhisperbookColors(
    val stage: Color,
    val stageRaised: Color,
    val paper: Color,
    val paperHighlight: Color,
    val ink: Color,
    val inkMuted: Color,
    val onStage: Color,
    val action: Color,
    val accent: Color,
    val ornament: Color,
    val outline: Color,
    val focus: Color,
    val narrator: Color,
    val elara: Color,
    val fox: Color,
    val error: Color,
    val shadow: Color,
)

internal val DefaultWhisperbookColors = WhisperbookColors(
    stage = WhisperPrimitives.Color.Midnight900,
    stageRaised = WhisperPrimitives.Color.Midnight800,
    paper = WhisperPrimitives.Color.Parchment300,
    paperHighlight = WhisperPrimitives.Color.Parchment100,
    ink = WhisperPrimitives.Color.Ink900,
    inkMuted = WhisperPrimitives.Color.Ink600,
    onStage = WhisperPrimitives.Color.Parchment100,
    action = WhisperPrimitives.Color.Blue600,
    accent = WhisperPrimitives.Color.Terracotta600,
    ornament = WhisperPrimitives.Color.Gold500,
    outline = WhisperPrimitives.Color.Gold700,
    focus = WhisperPrimitives.Color.Gold500,
    narrator = WhisperPrimitives.Color.Blue600,
    elara = WhisperPrimitives.Color.Terracotta600,
    fox = WhisperPrimitives.Color.Fox600,
    error = WhisperPrimitives.Color.Terracotta800,
    shadow = WhisperPrimitives.Color.Midnight900.copy(alpha = 0.36f),
)

@Immutable
data class WhisperbookSpacing(
    val xxs: Dp = WhisperPrimitives.Space.Xxs,
    val xs: Dp = WhisperPrimitives.Space.Xs,
    val sm: Dp = WhisperPrimitives.Space.Sm,
    val md: Dp = WhisperPrimitives.Space.Md,
    val lg: Dp = WhisperPrimitives.Space.Lg,
    val xl: Dp = WhisperPrimitives.Space.Xl,
    val xxl: Dp = WhisperPrimitives.Space.Xxl,
    val xxxl: Dp = WhisperPrimitives.Space.Xxxl,
)

@Immutable
data class WhisperbookElevations(
    val paperContact: Dp = WhisperPrimitives.Elevation.PaperContact,
    val raisedControl: Dp = WhisperPrimitives.Elevation.RaisedControl,
    val heroStage: Dp = WhisperPrimitives.Elevation.HeroStage,
)

internal val LocalWhisperbookColors = staticCompositionLocalOf { DefaultWhisperbookColors }
internal val LocalWhisperbookSpacing = staticCompositionLocalOf { WhisperbookSpacing() }
internal val LocalWhisperbookElevations = staticCompositionLocalOf { WhisperbookElevations() }
