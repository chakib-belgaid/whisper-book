package com.whisperbook.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whisperbook.app.domain.model.PreparationStage
import com.whisperbook.app.domain.model.PreparationState
import com.whisperbook.app.diagnostics.BetaDiagnostics
import com.whisperbook.app.integration.WhisperbookViewModel
import com.whisperbook.app.integration.flux.WhisperbookAction
import com.whisperbook.app.ui.components.StorybookDestination
import com.whisperbook.app.ui.components.StorybookNavigationSuite
import com.whisperbook.app.ui.components.WhisperBackdrop
import com.whisperbook.app.ui.components.PaperFold
import com.whisperbook.app.ui.components.paperClickable
import com.whisperbook.app.ui.navigation.WhisperbookDestination
import com.whisperbook.app.ui.navigation.WhisperbookNavHost
import com.whisperbook.app.ui.navigation.navigateToBottomDestination
import com.whisperbook.app.ui.screens.WhisperbookAppState
import com.whisperbook.app.ui.screens.WhisperbookUiActions
import com.whisperbook.app.ui.theme.WhisperbookTheme
import kotlin.math.roundToInt

@Composable
fun WhisperbookApp(
    viewModel: WhisperbookViewModel,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val snapshot by viewModel.uiState.collectAsStateWithLifecycle()
    val actions = remember(viewModel) { ViewModelUiActions(viewModel) }
    val appState = remember(viewModel) { WhisperbookAppState(actions) }
    LaunchedEffect(snapshot) { appState.synchronizeAsync(snapshot) }
    val startDestination = remember {
        if (snapshot.settings.onboardingComplete) WhisperbookDestination.Library.route
        else WhisperbookDestination.Welcome.route
    }
    LaunchedEffect(snapshot.settings.onboardingComplete) {
        if (
            snapshot.settings.onboardingComplete &&
            navController.currentDestination?.route == WhisperbookDestination.Welcome.route
        ) {
            navController.navigateToBottomDestination(WhisperbookDestination.Library.route)
        }
    }
    WhisperbookApp(
        appState = appState,
        modifier = modifier,
        navController = navController,
        startDestination = startDestination,
    )
}

