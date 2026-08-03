package com.whisperbook.app.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.whisperbook.app.ui.theme.WhisperbookTheme

@Composable
internal fun RemoveBookDialog(
    bookTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Outlined.DeleteOutline,
                contentDescription = null,
                tint = WhisperbookTheme.colors.error,
            )
        },
        title = { Text("Remove this book?") },
        text = {
            Text(
                "Remove \"$bookTitle\" and its generated audio from Whisperbook? " +
                    "Your original PDF or EPUB outside the app will not be deleted.",
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = WhisperbookTheme.colors.error),
            ) {
                Text("Remove book")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Keep book")
            }
        },
        containerColor = WhisperbookTheme.colors.paper,
        titleContentColor = WhisperbookTheme.colors.ink,
        textContentColor = WhisperbookTheme.colors.inkMuted,
        modifier = Modifier.testTag("remove-book-dialog"),
    )
}
