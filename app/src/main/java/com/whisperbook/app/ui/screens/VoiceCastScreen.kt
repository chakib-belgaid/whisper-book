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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whisperbook.app.ui.components.LeafOrnament
import com.whisperbook.app.ui.components.PapercraftButton
import com.whisperbook.app.ui.components.ParchmentPanel
import com.whisperbook.app.ui.theme.WhisperbookTheme

@Composable
fun VoiceCastScreen(
    contentPadding: PaddingValues,
    appState: WhisperbookAppState,
    onBack: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                    onChangeVoice = { appState.cycleVoice(member.id) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        OnDeviceVoiceNotice()
        Spacer(Modifier.height(4.dp))
        PapercraftButton("Apply to book", onApply, modifier = Modifier.fillMaxWidth())
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
