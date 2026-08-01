package com.whisperbook.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.whisperbook.app.R
import com.whisperbook.app.ui.theme.WhisperbookTheme

/** Full-screen, low-luminance paper stage with a subtle native gradient and texture. */
@Composable
fun WhisperBackdrop(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    showBotanicalCorners: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = WhisperbookTheme.colors
    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    colors = listOf(colors.stageRaised, colors.stage, colors.stage),
                ),
            ),
        contentAlignment = contentAlignment,
    ) {
        Image(
            painter = painterResource(R.drawable.texture_navy),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = 0.14f,
            modifier = Modifier
                .matchParentSize()
                .clearAndSetSemantics { },
        )
        if (showBotanicalCorners) {
            Image(
                painter = painterResource(R.drawable.botanical_corners),
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                alpha = 0.40f,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .aspectRatio(1.5f)
                    .clearAndSetSemantics { },
            )
            Image(
                painter = painterResource(R.drawable.botanical_corners),
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                alpha = 0.24f,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .aspectRatio(1.5f)
                    .graphicsLayer { rotationZ = 180f }
                    .clearAndSetSemantics { },
            )
        }
        content()
    }
}

/**
 * A tactile paper surface. Texture and border are decorative; caller content remains native.
 */
@Composable
fun ParchmentPanel(
    modifier: Modifier = Modifier,
    shape: Shape = WhisperbookTheme.shapes.panel,
    contentPadding: PaddingValues = PaddingValues(WhisperbookTheme.components.panelPadding),
    elevated: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = WhisperbookTheme.colors
    val elevation = if (elevated) WhisperbookTheme.elevations.paperContact else 0.dp
    Box(
        modifier = modifier
            .shadow(elevation, shape, clip = false, ambientColor = colors.shadow, spotColor = colors.shadow)
            .clip(shape)
            .background(colors.paper)
            .border(WhisperbookTheme.components.outlineWidth, colors.outline, shape),
    ) {
        Image(
            painter = painterResource(R.drawable.texture_parchment),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = 0.12f,
            modifier = Modifier
                .matchParentSize()
                .clearAndSetSemantics { },
        )
        Box(
            Modifier
                .matchParentSize()
                .padding(2.dp)
                .border(1.dp, colors.paperHighlight.copy(alpha = 0.48f), insetShape(shape)),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            content = content,
        )
    }
}

private fun insetShape(shape: Shape): Shape = when (shape) {
    is RoundedCornerShape -> RoundedCornerShape(16.dp)
    else -> shape
}
