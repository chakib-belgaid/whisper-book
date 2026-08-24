package com.whisperbook.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.whisperbook.app.ui.theme.WhisperbookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ComponentSemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun circularButton_hasAccessibleNameAndMinimumTarget() {
        composeRule.setContent {
            WhisperbookTheme {
                EmbossedCircularButton(onClick = {}, contentDescription = "Play story") {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                }
            }
        }

        composeRule.onNodeWithContentDescription("Play story")
            .assertIsEnabled()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun activePassage_exposesSpeakerTextAndNonColorState() {
        composeRule.setContent {
            WhisperbookTheme {
                SpeakerPassageCard(
                    speakerName = "Elara",
                    passage = "The lantern is fading.",
                    accentColor = WhisperbookTheme.colors.elara,
                    isActive = true,
                    onClick = {},
                )
            }
        }

        composeRule.onNodeWithText("ELARA").assertTextContains("ELARA")
        composeRule.onNodeWithText("Now speaking").assertTextContains("Now speaking")
    }

    @Test
    fun bottomBar_exposesOneSelectedTabAndAccessibleTargets() {
        var clickedRoute: String? = null
        composeRule.setContent {
            WhisperbookTheme {
                StorybookBottomBar(
                    destinations = listOf(
                        StorybookDestination("library", "Library", Icons.Outlined.Home),
                        StorybookDestination("listen", "Listen", Icons.Outlined.Headphones),
                        StorybookDestination("settings", "Settings", Icons.Outlined.Settings),
                    ),
                    selectedRoute = "listen",
                    onDestinationSelected = { clickedRoute = it.route },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Library")
            .assertIsNotSelected()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("Listen")
            .assertIsSelected()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("Settings")
            .assertIsNotSelected()
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        assertEquals("settings", clickedRoute)
    }
}
