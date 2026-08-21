package com.whisperbook.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.whisperbook.app.domain.model.SpeakerCorrectionScope
import com.whisperbook.app.ui.components.OrigamiSheet
import com.whisperbook.app.ui.components.PaperFold
import com.whisperbook.app.ui.components.paperSelectable
import com.whisperbook.app.ui.theme.WhisperbookTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AttributedSpeakerPickerSheet(
    passage: PassageUi,
    cast: List<CastMemberUi>,
    onDismiss: () -> Unit,
    onSpeakerSelected: (CastMemberUi) -> Unit,
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
        modifier = Modifier.testTag("attributed-voice-picker"),
    ) {
        OrigamiSheet(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Who should read this phrase?",
                    color = colors.ink,
                    style = WhisperbookTheme.typography.title,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 5.dp),
                )
                Text(
                    passage.text,
                    color = colors.inkMuted,
                    style = WhisperbookTheme.typography.body,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp),
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp),
                    contentPadding = PaddingValues(bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    items(cast, key = CastMemberUi::id) { member ->
                        val selected = member.id == passage.speakerId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 58.dp)
                                .paperSelectable(
                                    selected = selected,
                                    enabled = !selected,
                                    role = Role.RadioButton,
                                    onClick = { onSpeakerSelected(member) },
                                    fold = PaperFold.Card,
                                )
                                .background(
                                    color = if (selected) colors.paperHighlight else colors.paper,
                                    shape = RoundedCornerShape(10.dp),
                                )
                                .semantics {
                                    contentDescription = buildString {
                                        append(member.character)
                                        append(", using ")
                                        append(member.voice)
                                        if (selected) append(", current attribution")
                                    }
                                }
                                .testTag("attributed-speaker-${member.id}")
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Image(
                                painter = painterResource(member.portraitRes),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .padding(end = 10.dp)
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(member.character, color = colors.ink, style = WhisperbookTheme.typography.body)
                                Text(
                                    if (selected) "Current voice · ${member.voice}" else "Voice · ${member.voice}",
                                    color = colors.inkMuted,
                                    style = WhisperbookTheme.typography.label,
                                )
                            }
                            RadioButton(selected = selected, onClick = null, enabled = !selected)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SpeakerCorrectionScopeDialog(
    passage: PassageUi,
    target: CastMemberUi,
    bookTitle: String,
    onConfirm: (SpeakerCorrectionScope) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Apply this voice correction?") },
        text = {
            Text(
                "Use ${target.character}'s ${target.voice} voice for just this phrase, or for matching " +
                    "phrases in ${bookTitle.ifBlank { "this book" }}. Matching ignores case and punctuation " +
                    "but only changes phrases currently attributed to ${passage.speakerName}.",
            )
        },
        confirmButton = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(
                    onClick = { onConfirm(SpeakerCorrectionScope.THIS_PASSAGE) },
                    modifier = Modifier.fillMaxWidth().testTag("correct-this-phrase"),
                    colors = ButtonDefaults.textButtonColors(contentColor = WhisperbookTheme.colors.action),
                ) {
                    Text("Just this phrase")
                }
                TextButton(
                    onClick = { onConfirm(SpeakerCorrectionScope.MATCHING_PHRASES) },
                    modifier = Modifier.fillMaxWidth().testTag("correct-matching-phrases"),
                ) {
                    Text("All matching phrases")
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Keep current attribution")
                }
            }
        },
        containerColor = WhisperbookTheme.colors.paper,
        titleContentColor = WhisperbookTheme.colors.ink,
        textContentColor = WhisperbookTheme.colors.inkMuted,
        modifier = Modifier.testTag("speaker-correction-scope-dialog"),
    )
}
