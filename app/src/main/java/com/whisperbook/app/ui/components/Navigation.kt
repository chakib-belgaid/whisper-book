package com.whisperbook.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
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
fun StorybookBottomBar(
    destinations: List<StorybookDestination>,
    selectedRoute: String,
    onDestinationSelected: (StorybookDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WhisperbookTheme.colors
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("storybook-bottom-bar"),
        color = colors.stage,
        contentColor = colors.paper,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = WhisperbookTheme.components.bottomBarHeight)
                .border(width = 1.dp, color = colors.outline.copy(alpha = 0.72f))
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            destinations.forEachIndexed { index, destination ->
                StorybookDestinationItem(
                    destination = destination,
                    selected = destination.route == selectedRoute,
                    onClick = { onDestinationSelected(destination) },
                )
                if (index < destinations.lastIndex) {
                    Spacer(
                        Modifier
                            .heightIn(min = 40.dp)
                            .width(1.dp)
                            .background(colors.outline.copy(alpha = 0.55f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.StorybookDestinationItem(
    destination: StorybookDestination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = WhisperbookTheme.colors
    val foreground = if (selected) colors.ornament else colors.paper
    Box(
        modifier = Modifier
            .weight(1f)
            .defaultMinSize(minHeight = WhisperbookTheme.components.bottomBarHeight)
            .paperSelectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                fold = PaperFold.Tab,
            )
            .semantics {
                contentDescription = destination.label
                stateDescription = if (selected) "Selected" else "Not selected"
            }
            .testTag("bottom-navigation-${destination.route}"),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 5.dp)
                .background(
                    color = if (selected) colors.stageRaised else colors.stage,
                    shape = WhisperbookTheme.shapes.selectedNavigation,
                )
                .then(
                    if (selected) {
                        Modifier.border(
                            width = 1.dp,
                            color = colors.outline.copy(alpha = 0.72f),
                            shape = WhisperbookTheme.shapes.selectedNavigation,
                        )
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 4.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Icon(
                    imageVector = destination.icon,
                    contentDescription = null,
                    tint = foreground,
                    modifier = Modifier.size(25.dp),
                )
                Text(
                    text = destination.label,
                    color = foreground,
                    style = WhisperbookTheme.typography.title.copy(
                        fontSize = 16.sp,
                        lineHeight = 19.sp,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
