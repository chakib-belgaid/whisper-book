package com.whisperbook.app.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whisperbook.app.R
import com.whisperbook.app.ui.components.EmbossedCircularButton
import com.whisperbook.app.ui.components.LeafDivider
import com.whisperbook.app.ui.components.LeafOrnament
import com.whisperbook.app.ui.components.PaperFold
import com.whisperbook.app.ui.components.ParchmentPanel
import com.whisperbook.app.ui.components.RibbonTitle
import com.whisperbook.app.ui.components.paperClickable
import com.whisperbook.app.ui.components.paperSelectable
import com.whisperbook.app.ui.components.paperToggleable
import com.whisperbook.app.ui.theme.WhisperbookTheme

/** Screen-local high-fidelity pieces shared only by Book details, Voice cast, and Settings. */

@Composable
internal fun BookTheatreHero(
    title: String,
    @DrawableRes sceneRes: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth().height(194.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(166.dp)
                .align(Alignment.BottomCenter)
                .shadow(WhisperbookTheme.elevations.heroStage, RoundedCornerShape(topStart = 64.dp, topEnd = 64.dp, bottomStart = 8.dp, bottomEnd = 8.dp))
                .clip(RoundedCornerShape(topStart = 64.dp, topEnd = 64.dp, bottomStart = 8.dp, bottomEnd = 8.dp))
                .background(WhisperbookTheme.colors.stageRaised)
                .border(2.dp, WhisperbookTheme.colors.ornament, RoundedCornerShape(topStart = 64.dp, topEnd = 64.dp, bottomStart = 8.dp, bottomEnd = 8.dp)),
        ) {
            Image(
                painter = painterResource(sceneRes),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            Image(
                painter = painterResource(R.drawable.curtain_top),
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().height(37.dp).clearAndSetSemantics { },
            )
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(9.dp)
                    .align(Alignment.CenterStart)
                    .background(Brush.horizontalGradient(listOf(WhisperbookTheme.colors.ornament, Color.Transparent)))
                    .clearAndSetSemantics { },
            )
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(9.dp)
                    .align(Alignment.CenterEnd)
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, WhisperbookTheme.colors.ornament)))
                    .clearAndSetSemantics { },
            )
        }
        RibbonTitle(
            title = title,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(.78f),
        )
    }
}

@Composable
internal fun CompactChapterRow(
    chapter: ChapterUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = WhisperbookTheme.colors
    val selected = chapter.selected
    val background = if (selected) colors.stageRaised else colors.paperHighlight.copy(alpha = .34f)
    val foreground = if (selected) colors.onStage else colors.ink
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .alpha(if (enabled) 1f else .55f)
            .paperClickable(
                onClick = onClick,
                enabled = enabled,
                role = Role.Button,
                fold = PaperFold.Card,
            )
            .clip(shape)
            .background(background)
            .border(1.dp, if (selected) colors.ornament else colors.outline.copy(alpha = .65f), shape)
            .padding(horizontal = 9.dp, vertical = 5.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append("Chapter ${chapter.number}, ${chapter.title}")
                    if (selected) append(", current chapter")
                    if (chapter.isLoading) append(", preparing audio")
                    if (!enabled) append(", voices are still being prepared")
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(35.dp)
                .clip(CircleShape)
                .background(if (selected) colors.action else colors.paper)
                .border(1.dp, if (selected) colors.ornament else colors.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(chapter.number.toString(), color = foreground, style = WhisperbookTheme.typography.body.copy(fontWeight = FontWeight.Bold))
        }
        Column(Modifier.weight(1f).padding(horizontal = 9.dp)) {
            Text("Chapter ${chapter.number}", color = foreground, style = WhisperbookTheme.typography.label)
            Text(
                chapter.title,
                color = foreground,
                style = WhisperbookTheme.typography.body.copy(fontSize = 14.sp, lineHeight = 17.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (chapter.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp).testTag("chapter-${chapter.id}-loading"),
                color = foreground,
                strokeWidth = 2.dp,
            )
        } else Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = if (selected) colors.ornament else colors.ink,
            modifier = Modifier.size(25.dp),
        )
    }
}

@Composable
internal fun DetailTab(
    text: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(9.dp)
    val interaction = if (onClick != null) {
        Modifier.paperSelectable(
            selected = selected,
            onClick = onClick,
            role = Role.Tab,
            fold = PaperFold.Tab,
        )
    } else {
        Modifier
    }
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (selected) WhisperbookTheme.colors.paperHighlight.copy(alpha = .40f) else WhisperbookTheme.colors.paper)
            .border(1.dp, WhisperbookTheme.colors.outline.copy(alpha = .72f), shape)
            .then(interaction)
            .semantics(mergeDescendants = true) { this.selected = selected }
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = WhisperbookTheme.colors.ink, modifier = Modifier.size(23.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            color = WhisperbookTheme.colors.ink,
            style = WhisperbookTheme.typography.body.copy(fontSize = 14.sp, lineHeight = 17.sp),
            maxLines = 1,
        )
    }
}

