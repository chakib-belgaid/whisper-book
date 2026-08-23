package com.whisperbook.app.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import com.whisperbook.app.ui.theme.WhisperbookFontFamilies
import com.whisperbook.app.ui.theme.WhisperbookTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PapercraftButtonTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun longLabel_fitsNarrowPhoneButtonWithoutEllipsis() {
        var layout: TextLayoutResult? = null

        composeRule.setContent {
            WhisperbookTheme(fontFamilies = WhisperbookFontFamilies.systemFallback()) {
                Box(Modifier.width(288.dp)) {
                    PapercraftButton(
                        text = "Edit chapter selection and order",
                        onClick = {},
                        modifier = Modifier.fillMaxWidth().testTag("button"),
                        onLabelTextLayout = { layout = it },
                    )
                }
            }
        }

        composeRule.waitUntil { layout != null }
        composeRule.runOnIdle {
            assertLabelFits(layout)
        }
        composeRule.onNodeWithTag("button").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun label_fitsCompactButtonAtTwoHundredPercentText() {
        var layout: TextLayoutResult? = null

        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(baseDensity.density, fontScale = 2f),
            ) {
                WhisperbookTheme(fontFamilies = WhisperbookFontFamilies.systemFallback()) {
                    Box(Modifier.width(148.dp)) {
                        PapercraftButton(
                            text = "Change voice",
                            onClick = {},
                            modifier = Modifier.fillMaxWidth().testTag("button"),
                            onLabelTextLayout = { layout = it },
                        )
                    }
                }
            }
        }

        composeRule.waitUntil { layout != null }
        composeRule.runOnIdle {
            assertLabelFits(layout)
        }
        composeRule.onNodeWithTag("button").assertHeightIsAtLeast(48.dp)
    }

    private fun assertLabelFits(layout: TextLayoutResult?) {
        val result = layout
        assertNotNull(result)
        result ?: return
        assertFalse(result.didOverflowWidth)
        assertFalse(result.didOverflowHeight)
        assertTrue(result.lineCount in 1..2)
        repeat(result.lineCount) { line ->
            assertFalse("Button label line $line was ellipsized", result.isLineEllipsized(line))
        }
    }
}
