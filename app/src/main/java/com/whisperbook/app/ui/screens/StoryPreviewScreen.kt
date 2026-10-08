package com.whisperbook.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.whisperbook.app.domain.model.CharacterAgeGroup
import com.whisperbook.app.domain.model.CharacterGender
import com.whisperbook.app.domain.model.NarrationPerspective
import com.whisperbook.app.domain.model.SpeakerCorrectionScope
import com.whisperbook.app.ui.components.CharacterMedallion
import com.whisperbook.app.ui.components.PaperFold
import com.whisperbook.app.ui.components.PapercraftButton
import com.whisperbook.app.ui.components.PapercraftButtonVariant
import com.whisperbook.app.ui.components.ParchmentPanel
import com.whisperbook.app.ui.components.SpeakerPassageCard
import com.whisperbook.app.ui.components.paperClickable
import com.whisperbook.app.ui.components.paperSelectable
import com.whisperbook.app.ui.theme.WhisperbookTheme

enum class StoryPreviewTab { Characters, Chapters }

private data class PendingStoryCorrection(
    val passage: PassageUi,
    val target: CastMemberUi,
)

/**
 * Stateless story review shown after every selected chapter is attributed and before any voice
 * is cast. The bible, chapters and correction targets are supplied by the caller; only the
 * visible tab, chapter, scroll target and correction dialogs are owned by this screen.
 */
