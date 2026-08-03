package com.whisperbook.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Motion values for the restrained paper-and-cardboard physicality of the UI. */
@Immutable
data class WhisperbookMotion(
    val pressMillis: Int = 85,
    val pageUnfoldMillis: Int = 360,
    val sheetUnfoldMillis: Int = 300,
    val releaseDampingRatio: Float = .72f,
    val releaseStiffness: Float = 520f,
    val pageFoldDegrees: Float = 34f,
    val sheetFoldDegrees: Float = 24f,
    val cameraDistance: Dp = 28.dp,
)

internal val LocalWhisperbookMotion = staticCompositionLocalOf { WhisperbookMotion() }
