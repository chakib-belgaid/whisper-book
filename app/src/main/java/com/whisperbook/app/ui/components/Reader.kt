package com.whisperbook.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whisperbook.app.ui.theme.WhisperbookTheme

@Composable
fun SpeakerPassageCard(
    speakerName: String,
    passage: String,
    accentColor: Color,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float = 0f,
    activeLabel: String = "Now speaking",
    playPassageDescription: String = "Play passage read by $speakerName",
) {
    val colors = WhisperbookTheme.colors
    val shape = WhisperbookTheme.shapes.card
    val semanticState = if (isActive) activeLabel else "Not currently speaking"
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "$speakerName. $passage"
                stateDescription = semanticState
            },
    ) {
        Row(
            Modifier
                .padding(start = 14.dp)
                .fillMaxWidth()
                .shadow(
                    if (isActive) WhisperbookTheme.elevations.raisedControl else WhisperbookTheme.elevations.paperContact,
                    shape,
                    ambientColor = colors.shadow,
                    spotColor = colors.shadow,
                )
                .clip(shape)
                .background(colors.paperHighlight)
                .background(accentColor.copy(alpha = if (isActive) 0.18f else 0.075f))
                .border(if (isActive) 1.5.dp else 1.dp, accentColor.copy(alpha = 0.82f), shape)
                .height(IntrinsicSize.Min),
        ) {
            Spacer(
                Modifier
                    .width(16.dp)
                    .fillMaxHeight()
                    .background(accentColor.copy(alpha = 0.88f)),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 21.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LeafOrnament(Modifier.size(width = 19.dp, height = 10.dp), accentColor.copy(alpha = .62f))
                    Spacer(Modifier.width(5.dp))
                    Text(speakerName.uppercase(), color = accentColor, style = WhisperbookTheme.typography.label.copy(fontSize = 12.sp), modifier = Modifier.semantics { heading() })
                    Spacer(Modifier.width(5.dp))
                    LeafOrnament(Modifier.size(width = 19.dp, height = 10.dp), accentColor.copy(alpha = .62f))
                    Spacer(Modifier.weight(1f))
                    if (isActive) {
                        Icon(Icons.Outlined.GraphicEq, contentDescription = null, tint = accentColor, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(activeLabel, color = accentColor, style = WhisperbookTheme.typography.label.copy(fontSize = 11.sp))
                    }
                }
                Text(
                    text = passage,
                    color = if (isActive) accentColor.copy(red = accentColor.red * .58f, green = accentColor.green * .58f, blue = accentColor.blue * .58f) else colors.ink,
                    style = WhisperbookTheme.typography.reader.copy(fontSize = 20.sp, lineHeight = 25.sp),
                )
                if (isActive) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(accentColor.copy(alpha = 0.28f)),
                    ) {
                        Spacer(
                            Modifier
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .height(2.dp)
                                .background(accentColor),
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = 1.dp)
                .size(45.dp)
                .clip(WhisperbookTheme.shapes.control)
                .background(colors.paper)
                .border(1.5.dp, colors.outline, WhisperbookTheme.shapes.control)
                .semantics { contentDescription = playPassageDescription },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Outlined.VolumeUp, contentDescription = null, tint = accentColor, modifier = Modifier.size(25.dp))
        }
    }
}

@Composable
fun FloatingMiniPlayer(
    speakerName: String,
    voiceName: String,
    positionText: String,
    durationText: String,
    portraitRes: Int?,
    accentColor: Color,
    isPlaying: Boolean,
    progress: Float,
    onPlayPause: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val useStackedLayout = LocalDensity.current.fontScale >= 1.5f
    Box(modifier = modifier) {
    ParchmentPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 11.dp, topEnd = 11.dp, bottomStart = 15.dp, bottomEnd = 15.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 7.dp),
    ) {
        if (useStackedLayout) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MiniPlayerPortrait(speakerName, portraitRes, accentColor)
                MiniPlayerMetadata(speakerName, voiceName, positionText, durationText, accentColor, Modifier.weight(1f))
                MiniPlayPauseButton(isPlaying, accentColor, onPlayPause)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                FifteenSecondButton(onRewind, "Rewind 15 seconds", forward = false)
                FifteenSecondButton(onForward, "Forward 15 seconds", forward = true)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                MiniPlayerPortrait(speakerName, portraitRes, accentColor)
                MiniPlayerMetadata(speakerName, voiceName, positionText, durationText, accentColor, Modifier.weight(1f))
                FifteenSecondButton(onRewind, "Rewind 15 seconds", forward = false)
                MiniPlayPauseButton(isPlaying, accentColor, onPlayPause)
                FifteenSecondButton(onForward, "Forward 15 seconds", forward = true)
            }
        }
        PaperProgressBar(
            value = progress.coerceIn(0f, 1f),
            onValueChange = onSeek,
            accentColor = accentColor,
            modifier = Modifier.fillMaxWidth().height(19.dp),
        )
    }
    Canvas(Modifier.matchParentSize()) {
        val curtain = Color(0xFF71332F)
        val gold = Color(0xFFD6B06A)
        val depth = size.height * .40f
        val width = size.width * .075f
        val left = Path().apply { moveTo(0f, 0f); lineTo(width, 0f); lineTo(0f, depth); close() }
        val right = Path().apply { moveTo(size.width, 0f); lineTo(size.width - width, 0f); lineTo(size.width, depth); close() }
        drawPath(left, curtain.copy(alpha = .92f))
        drawPath(right, curtain.copy(alpha = .92f))
        drawLine(gold, Offset(0f, depth), Offset(width, 0f), strokeWidth = 1.5.dp.toPx())
        drawLine(gold, Offset(size.width, depth), Offset(size.width - width, 0f), strokeWidth = 1.5.dp.toPx())
    }
    }
}

