package com.whisperbook.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whisperbook.app.domain.model.PreparationStage
import com.whisperbook.app.domain.model.PreparationState
import com.whisperbook.app.integration.WhisperbookViewModel
import com.whisperbook.app.ui.components.StorybookBottomBar
import com.whisperbook.app.ui.components.StorybookDestination
import com.whisperbook.app.ui.components.WhisperBackdrop
import com.whisperbook.app.ui.navigation.WhisperbookDestination
import com.whisperbook.app.ui.navigation.WhisperbookNavHost
import com.whisperbook.app.ui.navigation.navigateToBottomDestination
import com.whisperbook.app.ui.screens.WhisperbookAppState
import com.whisperbook.app.ui.screens.WhisperbookUiActions
import com.whisperbook.app.ui.theme.WhisperbookTheme

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
        val showBottomBar = currentRoute in WhisperbookDestination.bottomBarRoutes

        WhisperBackdrop(modifier = modifier.fillMaxSize()) {
            Scaffold(
                containerColor = Color.Transparent,
                contentColor = WhisperbookTheme.colors.onStage,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    if (showBottomBar) {
                        StorybookBottomBar(
                            destinations = bottomDestinations,
                            selectedRoute = selectedBottomRoute(currentRoute),
                            onDestinationSelected = { destination ->
                                if (currentRoute == WhisperbookDestination.Welcome.route) {
                                    appState.completeOnboarding()
                                }
                                if (
                                    destination.route == WhisperbookDestination.NowPlaying.route &&
                                    !appState.canListen
                                ) {
                                    navController.navigate(WhisperbookDestination.Processing.route)
                                } else {
                                    navController.navigateToBottomDestination(destination.route)
                                }
                            },
                        )
                    }
                },
            ) { contentPadding ->
                Box(Modifier.fillMaxSize()) {
                    WhisperbookNavHost(
                        navController = navController,
                        appState = appState,
                        contentPadding = contentPadding,
                        startDestination = startDestination,
                    )
                    val preparation = appState.preparationStatus
                    val showPreparation = appState.isBookPreparing &&
                        currentRoute != WhisperbookDestination.Processing.route
                    if ((appState.isBusy || showPreparation) && currentRoute != WhisperbookDestination.Processing.route) {
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
                                preparation?.message ?: "Recording privately on this device"
                            },
                            progressFraction = if (operationTakesPriority) {
                                appState.backgroundProgressFraction
                            } else {
                                preparation?.unitProgress()
                            },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 16.dp)
                                .padding(bottom = contentPadding.calculateBottomPadding() + 10.dp),
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
    Row(
        modifier = modifier
            .widthIn(max = 520.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(WhisperbookTheme.colors.paperHighlight)
            .border(1.dp, WhisperbookTheme.colors.ornament, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag("background-operation-status"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (progressFraction == null) {
            CircularProgressIndicator(
                color = WhisperbookTheme.colors.action,
                strokeWidth = 2.dp,
                modifier = Modifier.size(22.dp),
            )
        } else {
            CircularProgressIndicator(
                progress = { progressFraction.coerceIn(0f, 1f) },
                color = WhisperbookTheme.colors.action,
                strokeWidth = 2.dp,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = message,
                color = WhisperbookTheme.colors.ink,
                style = WhisperbookTheme.typography.label,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = detail,
                color = WhisperbookTheme.colors.inkMuted,
                style = WhisperbookTheme.typography.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun PreparationState.backgroundTitle(): String = when {
    stage == PreparationStage.PREPARING_AUDIO && totalUnits > 0 ->
        "Recorded ${completedUnits.coerceIn(0, totalUnits)} of $totalUnits chapters"
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
    override fun importBook(uri: android.net.Uri) = viewModel.importBook(uri).let { Unit }
    override fun retryPreparation() {
        viewModel.clearMessage()
        viewModel.retryPreparation()
    }
    override fun deleteSelectedBook() = viewModel.deleteSelectedBook().let { Unit }
    override fun selectBook(bookId: String) = viewModel.selectBook(bookId)
    override fun selectChapter(chapterId: String) = viewModel.selectChapter(chapterId)
    override fun playPreviousChapter() = viewModel.playPreviousChapter()
    override fun playNextChapter() = viewModel.playNextChapter()
    override fun playSelectedChapter() = viewModel.playSelectedChapter()
    override fun playOrPause() = viewModel.playOrPause().let { Unit }
    override fun seekByFraction(delta: Float) = viewModel.seekBy(if (delta < 0f) -15_000L else 15_000L).let { Unit }
    override fun seekToFraction(fraction: Float) = viewModel.seekToFraction(fraction).let { Unit }
    override fun seekToPassage(passageId: String) = viewModel.seekToPassage(passageId).let { Unit }
    override fun cycleSpeed() = viewModel.cycleSpeed()
    override fun cycleDefaultNarratorVoice() = viewModel.cycleDefaultNarratorVoice()
    override fun chooseDefaultNarratorVoice(voiceId: String) = viewModel.chooseDefaultNarratorVoice(voiceId)
    override fun cycleSleepTimer() = viewModel.cycleSleepTimer()
    override fun cycleVoice(characterId: String) = viewModel.cycleVoice(characterId)
    override fun assignVoice(
        characterId: String,
        voiceId: String,
        regenerationScope: com.whisperbook.app.domain.model.VoiceRegenerationScope,
    ) = viewModel.assignVoice(characterId, voiceId, regenerationScope).let { Unit }
    override fun revertVoiceChange() = viewModel.revertVoiceChange().let { Unit }
    override fun previewCharacter(characterId: String) = viewModel.previewCharacter(characterId).let { Unit }
    override fun previewVoice(voiceId: String, characterName: String) =
        viewModel.previewVoice(voiceId, characterName).let { Unit }
    override fun setAutoScroll(enabled: Boolean) = viewModel.setAutoScroll(enabled).let { Unit }
    override fun setKeepScreenAwake(enabled: Boolean) = viewModel.setKeepScreenAwake(enabled).let { Unit }
    override fun setLargerText(enabled: Boolean) = viewModel.setLargerText(enabled).let { Unit }
    override fun completeOnboarding() = viewModel.completeOnboarding().let { Unit }
}
