package com.whisperbook.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whisperbook.app.integration.WhisperbookViewModel
import com.whisperbook.app.ui.components.StorybookBottomBar
import com.whisperbook.app.ui.components.StorybookDestination
import com.whisperbook.app.ui.components.WhisperBackdrop
import com.whisperbook.app.ui.navigation.WhisperbookDestination
import com.whisperbook.app.ui.navigation.WhisperbookNavHost
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
    LaunchedEffect(snapshot) { appState.synchronize(snapshot) }
    val startDestination = remember {
        if (snapshot.settings.onboardingComplete) WhisperbookDestination.Library.route
        else WhisperbookDestination.Welcome.route
    }
    LaunchedEffect(snapshot.settings.onboardingComplete) {
        if (
            snapshot.settings.onboardingComplete &&
            navController.currentDestination?.route == WhisperbookDestination.Welcome.route
        ) {
            navController.navigate(WhisperbookDestination.Library.route) {
                popUpTo(WhisperbookDestination.Welcome.route) { inclusive = true }
                launchSingleTop = true
            }
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
                                navController.navigate(destination.route) {
                                    launchSingleTop = true
                                    restoreState = true
                                    popUpTo(WhisperbookDestination.Library.route) { saveState = true }
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
                }
            }
        }
        }
    }
}

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
    override fun selectBook(bookId: String) = viewModel.selectBook(bookId)
    override fun selectChapter(chapterId: String) = viewModel.selectChapter(chapterId)
    override fun playOrPause() = viewModel.playOrPause().let { Unit }
    override fun seekByFraction(delta: Float) = viewModel.seekBy(if (delta < 0f) -15_000L else 15_000L).let { Unit }
    override fun seekToFraction(fraction: Float) = viewModel.seekToFraction(fraction).let { Unit }
    override fun seekToPassage(passageId: String) = viewModel.seekToPassage(passageId).let { Unit }
    override fun cycleSpeed() = viewModel.cycleSpeed()
    override fun cycleDefaultNarratorVoice() = viewModel.cycleDefaultNarratorVoice()
    override fun cycleSleepTimer() = viewModel.cycleSleepTimer()
    override fun cycleVoice(characterId: String) = viewModel.cycleVoice(characterId)
    override fun previewCharacter(characterId: String) = viewModel.previewCharacter(characterId).let { Unit }
    override fun setAutoScroll(enabled: Boolean) = viewModel.setAutoScroll(enabled).let { Unit }
    override fun setKeepScreenAwake(enabled: Boolean) = viewModel.setKeepScreenAwake(enabled).let { Unit }
    override fun setLargerText(enabled: Boolean) = viewModel.setLargerText(enabled).let { Unit }
    override fun completeOnboarding() = viewModel.completeOnboarding().let { Unit }
}
