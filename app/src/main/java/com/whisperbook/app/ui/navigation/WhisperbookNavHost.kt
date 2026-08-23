package com.whisperbook.app.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.whisperbook.app.domain.model.PreparationStage
import com.whisperbook.app.ui.components.OrigamiPage
import com.whisperbook.app.ui.screens.BookDetailsScreen
import com.whisperbook.app.ui.screens.ChapterReviewScreen
import com.whisperbook.app.ui.screens.CurrentChapterScreen
import com.whisperbook.app.ui.screens.ImportBookScreen
import com.whisperbook.app.ui.screens.LibraryScreen
import com.whisperbook.app.ui.screens.NowPlayingScreen
import com.whisperbook.app.ui.screens.NarrationSetupScreen
import com.whisperbook.app.ui.screens.ParsingChapterHeader
import com.whisperbook.app.ui.screens.ParsingScreen
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

    fun routeForBook(bookId: String, preferPlayback: Boolean = false): String {
        val book = appState.books.firstOrNull { it.id == bookId }
            ?: return WhisperbookDestination.Library.route
        return when {
            book.preparation.stage == PreparationStage.COPY_AND_VALIDATE ||
                book.preparation.stage == PreparationStage.READING_CHAPTERS ->
                WhisperbookDestination.Parsing.route
            book.needsChapterReview -> WhisperbookDestination.ChapterReview.route
            book.needsNarrationSetup -> WhisperbookDestination.NarrationSetup.route
            preferPlayback && book.canListen -> WhisperbookDestination.NowPlaying.route
            !book.canListen && book.preparation.stage != PreparationStage.READY ->
                WhisperbookDestination.Processing.route
            else -> WhisperbookDestination.BookDetails.route(bookId)
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
                        navController.navigate(routeForBook(it))
                    },
                    onResume = { bookId ->
                        appState.selectBook(bookId)
                        navController.navigate(routeForBook(bookId, preferPlayback = true))
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
                            navController.navigate(WhisperbookDestination.Parsing.route)
                        }
                    },
                    onRecentBook = { bookId ->
                        appState.selectBook(bookId)
                        navController.navigate(routeForBook(bookId))
                    },
                )
            }
        }
        composable(WhisperbookDestination.Parsing.route) {
            val book = appState.currentBook
            val preparation = appState.preparationStatus
            LaunchedEffect(preparation?.stage, appState.currentBookId) {
                if (preparation?.stage == PreparationStage.AWAITING_CHAPTER_SELECTION) {
                    navController.navigate(WhisperbookDestination.ChapterReview.route) {
                        popUpTo(WhisperbookDestination.Parsing.route) { inclusive = true }
                    }
                }
            }
            if (book != null && preparation != null) {
                OrigamiPage {
                    ParsingScreen(
                        contentPadding = contentPadding,
                        book = book,
                        preparation = preparation,
                        discoveredChapters = appState.chapters.map { chapter ->
                            ParsingChapterHeader(
                                id = chapter.id,
                                ordinal = chapter.number - 1,
                                title = chapter.title,
                            )
                        },
                        onBack = ::backOrLibrary,
                        onContinueInBackground = {
                            navController.navigateToBottomDestination(WhisperbookDestination.Library.route)
                        },
                        onPause = appState::pausePreparation,
                        onResume = appState::resumePreparation,
                        onCancel = appState::cancelPreparation,
                        onRetry = appState::retryPreparation,
                    )
                }
            }
        }
        composable(WhisperbookDestination.ChapterReview.route) {
            OrigamiPage {
                ChapterReviewScreen(
                    contentPadding = contentPadding,
                    chapters = appState.chapterPlan,
                    onToggleChapter = appState::setChapterSelected,
                    onMoveChapter = appState::moveChapter,
                    onSelectAll = appState::selectAllChapters,
                    onDeselectAll = appState::deselectAllChapters,
                    onRestoreOriginalOrder = appState::restoreOriginalChapterOrder,
                    onReset = appState::resetChapterPlan,
                    onContinue = {
                        appState.confirmChapterPlan {
                            val destination = if (appState.narrationSetupRequired) {
                                WhisperbookDestination.NarrationSetup.route
                            } else {
                                WhisperbookDestination.Processing.route
                            }
                            if (navController.currentDestination?.route == WhisperbookDestination.ChapterReview.route) {
                                navController.navigate(destination) {
                                    popUpTo(WhisperbookDestination.ChapterReview.route) { inclusive = true }
                                }
                            }
                        }
                    },
                    onBack = ::backOrLibrary,
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
            LaunchedEffect(appState.canListen, appState.currentBookId) {
                if (
                    appState.canListen &&
                    navController.currentDestination?.route == WhisperbookDestination.Processing.route
                ) {
                    appState.startPlayback()
                    navController.navigate(WhisperbookDestination.NowPlaying.route) {
                        popUpTo(WhisperbookDestination.Processing.route) { inclusive = true }
                    }
                }
            }
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
                    selectedChapterPlan = appState.chapterPlan.filter { it.isSelected },
                    onEditChapters = {
                        navController.navigate(WhisperbookDestination.ChapterReview.route)
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
                                appState.requiresChapterReview -> WhisperbookDestination.ChapterReview.route
                                appState.narrationSetupRequired -> WhisperbookDestination.NarrationSetup.route
                                appState.preparationStatus?.stage == PreparationStage.COPY_AND_VALIDATE ||
                                    appState.preparationStatus?.stage == PreparationStage.READING_CHAPTERS ->
                                    WhisperbookDestination.Parsing.route
                                !appState.canListen -> WhisperbookDestination.Processing.route
                                else -> WhisperbookDestination.NowPlaying.route
                            },
                        )
                    },
                    onEditChapters = {
                        navController.navigate(WhisperbookDestination.ChapterReview.route)
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
