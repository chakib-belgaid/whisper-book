package com.whisperbook.app.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.whisperbook.app.R
import com.whisperbook.app.ui.components.EmbossedCircularButton
import com.whisperbook.app.ui.theme.WhisperbookTheme

@Composable
internal fun StageTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            if (onBack != null) {
                EmbossedCircularButton(
                    onClick = onBack,
                    contentDescription = "Back",
                    size = 44.dp,
                ) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                }
            }
        }
        Text(
            text = title,
            color = WhisperbookTheme.colors.onStage,
            style = WhisperbookTheme.typography.display,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { trailing?.invoke() }
    }
}

@Composable
internal fun TheatreScene(
    @DrawableRes sceneRes: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    height: Dp = 218.dp,
    showCurtain: Boolean = true,
) {
    val colors = WhisperbookTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(topStart = 80.dp, topEnd = 80.dp, bottomStart = 10.dp, bottomEnd = 10.dp))
            .background(colors.stageRaised)
            .border(3.dp, colors.ornament, RoundedCornerShape(topStart = 80.dp, topEnd = 80.dp, bottomStart = 10.dp, bottomEnd = 10.dp)),
    ) {
        Image(
            painter = painterResource(sceneRes),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (showCurtain) {
            Image(
                painter = painterResource(R.drawable.curtain_top),
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().height(48.dp).align(Alignment.TopCenter).clearAndSetSemantics { },
            )
        }
    }
}

@Composable
internal fun SectionHeading(text: String, modifier: Modifier = Modifier, dark: Boolean = true) {
    Text(
        text = text,
        color = if (dark) WhisperbookTheme.colors.ink else WhisperbookTheme.colors.onStage,
        style = WhisperbookTheme.typography.title,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
internal fun speakerColor(role: SpeakerRole): Color = when (role) {
    SpeakerRole.Narrator -> WhisperbookTheme.colors.narrator
    SpeakerRole.Elara -> WhisperbookTheme.colors.elara
    SpeakerRole.Fox -> WhisperbookTheme.colors.fox
}
