package com.whisperbook.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whisperbook.app.ui.components.LeafOrnament
import com.whisperbook.app.ui.components.PapercraftButton
import com.whisperbook.app.ui.components.ParchmentPanel
import com.whisperbook.app.domain.model.NarrationLanguage
import com.whisperbook.app.ui.theme.WhisperbookTheme

private data class PendingVoiceChange(
    val characterId: String,
    val characterName: String,
    val voice: VoiceOptionUi,
)

@Composable
fun VoiceCastScreen(
    contentPadding: PaddingValues,
    appState: WhisperbookAppState,
    onBack: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var choosingVoiceFor by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingVoiceChange by remember { mutableStateOf<PendingVoiceChange?>(null) }
    Column(
        modifier = modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StageTopBar("Voice cast", onBack = onBack)
        Spacer(Modifier.height(3.dp))
        ParchmentPanel(modifier = Modifier.fillMaxWidth(.74f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)) {
            Text(
                "Automatically matched. You can adjust any voice.",
                color = WhisperbookTheme.colors.ink,
                style = WhisperbookTheme.typography.label.copy(fontSize = 10.sp, lineHeight = 13.sp),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            if (appState.currentBookTitle.isNotBlank() && appState.currentChapterTitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "${appState.currentBookTitle} · Chapter ${appState.currentChapterNumber}: " +
                        appState.currentChapterTitle,
                    color = WhisperbookTheme.colors.inkMuted,
                    style = WhisperbookTheme.typography.label.copy(fontSize = 9.sp, lineHeight = 12.sp),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val status = when {
                appState.isBusy -> appState.statusMessage ?: "Preparing your voice preview…"
                appState.importError != null -> appState.importError
                else -> appState.statusMessage
            }
            status?.let { message ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = message,
                    color = if (appState.importError != null) WhisperbookTheme.colors.action else WhisperbookTheme.colors.inkMuted,
                    style = WhisperbookTheme.typography.label.copy(fontSize = 10.sp, lineHeight = 13.sp),
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (appState.canRevertVoiceChange) {
                TextButton(
                    onClick = appState::revertVoiceChange,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text("Revert voice change")
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        ParchmentPanel(
            modifier = Modifier.fillMaxWidth().testTag("book-language-settings"),
            contentPadding = PaddingValues(horizontal = 9.dp, vertical = 7.dp),
        ) {
            SectionHeading("Book language")
            Text(
                "This choice applies only to ${appState.currentBookTitle.ifBlank { "this book" }}.",
                color = WhisperbookTheme.colors.inkMuted,
                style = WhisperbookTheme.typography.label.copy(fontSize = 10.sp, lineHeight = 13.sp),
                modifier = Modifier.padding(bottom = 3.dp),
            )
            NarrationLanguage.entries.forEachIndexed { index, language ->
                val installed = language.code in appState.installedLanguagePackCodes
                val selected = language.code == appState.narrationLanguageCode
                GoldenSettingsRow(
                    title = "${language.displayName} · ${language.nativeName}",
                    value = when {
                        selected -> "Selected for book"
                        installed -> "Use for book"
                        else -> "Download & use"
                    },
                    icon = if (language == NarrationLanguage.ENGLISH) {
                        Icons.Outlined.Mic
                    } else {
                        Icons.Outlined.Language
                    },
                    installed = selected,
                    onClick = when {
                        selected -> null
                        installed -> ({ appState.selectNarrationLanguage(language.code) })
                        else -> ({ appState.downloadLanguagePack(language.code) })
                    },
                )
                if (index < NarrationLanguage.entries.lastIndex) CompactDivider()
            }
            Text(
                "Changing language rebuilds this book's narration on device; other books stay unchanged.",
                color = WhisperbookTheme.colors.ink.copy(alpha = 0.72f),
                style = WhisperbookTheme.typography.body.copy(fontSize = 10.sp, lineHeight = 13.sp),
                modifier = Modifier.padding(horizontal = 3.dp, vertical = 3.dp),
            )
        }
        if (appState.cast.isEmpty()) {
            ParchmentPanel(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                contentPadding = PaddingValues(22.dp),
            ) {
                Text(
                    "No characters found yet",
                    color = WhisperbookTheme.colors.ink,
                    style = WhisperbookTheme.typography.title,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().semantics { heading() },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Finish preparing the book to assign private, on-device voices.",
                    color = WhisperbookTheme.colors.inkMuted,
                    style = WhisperbookTheme.typography.body,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.weight(1f))
            OnDeviceVoiceNotice()
            PapercraftButton("Back to book", onBack, modifier = Modifier.fillMaxWidth())
            return
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 5.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            itemsIndexed(appState.cast, key = { _, member -> member.id }) { _, member ->
                GoldenVoiceCastCard(
                    member = member,
                    onPreviewVoice = { appState.previewCharacter(member.id) },
                    onChangeVoice = { choosingVoiceFor = member.id },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        OnDeviceVoiceNotice()
        Spacer(Modifier.height(4.dp))
        PapercraftButton("Apply to book", onApply, modifier = Modifier.fillMaxWidth())
    }

    choosingVoiceFor?.let { characterId ->
        appState.cast.firstOrNull { it.id == characterId }?.let { member ->
            VoicePickerSheet(
                characterName = member.character,
                voices = appState.voiceOptions,
                selectedVoiceId = member.voiceId,
                onDismiss = { choosingVoiceFor = null },
                onPreviewVoice = { voice ->
                    appState.previewVoice(voice.id, member.character)
                },
                onVoiceSelected = { voice ->
                    choosingVoiceFor = null
                    if (voice.id != member.voiceId) {
                        pendingVoiceChange = PendingVoiceChange(member.id, member.character, voice)
                    }
                },
            )
        }
    }

    pendingVoiceChange?.let { pending ->
        VoiceRegenerationDialog(
            characterName = pending.characterName,
            voiceName = pending.voice.displayName,
            canApplyFromThisChapter = appState.hasNextChapter,
            onConfirm = { scope ->
                pendingVoiceChange = null
                appState.assignVoice(pending.characterId, pending.voice.id, scope)
            },
            onDismiss = { pendingVoiceChange = null },
        )
    }
}

@Composable
private fun OnDeviceVoiceNotice() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LeafOrnament(Modifier.size(27.dp, 13.dp), WhisperbookTheme.colors.ornament)
        Text(
            "Voices stay on this device.",
            color = WhisperbookTheme.colors.paper,
            style = WhisperbookTheme.typography.label.copy(fontSize = 11.sp),
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        LeafOrnament(Modifier.size(27.dp, 13.dp), WhisperbookTheme.colors.ornament)
    }
}