@Composable
private fun MiniPlayerPortrait(
    speakerName: String,
    portraitRes: Int?,
    accentColor: Color,
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(WhisperbookTheme.colors.paperHighlight)
            .border(3.dp, accentColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (portraitRes != null) {
            Image(
                painter = painterResource(portraitRes),
                contentDescription = speakerName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(54.dp).clip(CircleShape),
            )
        }
    }
}

@Composable
private fun MiniPlayerMetadata(
    speakerName: String,
    voiceName: String,
    positionText: String,
    durationText: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(speakerName, color = accentColor, style = WhisperbookTheme.typography.label.copy(fontSize = 12.sp), maxLines = 1)
            Text(" · ", color = WhisperbookTheme.colors.inkMuted, style = WhisperbookTheme.typography.label.copy(fontSize = 12.sp))
            Text(voiceName, color = WhisperbookTheme.colors.ink, style = WhisperbookTheme.typography.label.copy(fontSize = 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            "$positionText / $durationText",
            color = WhisperbookTheme.colors.ink,
            style = WhisperbookTheme.typography.body.copy(fontSize = 12.sp, lineHeight = 15.sp),
            maxLines = 1,
        )
    }
}

@Composable
private fun MiniPlayPauseButton(
    isPlaying: Boolean,
    accentColor: Color,
    onPlayPause: () -> Unit,
) {
    EmbossedCircularButton(
        onClick = onPlayPause,
        contentDescription = if (isPlaying) "Pause" else "Play",
        size = 56.dp,
        backgroundColor = accentColor,
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(29.dp),
        )
    }
}

@Composable
fun FifteenSecondButton(
    onClick: () -> Unit,
    description: String,
    forward: Boolean,
    modifier: Modifier = Modifier,
) {
    val ink = WhisperbookTheme.colors.ink
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(40.dp)) {
            val stroke = 2.1.dp.toPx()
            drawArc(
                color = ink,
                startAngle = if (forward) -70f else 110f,
                sweepAngle = if (forward) 280f else -280f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            val arrow = Path()
            if (forward) {
                arrow.moveTo(size.width * .76f, size.height * .06f)
                arrow.lineTo(size.width * .93f, size.height * .16f)
                arrow.lineTo(size.width * .75f, size.height * .24f)
            } else {
                arrow.moveTo(size.width * .24f, size.height * .06f)
                arrow.lineTo(size.width * .07f, size.height * .16f)
                arrow.lineTo(size.width * .25f, size.height * .24f)
            }
            arrow.close()
            drawPath(arrow, ink)
        }
        Text("15", color = WhisperbookTheme.colors.ink, style = WhisperbookTheme.typography.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
fun PaperProgressBar(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = WhisperbookTheme.colors.action,
    description: String = "Playback progress",
) {
    val fraction = value.coerceIn(0f, 1f)
    val colors = WhisperbookTheme.colors
    Canvas(
        modifier = modifier
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                setProgress { requested -> onValueChange(requested.coerceIn(0f, 1f)); true }
            }
            .pointerInput(onValueChange) {
                detectTapGestures { point -> onValueChange((point.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(onValueChange) {
                detectDragGestures { change, _ ->
                    change.consume()
                    onValueChange((change.position.x / size.width).coerceIn(0f, 1f))
                }
            },
    ) {
        val center = size.height / 2f
        val inset = 5.dp.toPx()
        val usable = (size.width - inset * 2f).coerceAtLeast(1f)
        drawLine(colors.outline.copy(alpha = .45f), Offset(inset, center), Offset(size.width - inset, center), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        drawLine(accentColor, Offset(inset, center), Offset(inset + usable * fraction, center), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(colors.paper, radius = 5.dp.toPx(), center = Offset(inset + usable * fraction, center))
        drawCircle(accentColor, radius = 4.dp.toPx(), center = Offset(inset + usable * fraction, center))
    }
}