@Composable
fun StoryPreviewScreen(
    contentPadding: PaddingValues,
    bookTitle: String,
    characters: List<StoryCharacterUi>,
    chapters: List<StoryChapterUi>,
    cast: List<CastMemberUi>,
    isBusy: Boolean,
    onCorrectSpeaker: (passageIds: List<String>, speakerId: String, scope: SpeakerCorrectionScope) -> Unit,
    onGenerateVoices: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    busyMessage: String? = null,
) {
    var selectedTab by rememberSaveable { mutableStateOf(StoryPreviewTab.Characters) }
    var selectedChapterId by rememberSaveable { mutableStateOf<String?>(null) }
    var scrollTargetPassageId by rememberSaveable { mutableStateOf<String?>(null) }
    var correctingPassageId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCorrection by remember { mutableStateOf<PendingStoryCorrection?>(null) }
    val selectedChapter = chapters.firstOrNull { it.id == selectedChapterId } ?: chapters.firstOrNull()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .testTag("story-preview-screen"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StageTopBar("Story preview", onBack = onBack)
        ParchmentPanel(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = storyPreviewSummary(bookTitle, characters.size, chapters.size),
                color = WhisperbookTheme.colors.ink,
                style = WhisperbookTheme.typography.body,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().testTag("story-preview-summary"),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DetailTab(
                text = "Characters",
                icon = Icons.Outlined.Groups,
                selected = selectedTab == StoryPreviewTab.Characters,
                onClick = { selectedTab = StoryPreviewTab.Characters },
                modifier = Modifier.weight(1f).testTag("story-tab-characters"),
            )
            DetailTab(
                text = "Chapters",
                icon = Icons.Outlined.AutoStories,
                selected = selectedTab == StoryPreviewTab.Chapters,
                onClick = { selectedTab = StoryPreviewTab.Chapters },
                modifier = Modifier.weight(1f).testTag("story-tab-chapters"),
            )
        }
        when (selectedTab) {
            StoryPreviewTab.Characters -> StoryBible(
                characters = characters,
                onCharacterSelected = { character ->
                    character.firstChapterId?.let { chapterId ->
                        selectedChapterId = chapterId
                        scrollTargetPassageId = character.firstPassageId
                        selectedTab = StoryPreviewTab.Chapters
                    }
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            StoryPreviewTab.Chapters -> StoryChapters(
                chapters = chapters,
                selectedChapter = selectedChapter,
                scrollTargetPassageId = scrollTargetPassageId,
                canCorrect = cast.isNotEmpty(),
                onChapterSelected = { chapterId ->
                    selectedChapterId = chapterId
                    scrollTargetPassageId = null
                },
                onScrolledToTarget = { scrollTargetPassageId = null },
                onCorrectPassage = { passage -> correctingPassageId = passage.id },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
        PapercraftButton(
            text = "Generate voices",
            onClick = onGenerateVoices,
            enabled = characters.isNotEmpty(),
            isLoading = isBusy,
            loadingDescription = busyMessage ?: "Saving your story review",
            variant = PapercraftButtonVariant.Accent,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp)
                .testTag("generate-voices"),
        )
    }

    correctingPassageId?.let { passageId ->
        selectedChapter?.passages?.firstOrNull { it.id == passageId }?.let { passage ->
            AttributedSpeakerPickerSheet(
                passage = passage,
                cast = cast,
                onDismiss = { correctingPassageId = null },
                onSpeakerSelected = { target ->
                    correctingPassageId = null
                    if (target.id != passage.speakerId) {
                        pendingCorrection = PendingStoryCorrection(passage, target)
                    }
                },
            )
        }
    }
    pendingCorrection?.let { pending ->
        SpeakerCorrectionScopeDialog(
            passage = pending.passage,
            target = pending.target,
            bookTitle = bookTitle,
            onConfirm = { scope ->
                pendingCorrection = null
                onCorrectSpeaker(pending.passage.sourcePassageIds, pending.target.id, scope)
            },
            onDismiss = { pendingCorrection = null },
        )
    }
}

@Composable
private fun StoryBible(
    characters: List<StoryCharacterUi>,
    onCharacterSelected: (StoryCharacterUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.testTag("story-bible"),
        contentPadding = PaddingValues(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (characters.isEmpty()) {
            item(key = "story-bible-empty") {
                StoryEmptyState("The characters will appear here once the story has been read.")
            }
        }
        items(characters, key = { "character-${it.id}" }) { character ->
            StoryCharacterCard(
                character = character,
                onClick = { onCharacterSelected(character) },
                modifier = Modifier.testTag("story-character-${character.id}"),
            )
        }
    }
}

@Composable
private fun StoryCharacterCard(
    character: StoryCharacterUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WhisperbookTheme.colors
    val accent = speakerColor(character.role)
    val profile = characterProfileText(character)
    val appearances = characterAppearanceText(character)
    ParchmentPanel(
        modifier = modifier
            .fillMaxWidth()
            .paperClickable(
                onClick = onClick,
                enabled = character.firstPassageId != null,
                role = Role.Button,
                fold = PaperFold.Card,
                onClickLabel = "Show ${character.name}'s first line",
            ),
        contentPadding = PaddingValues(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CharacterMedallion(
                speakerName = character.name,
                portraitRes = character.portraitRes,
                accentColor = accent,
                size = 76.dp,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = character.name,
                    color = colors.ink,
                    style = WhisperbookTheme.typography.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (character.aliases.isNotEmpty()) {
                    Text(
                        text = "Also called ${character.aliases.joinToString()}",
                        color = colors.inkMuted,
                        style = WhisperbookTheme.typography.label,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = profile,
                    color = colors.ink,
                    style = WhisperbookTheme.typography.label,
                    modifier = Modifier.testTag("story-character-profile-${character.id}"),
                )
                Text(
                    text = appearances,
                    color = colors.inkMuted,
                    style = WhisperbookTheme.typography.label,
                    modifier = Modifier.testTag("story-character-appearances-${character.id}"),
                )
                character.sampleLine?.let { sample ->
                    Text(
                        text = "“$sample”",
                        color = colors.ink,
                        style = WhisperbookTheme.typography.body.copy(fontStyle = FontStyle.Italic),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun StoryChapters(
    chapters: List<StoryChapterUi>,
    selectedChapter: StoryChapterUi?,
    scrollTargetPassageId: String?,
    canCorrect: Boolean,
    onChapterSelected: (String) -> Unit,
    onScrolledToTarget: () -> Unit,
    onCorrectPassage: (PassageUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chapterListState = rememberLazyListState()
    // Each chapter opens at its first passage unless a character card asked for a specific line.
    val passageListState = remember(selectedChapter?.id) { LazyListState() }
    LaunchedEffect(selectedChapter?.id) {
        val index = chapters.indexOfFirst { it.id == selectedChapter?.id }
        if (index >= 0) chapterListState.animateScrollToItem(index)
    }
    LaunchedEffect(selectedChapter?.id, scrollTargetPassageId) {
        val targetPassageId = scrollTargetPassageId ?: return@LaunchedEffect
        val index = selectedChapter?.passages.orEmpty()
            .indexOfFirst { targetPassageId in it.sourcePassageIds }
        if (index >= 0) passageListState.scrollToItem(index)
        onScrolledToTarget()
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LazyRow(
            state = chapterListState,
            modifier = Modifier.fillMaxWidth().testTag("story-chapter-picker"),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(chapters, key = { "chapter-${it.id}" }) { chapter ->
                StoryChapterChip(
                    chapter = chapter,
                    selected = chapter.id == selectedChapter?.id,
                    onClick = { onChapterSelected(chapter.id) },
                )
            }
        }
        LazyColumn(
            state = passageListState,
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("story-chapter-passages"),
            contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            val passages = selectedChapter?.passages.orEmpty()
            if (passages.isEmpty()) {
                item(key = "story-chapter-empty") {
                    StoryEmptyState("This chapter has no readable passages.")
                }
            }
            itemsIndexed(passages, key = { _, passage -> passage.id }) { index, passage ->
                val correct = { onCorrectPassage(passage) }
                SpeakerPassageCard(
                    speakerName = passage.speakerName,
                    passage = passage.text,
                    accentColor = speakerColor(passage.speaker),
                    isActive = false,
                    onClick = if (canCorrect) correct else ({}),
                    onChangeAttributedVoice = correct.takeIf { canCorrect && passage.speakerId.isNotBlank() },
                    changeAttributedVoiceDescription = "Correct who reads this passage, now ${passage.speakerName}",
                    showPlaybackAffordance = false,
                    modifier = Modifier.testTag("story-passage-${index + 1}"),
                )
            }
        }
    }
}

@Composable
private fun StoryChapterChip(
    chapter: StoryChapterUi,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = WhisperbookTheme.colors
    val shape = RoundedCornerShape(9.dp)
    Text(
        text = "${chapter.listeningPosition}. ${chapter.title.ifBlank { "Chapter ${chapter.number}" }}",
        color = colors.ink,
        style = WhisperbookTheme.typography.label,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .widthIn(max = 220.dp)
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (selected) colors.paperHighlight else colors.paper)
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.action else colors.outline, shape)
            .paperSelectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                fold = PaperFold.Tab,
            )
            .semantics {
                contentDescription = "Listening position ${chapter.listeningPosition}, " +
                    "chapter ${chapter.number}: ${chapter.title}"
            }
            .padding(horizontal = 12.dp, vertical = 14.dp)
            .testTag("story-chapter-${chapter.id}"),
    )
}

@Composable
private fun StoryEmptyState(text: String) {
    ParchmentPanel(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
        Text(
            text = text,
            color = WhisperbookTheme.colors.ink,
            style = WhisperbookTheme.typography.body,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

internal fun storyPreviewSummary(bookTitle: String, characterCount: Int, chapterCount: Int): String {
    val title = bookTitle.ifBlank { "this book" }
    val characters = "$characterCount ${if (characterCount == 1) "character" else "characters"}"
    val chapters = "$chapterCount ${if (chapterCount == 1) "chapter" else "chapters"}"
    return "We found $characters in $chapters of $title. Check who says what and correct anything " +
        "that looks wrong. Voices are generated only after you continue."
}

internal fun characterProfileText(character: StoryCharacterUi): String {
    if (character.isNarrator) {
        return when (character.narrationPerspective) {
            NarrationPerspective.FIRST_PERSON -> "First-person narrator"
            NarrationPerspective.THIRD_PERSON -> "Third-person narrator"
            NarrationPerspective.UNKNOWN -> "Narrator"
        }
    }
    val gender = when (character.gender) {
        CharacterGender.FEMALE -> "Female"
        CharacterGender.MALE -> "Male"
        CharacterGender.NON_BINARY -> "Non-binary"
        CharacterGender.UNKNOWN -> "Gender unknown"
    }
    val age = when (character.ageGroup) {
        CharacterAgeGroup.CHILD -> "child"
        CharacterAgeGroup.TEEN -> "teen"
        CharacterAgeGroup.YOUNG_ADULT -> "young adult"
        CharacterAgeGroup.ADULT -> "adult"
        CharacterAgeGroup.OLDER_ADULT -> "older adult"
        CharacterAgeGroup.UNKNOWN -> "age unknown"
    }
    val hint = if (character.profileUncertain) " · low confidence" else ""
    return "$gender · $age$hint"
}

internal fun characterAppearanceText(character: StoryCharacterUi): String {
    val lines = "${character.lineCount} ${if (character.lineCount == 1) "line" else "lines"}"
    val chapters = character.chapterNumbers
    return when {
        chapters.isEmpty() -> "$lines · not in the selected chapters"
        chapters.size == 1 -> "$lines · chapter ${chapters.single()}"
        else -> "$lines · chapters ${chapters.joinToString()}"
    }
}
