package com.whisperbook.app.ui.screens

import android.widget.Toast
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
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whisperbook.app.diagnostics.BetaDiagnostics
import com.whisperbook.app.ui.theme.WhisperbookTheme

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    appState: WhisperbookAppState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        item {
            SettingsMoonHeader()
        }
        item {
            GoldenSettingsSection("Playback & preparation") {
                GoldenSettingsRow("Speaking speed", value = "${appState.speed}×", icon = Icons.Outlined.Speed, onClick = appState::cycleSpeed)
                CompactDivider()
                GoldenSettingsRow(
                    "Narration chunk size",
                    value = "${appState.narrationChunkChars} chars",
                    icon = Icons.Outlined.Tune,
                    onClick = appState::cycleNarrationChunkSize,
                )
                Text(
                    "Smaller chunks start sooner. Whisperbook records only the first chunk before you listen.",
                    color = WhisperbookTheme.colors.ink.copy(alpha = 0.72f),
                    style = WhisperbookTheme.typography.body.copy(fontSize = 12.sp, lineHeight = 16.sp),
                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 4.dp),
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
                        style = WhisperbookTheme.typography.body.copy(fontSize = 14.sp, lineHeight = 18.sp),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${formatStorageBytes(appState.localStorageBytes)} used",
                        color = WhisperbookTheme.colors.action,
                        style = WhisperbookTheme.typography.label.copy(fontSize = 13.sp, lineHeight = 16.sp),
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
        item {
            GoldenSettingsSection("Beta diagnostics") {
                GoldenSettingsRow(
                    "Share diagnostic log",
                    value = "On-device only",
                    icon = Icons.Outlined.Share,
                    onClick = {
                        runCatching {
                            context.startActivity(BetaDiagnostics.createShareChooser(context))
                        }.onFailure {
                            Toast.makeText(context, "Could not prepare the diagnostic log", Toast.LENGTH_LONG).show()
                        }
                    },
                )
                CompactDivider()
                GoldenSettingsRow(
                    "App version",
                    value = BetaDiagnostics.versionLabel,
                    icon = Icons.Outlined.Info,
                )
                CompactDivider()
                GoldenSettingsRow("Commit ID", value = BetaDiagnostics.commitId)
                Text(
                    "Includes crashes, slow frames, playback, preparation, and narration timings. " +
                        "It never includes book text or audio; you choose when to share it." +
                        if (BetaDiagnostics.hasLocalChanges) " This build also has local changes." else "",
                    color = WhisperbookTheme.colors.ink.copy(alpha = 0.72f),
                    style = WhisperbookTheme.typography.body.copy(fontSize = 12.sp, lineHeight = 16.sp),
                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 4.dp),
                )
            }
        }
    }

}

internal fun formatStorageBytes(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0L).toDouble()
    val gib = 1024.0 * 1024.0 * 1024.0
    val mib = 1024.0 * 1024.0
    return if (safe >= gib) "%.1f GB".format(safe / gib) else "%.0f MB".format(safe / mib)
}
