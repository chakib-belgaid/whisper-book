package com.whisperbook.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.whisperbook.app.domain.model.VoiceRegenerationScope
import com.whisperbook.app.ui.theme.WhisperbookTheme

@Composable
internal fun VoiceRegenerationDialog(
    characterName: String,
    voiceName: String,
    canApplyFromThisChapter: Boolean,
    onConfirm: (VoiceRegenerationScope) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Where should $voiceName begin?") },
        text = {
            Text(
                "Choose which custom chapter voice sets should use $voiceName for $characterName. " +
                    "Previous audio stays available for 24 hours while the selected chapters are prepared.",
            )
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                TextButton(
                    onClick = { onConfirm(VoiceRegenerationScope.THIS_CHAPTER) },
                    modifier = Modifier.fillMaxWidth().testTag("regenerate-this-chapter"),
                    colors = ButtonDefaults.textButtonColors(contentColor = WhisperbookTheme.colors.action),
                ) {
                    Text("This chapter")
                }
                TextButton(
                    onClick = { onConfirm(VoiceRegenerationScope.FROM_THIS_CHAPTER) },
                    enabled = canApplyFromThisChapter,
                    modifier = Modifier.fillMaxWidth().testTag("regenerate-from-this-chapter"),
                ) {
                    Text("This chapter and later")
                }
                TextButton(
                    onClick = { onConfirm(VoiceRegenerationScope.WHOLE_BOOK) },
                    modifier = Modifier.fillMaxWidth().testTag("regenerate-whole-book"),
                ) {
                    Text("Whole book")
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Keep current voice")
                }
            }
        },
        containerColor = WhisperbookTheme.colors.paper,
        titleContentColor = WhisperbookTheme.colors.ink,
        textContentColor = WhisperbookTheme.colors.inkMuted,
        modifier = Modifier.testTag("voice-regeneration-dialog"),
    )
}
