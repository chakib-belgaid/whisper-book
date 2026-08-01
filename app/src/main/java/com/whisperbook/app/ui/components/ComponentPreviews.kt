package com.whisperbook.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.whisperbook.app.R
import com.whisperbook.app.ui.theme.WhisperbookTheme

@Preview(name = "Core components", widthDp = 360, heightDp = 640, showBackground = true)
@Composable
private fun CoreComponentsPreview() {
    WhisperbookTheme {
        WhisperBackdrop {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OrnamentHeader(title = "Whisperbook")
                OfflineBadge(text = "Offline")
                TheatreHero(
                    sceneRes = R.drawable.scene_moonlit_wood,
                    sceneContentDescription = "Moonlit woodland stage",
                    title = "The Moonlit Wood",
                    ribbonText = "Chapter 7",
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
                PapercraftButton(
                    text = "Continue listening",
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                )
                StorybookBottomBar(
                    destinations = listOf(
                        StorybookDestination("library", "Library", Icons.Outlined.AutoStories),
                        StorybookDestination("listen", "Listen", Icons.Outlined.Headphones),
                        StorybookDestination("settings", "Settings", Icons.Outlined.Settings),
                    ),
                    selectedRoute = "listen",
                    onDestinationSelected = {},
                )
            }
        }
    }
}

@Preview(name = "Read along", widthDp = 360, heightDp = 640, showBackground = true)
@Composable
private fun ReaderComponentsPreview() {
    WhisperbookTheme {
        WhisperBackdrop(showBotanicalCorners = false) {
            Column(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SpeakerPassageCard(
                    speakerName = "Narrator",
                    passage = "The trees leaned close as the path narrowed beneath the moon.",
                    accentColor = WhisperbookTheme.colors.narrator,
                    isActive = false,
                    onClick = {},
                )
                SpeakerPassageCard(
                    speakerName = "Elara",
                    passage = "We should turn back before the lantern fades.",
                    accentColor = WhisperbookTheme.colors.elara,
                    isActive = true,
                    progress = 0.68f,
                    onClick = {},
                )
                FloatingMiniPlayer(
                    speakerName = "Elara",
                    voiceName = "Celeste",
                    positionText = "18:42",
                    durationText = "24:16",
                    portraitRes = R.drawable.portrait_elara,
                    accentColor = WhisperbookTheme.colors.elara,
                    isPlaying = true,
                    progress = 0.63f,
                    onPlayPause = {},
                    onRewind = {},
                    onForward = {},
                    onSeek = {},
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Preview(name = "Processing", widthDp = 360, heightDp = 640, showBackground = true)
@Composable
private fun ProcessingComponentsPreview() {
    WhisperbookTheme {
        WhisperBackdrop {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                RibbonTitle("Preparing your audiobook", modifier = Modifier.fillMaxWidth())
                ParchmentPanel(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(18.dp),
                ) {
                    ProcessingStepper(
                        listOf(
                            ProcessingStep("Reading chapters", ProcessingStepState.Complete),
                            ProcessingStep("Finding characters", ProcessingStepState.Current),
                            ProcessingStep("Assigning voices", ProcessingStepState.Pending),
                            ProcessingStep("Ready to listen", ProcessingStepState.Pending),
                        ),
                    )
                }
                VoiceCastCard(
                    speakerName = "Fox",
                    assignedVoice = "Rowan",
                    confidencePercent = 87,
                    lineCount = 39,
                    portraitRes = R.drawable.portrait_fox,
                    accentColor = WhisperbookTheme.colors.fox,
                    onPreviewVoice = {},
                    onChangeVoice = {},
                )
            }
        }
    }
}
