package com.whisperbook.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(WhisperbookTheme.components.bottomBarHeight)
            .background(colors.stage)
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
                        .fillMaxHeight(0.64f)
                        .width(1.dp)
                        .background(colors.outline.copy(alpha = 0.55f)),
                )
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
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
            )
            .semantics {
                contentDescription = destination.label
                stateDescription = if (selected) "Selected" else "Not selected"
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Icon(
                imageVector = destination.icon,
                contentDescription = null,
                tint = if (selected) colors.ornament else colors.paper,
                modifier = Modifier.size(25.dp),
            )
            Text(
                text = destination.label,
                color = if (selected) colors.onStage else colors.paper,
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
