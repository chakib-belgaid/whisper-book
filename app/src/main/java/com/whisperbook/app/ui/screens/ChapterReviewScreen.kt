package com.whisperbook.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.whisperbook.app.domain.model.ChapterPlanEntry
import com.whisperbook.app.ui.components.EmbossedCircularButton
import com.whisperbook.app.ui.components.PapercraftButton
import com.whisperbook.app.ui.components.PapercraftButtonVariant
import com.whisperbook.app.ui.components.ParchmentPanel
import com.whisperbook.app.ui.theme.WhisperbookTheme

/**
 * Stateless chapter-plan editor. Selection and order are supplied by the caller; only the
 * transient search query and actions-menu visibility are owned by this screen.
 */
@Composable
fun ChapterReviewScreen(
    contentPadding: PaddingValues,
    chapters: List<ChapterPlanEntry>,
    onToggleChapter: (chapterId: String, selected: Boolean) -> Unit,
    onMoveChapter: (chapterId: String, targetPosition: Int) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onRestoreOriginalOrder: () -> Unit,
    onReset: () -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var actionsExpanded by rememberSaveable { mutableStateOf(false) }
    val orderedChapters = chapters.sortedBy { it.customPosition }
    val selectedChapters = orderedChapters.filter { it.isSelected }
    val normalizedQuery = searchQuery.trim()
    val searchActive = normalizedQuery.isNotEmpty()
    val visibleChapters = orderedChapters.filter { it.matches(normalizedQuery) }
    val includedResults = visibleChapters.filter { it.isSelected }
    val skippedResults = visibleChapters.filterNot { it.isSelected }
    val selectedPositionById = selectedChapters
        .mapIndexed { index, entry -> entry.chapter.id to index }
        .toMap()
    val selectedCount = selectedChapters.size
    val canContinue = selectedCount > 0
    val fullContinueLabel = continueButtonLabel(selectedCount, chapters.size)
    val continueLabel = if (LocalDensity.current.fontScale >= 1.6f) {
        if (selectedCount == chapters.size && chapters.isNotEmpty()) "Continue · All" else "Continue · $selectedCount"
    } else {
        fullContinueLabel
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .testTag("chapter-review-screen"),
    ) {
        ChapterReviewTopBar(onBack = onBack)

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(top = 6.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "chapter-review-introduction") {
                ParchmentPanel(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = "All chapters are included. Turn off anything you don't want to hear, then arrange your listening order.",
                        color = WhisperbookTheme.colors.ink,
                        style = WhisperbookTheme.typography.body,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item(key = "chapter-review-controls") {
                ParchmentPanel(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "$selectedCount of ${chapters.size} selected",
                            color = WhisperbookTheme.colors.ink,
                            style = WhisperbookTheme.typography.title,
                            modifier = Modifier.weight(1f).testTag("chapter-selection-count"),
                        )
                        Box {
                            IconButton(
                                onClick = { actionsExpanded = true },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("chapter-actions"),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.MoreVert,
                                    contentDescription = "Chapter actions",
                                    tint = WhisperbookTheme.colors.action,
                                )
                            }
                            DropdownMenu(
                                expanded = actionsExpanded,
                                onDismissRequest = { actionsExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (searchActive) "Select all results" else "Select all") },
                                    onClick = {
                                        actionsExpanded = false
                                        if (searchActive) {
                                            skippedResults.forEach { entry ->
                                                onToggleChapter(entry.chapter.id, true)
                                            }
                                        } else {
                                            onSelectAll()
                                        }
                                    },
                                    modifier = Modifier.testTag("select-all-chapters"),
                                )
                                DropdownMenuItem(
                                    text = { Text(if (searchActive) "Deselect all results" else "Deselect all") },
                                    onClick = {
                                        actionsExpanded = false
                                        if (searchActive) {
                                            includedResults.forEach { entry ->
                                                onToggleChapter(entry.chapter.id, false)
                                            }
                                        } else {
                                            onDeselectAll()
                                        }
                                    },
                                    modifier = Modifier.testTag("deselect-all-chapters"),
                                )
                                DropdownMenuItem(
                                    text = { Text("Restore original order") },
                                    onClick = {
                                        actionsExpanded = false
                                        onRestoreOriginalOrder()
                                    },
                                    modifier = Modifier.testTag("restore-chapter-order"),
                                )
                                DropdownMenuItem(
                                    text = { Text("Reset chapters") },
                                    onClick = {
                                        actionsExpanded = false
                                        onReset()
                                    },
                                    modifier = Modifier.testTag("reset-chapters"),
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search chapters") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Outlined.Search, contentDescription = null)
                        },
                        trailingIcon = if (searchQuery.isNotEmpty()) {
                            {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(Icons.Outlined.Clear, contentDescription = "Clear search")
                                }
                            }
                        } else {
                            null
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = WhisperbookTheme.colors.ink,
                            unfocusedTextColor = WhisperbookTheme.colors.ink,
                            focusedBorderColor = WhisperbookTheme.colors.action,
                            unfocusedBorderColor = WhisperbookTheme.colors.outline,
                            focusedLabelColor = WhisperbookTheme.colors.action,
                            unfocusedLabelColor = WhisperbookTheme.colors.inkMuted,
                            focusedLeadingIconColor = WhisperbookTheme.colors.action,
                            unfocusedLeadingIconColor = WhisperbookTheme.colors.inkMuted,
                            focusedTrailingIconColor = WhisperbookTheme.colors.action,
                            unfocusedTrailingIconColor = WhisperbookTheme.colors.inkMuted,
                            cursorColor = WhisperbookTheme.colors.action,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .testTag("chapter-search"),
                    )
                    if (searchActive) {
                        Text(
                            text = "Reordering is available after clearing search.",
                            color = WhisperbookTheme.colors.inkMuted,
                            style = WhisperbookTheme.typography.label,
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        )
                    }
                }
            }

            item(key = "chapter-list-heading") {
                ChapterSectionHeading(
                    title = "Chapters",
                    supportingText = when {
                        searchActive -> "${visibleChapters.size} matching chapters"
                        chapters.isEmpty() -> "No chapters found"
                        else -> "Selected chapters play in listening order"
                    },
                )
            }
            items(
                items = visibleChapters,
                key = { "chapter-${it.chapter.id}" },
            ) { entry ->
                val selectedIndex = selectedPositionById[entry.chapter.id]
                ChapterPlanRow(
                    entry = entry,
                    listeningPosition = selectedIndex?.plus(1),
                    canMoveEarlier = !searchActive && selectedIndex != null && selectedIndex > 0,
                    canMoveLater = !searchActive && selectedIndex != null && selectedIndex < selectedChapters.lastIndex,
                    onToggle = { onToggleChapter(entry.chapter.id, !entry.isSelected) },
                    onMoveEarlier = {
                        selectedIndex?.let { onMoveChapter(entry.chapter.id, it - 1) }
                    },
                    onMoveLater = {
                        selectedIndex?.let { onMoveChapter(entry.chapter.id, it + 1) }
                    },
                )
            }

            if (searchActive && visibleChapters.isEmpty()) {
                item(key = "empty-search") {
                    Text(
                        text = "No chapters match \"$normalizedQuery\".",
                        color = WhisperbookTheme.colors.onStage,
                        style = WhisperbookTheme.typography.body,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    )
                }
            }
        }

        if (!canContinue) {
            Text(
                text = "Select at least one chapter to continue.",
                color = WhisperbookTheme.colors.paper,
                style = WhisperbookTheme.typography.label,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .testTag("chapter-selection-required"),
            )
        }
        PapercraftButton(
            text = continueLabel,
            onClick = onContinue,
            enabled = canContinue,
            variant = PapercraftButtonVariant.Accent,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp)
                .semantics { contentDescription = fullContinueLabel }
                .testTag("confirm-chapter-plan"),
        )
    }
}