@Composable
internal fun GoldenVoiceCastCard(
    member: CastMemberUi,
    onPreviewVoice: () -> Unit,
    onChangeVoice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = speakerColor(member.role)
    val largeText = LocalDensity.current.fontScale >= 1.3f
    ParchmentPanel(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
    ) {
        if (largeText) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CastPortrait(member, accent, Modifier.size(112.dp))
                VoiceIdentity(member, accent, Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    VoicePreviewButton(member, accent, onPreviewVoice)
                    ChangeVoiceButton(member, onChangeVoice)
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 124.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CastPortrait(member, accent, Modifier.size(112.dp))
                VoiceIdentity(member, accent, Modifier.weight(1f))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    VoicePreviewButton(member, accent, onPreviewVoice)
                    ChangeVoiceButton(member, onChangeVoice)
                }
            }
        }
    }
}

@Composable
private fun CastPortrait(member: CastMemberUi, accent: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.semantics { contentDescription = "${member.character} portrait" },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Image(
            painter = painterResource(member.portraitRes),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.matchParentSize(),
        )
        Text(
            text = member.character,
            color = WhisperbookTheme.colors.onStage,
            style = WhisperbookTheme.typography.body.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(3.dp))
                .background(accent)
                .border(1.dp, WhisperbookTheme.colors.outline, RoundedCornerShape(3.dp))
                .padding(horizontal = 4.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun VoiceIdentity(
    member: CastMemberUi,
    accent: Color,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (textAlign == TextAlign.Center) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            "Assigned voice",
            color = WhisperbookTheme.colors.inkMuted,
            style = WhisperbookTheme.typography.label.copy(fontSize = 11.sp, lineHeight = 13.sp),
            textAlign = textAlign,
        )
        Text(
            member.voice,
            color = WhisperbookTheme.colors.ink,
            style = WhisperbookTheme.typography.headline.copy(fontSize = 27.sp, lineHeight = 29.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign,
        )
        LeafDivider(modifier = Modifier.fillMaxWidth(), color = accent)
        Text(
            "${member.confidence.coerceIn(0, 100)}% confidence\n${member.lines} lines",
            color = WhisperbookTheme.colors.ink,
            style = WhisperbookTheme.typography.label.copy(fontSize = 10.sp, lineHeight = 14.sp),
            textAlign = textAlign,
        )
    }
}

@Composable
private fun VoicePreviewButton(member: CastMemberUi, accent: Color, onClick: () -> Unit) {
    EmbossedCircularButton(
        onClick = onClick,
        contentDescription = "Preview ${member.voice} for ${member.character}",
        size = 52.dp,
        backgroundColor = accent,
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun ChangeVoiceButton(member: CastMemberUi, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .width(84.dp)
            .heightIn(min = 48.dp)
            .paperClickable(onClick = onClick, role = Role.Button, fold = PaperFold.Control)
            .shadow(WhisperbookTheme.elevations.paperContact, shape)
            .clip(shape)
            .background(WhisperbookTheme.colors.paperHighlight.copy(alpha = .44f))
            .border(1.dp, WhisperbookTheme.colors.outline, shape)
            .semantics { contentDescription = "Change voice for ${member.character}" }
            .padding(horizontal = 6.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Change voice",
            color = WhisperbookTheme.colors.ink,
            style = WhisperbookTheme.typography.label.copy(fontSize = 10.sp, lineHeight = 13.sp),
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun SettingsMoonHeader(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().height(82.dp)) {
        com.whisperbook.app.ui.components.OfflineBadge(
            text = "Offline",
            modifier = Modifier.align(Alignment.TopStart),
        )
        Text(
            text = "Settings",
            color = WhisperbookTheme.colors.onStage,
            style = WhisperbookTheme.typography.display,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).semantics { heading() },
        )
        Row(
            modifier = Modifier.align(Alignment.BottomCenter),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LeafOrnament(Modifier.size(42.dp, 19.dp), WhisperbookTheme.colors.ornament)
            Text("✦", color = WhisperbookTheme.colors.ornament, style = WhisperbookTheme.typography.body)
            Text("☾", color = WhisperbookTheme.colors.ornament, style = WhisperbookTheme.typography.display)
            Text("✦", color = WhisperbookTheme.colors.ornament, style = WhisperbookTheme.typography.body)
            LeafOrnament(Modifier.size(42.dp, 19.dp), WhisperbookTheme.colors.ornament)
        }
    }
}

@Composable
internal fun GoldenSettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        ParchmentPanel(
            modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 15.dp, start = 12.dp, end = 12.dp, bottom = 3.dp),
        ) {
            content()
        }
        GoldenSettingsRibbon(
            title = title,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun GoldenSettingsRibbon(title: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(7.dp)
    Row(
        modifier = modifier
            .shadow(WhisperbookTheme.elevations.paperContact, shape)
            .clip(shape)
            .background(WhisperbookTheme.colors.paper)
            .border(1.dp, WhisperbookTheme.colors.outline, shape)
            .padding(horizontal = 12.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            "❧",
            color = WhisperbookTheme.colors.outline,
            style = WhisperbookTheme.typography.body.copy(fontSize = 15.sp, lineHeight = 18.sp),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            title,
            color = WhisperbookTheme.colors.ink,
            style = WhisperbookTheme.typography.body.copy(fontSize = 15.sp, lineHeight = 19.sp),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Spacer(Modifier.width(7.dp))
        Text(
            "❧",
            color = WhisperbookTheme.colors.outline,
            style = WhisperbookTheme.typography.body.copy(fontSize = 15.sp, lineHeight = 18.sp),
        )
    }
}

@Composable
internal fun GoldenSettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    icon: ImageVector? = null,
    leadingText: String? = null,
    onClick: (() -> Unit)? = null,
    installed: Boolean = false,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
) {
    val interaction = if (onClick != null) {
        Modifier.paperClickable(onClick = onClick, role = Role.Button, fold = PaperFold.Card)
    } else {
        Modifier
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 45.dp)
            .then(interaction)
            .padding(horizontal = 3.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = WhisperbookTheme.colors.action, modifier = Modifier.size(25.dp))
        } else if (leadingText != null) {
            Text(
                leadingText,
                color = WhisperbookTheme.colors.action,
                style = WhisperbookTheme.typography.title,
                modifier = Modifier.width(38.dp),
                textAlign = TextAlign.Center,
            )
        }
        Text(
            title,
            color = WhisperbookTheme.colors.ink,
            style = WhisperbookTheme.typography.body.copy(fontSize = 13.sp, lineHeight = 17.sp),
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (value != null) {
            Text(
                value,
                color = WhisperbookTheme.colors.action,
                style = WhisperbookTheme.typography.body.copy(fontSize = 11.sp, lineHeight = 15.sp),
                maxLines = 1,
            )
        }
        when {
            trailingContent != null -> trailingContent()
            installed -> Icon(Icons.Filled.Check, contentDescription = null, tint = WhisperbookTheme.colors.action, modifier = Modifier.size(25.dp))
            onClick != null -> Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = WhisperbookTheme.colors.action, modifier = Modifier.size(25.dp))
        }
    }
}

@Composable
internal fun GoldenSettingsToggleRow(
    title: String,
    icon: ImageVector? = null,
    leadingText: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    GoldenSettingsRow(
        title = title,
        icon = icon,
        leadingText = leadingText,
        modifier = Modifier.paperToggleable(
            value = checked,
            role = Role.Switch,
            onValueChange = onCheckedChange,
            fold = PaperFold.Toggle,
        ),
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                modifier = Modifier.clearAndSetSemantics { },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = WhisperbookTheme.colors.paperHighlight,
                    checkedTrackColor = WhisperbookTheme.colors.action,
                    checkedBorderColor = WhisperbookTheme.colors.ornament,
                    uncheckedThumbColor = WhisperbookTheme.colors.paperHighlight,
                    uncheckedTrackColor = WhisperbookTheme.colors.paper.copy(alpha = .72f),
                    uncheckedBorderColor = WhisperbookTheme.colors.outline,
                ),
            )
        },
    )
}

@Composable
internal fun CompactDivider(modifier: Modifier = Modifier) {
    Spacer(modifier.fillMaxWidth().height(1.dp).background(WhisperbookTheme.colors.outline.copy(alpha = .35f)))
}

@Composable
internal fun StorageMeter(
    usedBytes: Long,
    limitBytes: Long,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(99.dp)
    val fraction = if (limitBytes <= 0L) 0f else usedBytes.toFloat().div(limitBytes).coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(shape)
            .background(WhisperbookTheme.colors.paperHighlight.copy(alpha = .45f))
            .border(1.dp, WhisperbookTheme.colors.outline, shape)
            .semantics { contentDescription = "Storage: ${formatStorageBytes(usedBytes)} used by books and voices" },
    ) {
        if (fraction > 0f) {
            Spacer(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(WhisperbookTheme.colors.action))
        }
    }
}
