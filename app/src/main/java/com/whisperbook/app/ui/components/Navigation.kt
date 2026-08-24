package com.whisperbook.app.ui.components

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldValue
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whisperbook.app.ui.theme.WhisperbookTheme

@Immutable
data class StorybookDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

@Composable
fun StorybookNavigationSuite(
    destinations: List<StorybookDestination>,
    selectedRoute: String,
    onDestinationSelected: (StorybookDestination) -> Unit,
    modifier: Modifier = Modifier,
    showNavigation: Boolean = true,
    layoutType: NavigationSuiteType = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(
        currentWindowAdaptiveInfo(),
    ),
    content: @Composable () -> Unit,
) {
    val colors = WhisperbookTheme.colors
    val scaffoldState = rememberNavigationSuiteScaffoldState(
        initialValue = if (showNavigation) {
            NavigationSuiteScaffoldValue.Visible
        } else {
            NavigationSuiteScaffoldValue.Hidden
        },
    )
    LaunchedEffect(showNavigation) {
        scaffoldState.snapTo(
            if (showNavigation) {
                NavigationSuiteScaffoldValue.Visible
            } else {
                NavigationSuiteScaffoldValue.Hidden
            },
        )
    }
    val navigationSuiteColors = NavigationSuiteDefaults.colors(
        navigationBarContainerColor = colors.stage,
        navigationRailContainerColor = colors.stage,
        navigationDrawerContainerColor = colors.stage,
    )
    val itemColors = NavigationSuiteDefaults.itemColors(
        navigationBarItemColors = NavigationBarItemDefaults.colors(
            selectedIconColor = colors.ornament,
            selectedTextColor = colors.ornament,
            indicatorColor = colors.stageRaised,
            unselectedIconColor = colors.paper,
            unselectedTextColor = colors.paper,
        ),
        navigationRailItemColors = NavigationRailItemDefaults.colors(
            selectedIconColor = colors.ornament,
            selectedTextColor = colors.ornament,
            indicatorColor = colors.stageRaised,
            unselectedIconColor = colors.paper,
            unselectedTextColor = colors.paper,
        ),
        navigationDrawerItemColors = NavigationDrawerItemDefaults.colors(
            selectedIconColor = colors.ornament,
            selectedTextColor = colors.ornament,
            selectedContainerColor = colors.stageRaised,
            unselectedIconColor = colors.paper,
            unselectedTextColor = colors.paper,
        ),
    )

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            destinations.forEach { destination ->
                val selected = destination.route == selectedRoute
                item(
                    selected = selected,
                    onClick = { onDestinationSelected(destination) },
                    icon = {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = null,
                            modifier = Modifier.size(25.dp),
                        )
                    },
                    label = {
                        Text(
                            text = destination.label,
                            style = WhisperbookTheme.typography.title.copy(
                                fontSize = 16.sp,
                                lineHeight = 19.sp,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .semantics {
                            contentDescription = destination.label
                            stateDescription = if (selected) "Selected" else "Not selected"
                        }
                        .testTag("bottom-navigation-${destination.route}"),
                    colors = itemColors,
                )
            }
        },
        modifier = modifier.testTag("storybook-navigation-suite"),
        layoutType = layoutType,
        navigationSuiteColors = navigationSuiteColors,
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = colors.onStage,
        state = scaffoldState,
        content = content,
    )
}