@Composable
private fun ChapterReviewTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            EmbossedCircularButton(
                onClick = onBack,
                contentDescription = "Back",
                size = 44.dp,
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
            }
        }
        Text(
            text = "Choose chapters",
            color = WhisperbookTheme.colors.onStage,
            style = WhisperbookTheme.typography.display,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.size(48.dp))
    }
}

@Composable
private fun ChapterSectionHeading(
    title: String,
    supportingText: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Text(
            text = title,
            color = WhisperbookTheme.colors.onStage,
            style = WhisperbookTheme.typography.title,
        )
        Text(
            text = supportingText,
            color = WhisperbookTheme.colors.onStage.copy(alpha = 0.78f),
            style = WhisperbookTheme.typography.label,
        )
    }
}

@Composable
private fun ChapterPlanRow(
    entry: ChapterPlanEntry,
    listeningPosition: Int?,
    canMoveEarlier: Boolean,
    canMoveLater: Boolean,
    onToggle: () -> Unit,
    onMoveEarlier: () -> Unit,
    onMoveLater: () -> Unit,
) {
    val colors = WhisperbookTheme.colors
    val selected = entry.isSelected
    val movementActions = buildList {
        if (canMoveEarlier) {
            add(CustomAccessibilityAction("Move earlier") {
                onMoveEarlier()
                true
            })
        }
        if (canMoveLater) {
            add(CustomAccessibilityAction("Move later") {
                onMoveLater()
                true
            })
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 76.dp)
            .clip(WhisperbookTheme.shapes.card)
            .background(colors.paper)
            .border(1.dp, colors.outline, WhisperbookTheme.shapes.card)
            .toggleable(
                value = selected,
                role = Role.Checkbox,
                onValueChange = { onToggle() },
            )
            .semantics {
                this.selected = selected
                stateDescription = if (selected) "Included" else "Skipped for now"
                customActions = movementActions
            }
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .testTag("chapter-plan-${entry.chapter.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(
                checkedColor = colors.action,
                uncheckedColor = colors.inkMuted,
                checkmarkColor = colors.onStage,
            ),
            modifier = Modifier.size(48.dp),
        )
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = entry.chapter.title.ifBlank { "Untitled chapter" },
                color = colors.ink,
                style = WhisperbookTheme.typography.body,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (listeningPosition != null) {
                    "Listening position $listeningPosition · Original chapter ${entry.chapter.ordinal + 1}"
                } else {
                    "Original chapter ${entry.chapter.ordinal + 1}"
                },
                color = colors.inkMuted,
                style = WhisperbookTheme.typography.label,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MoveChapterButton(
                    contentDescription = "Move ${entry.chapter.title} earlier",
                    enabled = canMoveEarlier,
                    onClick = onMoveEarlier,
                    icon = { Icon(Icons.Outlined.ArrowUpward, contentDescription = null) },
                    testTag = "move-${entry.chapter.id}-earlier",
                )
                MoveChapterButton(
                    contentDescription = "Move ${entry.chapter.title} later",
                    enabled = canMoveLater,
                    onClick = onMoveLater,
                    icon = { Icon(Icons.Outlined.ArrowDownward, contentDescription = null) },
                    testTag = "move-${entry.chapter.id}-later",
                )
            }
        }
    }
}

@Composable
private fun MoveChapterButton(
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    testTag: String,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(48.dp)
            .testTag(testTag)
            .semantics {
                this.contentDescription = contentDescription
                if (!enabled) disabled()
            },
    ) {
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
    }
}

private fun ChapterPlanEntry.matches(normalizedQuery: String): Boolean {
    if (normalizedQuery.isEmpty()) return true
    return chapter.title.contains(normalizedQuery, ignoreCase = true) ||
        (chapter.ordinal + 1).toString() == normalizedQuery
}

internal fun continueButtonLabel(selectedCount: Int, totalCount: Int): String = when {
    selectedCount == totalCount && totalCount > 0 -> "Continue with all chapters"
    else -> "Continue with $selectedCount ${if (selectedCount == 1) "chapter" else "chapters"}"
}
