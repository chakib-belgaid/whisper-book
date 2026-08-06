package com.whisperbook.app.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.whisperbook.app.ui.components.OrigamiPage
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
        if (!navController.popBackStack()) {
            navController.navigateToBottomDestination(WhisperbookDestination.Library.route)
        }
    }

    NavHost(navController = navController, startDestination = startDestination, modifier = modifier) {
        composable(WhisperbookDestination.Welcome.route) {
            OrigamiPage {
                WelcomeScreen(
                    contentPadding = contentPadding,
                    onImport = {
                        appState.completeOnboarding()
                        navController.navigateToBottomDestination(WhisperbookDestination.Library.route)
                        navController.navigate(WhisperbookDestination.ImportBook.route)
                    },
                    onExplore = {
                        appState.completeOnboarding()
                        navController.navigateToBottomDestination(WhisperbookDestination.Library.route)
                    },
                )
            }
        }
        composable(WhisperbookDestination.Library.route) {
            OrigamiPage {
                LibraryScreen(
                    contentPadding = contentPadding,
                    appState = appState,
                    onImport = { navController.navigate(WhisperbookDestination.ImportBook.route) },
                    onBook = {
                        appState.selectBook(it)
                        navController.navigate(WhisperbookDestination.BookDetails.route(it))
                    },
                    onResume = {
                        navController.navigate(
                            if (!appState.canListen) {
                                WhisperbookDestination.Processing.route
                            } else {
                                WhisperbookDestination.NowPlaying.route
                            },
                        )
                    },
                    onRemoveBook = appState::deleteBook,
                )
            }
        }
        composable(WhisperbookDestination.ImportBook.route) {
            OrigamiPage {
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
        }
        composable(WhisperbookDestination.Processing.route) {
            OrigamiPage {
                ProcessingScreen(
                    contentPadding = contentPadding,
                    appState = appState,
                    onContinueInBackground = {
                        navController.navigateToBottomDestination(WhisperbookDestination.Library.route)
                    },
                    onReady = {
                        appState.startPlayback()
                        navController.navigate(WhisperbookDestination.NowPlaying.route)
                    },
                    onRetry = appState::retryPreparation,
                    onBackToImport = {
                        navController.navigate(WhisperbookDestination.ImportBook.route) {
                            popUpTo(WhisperbookDestination.ImportBook.route) { inclusive = true }
                        }
                    },
                )
            }
        }
        composable(WhisperbookDestination.NowPlaying.route) {
            OrigamiPage {
                NowPlayingScreen(
                    contentPadding = contentPadding,
                    appState = appState,
                    onBookDetails = { navController.navigate(WhisperbookDestination.BookDetails.route()) },
                    onVoiceCast = { navController.navigate(WhisperbookDestination.VoiceCast.route()) },
                    onCurrentChapter = { navController.navigate(WhisperbookDestination.CurrentChapter.route()) },
                    onSettings = { navController.navigate(WhisperbookDestination.Settings.route) },
                )
            }
        }
        composable(WhisperbookDestination.BookDetails.route) {
            OrigamiPage {
                BookDetailsScreen(
                    contentPadding = contentPadding,
                    appState = appState,
                    onBack = ::backOrLibrary,
                    onListen = {
                        navController.navigate(
                            if (!appState.canListen) {
                                WhisperbookDestination.Processing.route
                            } else {
                                WhisperbookDestination.NowPlaying.route
                            },
                        )
                    },
                    onVoiceCast = { navController.navigate(WhisperbookDestination.VoiceCast.route()) },
                    onSettings = { navController.navigate(WhisperbookDestination.Settings.route) },
                    onRemove = {
                        appState.deleteSelectedBook()
                        navController.navigateToBottomDestination(WhisperbookDestination.Library.route)
                    },
                )
            }
        }
        composable(WhisperbookDestination.VoiceCast.route) {
            OrigamiPage {
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
        }
        composable(WhisperbookDestination.Settings.route) {
            OrigamiPage {
                SettingsScreen(
                    contentPadding = contentPadding,
                    appState = appState,
                    onManageVoices = { navController.navigate(WhisperbookDestination.VoiceCast.route()) },
                )
            }
        }
        composable(WhisperbookDestination.CurrentChapter.route) {
            OrigamiPage {
                CurrentChapterScreen(
                    contentPadding = contentPadding,
                    appState = appState,
                    onBack = ::backOrLibrary,
                    onVoiceCast = { navController.navigate(WhisperbookDestination.VoiceCast.route()) },
                )
            }
        }
    }
}
