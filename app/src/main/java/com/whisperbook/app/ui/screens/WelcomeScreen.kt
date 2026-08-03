package com.whisperbook.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whisperbook.app.R
import com.whisperbook.app.ui.components.LeafDivider
import com.whisperbook.app.ui.components.LeafOrnament
import com.whisperbook.app.ui.components.ParchmentPanel
import com.whisperbook.app.ui.components.PaperFold
import com.whisperbook.app.ui.components.paperFold
import com.whisperbook.app.ui.theme.WhisperbookTheme

/**
 * The approved welcome composition is a fixed 360 x 575 dp theatre poster above
 * the shared 65 dp navigation rail. Content remains native and interactive; the
 * generated papercraft frame is only a transparent decorative overlay.
 */
@Composable
fun WelcomeScreen(
    contentPadding: PaddingValues,
    onImport: () -> Unit,
    onExplore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            // Full-screen mode intentionally owns the top edge. Scaffold's
            // remembered status-bar inset must not displace the poster.
            .padding(bottom = contentPadding.calculateBottomPadding()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Text(
            text = "Whisperbook",
            color = WhisperbookTheme.colors.onStage,
            style = WhisperbookTheme.typography.display,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = 36.dp),
        )

        WelcomeTheatreHero(
            modifier = Modifier
                .width(328.dp)
                .height(270.dp)
                .offset(y = 80.dp),
        )

        WelcomeParchmentPanel(
            onImport = onImport,
            onExplore = onExplore,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 10.dp)
                .fillMaxWidth()
                .height(232.dp),
        )
    }
}

@Composable
private fun WelcomeTheatreHero(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.scene_moonlit_wood),
            contentDescription = "A moonlit papercraft woodland stage with a fox and rabbit",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 25.dp, vertical = 13.dp)
                .padding(top = 37.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 112.dp,
                        topEnd = 112.dp,
                        bottomStart = 2.dp,
                        bottomEnd = 2.dp,
                    ),
                ),
        )
        Image(
            painter = painterResource(R.drawable.theatre_welcome_overlay),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun WelcomeParchmentPanel(
    onImport: () -> Unit,
    onExplore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ParchmentPanel(
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
        contentPadding = PaddingValues(
            start = 18.dp,
            top = 34.dp,
            end = 18.dp,
            bottom = 0.dp,
        ),
    ) {
        Text(
            text = "Your stories, softly spoken.",
            color = WhisperbookTheme.colors.ink,
            style = WhisperbookTheme.typography.title.copy(
                fontSize = 20.sp,
                lineHeight = 27.sp,
            ),
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = "Private. Offline. Yours.",
            color = WhisperbookTheme.colors.ink,
            style = WhisperbookTheme.typography.body.copy(
                fontSize = 15.sp,
                lineHeight = 21.sp,
            ),
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth(),
        )
        LeafDivider(Modifier.padding(horizontal = 28.dp, vertical = 2.dp))
        WelcomeButton(
            text = "Import a book",
            onClick = onImport,
            dark = true,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(7.dp))
        WelcomeButton(
            text = "Explore the app",
            onClick = onExplore,
            dark = false,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(3.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LeafOrnament(
                modifier = Modifier.size(width = 24.dp, height = 12.dp),
                color = WhisperbookTheme.colors.outline,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "PDF and EPUB",
                color = WhisperbookTheme.colors.ink,
                style = WhisperbookTheme.typography.label.copy(
                    fontFamily = WhisperbookTheme.typography.body.fontFamily,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                ),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.width(8.dp))
            LeafOrnament(
                modifier = Modifier.size(width = 24.dp, height = 12.dp),
                color = WhisperbookTheme.colors.outline,
            )
        }
    }
}

@Composable
private fun WelcomeButton(
    text: String,
    onClick: () -> Unit,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = WhisperbookTheme.colors
    val shape = RoundedCornerShape(10.dp)
    val interactionSource = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier
            .width(258.dp)
            .height(46.dp)
            .paperFold(interactionSource, fold = PaperFold.Control)
            .shadow(
                elevation = if (dark) 4.dp else 2.dp,
                shape = shape,
                ambientColor = colors.shadow,
                spotColor = colors.shadow,
            ),
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (dark) colors.stageRaised else colors.paper,
            contentColor = if (dark) colors.onStage else colors.ink,
        ),
        border = BorderStroke(1.dp, if (dark) colors.ornament else colors.outline),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 5.dp),
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontFamily = WhisperbookTheme.typography.title.fontFamily,
                fontWeight = WhisperbookTheme.typography.title.fontWeight,
                fontSize = 19.sp,
                lineHeight = 23.sp,
            ),
            maxLines = 1,
        )
    }
}
