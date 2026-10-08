package com.whisperbook.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.whisperbook.app.R
import com.whisperbook.app.domain.model.CharacterAgeGroup
import com.whisperbook.app.domain.model.CharacterGender
import com.whisperbook.app.domain.model.NarrationPerspective
import com.whisperbook.app.domain.model.SpeakerCorrectionScope
import com.whisperbook.app.ui.screens.CastMemberUi
import com.whisperbook.app.ui.screens.PassageUi
import com.whisperbook.app.ui.screens.SpeakerRole
import com.whisperbook.app.ui.screens.StoryChapterUi
import com.whisperbook.app.ui.screens.StoryCharacterUi
import com.whisperbook.app.ui.screens.StoryPreviewScreen
import com.whisperbook.app.ui.theme.WhisperbookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StoryPreviewScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun bibleListsEveryCharacterWithProfileAndAppearances() {
        showScreen()

        composeRule.onNodeWithTag("story-bible").assertIsDisplayed()
        composeRule.onNodeWithTag("story-character-narrator").assertIsDisplayed()
        composeRule.onNodeWithText("First-person narrator").assertIsDisplayed()
        composeRule.onNodeWithText("Also called Mara Lind").assertIsDisplayed()
        composeRule.onNodeWithText("Female · adult").assertIsDisplayed()
        composeRule.onNodeWithText("Male · child · low confidence").assertIsDisplayed()
        composeRule.onNodeWithText("2 lines · chapters 3, 1").assertIsDisplayed()
        composeRule.onNodeWithText("“\"Wake up,\" Mara whispered.”").assertIsDisplayed()
    }

    @Test
    fun chaptersShowAttributedPassagesWithoutPlaybackControls() {
        showScreen()

        composeRule.onNodeWithTag("story-tab-chapters").performClick()

        composeRule.onNodeWithTag("story-chapter-passages").assertIsDisplayed()
        composeRule.onNodeWithTag("story-chapter-chapter-3").assertIsSelected()
        composeRule.onNodeWithText("\"Wake up,\" Mara whispered.").assertIsDisplayed()
        composeRule.onNodeWithText("MARA").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Play passage read by Mara").assertDoesNotExist()

        composeRule.onNodeWithTag("story-chapter-chapter-1").performClick()
        composeRule.onNodeWithText("The house was quiet.").assertIsDisplayed()
    }

    @Test
    fun characterCardOpensItsChapterAtTheFirstLine() {
        showScreen()

        composeRule.onNodeWithTag("story-character-narrator").performClick()

        composeRule.onNodeWithTag("story-chapter-chapter-1").assertIsSelected()
        composeRule.onNodeWithText("The house was quiet.").assertIsDisplayed()
    }

    @Test
    fun passageCorrectionUsesThePickerAndScopeDialog() {
        val corrections = mutableListOf<Triple<List<String>, String, SpeakerCorrectionScope>>()
        showScreen(onCorrectSpeaker = { ids, speakerId, scope -> corrections += Triple(ids, speakerId, scope) })

        composeRule.onNodeWithTag("story-tab-chapters").performClick()
        composeRule.onNodeWithContentDescription("Correct who reads this passage, now Mara").performClick()
        composeRule.onNodeWithTag("attributed-voice-picker").assertIsDisplayed()
        composeRule.onNodeWithTag("attributed-speaker-tom").performClick()
        composeRule.onNodeWithTag("speaker-correction-scope-dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("correct-this-phrase").performClick()

        composeRule.runOnIdle {
            assertEquals(
                listOf(Triple(listOf("c3-p1"), "tom", SpeakerCorrectionScope.THIS_PASSAGE)),
                corrections,
            )
        }
    }

    @Test
    fun generateVoicesConfirmsTheReview() {
        var generated = false
        showScreen(onGenerateVoices = { generated = true })

        composeRule.onNodeWithTag("generate-voices").assertIsEnabled().performClick()

        composeRule.runOnIdle { assertTrue(generated) }
    }

    private fun showScreen(
        onCorrectSpeaker: (List<String>, String, SpeakerCorrectionScope) -> Unit = { _, _, _ -> },
        onGenerateVoices: () -> Unit = {},
    ) {
        composeRule.setContent {
            WhisperbookTheme {
                StoryPreviewScreen(
                    contentPadding = PaddingValues(),
                    bookTitle = "The Quiet House",
                    characters = characters,
                    chapters = chapters,
                    cast = cast,
                    isBusy = false,
                    onCorrectSpeaker = onCorrectSpeaker,
                    onGenerateVoices = onGenerateVoices,
                    onBack = {},
                )
            }
        }
    }

    private val characters = listOf(
        character(
            id = "narrator",
            name = "Narrator",
            role = SpeakerRole.Narrator,
            isNarrator = true,
            lineCount = 1,
            chapterNumbers = listOf(1),
            firstChapterId = "chapter-1",
            firstPassageId = "c1-p1",
            sampleLine = "The house was quiet.",
            perspective = NarrationPerspective.FIRST_PERSON,
        ),
        character(
            id = "mara",
            name = "Mara",
            role = SpeakerRole.Elara,
            aliases = listOf("Mara Lind"),
            gender = CharacterGender.FEMALE,
            ageGroup = CharacterAgeGroup.ADULT,
            lineCount = 2,
            chapterNumbers = listOf(3, 1),
            firstChapterId = "chapter-3",
            firstPassageId = "c3-p1",
            sampleLine = "\"Wake up,\" Mara whispered.",
        ),
        character(
            id = "tom",
            name = "Tom",
            role = SpeakerRole.Fox,
            gender = CharacterGender.MALE,
            ageGroup = CharacterAgeGroup.CHILD,
            profileUncertain = true,
            lineCount = 1,
            chapterNumbers = listOf(3),
            firstChapterId = "chapter-3",
            firstPassageId = "c3-p2",
            sampleLine = "\"I am awake,\" said Tom.",
        ),
    )

    private val chapters = listOf(
        StoryChapterUi(
            id = "chapter-3",
            number = 3,
            listeningPosition = 1,
            title = "Morning",
            passages = listOf(
                passage("c3-p1", SpeakerRole.Elara, "mara", "Mara", "\"Wake up,\" Mara whispered."),
                passage("c3-p2", SpeakerRole.Fox, "tom", "Tom", "\"I am awake,\" said Tom."),
            ),
        ),
        StoryChapterUi(
            id = "chapter-1",
            number = 1,
            listeningPosition = 2,
            title = "The House",
            passages = listOf(
                passage("c1-p1", SpeakerRole.Narrator, "narrator", "Narrator", "The house was quiet."),
                passage("c1-p2", SpeakerRole.Elara, "mara", "Mara", "\"Who is there?\""),
            ),
        ),
    )

    private val cast = characters.map { character ->
        CastMemberUi(
            id = character.id,
            character = character.name,
            voice = "",
            confidence = 0,
            lines = character.lineCount,
            portraitRes = character.portraitRes,
            role = character.role,
        )
    }

    private fun character(
        id: String,
        name: String,
        role: SpeakerRole,
        lineCount: Int,
        chapterNumbers: List<Int>,
        firstChapterId: String,
        firstPassageId: String,
        sampleLine: String,
        isNarrator: Boolean = false,
        aliases: List<String> = emptyList(),
        gender: CharacterGender = CharacterGender.UNKNOWN,
        ageGroup: CharacterAgeGroup = CharacterAgeGroup.UNKNOWN,
        profileUncertain: Boolean = false,
        perspective: NarrationPerspective = NarrationPerspective.UNKNOWN,
    ) = StoryCharacterUi(
        id = id,
        name = name,
        role = role,
        portraitRes = R.drawable.portrait_narrator,
        isNarrator = isNarrator,
        aliases = aliases,
        gender = gender,
        ageGroup = ageGroup,
        profileUncertain = profileUncertain,
        narrationPerspective = perspective,
        lineCount = lineCount,
        chapterNumbers = chapterNumbers,
        firstChapterId = firstChapterId,
        firstPassageId = firstPassageId,
        sampleLine = sampleLine,
    )

    private fun passage(
        id: String,
        role: SpeakerRole,
        speakerId: String,
        speakerName: String,
        text: String,
    ) = PassageUi(
        id = id,
        speaker = role,
        text = text,
        speakerId = speakerId,
        speakerName = speakerName,
    )
}
