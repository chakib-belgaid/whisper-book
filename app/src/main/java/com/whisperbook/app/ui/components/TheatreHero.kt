package com.whisperbook.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.whisperbook.app.R
import com.whisperbook.app.ui.theme.WhisperbookTheme

/** Native content slot framed by transparent papercraft layers. */
@Composable
fun TheatreHero(
    sceneRes: Int,
    sceneContentDescription: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    ribbonText: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val colors = WhisperbookTheme.colors
    val shape = WhisperbookTheme.shapes.panel
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.5f)
            .sizeIn(minHeight = 220.dp)
            .shadow(
                WhisperbookTheme.elevations.heroStage,
                shape,
                ambientColor = colors.shadow,
                spotColor = colors.shadow,
            )
            .clip(shape)
            .background(colors.stage)
            .border(1.dp, colors.ornament, shape),
    ) {
        Image(
            painter = painterResource(sceneRes),
            contentDescription = sceneContentDescription,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize(),
        )
        Image(
            painter = painterResource(R.drawable.curtain_top),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 38.dp)
                .clearAndSetSemantics { },
        )
        Image(
            painter = painterResource(R.drawable.theatre_frame),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .fillMaxSize()
                .clearAndSetSemantics { },
        )
        if (title != null) {
            Text(
                text = title,
                color = colors.ink,
                style = WhisperbookTheme.typography.title,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(0.62f)
                    .padding(top = 10.dp),
            )
        }
        if (ribbonText != null) {
            RibbonTitle(
                title = ribbonText,
                backgroundColor = colors.accent,
                contentColor = colors.onStage,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
                    .wrapContentSize(),
            )
        }
    }
}
