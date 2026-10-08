# Story Preview: character bible and annotated chapters before voice generation

Status: approved for implementation (2026-10-08)
Base branch: `feat/markdown-narration-preprocessing` @ `3732750`

## Goal

Before any speech is synthesized, the user can see:

1. A **character bible**: every character detected across the selected chapters of the book.
2. Each selected **chapter's text annotated with speaker attribution**: who says each passage.

They can also fix wrong speaker attributions, then tap **Generate voices** to start synthesis.

Speaker attribution is the only annotation in scope. Narration preprocessing (phrase splits and the
`SpokenTextCleaner` output) is a later, separate feature.

## Current behaviour (what changes)

Flow today: Import → Parsing → Chapter Review → Narration Setup → *Start generation* → Processing → Now Playing.

- `PreparationWorkPlan.narrationStages` = `FINDING_CHARACTERS, ASSIGNING_VOICES, PREPARING_AUDIO`
  ([ProductionPreparationCoordinator.kt](../../../app/src/main/java/com/whisperbook/app/engine/preparation/ProductionPreparationCoordinator.kt)).
  They are enqueued together when Narration Setup is confirmed (`WhisperbookViewModel.confirmNarrationSetup`).
- `FINDING_CHARACTERS` (`PreparationStageRunner.attributeSpeakers` in
  [PreparationWorker.kt](../../../app/src/main/java/com/whisperbook/app/engine/preparation/PreparationWorker.kt))
  attributes **only the first selected chapter**. Later chapters are attributed lazily inside
  `PREPARING_AUDIO` through `ensureChapterAttributed`, which is idempotent: it skips chapters
  whose passages no longer carry `UNATTRIBUTED_RULE`. It passes `knownCharacters` forward and records
  chapter metadata (the `characters.json` mirror).
- Gates already exist as durable checkpoints: `AWAITING_CHAPTER_SELECTION` (`job.chapterPlanConfirmed`)
  and `AWAITING_NARRATION_SETUP` (`book.narrationSetupConfirmed`), enforced in `canPrepareNarration`.
  Reaching a gate is a successful worker stop, not a failure.
- Speaker corrections (`WhisperbookServices.applySpeakerCorrections`, implemented in
  `WhisperbookAppContainer`) are pure database edits and do not depend on playback. The UI pieces
  are in `SpeakerCorrectionDialogs.kt` and are used by `CurrentChapterScreen`.

## New behaviour

Flow: Import → Parsing → Chapter Review → Narration Setup → **Reading the story** (Processing screen)
→ **Story Preview** → *Generate voices* → Processing → Now Playing.

### 1. Pipeline: attribute the whole book, then stop at a new gate

- Add `PreparationStage.AWAITING_STORY_REVIEW`, placed between `FINDING_CHARACTERS` and
  `ASSIGNING_VOICES` in the enum. Persisted stages are stored by name, so check how the stage is
  stored (`PreparationJob` entity/mapper) and make sure adding a value is safe.
- `FINDING_CHARACTERS` now calls `ensureChapterAttributed` for **every selected chapter** in plan order,
  not just the first. Checkpoint progress after each chapter: `completedUnits = i + 1`,
  `totalUnits = selectedChapters.size`, message e.g. "Reading chapter 3 of 24". It must
  stay restart-safe. A retried worker skips chapters that are already attributed (the
  existing idempotency gives this). `isFinalChapter` must be true only for the last selected chapter.
- After all chapters are attributed:
  - if `book.storyReviewConfirmed == false`, checkpoint `AWAITING_STORY_REVIEW`
    (message "Review the characters before generating voices") and stop successfully.
  - otherwise checkpoint `ASSIGNING_VOICES` as today.
- Extend `canPrepareNarration` (or add a sibling check used only by `ASSIGNING_VOICES` and
  `PREPARING_AUDIO`) so that neither stage runs while `storyReviewConfirmed == false`. A stale or
  retried WorkManager request must never synthesize audio past an unconfirmed review.
