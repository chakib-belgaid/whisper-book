package com.whisperbook.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.whisperbook.app.ui.theme.WhisperbookTheme

/**
 * Physical fold profiles for the app's paper surfaces. Each profile keeps the
 * movement shallow enough that text stays readable while still suggesting a
 * card hinge, folded tab, or pressed paper control.
 */
enum class PaperFold {
    Control,
    Card,
    Tab,
    Toggle,
}

private data class PaperFoldProfile(
    val rotationX: Float,
    val rotationY: Float,
    val rotationZ: Float,
    val scaleX: Float,
    val scaleY: Float,
    val translationY: Float,
    val origin: TransformOrigin,
)

private fun PaperFold.profile(): PaperFoldProfile = when (this) {
    PaperFold.Control -> PaperFoldProfile(
        rotationX = 4.5f,
        rotationY = -1.25f,
        rotationZ = -0.22f,
        scaleX = .985f,
        scaleY = .965f,
        translationY = 1.5f,
        origin = TransformOrigin(.5f, 0f),
    )
    PaperFold.Card -> PaperFoldProfile(
        rotationX = 2.2f,
        rotationY = -3.5f,
        rotationZ = -.12f,
        scaleX = .988f,
        scaleY = .978f,
        translationY = 1f,
        origin = TransformOrigin(0f, .5f),
    )
    PaperFold.Tab -> PaperFoldProfile(
        rotationX = -5.5f,
        rotationY = 0f,
        rotationZ = 0f,
        scaleX = .985f,
        scaleY = .96f,
        translationY = 1f,
        origin = TransformOrigin(.5f, 1f),
    )
    PaperFold.Toggle -> PaperFoldProfile(
        rotationX = 0f,
        rotationY = 7f,
        rotationZ = 0f,
        scaleX = .972f,
        scaleY = .985f,
        translationY = 0f,
        origin = TransformOrigin(0f, .5f),
    )
}

/** Applies the shared fold response to an existing Compose interaction source. */
@Composable
fun Modifier.paperFold(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
    fold: PaperFold = PaperFold.Control,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val dragged by interactionSource.collectIsDraggedAsState()
    return paperFold(engaged = enabled && (pressed || dragged), fold = fold)
}

/** Applies a fold response to custom pointer-driven controls such as the seek rail. */
@Composable
fun Modifier.paperFold(
    engaged: Boolean,
    fold: PaperFold = PaperFold.Control,
): Modifier {
    val motion = WhisperbookTheme.motion
    val profile = fold.profile()
    val progress by animateFloatAsState(
        targetValue = if (engaged) 1f else 0f,
        animationSpec = if (engaged) {
            tween(durationMillis = motion.pressMillis, easing = FastOutSlowInEasing)
        } else {
            spring(
                dampingRatio = motion.releaseDampingRatio,
                stiffness = motion.releaseStiffness,
            )
        },
        label = "paper-fold-${fold.name}",
    )
    val density = LocalDensity.current
    val translation = with(density) { profile.translationY.dp.toPx() }
    val cameraDistancePx = with(density) { motion.cameraDistance.toPx() }
    return graphicsLayer {
        rotationX = profile.rotationX * progress
        rotationY = profile.rotationY * progress
        rotationZ = profile.rotationZ * progress
        scaleX = 1f - ((1f - profile.scaleX) * progress)
        scaleY = 1f - ((1f - profile.scaleY) * progress)
        translationY = translation * progress
        transformOrigin = profile.origin
        cameraDistance = cameraDistancePx
    }
}

@Composable
fun Modifier.paperClickable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    role: Role? = null,
    fold: PaperFold = PaperFold.Control,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return paperFold(interactionSource, enabled, fold)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            role = role,
            onClick = onClick,
        )
}

@Composable
fun Modifier.paperSelectable(
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    role: Role? = null,
    fold: PaperFold = PaperFold.Card,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return paperFold(interactionSource, enabled, fold)
        .selectable(
            selected = selected,
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            role = role,
            onClick = onClick,
        )
}

@Composable
fun Modifier.paperToggleable(
    value: Boolean,
    onValueChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    role: Role? = null,
    fold: PaperFold = PaperFold.Toggle,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return paperFold(interactionSource, enabled, fold)
        .toggleable(
            value = value,
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            role = role,
            onValueChange = onValueChange,
        )
}

/** A hinged page reveal used whenever navigation replaces the current screen. */
@Composable
fun OrigamiPage(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val motion = WhisperbookTheme.motion
    var unfolded by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (unfolded) 1f else 0f,
        animationSpec = tween(
            durationMillis = motion.pageUnfoldMillis,
            easing = FastOutSlowInEasing,
        ),
        label = "origami-page-unfold",
    )
    val cameraDistancePx = with(LocalDensity.current) { motion.cameraDistance.toPx() }
    LaunchedEffect(Unit) { unfolded = true }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                rotationY = -motion.pageFoldDegrees * (1f - progress)
                scaleX = .97f + (.03f * progress)
                alpha = .55f + (.45f * progress)
                transformOrigin = TransformOrigin(0f, .5f)
                cameraDistance = cameraDistancePx
            },
        content = content,
    )
}

/** A top-hinged reveal for paper sheets presented above the current page. */
@Composable
fun OrigamiSheet(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val motion = WhisperbookTheme.motion
    var unfolded by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (unfolded) 1f else 0f,
        animationSpec = tween(
            durationMillis = motion.sheetUnfoldMillis,
            easing = FastOutSlowInEasing,
        ),
        label = "origami-sheet-unfold",
    )
    val cameraDistancePx = with(LocalDensity.current) { motion.cameraDistance.toPx() }
    LaunchedEffect(Unit) { unfolded = true }

    Box(
        modifier = modifier.graphicsLayer {
            rotationX = -motion.sheetFoldDegrees * (1f - progress)
            alpha = .65f + (.35f * progress)
            transformOrigin = TransformOrigin(.5f, 0f)
            cameraDistance = cameraDistancePx
        },
        content = content,
    )
}
