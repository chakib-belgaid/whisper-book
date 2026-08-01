package com.whisperbook.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Layer 1: raw, context-free values extracted from the approved visual direction.
 * Components must consume semantic or component tokens rather than these values.
 */
object WhisperPrimitives {
    object Color {
        val Midnight900 = androidx.compose.ui.graphics.Color(0xFF10243D)
        val Midnight800 = androidx.compose.ui.graphics.Color(0xFF183451)
        val Blue600 = androidx.compose.ui.graphics.Color(0xFF355A83)
        val Blue300 = androidx.compose.ui.graphics.Color(0xFFAEBFD2)
        val Parchment100 = androidx.compose.ui.graphics.Color(0xFFF7EDD7)
        val Parchment300 = androidx.compose.ui.graphics.Color(0xFFE1CBAA)
        val Parchment500 = androidx.compose.ui.graphics.Color(0xFFC4A675)
        val Ink900 = androidx.compose.ui.graphics.Color(0xFF1D1B18)
        val Ink600 = androidx.compose.ui.graphics.Color(0xFF526174)
        val Gold500 = androidx.compose.ui.graphics.Color(0xFFD6B06A)
        val Gold700 = androidx.compose.ui.graphics.Color(0xFF8E6835)
        val Terracotta600 = androidx.compose.ui.graphics.Color(0xFF9B493F)
        val Terracotta800 = androidx.compose.ui.graphics.Color(0xFF71332F)
        val Fox600 = androidx.compose.ui.graphics.Color(0xFFA45A2A)
        val Fox800 = androidx.compose.ui.graphics.Color(0xFF7B3C1B)
        val White = androidx.compose.ui.graphics.Color(0xFFFFFFFF)
        val Black = androidx.compose.ui.graphics.Color(0xFF000000)
    }

    object Space {
        val Xxs = 2.dp
        val Xs = 4.dp
        val Sm = 8.dp
        val Md = 12.dp
        val Lg = 16.dp
        val Xl = 24.dp
        val Xxl = 32.dp
        val Xxxl = 40.dp
    }

    object Radius {
        val Small = 6.dp
        val Medium = 10.dp
        val Large = 14.dp
        val Panel = 18.dp
        val Full = 999.dp
    }

    object Elevation {
        val None = 0.dp
        val PaperContact = 3.dp
        val RaisedControl = 5.dp
        val HeroStage = 8.dp
    }

    object Size {
        val MinimumTouchTarget = 48.dp
        val ButtonHeight = 52.dp
        val BottomBarHeight = 65.dp
        val PassageRailWidth = 7.dp
    }
}

internal fun Color.disabled(): Color = copy(alpha = 0.50f)
