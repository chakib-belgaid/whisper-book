package com.whisperbook.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whisperbook.app.ui.theme.WhisperbookTheme

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    appState: WhisperbookAppState,
    onManageVoices: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showNarratorPicker by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        item {
            SettingsMoonHeader()
        }
        item {
            GoldenSettingsSection("Voice & narration") {
                GoldenSettingsRow(
                    "Default narrator",
                    value = appState.defaultNarratorVoice,
                    icon = Icons.Outlined.RecordVoiceOver,
                    onClick = { showNarratorPicker = true },
                )
                CompactDivider()
                GoldenSettingsRow("Speaking speed", value = "${appState.speed}×", icon = Icons.Outlined.Speed, onClick = appState::cycleSpeed)
            }
        }
        item {
            GoldenSettingsSection("Offline models") {
                GoldenSettingsRow("English voice pack", value = "Installed", icon = Icons.Outlined.Mic, installed = true)
                CompactDivider()
                GoldenSettingsRow(
                    "Manage voices",
                    icon = Icons.Outlined.Inventory2,
                    onClick = onManageVoices,
                )
            }
        }
        item {
            GoldenSettingsSection("Storage") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 3.dp, vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Books\nVoices",
                        color = WhisperbookTheme.colors.ink,
                        style = WhisperbookTheme.typography.body.copy(fontSize = 13.sp, lineHeight = 17.sp),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${formatStorageBytes(appState.localStorageBytes)} used",
                        color = WhisperbookTheme.colors.action,
                        style = WhisperbookTheme.typography.label.copy(fontSize = 12.sp),
                        textAlign = TextAlign.End,
                    )
                }
                Spacer(Modifier.height(4.dp))
                StorageMeter(
                    usedBytes = appState.localStorageBytes,
                    limitBytes = appState.storageLimitBytes,
                )
                Spacer(Modifier.height(3.dp))
            }
        }
        item {
            GoldenSettingsSection("Listening") {
                GoldenSettingsRow(
                    "Sleep timer",
                    value = if (appState.sleepMinutes == 0) "Off" else "${appState.sleepMinutes} min",
                    icon = Icons.Outlined.Bedtime,
                    onClick = appState::cycleSleepTimer,
                )
                CompactDivider()
                GoldenSettingsToggleRow("Keep screen awake", icon = Icons.Outlined.WbSunny, checked = appState.keepScreenAwake, onCheckedChange = appState::updateKeepScreenAwake)
            }
        }
        item {
            GoldenSettingsSection("Accessibility") {
                GoldenSettingsToggleRow(
                    "Larger text",
                    leadingText = "Aa",
                    checked = appState.largerText,
                    onCheckedChange = appState::updateLargerText,
                )
            }
        }
    }

    if (showNarratorPicker) {
        VoicePickerSheet(
            characterName = "Narrator",
            voices = appState.voiceOptions,
            selectedVoiceName = appState.defaultNarratorVoice,
            onDismiss = { showNarratorPicker = false },
            onPreviewVoice = { voice -> appState.previewVoice(voice.id, "Narrator") },
            onVoiceSelected = { voice ->
                appState.chooseDefaultNarratorVoice(voice.id)
                showNarratorPicker = false
            },
        )
    }
}

internal fun formatStorageBytes(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0L).toDouble()
    val gib = 1024.0 * 1024.0 * 1024.0
    val mib = 1024.0 * 1024.0
    return if (safe >= gib) "%.1f GB".format(safe / gib) else "%.0f MB".format(safe / mib)
}
