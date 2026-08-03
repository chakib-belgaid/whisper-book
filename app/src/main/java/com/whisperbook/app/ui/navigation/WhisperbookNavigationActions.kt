package com.whisperbook.app.ui.navigation

import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination

/**
 * Moves between the three persistent destinations while keeping Library as the
 * single root. Onboarding and deep-link entry can reach a tab before Library
 * exists, so the anchor is created before applying the normal saved-state tab
 * behavior.
 */
internal fun NavHostController.navigateToBottomDestination(route: String) {
    val libraryRoute = WhisperbookDestination.Library.route
    val hasLibraryAnchor = runCatching { getBackStackEntry(libraryRoute) }.isSuccess

    if (!hasLibraryAnchor) {
        navigate(libraryRoute) {
            popUpTo(graph.findStartDestination().id) { inclusive = true }
            launchSingleTop = true
        }
    }

    if (route == libraryRoute) {
        if (hasLibraryAnchor) {
            navigate(libraryRoute) {
                popUpTo(libraryRoute) { inclusive = false }
                launchSingleTop = true
                restoreState = true
            }
        }
        return
    }

    navigate(route) {
        popUpTo(libraryRoute) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
