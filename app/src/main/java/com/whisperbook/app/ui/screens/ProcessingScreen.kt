package com.whisperbook.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.whisperbook.app.R
import com.whisperbook.app.ui.components.PapercraftButton
import com.whisperbook.app.ui.components.PapercraftButtonVariant
import com.whisperbook.app.ui.components.ParchmentPanel
import com.whisperbook.app.ui.components.TheatreFrameOverlay
import com.whisperbook.app.ui.theme.WhisperbookTheme
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val PreparationLabels = listOf(
    "Reading chapters",
    "Finding characters",
    "Assigning voices",
    "Ready to listen",
)

private const val ProcessingReferenceWidthDp = 400f
private const val ProcessingMaximumScale = 1.8f

/**
 * Keeps the illustrated processing composition legible when Android display sizing exposes a
 * phone as a very wide logical viewport. Regular phone widths remain exactly 1:1.
 */
internal fun processingContentScale(maxWidthDp: Float): Float =
    (maxWidthDp / ProcessingReferenceWidthDp).coerceIn(1f, ProcessingMaximumScale)

@Composable
fun ProcessingScreen(
    contentPadding: PaddingValues,
    appState: WhisperbookAppState,
    onContinueInBackground: () -> Unit,
    onReady: () -> Unit,
    onRetry: () -> Unit,
    onBackToImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val baseDensity = LocalDensity.current
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(appState.importedUri, appState.isProductionBacked) {
        if (appState.importedUri != null && !appState.isProductionBacked) {
            repeat(3) {
                delay(850)
                appState.advancePreparation()
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        val contentScale = processingContentScale(maxWidth.value)
        val responsiveDensity = Density(
            density = baseDensity.density * contentScale,
            fontScale = baseDensity.fontScale,
        )
        CompositionLocalProvider(LocalDensity provides responsiveDensity) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .testTag("processing-screen"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Preparing your audiobook",
                    color = WhisperbookTheme.colors.onStage,
                    style = WhisperbookTheme.typography.display.copy(fontSize = 29.sp, lineHeight = 34.sp),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().testTag("processing-header"),
                )
                Spacer(Modifier.height(5.dp))

                if (appState.preparationFailed || appState.importError != null) {
                    PreparationFailure(
                        message = appState.importError
                            ?: "Preparation stopped before the audiobook was ready.",
                        isBusy = appState.isBusy,
                        onRetry = onRetry,
                        onBackToImport = onBackToImport,
                    )
                    return@Column
                }

                ProcessingTheatre(
                    title = appState.currentBookTitle,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    text = "${(appState.preparationProgress * 100).roundToInt()}%",
                    color = WhisperbookTheme.colors.onStage,
                    style = WhisperbookTheme.typography.display.copy(fontSize = 53.sp, lineHeight = 55.sp),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics {
                        contentDescription = "${(appState.preparationProgress * 100).roundToInt()} percent prepared"
                    },
                )
                Spacer(Modifier.height(3.dp))
                StoryProgressBar(appState.preparationProgress)
                Spacer(Modifier.height(10.dp))

                ParchmentPanel(
                    modifier = Modifier.fillMaxWidth().testTag("processing-steps"),
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 11.dp),
                ) {
                    PreparationStepper(
                        currentStage = appState.preparationStage,
                        failed = false,
                    )
                }
                Spacer(Modifier.height(10.dp))
                PapercraftButton(
                    text = if (appState.preparationStage >= 3) {
                        "Listen now"
                    } else {
                        "Continue in background"
                    },
                    onClick = if (appState.preparationStage >= 3) {
                        onReady
                    } else {
                        {
                            if (
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS,
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            onContinueInBackground()
                        }
                    },
                    variant = PapercraftButtonVariant.Accent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .testTag("processing-primary-action"),
                    isLoading = appState.isBusy,
                    loadingDescription = appState.statusMessage ?: "Preparing your audiobook",
                )
                if (appState.preparationStage >= 3) {
                    Text(
                        text = "Playback starts with the opening lines while the rest records in the background.",
                        color = WhisperbookTheme.colors.onStage,
                        style = WhisperbookTheme.typography.label,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                OnDevicePromise()
            }
        }
    }
}

@Composable
private fun ProcessingTheatre(
    title: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(225.dp)
            .testTag("processing-theatre")
            .semantics(mergeDescendants = true) {
                contentDescription = "$title is being prepared in the papercraft story workshop"
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Image(
            painter = painterResource(R.drawable.scene_book_machine),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 49.dp, end = 49.dp, bottom = 26.dp)
                .fillMaxWidth()
                .height(142.dp)
                .clip(RoundedCornerShape(topStart = 60.dp, topEnd = 60.dp, bottomStart = 5.dp, bottomEnd = 5.dp)),
        )
        TheatreFrameOverlay(
            title = title,
            modifier = Modifier.fillMaxSize(),
            titleStyle = WhisperbookTheme.typography.title.copy(fontSize = 24.sp, lineHeight = 28.sp),
            plaqueModifier = Modifier.testTag("processing-title-plaque"),
            titleModifier = Modifier.testTag("processing-book-title"),
        )
    }
}

@Composable
private fun StoryProgressBar(progress: Float) {
    val fraction = progress.coerceIn(0f, 1f)
    val shape = RoundedCornerShape(50)
    val percent = (fraction * 100).roundToInt()
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 27.dp)
            .height(20.dp)
            .shadow(3.dp, shape)
            .clip(shape)
            .background(WhisperbookTheme.colors.paperHighlight)
            .border(2.dp, WhisperbookTheme.colors.ornament, shape)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                stateDescription = "$percent percent prepared"
            },
    ) {
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(20.dp)
                    .background(WhisperbookTheme.colors.action),
            )
        }
        if (fraction in 0.03f..0.97f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = maxWidth * fraction - 3.dp)
                    .width(6.dp)
                    .height(20.dp)
                    .background(WhisperbookTheme.colors.ornament)
                    .border(1.dp, WhisperbookTheme.colors.outline),
            )
        }
    }
}

