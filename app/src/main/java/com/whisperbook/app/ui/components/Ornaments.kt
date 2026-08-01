package com.whisperbook.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.whisperbook.app.ui.theme.WhisperbookTheme

@Composable
fun OrnamentHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = WhisperbookTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = WhisperbookTheme.spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WhisperbookTheme.spacing.sm),
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { leading?.invoke() }
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                color = colors.onStage,
                style = WhisperbookTheme.typography.display,
                textAlign = TextAlign.Center,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = colors.paper,
                    style = WhisperbookTheme.typography.body,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { trailing?.invoke() }
    }
}

@Composable
fun LeafDivider(
    modifier: Modifier = Modifier,
    color: Color = WhisperbookTheme.colors.outline,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        DividerRule(Modifier.weight(1f), color)
        Spacer(Modifier.width(8.dp))
        LeafOrnament(color = color, modifier = Modifier.size(width = 40.dp, height = 20.dp))
        Spacer(Modifier.width(8.dp))
        DividerRule(Modifier.weight(1f), color)
    }
}

@Composable
private fun DividerRule(modifier: Modifier, color: Color) {
    Spacer(modifier.height(1.dp).background(color.copy(alpha = 0.55f)))
}

@Composable
fun LeafOrnament(
    modifier: Modifier = Modifier,
    color: Color = WhisperbookTheme.colors.ornament,
) {
    Canvas(modifier.clearAndSetSemantics { }) {
        val centerY = size.height * 0.62f
        drawLine(
            color = color,
            start = Offset(size.width * 0.10f, centerY),
            end = Offset(size.width * 0.90f, size.height * 0.34f),
            strokeWidth = size.height * 0.08f,
            cap = StrokeCap.Round,
        )
        val leafSize = Size(size.width * 0.22f, size.height * 0.32f)
        drawOval(color, topLeft = Offset(size.width * 0.22f, size.height * 0.18f), size = leafSize)
        drawOval(color, topLeft = Offset(size.width * 0.43f, size.height * 0.54f), size = leafSize)
        drawOval(color, topLeft = Offset(size.width * 0.63f, size.height * 0.05f), size = leafSize)
    }
}

@Composable
fun RibbonTitle(
    title: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = WhisperbookTheme.colors.paper,
    contentColor: Color = WhisperbookTheme.colors.ink,
) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = modifier
            .shadow(
                WhisperbookTheme.elevations.paperContact,
                shape,
                ambientColor = WhisperbookTheme.colors.shadow,
                spotColor = WhisperbookTheme.colors.shadow,
            )
            .clip(shape)
            .background(backgroundColor)
            .border(1.dp, WhisperbookTheme.colors.outline, shape)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LeafOrnament(Modifier.size(width = 28.dp, height = 15.dp), contentColor.copy(alpha = 0.55f))
        Spacer(Modifier.width(8.dp))
        Text(title, color = contentColor, style = WhisperbookTheme.typography.title, textAlign = TextAlign.Center)
        Spacer(Modifier.width(8.dp))
        LeafOrnament(Modifier.size(width = 28.dp, height = 15.dp), contentColor.copy(alpha = 0.55f))
    }
}

@Composable
internal fun EmptyStoryIcon(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Outlined.AutoStories,
        contentDescription = null,
        tint = WhisperbookTheme.colors.action,
        modifier = modifier.clearAndSetSemantics { },
    )
}
