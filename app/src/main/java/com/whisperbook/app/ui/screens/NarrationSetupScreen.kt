package com.whisperbook.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whisperbook.app.domain.model.NarrationLanguage
import com.whisperbook.app.ui.components.PapercraftButton
import com.whisperbook.app.ui.components.ParchmentPanel
import com.whisperbook.app.ui.theme.WhisperbookTheme

/** The persisted user-controlled gate between opening a book and generating any speech. */
@Composable
fun NarrationSetupScreen(
    contentPadding: PaddingValues,
    appState: WhisperbookAppState,
    onBack: () -> Unit,
    onStartGeneration: () -> Unit,
    onOpenBook: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var choosingNarrator by rememberSaveable { mutableStateOf(false) }
    val selectedVoice = appState.narrationSetupVoiceOptions.firstOrNull {
        it.id == appState.narrationSetupNarratorVoiceId
    }
    val bookReady = appState.currentBookTitle.isNotBlank() && selectedVoice != null
    val canEdit = appState.narrationSetupRequired && !appState.isBusy

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 11.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        StageTopBar("Narration setup", onBack = onBack)
        ParchmentPanel(
            modifier = Modifier.fillMaxWidth().testTag("narration-setup-summary"),
            contentPadding = PaddingValues(horizontal = 13.dp, vertical = 11.dp),
        ) {
            SectionHeading(
                if (appState.narrationSetupRequired) {
                    "Confirm before voices are generated"
                } else {
                    "This book is ready"
                },
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = if (appState.currentBookTitle.isBlank()) {
                    appState.statusMessage ?: "Opening your book on this device…"
                } else {
                    appState.currentBookTitle
                },
                color = WhisperbookTheme.colors.ink,
                style = WhisperbookTheme.typography.title.copy(fontSize = 18.sp, lineHeight = 22.sp),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "Nothing will be recorded until you confirm these choices.",
                color = WhisperbookTheme.colors.inkMuted,
                style = WhisperbookTheme.typography.body,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
            )
        }

        ParchmentPanel(
            modifier = Modifier.fillMaxWidth().testTag("narration-language-choice"),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        ) {
            SectionHeading("Language")
            NarrationLanguage.entries.forEachIndexed { index, language ->
                val selected = language.code == appState.narrationSetupLanguageCode
                GoldenSettingsRow(
                    title = "${language.displayName} · ${language.nativeName}",
                    value = if (selected) "Chosen" else null,
                    icon = if (language == NarrationLanguage.ENGLISH) {
                        Icons.Outlined.Mic
                    } else {
                        Icons.Outlined.Language
                    },
                    installed = selected,
                    onClick = if (canEdit && !selected) {
                        { appState.chooseNarrationSetupLanguage(language.code) }
                    } else {
                        null
                    },
                    modifier = Modifier.testTag("setup-language-${language.code}"),
                )
                if (index < NarrationLanguage.entries.lastIndex) CompactDivider()
            }
        }

        selectedVoice?.let { voice ->
            ParchmentPanel(
                modifier = Modifier.fillMaxWidth().testTag("narration-narrator-choice"),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            ) {
                SectionHeading("Narrator")
                Text(
                    text = "${voice.displayName} is chosen for the narration outside character dialogue.",
                    color = WhisperbookTheme.colors.inkMuted,
                    style = WhisperbookTheme.typography.body,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                )
                GoldenSettingsRow(
                    title = voice.displayName,
                    value = "Chosen",
                    icon = Icons.Outlined.Mic,
                    installed = true,
                    onClick = if (canEdit) ({ choosingNarrator = true }) else null,
                    modifier = Modifier.testTag("setup-narrator-${voice.id}"),
                )
                PapercraftButton(
                    text = "Test ${voice.displayName}",
                    onClick = { appState.previewNarrationSetupVoice(voice.id) },
                    enabled = bookReady && !appState.isBusy,
                    variant = com.whisperbook.app.ui.components.PapercraftButtonVariant.Parchment,
                    modifier = Modifier.fillMaxWidth().testTag("test-setup-narrator"),
                )
            }
        }

        appState.importError?.let { error ->
            Text(
                text = error,
                color = WhisperbookTheme.colors.error,
                style = WhisperbookTheme.typography.label,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            text = "Language and voice generation stay private on this device.",
            color = WhisperbookTheme.colors.paper,
            style = WhisperbookTheme.typography.label,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        PapercraftButton(
            text = if (appState.narrationSetupRequired) "Start voice generation" else "Open book",
            onClick = if (appState.narrationSetupRequired) onStartGeneration else onOpenBook,
            enabled = bookReady && !appState.isBusy,
            isLoading = appState.isBusy,
            loadingDescription = appState.statusMessage ?: "Opening your book",
            modifier = Modifier.fillMaxWidth().testTag("confirm-narration-setup"),
        )
        Spacer(Modifier.height(4.dp))
    }

    if (choosingNarrator) {
        VoicePickerSheet(
            characterName = "Narrator",
            voices = appState.narrationSetupVoiceOptions,
            selectedVoiceId = appState.narrationSetupNarratorVoiceId,
            onDismiss = { choosingNarrator = false },
            onPreviewVoice = { voice -> appState.previewNarrationSetupVoice(voice.id) },
            onVoiceSelected = { voice ->
                appState.chooseNarrationSetupNarrator(voice.id)
                choosingNarrator = false
            },
        )
    }
}
