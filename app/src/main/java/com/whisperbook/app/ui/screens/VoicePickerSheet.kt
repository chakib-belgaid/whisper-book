package com.whisperbook.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.whisperbook.app.ui.theme.WhisperbookTheme
import com.whisperbook.app.ui.components.OrigamiSheet
import com.whisperbook.app.ui.components.PaperFold
import com.whisperbook.app.ui.components.paperSelectable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VoicePickerSheet(
    characterName: String,
    voices: List<VoiceOptionUi>,
    selectedVoiceId: String,
    onDismiss: () -> Unit,
    onPreviewVoice: (VoiceOptionUi) -> Unit,
    onVoiceSelected: (VoiceOptionUi) -> Unit,
) {
    val colors = WhisperbookTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.paper,
        contentColor = colors.ink,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 5.dp)
                    .fillMaxWidth(.16f)
                    .height(4.dp)
                    .background(colors.ornament, RoundedCornerShape(50)),
            )
        },
        modifier = Modifier.testTag("voice-picker"),
    ) {
        OrigamiSheet(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Choose a voice for $characterName",
                    color = colors.ink,
                    style = WhisperbookTheme.typography.title,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
                Text(
                    text = "Test as many voices as you like, then choose one.",
                    color = colors.inkMuted,
                    style = WhisperbookTheme.typography.label,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp),
                    contentPadding = PaddingValues(bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    items(voices, key = VoiceOptionUi::id) { voice ->
                        val selected = voice.id == selectedVoiceId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 58.dp)
                                .paperSelectable(
                                    selected = selected,
                                    role = Role.RadioButton,
                                    onClick = { onVoiceSelected(voice) },
                                    fold = PaperFold.Card,
                                )
                                .background(
                                    color = if (selected) colors.paperHighlight else colors.paper,
                                    shape = RoundedCornerShape(10.dp),
                                )
                                .semantics {
                                    contentDescription = buildString {
                                        append(voice.displayName)
                                        append(" voice, embedded and offline")
                                        if (selected) append(", currently selected")
                                    }
                                }
                                .testTag("voice-option-${voice.id}")
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Image(
                                painter = painterResource(voice.portraitRes),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .padding(end = 10.dp)
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = voice.displayName,
                                    color = colors.ink,
                                    style = WhisperbookTheme.typography.body,
                                )
                                Text(
                                    text = "Embedded · Offline",
                                    color = colors.inkMuted,
                                    style = WhisperbookTheme.typography.label,
                                )
                            }
                            TextButton(
                                onClick = { onPreviewVoice(voice) },
                                modifier = Modifier
                                    .testTag("voice-preview-${voice.id}")
                                    .semantics {
                                        contentDescription =
                                            "Test ${voice.displayName} voice for $characterName"
                                    },
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Text("Test")
                            }
                            RadioButton(selected = selected, onClick = null)
                        }
                    }
                }
            }
        }
    }
}