@Composable
private fun PreparationStepper(
    currentStage: Int,
    failed: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Audiobook preparation steps"
            },
    ) {
        Canvas(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 15.dp)
                .size(width = 2.dp, height = 116.dp),
        ) {
            drawLine(
                color = androidx.compose.ui.graphics.Color(0xFF806642),
                start = Offset(size.width / 2f, 0f),
                end = Offset(size.width / 2f, size.height),
                strokeWidth = size.width,
                cap = StrokeCap.Round,
            )
        }
        Column {
            PreparationLabels.forEachIndexed { index, label ->
                val state = when {
                    failed && index == currentStage -> PreparationStepVisual.Error
                    index < currentStage -> PreparationStepVisual.Complete
                    index == currentStage -> PreparationStepVisual.Active
                    else -> PreparationStepVisual.Pending
                }
                PreparationStepRow(label, state)
            }
        }
    }
}

private enum class PreparationStepVisual { Complete, Active, Pending, Error }

@Composable
private fun PreparationStepRow(
    label: String,
    state: PreparationStepVisual,
) {
    val stateLabel = when (state) {
        PreparationStepVisual.Complete -> "complete"
        PreparationStepVisual.Active -> "in progress"
        PreparationStepVisual.Pending -> "waiting"
        PreparationStepVisual.Error -> "needs attention"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "$label, $stateLabel"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    when (state) {
                        PreparationStepVisual.Complete -> WhisperbookTheme.colors.outline
                        PreparationStepVisual.Active -> WhisperbookTheme.colors.stageRaised
                        PreparationStepVisual.Pending -> WhisperbookTheme.colors.paperHighlight
                        PreparationStepVisual.Error -> WhisperbookTheme.colors.error
                    },
                )
                .border(2.dp, WhisperbookTheme.colors.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = when (state) {
                    PreparationStepVisual.Complete -> Icons.Filled.Check
                    PreparationStepVisual.Active -> Icons.Outlined.AutoAwesome
                    PreparationStepVisual.Pending, PreparationStepVisual.Error -> Icons.Outlined.Circle
                },
                contentDescription = null,
                tint = when (state) {
                    PreparationStepVisual.Active -> WhisperbookTheme.colors.ornament
                    PreparationStepVisual.Pending -> WhisperbookTheme.colors.inkMuted
                    else -> WhisperbookTheme.colors.onStage
                },
                modifier = Modifier.size(if (state == PreparationStepVisual.Pending) 15.dp else 21.dp),
            )
        }
        Text(
            text = label,
            color = when (state) {
                PreparationStepVisual.Active -> WhisperbookTheme.colors.action
                PreparationStepVisual.Error -> WhisperbookTheme.colors.error
                else -> WhisperbookTheme.colors.ink
            },
            style = WhisperbookTheme.typography.body.copy(
                fontSize = if (state == PreparationStepVisual.Active) 17.sp else 16.sp,
                lineHeight = 20.sp,
            ),
            modifier = Modifier.padding(start = 13.dp).weight(1f),
        )
        StepLeaf()
    }
}

@Composable
private fun StepLeaf() {
    val color = WhisperbookTheme.colors.outline.copy(alpha = 0.65f)
    Canvas(modifier = Modifier.size(width = 23.dp, height = 13.dp)) {
        drawLine(
            color = color,
            start = Offset(size.width * .08f, size.height * .72f),
            end = Offset(size.width * .92f, size.height * .28f),
            strokeWidth = 1.4.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawOval(
            color = color,
            topLeft = Offset(size.width * .25f, 0f),
            size = androidx.compose.ui.geometry.Size(size.width * .27f, size.height * .48f),
        )
        drawOval(
            color = color,
            topLeft = Offset(size.width * .48f, size.height * .48f),
            size = androidx.compose.ui.geometry.Size(size.width * .27f, size.height * .48f),
        )
    }
}

@Composable
private fun OnDevicePromise() {
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Security,
            contentDescription = null,
            tint = WhisperbookTheme.colors.ornament,
            modifier = Modifier.size(25.dp),
        )
        Text(
            text = "Everything stays on this device",
            color = WhisperbookTheme.colors.onStage,
            style = WhisperbookTheme.typography.label.copy(fontSize = 12.sp, lineHeight = 16.sp),
        )
    }
}

@Composable
private fun PreparationFailure(
    message: String,
    isBusy: Boolean,
    onRetry: () -> Unit,
    onBackToImport: () -> Unit,
) {
    Spacer(Modifier.height(18.dp))
    ParchmentPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(22.dp),
    ) {
        Text(
            text = "This book needs your attention",
            color = WhisperbookTheme.colors.ink,
            style = WhisperbookTheme.typography.title,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = message,
            color = WhisperbookTheme.colors.error,
            style = WhisperbookTheme.typography.body,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        PapercraftButton(
            text = "Try again",
            onClick = onRetry,
            enabled = !isBusy,
            isLoading = isBusy,
            loadingDescription = "Retrying preparation",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        PapercraftButton(
            text = "Choose another file",
            onClick = onBackToImport,
            enabled = !isBusy,
            variant = PapercraftButtonVariant.Parchment,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "No upload is needed. You can retry fully offline.",
            color = WhisperbookTheme.colors.inkMuted,
            style = WhisperbookTheme.typography.label,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
