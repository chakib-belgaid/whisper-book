package com.whisperbook.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.whisperbook.app.R

/**
 * Bundled local variable fonts are the default and never invoke a network provider.
 * Android's font fallback handles glyphs absent from the bundled families; callers
 * can also opt into [systemFallback] for recovery and isolated tests.
 */
@Immutable
data class WhisperbookFontFamilies(
    val display: FontFamily = FontFamily(
        Font(R.font.cormorant_garamond, weight = FontWeight.SemiBold),
    ),
    val body: FontFamily = FontFamily(
        Font(R.font.libre_baskerville, weight = FontWeight.Normal),
        Font(R.font.libre_baskerville, weight = FontWeight.Bold),
    ),
    val label: FontFamily = FontFamily(
        Font(R.font.inter, weight = FontWeight.SemiBold),
    ),
) {
    companion object {
        fun systemFallback() = WhisperbookFontFamilies(
            display = FontFamily.Serif,
            body = FontFamily.Serif,
            label = FontFamily.SansSerif,
        )
    }
}

@Immutable
data class WhisperbookTypography(
    val display: TextStyle,
    val headline: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val reader: TextStyle,
    val label: TextStyle,
)

fun whisperbookTypography(
    fontFamilies: WhisperbookFontFamilies = WhisperbookFontFamilies(),
): WhisperbookTypography = WhisperbookTypography(
    display = TextStyle(
        fontFamily = fontFamilies.display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 40.sp,
    ),
    headline = TextStyle(
        fontFamily = fontFamilies.display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 36.sp,
    ),
    title = TextStyle(
        fontFamily = fontFamilies.body,
        fontWeight = FontWeight.Bold,
        fontSize = 23.sp,
        lineHeight = 28.sp,
    ),
    body = TextStyle(
        fontFamily = fontFamilies.body,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 23.sp,
    ),
    reader = TextStyle(
        fontFamily = fontFamilies.body,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    label = TextStyle(
        fontFamily = fontFamilies.label,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 19.sp,
    ),
)

internal val LocalWhisperbookTypography = staticCompositionLocalOf { whisperbookTypography() }