- `stagesToSchedule`: `AWAITING_STORY_REVIEW` → `narrationStages.drop(1)` when confirmed, else `emptyList()`.
  Check the `FAILED` branch as well. A failure after review confirmation must not re-gate.
- Keep the lazy `ensureChapterAttributed` call in `PREPARING_AUDIO` as a fallback. If chapters are
  added to the plan after review (Processing → Edit chapters), they are attributed there and do **not**
  re-open the review gate. Once confirmed, the review stays confirmed.

### 2. Persistence

- Add `storyReviewConfirmed: Boolean` to the book entity, next to `narrationSetupConfirmed` in
  [Entities.kt](../../../app/src/main/java/com/whisperbook/app/data/local/db/Entities.kt), the
  domain `Book` model, and the mappers.
- Room migration **7 → 8**, exporting `schemas/…/8.json`. Existing rows get `storyReviewConfirmed = 1`
  so books that are already imported or mid-preparation are never newly gated. Newly imported books
  start with `false`. Copy exactly how new imports set `narrationSetupConfirmed = false`.
- Repository: `confirmStoryReview(bookId)`, modelled on `confirmNarrationSetup`.
- Removing or re-importing a book, and any "reset preparation" path, should behave the same as they
  already do for `narrationSetupConfirmed`. Grep for every use of that flag and decide each one.

### 3. ViewModel and UI state

- `WhisperbookViewModel.confirmStoryReview()`, modelled on `confirmNarrationSetup`: it validates that
  the selected book is at `AWAITING_STORY_REVIEW`, persists the flag, then calls
  `ensurePreparationScheduled(bookId, force = true, knownConfirmed = true)`.
- Expose on `WhisperbookAppState` ([WhisperbookUiState.kt](../../../app/src/main/java/com/whisperbook/app/ui/screens/WhisperbookUiState.kt)):
  - `storyReviewRequired: Boolean`
  - the character bible list (see §4)
  - per-chapter annotated passages for the selected chapters. Reuse the existing reader/passage
    projection where possible. Don't build a second passage model if `readerPassages` can be
    parameterised by chapter.
  - `confirmStoryReview(onConfirmed)`, following the `confirmNarrationSetup { … }` callback style.
