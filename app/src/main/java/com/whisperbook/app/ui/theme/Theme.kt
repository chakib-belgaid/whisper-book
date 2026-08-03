package com.whisperbook.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

private fun materialTypography(type: WhisperbookTypography) = Typography(
    displayLarge = type.display,
    headlineLarge = type.headline,
    titleLarge = type.title,
    bodyLarge = type.body,
    labelLarge = type.label,
)

@Composable
fun WhisperbookTheme(
    colors: WhisperbookColors = DefaultWhisperbookColors,
    fontFamilies: WhisperbookFontFamilies = WhisperbookFontFamilies(),
    content: @Composable () -> Unit,
) {
    val type = whisperbookTypography(fontFamilies)
    val materialColors = darkColorScheme(
        primary = colors.action,
        onPrimary = colors.onStage,
        secondary = colors.accent,
        onSecondary = colors.onStage,
        background = colors.stage,
        onBackground = colors.onStage,
        surface = colors.stageRaised,
        onSurface = colors.onStage,
        surfaceVariant = colors.paper,
        onSurfaceVariant = colors.ink,
        outline = colors.outline,
        error = colors.error,
    )

    CompositionLocalProvider(
        LocalWhisperbookColors provides colors,
        LocalWhisperbookSpacing provides WhisperbookSpacing(),
        LocalWhisperbookElevations provides WhisperbookElevations(),
        LocalWhisperbookShapes provides WhisperbookShapes(),
        LocalWhisperbookComponents provides WhisperbookComponentTokens(),
        LocalWhisperbookMotion provides WhisperbookMotion(),
        LocalWhisperbookTypography provides type,
    ) {
        MaterialTheme(
            colorScheme = materialColors,
            typography = materialTypography(type),
            content = content,
        )
    }
}

object WhisperbookTheme {
    val colors: WhisperbookColors
        @Composable @ReadOnlyComposable get() = LocalWhisperbookColors.current

    val spacing: WhisperbookSpacing
        @Composable @ReadOnlyComposable get() = LocalWhisperbookSpacing.current

    val elevations: WhisperbookElevations
        @Composable @ReadOnlyComposable get() = LocalWhisperbookElevations.current

    val shapes: WhisperbookShapes
        @Composable @ReadOnlyComposable get() = LocalWhisperbookShapes.current

    val components: WhisperbookComponentTokens
        @Composable @ReadOnlyComposable get() = LocalWhisperbookComponents.current

    val motion: WhisperbookMotion
        @Composable @ReadOnlyComposable get() = LocalWhisperbookMotion.current

    val typography: WhisperbookTypography
        @Composable @ReadOnlyComposable get() = LocalWhisperbookTypography.current
}
