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
import com.whisperbook.app.ui.screens.NarrationSetupScreen
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
                        navController.navigate(
                            if (appState.requiresNarrationSetup(it)) {
                                WhisperbookDestination.NarrationSetup.route
                            } else {
                                WhisperbookDestination.BookDetails.route(it)
                            },
                        )
                    },
                    onResume = { bookId ->
                        appState.selectBook(bookId)
                        navController.navigate(
                            if (appState.requiresNarrationSetup(bookId)) {
                                WhisperbookDestination.NarrationSetup.route
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
                    onChosen = {
                        if (navController.currentDestination?.route == WhisperbookDestination.ImportBook.route) {
                            navController.navigate(
                                if (appState.narrationSetupRequired) {
                                    WhisperbookDestination.NarrationSetup.route
                                } else {
                                    WhisperbookDestination.BookDetails.route(appState.currentBookId)
                                },
                            )
                        }
                    },
                    onRecentBook = { bookId ->
                        appState.selectBook(bookId)
                        navController.navigate(
                            if (appState.requiresNarrationSetup(bookId)) {
                                WhisperbookDestination.NarrationSetup.route
                            } else {
                                WhisperbookDestination.BookDetails.route(bookId)
                            },
                        )
                    },
                )
            }
        }
        composable(WhisperbookDestination.NarrationSetup.route) {
            OrigamiPage {
                NarrationSetupScreen(
                    contentPadding = contentPadding,
                    appState = appState,
                    onBack = ::backOrLibrary,
                    onStartGeneration = {
                        appState.confirmNarrationSetup {
                            if (
                                navController.currentDestination?.route ==
                                WhisperbookDestination.NarrationSetup.route
                            ) {
                                navController.navigate(WhisperbookDestination.Processing.route) {
                                    popUpTo(WhisperbookDestination.NarrationSetup.route) { inclusive = true }
                                }
                            }
                        }
                    },
                    onOpenBook = {
                        navController.navigate(WhisperbookDestination.BookDetails.route()) {
                            popUpTo(WhisperbookDestination.NarrationSetup.route) { inclusive = true }
                        }
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
                    onPause = appState::pausePreparation,
                    onResume = appState::resumePreparation,
                    onCancel = appState::cancelPreparation,
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
                            when {
                                appState.narrationSetupRequired -> WhisperbookDestination.NarrationSetup.route
                                !appState.canListen -> WhisperbookDestination.Processing.route
                                else -> WhisperbookDestination.NowPlaying.route
                            },
                        )
                    },
                    onVoiceCast = { navController.navigate(WhisperbookDestination.VoiceCast.route()) },
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
