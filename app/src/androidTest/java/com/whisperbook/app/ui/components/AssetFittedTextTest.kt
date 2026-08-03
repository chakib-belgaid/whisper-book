package com.whisperbook.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AssetFittedTextTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun longTitle_scalesDownAsAssetWidthShrinks() {
        var wideLayout: TextLayoutResult? = null
        var narrowLayout: TextLayoutResult? = null
        val longTitle = "The Moonlit Woodland Adventure"

        composeRule.setContent {
            Column {
                Box(Modifier.width(320.dp)) {
                    AssetFittedText(
                        text = longTitle,
                        color = Color.Black,
                        style = TextStyle(fontSize = 30.sp),
                        minFontSize = 10.sp,
                        onTextLayout = { wideLayout = it },
                    )
                }
                Box(Modifier.width(150.dp)) {
                    AssetFittedText(
                        text = longTitle,
                        color = Color.Black,
                        style = TextStyle(fontSize = 30.sp),
                        minFontSize = 10.sp,
                        onTextLayout = { narrowLayout = it },
                    )
                }
            }
        }

        composeRule.waitUntil {
            wideLayout != null && narrowLayout != null
        }
        composeRule.runOnIdle {
            val wide = wideLayout
            val narrow = narrowLayout
            assertNotNull(wide)
            assertNotNull(narrow)
            assertFalse(narrow!!.didOverflowWidth)
            assertTrue(narrow.layoutInput.style.fontSize < wide!!.layoutInput.style.fontSize)
        }
    }

    @Test
    fun productionLengthTitle_fitsTheatrePlaqueBounds() {
        var layout: TextLayoutResult? = null

        composeRule.setContent {
            Box(Modifier.width(180.dp).height(38.dp)) {
                AssetFittedText(
                    text = "Chronicles of Amber, The - Roger Zelazny (1)",
                    color = Color.Black,
                    style = TextStyle(fontSize = 21.sp),
                    minFontSize = 10.sp,
                    maxLines = 2,
                    onTextLayout = { layout = it },
                )
            }
        }

        composeRule.waitUntil { layout != null }
        composeRule.runOnIdle {
            val result = layout
            assertNotNull(result)
            assertFalse(result!!.didOverflowWidth)
            assertFalse(result.didOverflowHeight)
        }
    }
}
