package com.whisperbook.app.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.whisperbook.app.ui.screens.BookDetailsScreen
import com.whisperbook.app.ui.screens.CurrentChapterScreen
import com.whisperbook.app.ui.screens.ImportBookScreen
import com.whisperbook.app.ui.screens.LibraryScreen
import com.whisperbook.app.ui.screens.NowPlayingScreen
import com.whisperbook.app.ui.screens.ProcessingScreen
import com.whisperbook.app.ui.screens.SettingsScreen
import com.whisperbook.app.ui.screens.VoiceCastScreen
import com.whisperbook.app.ui.screens.WelcomeScreen
import com.whisperbook.app.ui.screens.WhisperbookAppState

@Composable
fun WhisperbookNavHost(
    navController: NavHostController,
    appState: WhisperbookAppState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    startDestination: String = WhisperbookDestination.Welcome.route,
) {
    fun backOrLibrary() {
        if (!navController.popBackStack()) navController.navigate(WhisperbookDestination.Library.route)
    }

    NavHost(navController = navController, startDestination = startDestination, modifier = modifier) {
        composable(WhisperbookDestination.Welcome.route) {
            WelcomeScreen(
                contentPadding = contentPadding,
                onImport = {
                    appState.completeOnboarding()
                    navController.navigate(WhisperbookDestination.ImportBook.route)
                },
                onExplore = {
                    appState.completeOnboarding()
                    navController.navigate(WhisperbookDestination.Library.route)
                },
            )
        }
        composable(WhisperbookDestination.Library.route) {
            LibraryScreen(
                contentPadding = contentPadding,
                appState = appState,
                onImport = { navController.navigate(WhisperbookDestination.ImportBook.route) },
                onBook = {
                    appState.selectBook(it)
                    navController.navigate(WhisperbookDestination.BookDetails.route(it))
                },
                onResume = { navController.navigate(WhisperbookDestination.NowPlaying.route) },
            )
        }
        composable(WhisperbookDestination.ImportBook.route) {
            ImportBookScreen(
                contentPadding = contentPadding,
                appState = appState,
                onBack = ::backOrLibrary,
                onChosen = { navController.navigate(WhisperbookDestination.Processing.route) },
                onRecentBook = { bookId ->
                    appState.selectBook(bookId)
                    navController.navigate(WhisperbookDestination.BookDetails.route(bookId))
                },
            )
        }
        composable(WhisperbookDestination.Processing.route) {
            ProcessingScreen(
                contentPadding = contentPadding,
                appState = appState,
                onContinueInBackground = {
                    navController.navigate(WhisperbookDestination.Library.route) {
                        popUpTo(WhisperbookDestination.Library.route) { inclusive = true }
                    }
                },
                onReady = { navController.navigate(WhisperbookDestination.BookDetails.route()) },
                onRetry = appState::retryPreparation,
                onBackToImport = {
                    navController.navigate(WhisperbookDestination.ImportBook.route) {
                        popUpTo(WhisperbookDestination.ImportBook.route) { inclusive = true }
                    }
                },
            )
        }
        composable(WhisperbookDestination.NowPlaying.route) {
            NowPlayingScreen(
                contentPadding = contentPadding,
                appState = appState,
                onBookDetails = { navController.navigate(WhisperbookDestination.BookDetails.route()) },
                onVoiceCast = { navController.navigate(WhisperbookDestination.VoiceCast.route()) },
                onCurrentChapter = { navController.navigate(WhisperbookDestination.CurrentChapter.route()) },
                onSettings = { navController.navigate(WhisperbookDestination.Settings.route) },
            )
        }
        composable(WhisperbookDestination.BookDetails.route) {
            BookDetailsScreen(
                contentPadding = contentPadding,
                appState = appState,
                onBack = ::backOrLibrary,
                onListen = { navController.navigate(WhisperbookDestination.NowPlaying.route) },
                onVoiceCast = { navController.navigate(WhisperbookDestination.VoiceCast.route()) },
                onSettings = { navController.navigate(WhisperbookDestination.Settings.route) },
            )
        }
        composable(WhisperbookDestination.VoiceCast.route) {
            VoiceCastScreen(
                contentPadding = contentPadding,
                appState = appState,
                onBack = ::backOrLibrary,
                onApply = {
                    navController.navigate(WhisperbookDestination.BookDetails.route()) {
                        popUpTo(WhisperbookDestination.BookDetails.route) { inclusive = true }
                    }
                },
            )
        }
        composable(WhisperbookDestination.Settings.route) {
            SettingsScreen(
                contentPadding = contentPadding,
                appState = appState,
                onManageVoices = { navController.navigate(WhisperbookDestination.VoiceCast.route()) },
            )
        }
        composable(WhisperbookDestination.CurrentChapter.route) {
            CurrentChapterScreen(
                contentPadding = contentPadding,
                appState = appState,
                onBack = ::backOrLibrary,
                onVoiceCast = { navController.navigate(WhisperbookDestination.VoiceCast.route()) },
            )
        }
    }
}
