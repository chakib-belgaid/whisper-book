package com.whisperbook.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.whisperbook.app.R
import com.whisperbook.app.ui.theme.WhisperbookTheme

private const val TheatrePlaqueWidthFraction = 0.62f
private const val TheatrePlaqueTextWidthFraction = 0.78f
private const val TheatrePlaqueTextHeightFraction = 0.48f
private const val TheatrePlaqueAspectRatio = 800f / 286f

internal fun theatrePlaqueHeight(containerWidth: Dp): Dp =
    containerWidth * TheatrePlaqueWidthFraction / TheatrePlaqueAspectRatio

/**
 * Responsive frame assembled from independently scalable arch and plaque rasters.
 * Dynamic titles are measured only against the plaque's inner text slot.
 */
@Composable
internal fun TheatreFrameOverlay(
    title: String?,
    modifier: Modifier = Modifier,
    titleColor: Color = WhisperbookTheme.colors.ink,
    titleStyle: TextStyle = WhisperbookTheme.typography.title,
    minimumTitleSize: TextUnit = 12.sp,
    plaqueModifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Image(
            painter = painterResource(R.drawable.theatre_arch),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize().clearAndSetSemantics { },
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(TheatrePlaqueWidthFraction)
                .aspectRatio(TheatrePlaqueAspectRatio)
                .then(plaqueModifier),
        ) {
            Image(
                painter = painterResource(R.drawable.theatre_plaque),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize().clearAndSetSemantics { },
            )
            if (title != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(TheatrePlaqueTextWidthFraction)
                        .fillMaxHeight(TheatrePlaqueTextHeightFraction),
                    contentAlignment = Alignment.Center,
                ) {
                    AssetFittedText(
                        text = title,
                        color = titleColor,
                        style = titleStyle,
                        minFontSize = minimumTitleSize,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().then(titleModifier),
                    )
                }
            }
        }
    }
}