@Composable
fun WhisperbookApp(
    appState: WhisperbookAppState,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = WhisperbookDestination.Welcome.route,
) {
    WhisperbookTheme {
        val baseDensity = LocalDensity.current
        val view = LocalView.current
        DisposableEffect(view, appState.keepScreenAwake) {
            view.keepScreenOn = appState.keepScreenAwake
            onDispose { view.keepScreenOn = false }
        }
        CompositionLocalProvider(
            LocalDensity provides Density(
                density = baseDensity.density,
                fontScale = if (appState.largerText) maxOf(1.2f, baseDensity.fontScale) else baseDensity.fontScale,
            ),
        ) {
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route ?: startDestination
        val showNavigation = currentRoute in WhisperbookDestination.bottomBarRoutes
        LaunchedEffect(currentRoute) {
            BetaDiagnostics.info("screen_view", mapOf("route" to currentRoute))
        }

        WhisperBackdrop(modifier = modifier.fillMaxSize()) {
            StorybookNavigationSuite(
                destinations = bottomDestinations,
                selectedRoute = selectedBottomRoute(currentRoute),
                showNavigation = showNavigation,
                onDestinationSelected = { destination ->
                    if (currentRoute == WhisperbookDestination.Welcome.route) {
                        appState.completeOnboarding()
                    }
                    when {
                        destination.route == WhisperbookDestination.NowPlaying.route &&
                            appState.currentBookId.isBlank() -> {
                            navController.navigateToBottomDestination(
                                WhisperbookDestination.Library.route,
                            )
                        }
                        destination.route == WhisperbookDestination.NowPlaying.route &&
                            appState.requiresChapterReview -> {
                            navController.navigate(WhisperbookDestination.ChapterReview.route)
                        }
                        destination.route == WhisperbookDestination.NowPlaying.route &&
                            appState.preparationStatus?.stage in setOf(
                                PreparationStage.COPY_AND_VALIDATE,
                                PreparationStage.READING_CHAPTERS,
                            ) -> {
                            navController.navigate(WhisperbookDestination.Parsing.route)
                        }
                        destination.route == WhisperbookDestination.NowPlaying.route &&
                            appState.narrationSetupRequired -> {
                            navController.navigate(WhisperbookDestination.NarrationSetup.route)
                        }
                        destination.route == WhisperbookDestination.NowPlaying.route &&
                            !appState.canListen -> {
                            navController.navigate(WhisperbookDestination.Processing.route)
                        }
                        else -> navController.navigateToBottomDestination(destination.route)
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(top = WhisperbookTheme.spacing.sm)
                    .testTag("app-safe-area"),
            ) {
                Box(Modifier.fillMaxSize()) {
                    WhisperbookNavHost(
                        navController = navController,
                        appState = appState,
                        contentPadding = PaddingValues.Zero,
                        startDestination = startDestination,
                    )
                    val preparation = appState.preparationStatus
                    val showPreparation = (appState.isBookPreparing || appState.isBookParsing) &&
                        currentRoute !in setOf(
                            WhisperbookDestination.Parsing.route,
                            WhisperbookDestination.Processing.route,
                        )
                    if (
                        (appState.isBusy || showPreparation) &&
                        currentRoute !in setOf(
                            WhisperbookDestination.Parsing.route,
                            WhisperbookDestination.Processing.route,
                        )
                    ) {
                        val operationTakesPriority = appState.isBusy
                        BackgroundWorkStatus(
                            message = if (operationTakesPriority) {
                                appState.statusMessage ?: "Working privately on this device"
                            } else {
                                preparation?.backgroundTitle() ?: "Preparing your audiobook"
                            },
                            detail = if (operationTakesPriority) {
                                "Running in the background — you can keep using the app."
                            } else {
                                preparation?.message ?: "Preparing privately on this device"
                            },
                            progressFraction = if (operationTakesPriority) {
                                appState.backgroundProgressFraction
                            } else {
                                preparation?.unitProgress()
                            },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 10.dp),
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun BackgroundWorkStatus(
    message: String,
    detail: String,
    progressFraction: Float?,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val progress = progressFraction?.coerceIn(0f, 1f)
    val progressPercent = progress?.let { (it * 100).roundToInt() }
    val progressLabel = progressPercent?.let { "$it%" } ?: "In progress"
    val expansionAction = if (expanded) "Collapse preparation status" else "Expand preparation status"

    Column(
        modifier = modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .animateContentSize()
            .clip(RoundedCornerShape(18.dp))
            .background(WhisperbookTheme.colors.paperHighlight)
            .border(1.dp, WhisperbookTheme.colors.ornament, RoundedCornerShape(18.dp))
            .paperClickable(
                onClick = { expanded = !expanded },
                role = Role.Button,
                fold = PaperFold.Card,
                onClickLabel = expansionAction,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                stateDescription = buildString {
                    append(if (expanded) "Expanded" else "Collapsed")
                    append(", ")
                    append(progressPercent?.let { "$it percent complete" } ?: "progress ongoing")
                }
            }
            .testTag("background-operation-status"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = if (expanded) Alignment.Top else Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = message,
                        color = WhisperbookTheme.colors.ink,
                        style = WhisperbookTheme.typography.label,
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = progressLabel,
                        color = WhisperbookTheme.colors.action,
                        style = WhisperbookTheme.typography.label,
                        modifier = Modifier.testTag("background-operation-progress-label"),
                    )
                }
                Text(
                    text = detail,
                    color = WhisperbookTheme.colors.inkMuted,
                    style = WhisperbookTheme.typography.label,
                    maxLines = if (expanded) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = WhisperbookTheme.colors.inkMuted,
                modifier = Modifier.size(20.dp),
            )
        }
        if (progress == null) {
            LinearProgressIndicator(
                color = WhisperbookTheme.colors.action,
                trackColor = WhisperbookTheme.colors.inkMuted.copy(alpha = 0.22f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .semantics {
                        progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                        contentDescription = "Background work in progress"
                    }
                    .testTag("background-operation-progress"),
            )
        } else {
            LinearProgressIndicator(
                progress = { progress },
                color = WhisperbookTheme.colors.action,
                trackColor = WhisperbookTheme.colors.inkMuted.copy(alpha = 0.22f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .semantics {
                        progressBarRangeInfo = ProgressBarRangeInfo(
                            current = progress,
                            range = 0f..1f,
                            steps = 0,
                        )
                        contentDescription = "$progressPercent percent complete"
                    }
                    .testTag("background-operation-progress"),
            )
        }
    }
}

private fun PreparationState.backgroundTitle(): String = when {
    stage == PreparationStage.PREPARING_AUDIO && totalUnits > 0 ->
        "Prepared ${completedUnits.coerceIn(0, totalUnits)} of $totalUnits chapters"
    stage == PreparationStage.READING_CHAPTERS && totalUnits > 0 ->
        "Reading chapter ${completedUnits.coerceIn(0, totalUnits)} of $totalUnits"
    stage == PreparationStage.FINDING_CHARACTERS -> "Finding story voices"
    stage == PreparationStage.ASSIGNING_VOICES -> "Assigning offline voices"
    else -> "Preparing your audiobook"
}

private fun PreparationState.unitProgress(): Float? = totalUnits
    .takeIf { it > 0 }
    ?.let { completedUnits.coerceIn(0, it).toFloat() / it }

private val bottomDestinations = listOf(
    StorybookDestination(WhisperbookDestination.Library.route, "Library", Icons.AutoMirrored.Outlined.LibraryBooks),
    StorybookDestination(WhisperbookDestination.NowPlaying.route, "Listen", Icons.Outlined.Headphones),
    StorybookDestination(WhisperbookDestination.Settings.route, "Settings", Icons.Outlined.Settings),
)

private fun selectedBottomRoute(route: String): String = when (route) {
    WhisperbookDestination.NowPlaying.route,
    WhisperbookDestination.CurrentChapter.route
    -> WhisperbookDestination.NowPlaying.route
    WhisperbookDestination.Settings.route -> WhisperbookDestination.Settings.route
    else -> WhisperbookDestination.Library.route
}

private class ViewModelUiActions(
    private val viewModel: WhisperbookViewModel,
) : WhisperbookUiActions {
    override fun dispatch(action: WhisperbookAction) = viewModel.dispatch(action)
}