- Book-level routing helpers (`needsChapterReview` and `needsNarrationSetup` in the NavHost resolver near
  [WhisperbookNavHost.kt:44](../../../app/src/main/java/com/whisperbook/app/ui/navigation/WhisperbookNavHost.kt#L44),
  and the BookDetails continue routing near line 261) gain a `needsStoryReview` case. It goes after narration
  setup and before Processing and NowPlaying.

### 4. Story Preview screen

New destination `WhisperbookDestination.StoryPreview` with route `"story-preview"`. It is not
parameterised, like `ChapterReview` and `NarrationSetup`, and is not in `bottomBarRoutes`.
New file `ui/screens/StoryPreviewScreen.kt`, stateless like `ChapterReviewScreen`
(state and callbacks come in as parameters). Use the existing design-system pieces
(`StageTopBar`, `ParchmentPanel`, `PapercraftButton`, the speaker colour roles, `VoiceAvatarResources`)
and check `design-system/` for the visual language.

Two tabs or segments:

**Characters (the bible)**: one card per `StoryCharacter`, narrator first, then by `dialogueLineCount`
descending. Each card shows:
- display name, colour role, and portrait/avatar where the existing UI already derives one
- aliases (if any)
- gender and age group, with a low-confidence hint when confidence is below the threshold the casting
  code already uses (find it in `CharacterVoiceCaster` and `CharacterProfileInferencer`, don't invent a new one)
- narrator perspective for the narrator entry
- number of lines and the chapters the character appears in (derive this from passages)
- one sample line: the first attributed passage, truncated

Tapping a character opens the Chapters view at that character's first passage. Keep this simple.
Scroll-to-passage is enough.

**Chapters**: a list of selected chapters in plan order. Selecting one shows its passages with speaker
labels, the same way as read-along but **without playback controls or active-passage tracking**. Each
passage offers the existing speaker-correction action (`SpeakerCorrectionDialogs`,
`appState.correctPassageSpeaker`, including the `THIS_PASSAGE` / `MATCHING_PHRASES` scope). The bible
updates after a correction because it is derived from the same flows. Show a low-confidence marker on
passages whose `confidence` falls below the threshold read-along already uses, if there is one.

Bottom action: **Generate voices** (primary), which calls `appState.confirmStoryReview { navigate to
Processing, popUpTo StoryPreview inclusive }`. Back goes to Library, like `backOrLibrary`, and
does not confirm anything.

### 5. Navigation wiring

- After Narration Setup is confirmed, the app navigates to Processing as it does today. Processing shows
  `FINDING_CHARACTERS` progress ("Reading the story", "Reading chapter n of N").
- When the current book reaches `AWAITING_STORY_REVIEW`, Processing navigates to `StoryPreview`
  with `popUpTo(Processing) { inclusive = true }`. Use the same `LaunchedEffect` pattern that Processing
  uses for `canListen`, and the pattern Parsing uses to go to ChapterReview.
- Update `ProcessingScreen` stage copy and `preparationNotificationText`/`notificationMessage` for the
  new stage and the new per-chapter message.

## Non-goals (v1)

- Renaming, merging or deleting characters in the bible.
- Assigning or previewing voices in the Story Preview. Voice Cast still does that, after
  `ASSIGNING_VOICES`.
- Re-opening the Story Preview from Book Details after confirmation.
- Showing narration preprocessing (phrase splits, cleaned spoken text).
- A "skip review" setting.

## Testing

TDD where practical. Required:

- **JVM tests** (`app/src/test`), following the existing tests for the worker, coordinator and ViewModel:
  - `FINDING_CHARACTERS` attributes every selected chapter in plan order, checkpoints per chapter,
    and ends at `AWAITING_STORY_REVIEW` when the review is unconfirmed, or at `ASSIGNING_VOICES` when it is confirmed.
  - A retry after a mid-run interruption does not re-attribute chapters that are already done.
  - `ASSIGNING_VOICES` and `PREPARING_AUDIO` stop at the gate when unconfirmed, with no synthesis calls.
  - `stagesToSchedule` for `AWAITING_STORY_REVIEW` (confirmed and unconfirmed) and for `FAILED` after confirmation.
  - `confirmStoryReview` persists the flag and schedules the remaining stages. It rejects being called in the wrong stage.
  - Bible projection: ordering, line counts, chapter appearances, sample line, and the update after a correction.
  - Navigation contract: `WhisperbookDestinationTest` includes the new route and it is not a bottom-bar route.
- **Room migration 7 → 8** test: existing rows end up with `storyReviewConfirmed = true`. Look for the
  existing migration test pattern. If it is androidTest-only, add it there.
- **Compose androidTest** `StoryPreviewScreenTest`, modelled on `ChapterReviewScreenTest`: renders the bible,
  switches to Chapters, triggers the correction dialog, and Generate calls the callback.
  Run connected tests only if `adb devices` shows an arm64 device or emulator. Otherwise state clearly that they
  were compiled but not run.

Quality gate that must pass before handing back:

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
./gradlew :app:compileDebugAndroidTestKotlin
```

## Docs to update

- README "How a book becomes audio": opening-chapter priority no longer holds for attribution, so
  the whole book is attributed and reviewed before the first audio. Keep the rest accurate.
- `docs/architecture/README.md` runtime flow and stage list.
- `design-system/IMPLEMENTATION_MAP.md`: add the Story Preview screen to the journey.
- Do **not** regenerate the `.drawio`/`.svg` diagrams. List them as follow-ups in the hand-back.
