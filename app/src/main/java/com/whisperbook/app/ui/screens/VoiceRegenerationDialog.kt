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
    canStartFromNextChapter: Boolean,
    onConfirm: (VoiceRegenerationScope) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Where should $voiceName begin?") },
        text = {
            Text(
                if (canStartFromNextChapter) {
                    "Choose where to regenerate $characterName's narration. The previous audio stays " +
                        "available for 24 hours, so you can keep listening while the new chapters are prepared."
                } else {
                    "This is the final chapter, so there is no next chapter to regenerate. " +
                        "You can apply $voiceName to the whole book or keep the current voice."
                },
            )
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                TextButton(
                    onClick = { onConfirm(VoiceRegenerationScope.WHOLE_BOOK) },
                    modifier = Modifier.fillMaxWidth().testTag("regenerate-whole-book"),
                    colors = ButtonDefaults.textButtonColors(contentColor = WhisperbookTheme.colors.action),
                ) {
                    Text("Regenerate whole book")
                }
                TextButton(
                    onClick = { onConfirm(VoiceRegenerationScope.FROM_NEXT_CHAPTER) },
                    enabled = canStartFromNextChapter,
                    modifier = Modifier.fillMaxWidth().testTag("regenerate-from-next-chapter"),
                ) {
                    Text("Start with next chapter")
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
