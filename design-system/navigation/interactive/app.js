const WORLD = { width: 1740, height: 1140 };

const iconPaths = {
  welcome: '<path d="M4 18.5V8.7c3.2-1.1 5.8-.4 8 2v9.8c-2.2-2.3-4.8-2.9-8-2Z"/><path d="M20 18.5V8.7c-3.2-1.1-5.8-.4-8 2v9.8c2.2-2.3 4.8-2.9 8-2Z"/><path d="M12 10.7v9.8"/>',
  library: '<path d="M4 5h5v14H4zM10.5 5h4v14h-4zM16 6l3.5-1 2.5 13.5-3.5.7z"/>',
  import: '<path d="M5 4h9l5 5v11H5z"/><path d="M14 4v5h5M12 11v6M9.5 13.5 12 11l2.5 2.5"/>',
  parsing: '<path d="M4 5h16v14H4zM7 9h7M7 12h10M7 15h6"/><circle cx="17" cy="8" r="2.5"/>',
  review: '<path d="M7 4h10v3H7zM5 6h14v14H5z"/><path d="m8 12 2 2 5-5M8 17h8"/>',
  setup: '<circle cx="12" cy="8" r="3"/><path d="M6.5 20c.6-4 2.4-6 5.5-6s4.9 2 5.5 6M19 5v5M16.5 7.5h5"/>',
  processing: '<path d="M12 3a9 9 0 1 0 9 9"/><path d="M12 7v5l3 2M18 3v5h-5"/>',
  playing: '<path d="M5 6h3v12H5zM16 6h3v12h-3z"/><path d="M8 8c2.5-2 5.5-2 8 0M8 16c2.5 2 5.5 2 8 0"/>',
  details: '<path d="M5 4h14v16H5zM8 8h8M8 12h8M8 16h5"/>',
  cast: '<circle cx="9" cy="8" r="3"/><circle cx="17" cy="9" r="2.5"/><path d="M3.5 20c.5-4.3 2.3-6.5 5.5-6.5s5 2.2 5.5 6.5M14 14.5c3.8-.7 6 1.1 6.5 5.5"/>',
  reader: '<path d="M4 5h6.5c1 0 1.5.5 1.5 1.5V20c0-1.3-.8-2-2.4-2H4zM20 5h-6.5c-1 0-1.5.5-1.5 1.5V20c0-1.3.8-2 2.4-2H20z"/>',
  settings: '<circle cx="12" cy="12" r="3"/><path d="M12 3v3M12 18v3M3 12h3M18 12h3M5.6 5.6l2.1 2.1M16.3 16.3l2.1 2.1M18.4 5.6l-2.1 2.1M7.7 16.3l-2.1 2.1"/>',
  tabs: '<path d="M4 6h16v12H4zM4 14h16M9.3 14v4M14.7 14v4"/>',
  back: '<path d="m10 6-6 6 6 6M4 12h11a5 5 0 0 1 5 5"/>',
  picker: '<path d="M4 5h16v14H4zM8 3v4M16 3v4M7 11h10M7 15h6"/>',
  dialog: '<path d="M4 5h16v12H9l-5 4z"/><path d="M8 9h8M8 13h5"/>',
  export: '<path d="M5 4h14v16H5zM12 5v10M8.5 11.5 12 15l3.5-3.5"/>',
  share: '<circle cx="18" cy="5" r="2.5"/><circle cx="6" cy="12" r="2.5"/><circle cx="18" cy="19" r="2.5"/><path d="m8.2 10.8 7.6-4.5M8.2 13.2l7.6 4.5"/>',
  voice: '<path d="M9 18V6l10-2v12"/><circle cx="6.5" cy="18" r="2.5"/><circle cx="16.5" cy="16" r="2.5"/>',
  correction: '<path d="M4 18.5V8.7c3.2-1.1 5.8-.4 8 2v9.8c-2.2-2.3-4.8-2.9-8-2Z"/><path d="M20 18.5V8.7c-3.2-1.1-5.8-.4-8 2v9.8c2.2-2.3 4.8-2.9 8-2Z"/><path d="m15.5 5 1.5 1.5L20 3.5"/>'
};

const svgIcon = (name) => `<svg viewBox="0 0 24 24" aria-hidden="true">${iconPaths[name] || iconPaths.details}</svg>`;

const nodes = [
  {
    id: 'welcome', index: '01', title: 'Welcome', route: 'welcome', kind: 'screen', icon: 'welcome', accent: true,
    summary: 'First launch · privacy promise', purpose: 'Introduces private, offline listening and completes onboarding on the first choice.',
    source: 'WhisperbookNavHost.kt · WelcomeScreen.kt', bottomNav: true, entry: 'Start destination only while onboarding is incomplete.',
    x: 35, y: 70,
    actions: [
      { label: 'Import a book', kind: 'navigate', target: 'import', outcome: 'Completes onboarding, creates Library as the back-stack root, then opens Import.' },
      { label: 'Explore the app', kind: 'navigate', target: 'library', outcome: 'Completes onboarding and opens Library as the clean root.' }
    ]
  },
  {
    id: 'library', index: '02', title: 'Library', route: 'library', kind: 'screen', icon: 'library',
    summary: 'Books · resume · add · remove', purpose: 'The stable root for browsing books and resolving each book to its next valid screen.',
    source: 'WhisperbookNavHost.kt · LibraryScreen.kt', bottomNav: true, entry: 'Default start after onboarding. Also the fallback when back navigation has no prior entry.',
    x: 275, y: 70,
    actions: [
      { label: 'Add a book', kind: 'navigate', target: 'import', outcome: 'Opens the local PDF / EPUB import screen.' },
      { label: 'Open a book', kind: 'conditional', targets: ['parsing', 'chapter-review', 'narration-setup', 'processing', 'book-details'], outcome: 'Routes by book state: parsing → chapter review → narration setup → preparation → details.' },
      { label: 'Resume listening', kind: 'conditional', targets: ['parsing', 'chapter-review', 'narration-setup', 'processing', 'now-playing'], outcome: 'Uses the same gates, then goes directly to Listen when audio is ready.' },
      { label: 'Remove a book', kind: 'destructive', target: 'remove-confirm', outcome: 'Opens a confirmation dialog. The external original file is preserved.' }
    ]
  },
  {
    id: 'import', index: '03', title: 'Import book', route: 'import', kind: 'screen', icon: 'import', accent: true,
    summary: 'PDF · EPUB · recent files', purpose: 'Hands the user to Android’s document picker, then begins private on-device parsing.',
    source: 'WhisperbookNavHost.kt · ImportBookScreen.kt', bottomNav: true, entry: 'Reached from Welcome, Library, a failed preparation, or a recent-file re-entry.',
    x: 515, y: 70,
    actions: [
      { label: 'Choose a file', kind: 'overlay', target: 'file-picker', outcome: 'Opens Android OpenDocument for PDF, EPUB, ZIP, or compatible binary files.' },
      { label: 'Open a recent file', kind: 'conditional', targets: ['parsing', 'chapter-review', 'narration-setup', 'processing', 'book-details'], outcome: 'Selects the book, then resolves its current state.' },
      { label: 'Back', kind: 'navigate', target: 'back-stack', outcome: 'Pops the previous screen; falls back to Library if the stack is empty.' }
    ]
  },
  {
    id: 'parsing', index: '04', title: 'Parsing', route: 'parsing', kind: 'screen', icon: 'parsing',
    summary: 'Validate · read · find chapters', purpose: 'Shows long-running extraction and chapter discovery before the user chooses the listening plan.',
    source: 'WhisperbookNavHost.kt · ParsingScreen.kt', bottomNav: false, entry: 'Shown while preparation is COPY_AND_VALIDATE or READING_CHAPTERS.',
    x: 755, y: 70,
    actions: [
      { label: 'Chapter scan completes', kind: 'automatic', target: 'chapter-review', outcome: 'Automatically replaces Parsing when the stage becomes AWAITING_CHAPTER_SELECTION.' },
      { label: 'Continue in background', kind: 'navigate', target: 'library', outcome: 'Returns to Library while parsing continues with visible background status.' },
      { label: 'Pause / resume parsing', kind: 'local', outcome: 'Updates the durable preparation job state without leaving this screen.' },
      { label: 'Cancel / start again / retry', kind: 'local', outcome: 'Cancels or re-enqueues the same book preparation while preserving a visible state.' },
      { label: 'Back', kind: 'navigate', target: 'back-stack', outcome: 'Pops the previous screen; falls back to Library.' }
    ]
  },
  {
    id: 'chapter-review', index: '05', title: 'Choose chapters', route: 'chapter-review', kind: 'screen', icon: 'review', accent: true,
    summary: 'Include · skip · reorder', purpose: 'Lets the user decide which detected chapters are prepared and in what listening order.',
    source: 'WhisperbookNavHost.kt · ChapterReviewScreen.kt', bottomNav: false, entry: 'Required after parsing when a book has an unconfirmed chapter plan.',
    x: 995, y: 70,
    actions: [
      { label: 'Continue', kind: 'conditional', targets: ['narration-setup', 'processing'], outcome: 'Confirms at least one selected chapter; asks for narration setup when required, otherwise prepares audio.' },
      { label: 'Include or skip a chapter', kind: 'local', outcome: 'Toggles one chapter in the listening plan.' },
      { label: 'Move earlier / later', kind: 'local', outcome: 'Changes listening order when search is clear.' },
      { label: 'Select all / deselect all', kind: 'local', outcome: 'Applies to the whole plan or only current search results.' },
      { label: 'Restore order / reset chapters', kind: 'local', outcome: 'Restores extracted order or rebuilds the editable plan.' },
      { label: 'Search / clear search', kind: 'local', outcome: 'Filters included and skipped chapters without navigating.' },
      { label: 'Back', kind: 'navigate', target: 'back-stack', outcome: 'Returns to the previous screen or Library fallback.' }
    ]
  },
  {
    id: 'narration-setup', index: '06', title: 'Narration setup', route: 'narration-setup', kind: 'screen', icon: 'setup',
    summary: 'Language · narrator · consent', purpose: 'The persisted gate that prevents any speech generation until language and narrator are confirmed.',
    source: 'WhisperbookNavHost.kt · NarrationSetupScreen.kt', bottomNav: false, entry: 'Required for a newly imported book until setup is explicitly confirmed.',
    x: 1235, y: 70,
    actions: [
      { label: 'Choose book language', kind: 'local', outcome: 'Updates the pending book-scoped language before generation.' },
      { label: 'Choose narrator', kind: 'overlay', target: 'voice-picker', outcome: 'Opens the offline voice picker for Narrator.' },
      { label: 'Test narrator', kind: 'local', outcome: 'Synthesizes and plays a private on-device preview.' },
      { label: 'Start voice generation', kind: 'navigate', target: 'processing', outcome: 'Persists language and narrator, installs the needed local pack, then starts preparation.' },
      { label: 'Open book', kind: 'navigate', target: 'book-details', outcome: 'When setup is already complete, replaces this screen with Book details.' },
      { label: 'Back', kind: 'navigate', target: 'back-stack', outcome: 'Returns to the previous screen or Library fallback.' }
    ]
  },
  {
    id: 'processing', index: '07', title: 'Processing', route: 'processing', kind: 'screen', icon: 'processing', accent: true,
    summary: 'Cast · voices · chapter audio', purpose: 'Shows progressive offline generation and allows listening as soon as the first chapter is playable.',
    source: 'WhisperbookNavHost.kt · ProcessingScreen.kt', bottomNav: false, entry: 'Used after setup, or when a selected book is not yet listenable and is not parsing.',
    x: 1475, y: 70,
    actions: [
      { label: 'First chapter becomes playable', kind: 'automatic', target: 'now-playing', outcome: 'Starts playback and replaces Processing with Listen.' },
      { label: 'Listen now', kind: 'navigate', target: 'now-playing', outcome: 'Starts available audio while later chapters continue preparing.' },
      { label: 'Continue in background', kind: 'navigate', target: 'library', outcome: 'May request notification permission, then returns to Library.' },
      { label: 'Edit chapters', kind: 'navigate', target: 'chapter-review', outcome: 'Returns to the selected listening plan.' },
      { label: 'Pause / resume / cancel', kind: 'local', outcome: 'Controls the durable WorkManager job without losing its checkpoint.' },
      { label: 'Try again', kind: 'local', outcome: 'Retries a failed preparation for the same book.' },
      { label: 'Choose another file', kind: 'navigate', target: 'import', outcome: 'On failure, replaces the import route with a clean file choice.' }
    ]
  },
  {
    id: 'now-playing', index: '08', title: 'Now playing', route: 'listen', kind: 'screen', icon: 'playing',
    summary: 'Play · seek · read along', purpose: 'The main listening surface for playback, chapter changes, cast access, and synchronized reading.',
    source: 'WhisperbookNavHost.kt · NowPlayingScreen.kt', bottomNav: true, entry: 'Reached only after the selected book passes all gates and can listen.',
    x: 1235, y: 360,
    actions: [
      { label: 'Open book artwork', kind: 'navigate', target: 'book-details', outcome: 'Opens Book details.' },
      { label: 'Open current passage', kind: 'navigate', target: 'current-chapter', outcome: 'Opens synchronized read-along.' },
      { label: 'Open voice cast', kind: 'navigate', target: 'voice-cast', outcome: 'Opens book- and chapter-scoped voice controls.' },
      { label: 'Settings button', kind: 'navigate', target: 'settings', outcome: 'Opens Settings.' },
      { label: 'Choose a chapter', kind: 'overlay', target: 'chapter-picker', outcome: 'Opens the chapter sheet; a selection switches the chapter and opens read-along.' },
      { label: 'Play / pause', kind: 'local', outcome: 'Toggles Media3 playback.' },
      { label: 'Back 15s / forward 15s', kind: 'local', outcome: 'Seeks within the current chapter and updates the active passage.' },
      { label: 'Scrub chapter progress', kind: 'local', outcome: 'Seeks to the chosen chapter fraction.' },
      { label: 'Cycle speed / sleep timer', kind: 'local', outcome: 'Updates playback rate or timer without leaving the player.' }
    ]
  },
  {
    id: 'book-details', index: '09', title: 'Book details', route: 'book/{bookId}', kind: 'screen', icon: 'details', accent: true,
    summary: 'Progress · chapters · export', purpose: 'Owns durable book progress, chapter entry, MP3 export, cast access, and removal.',
    source: 'WhisperbookNavHost.kt · BookDetailsScreen.kt', bottomNav: true, entry: 'Reached for a ready book, from the player artwork, or after applying cast changes.',
    x: 995, y: 360,
    actions: [
      { label: 'Continue listening', kind: 'conditional', targets: ['chapter-review', 'narration-setup', 'parsing', 'processing', 'now-playing'], outcome: 'Rechecks every gate before entering Listen.' },
      { label: 'Choose a chapter row', kind: 'navigate', target: 'now-playing', outcome: 'Selects an available chapter, then opens Listen.' },
      { label: 'Voice cast', kind: 'navigate', target: 'voice-cast', outcome: 'Opens character and narrator assignments.' },
      { label: 'Edit chapter selection and order', kind: 'navigate', target: 'chapter-review', outcome: 'Opens the chapter plan.' },
      { label: 'Export MP3', kind: 'overlay', target: 'export-picker', outcome: 'Opens Android CreateDocument; export may generate missing narration first.' },
      { label: 'Remove book', kind: 'destructive', target: 'remove-confirm', outcome: 'Confirms deletion, then returns to Library. The external original remains.' },
      { label: 'Back', kind: 'navigate', target: 'back-stack', outcome: 'Returns to the previous screen or Library fallback.' }
    ]
  },
  {
    id: 'voice-cast', index: '10', title: 'Voice cast', route: 'book/{bookId}/cast', kind: 'screen', icon: 'cast',
    summary: 'Preview · assign · regenerate', purpose: 'Controls book language and chapter-aware character voices, with safe regeneration scope choices.',
    source: 'WhisperbookNavHost.kt · VoiceCastScreen.kt', bottomNav: false, entry: 'Reached from Book details, Now playing, or Current chapter.',
    x: 755, y: 360,
    actions: [
      { label: 'Apply to book', kind: 'navigate', target: 'book-details', outcome: 'Replaces the details route and returns to Book details.' },
      { label: 'Back to book / Back', kind: 'navigate', target: 'back-stack', outcome: 'Pops to the originating screen; Library is the fallback.' },
      { label: 'Preview character', kind: 'local', outcome: 'Synthesizes the assigned voice on device.' },
      { label: 'Change voice', kind: 'overlay', target: 'voice-picker', outcome: 'Opens the voice picker; a changed voice continues to regeneration scope.' },
      { label: 'Change book language', kind: 'local', outcome: 'Selects an installed pack or downloads and uses an optional pack, then rebuilds this book.' },
      { label: 'Revert voice change', kind: 'local', outcome: 'Restores the retained prior audio/assignment during the grace window.' }
    ]
  },
  {
    id: 'current-chapter', index: '11', title: 'Current chapter', route: 'book/{bookId}/chapter/{chapterId}', kind: 'screen', icon: 'reader', accent: true,
    summary: 'Read along · correct speaker', purpose: 'Keeps text, speaker color, passage position, and playback synchronized while reading.',
    source: 'WhisperbookNavHost.kt · CurrentChapterScreen.kt', bottomNav: true, entry: 'Reached from the current passage, read-along action, or a chapter choice in Now playing.',
    x: 515, y: 360,
    actions: [
      { label: 'Open voice cast', kind: 'navigate', target: 'voice-cast', outcome: 'Opens voice assignments for the active book/chapter.' },
      { label: 'Choose chapter', kind: 'overlay', target: 'chapter-picker', outcome: 'Selects another chapter while staying in read-along.' },
      { label: 'Select a passage', kind: 'local', outcome: 'Seeks playback to the first audio segment represented by that reader card.' },
      { label: 'Change attributed speaker', kind: 'overlay', target: 'speaker-picker', outcome: 'Opens the cast picker, then asks whether to fix one or matching sections.' },
      { label: 'Auto-scroll', kind: 'local', outcome: 'Turns active-passage following on or off.' },
      { label: 'Mini player controls', kind: 'local', outcome: 'Play/pause, previous chapter, next chapter, and scrub stay on this screen.' },
      { label: 'Back', kind: 'navigate', target: 'back-stack', outcome: 'Returns to the previous screen or Library fallback.' }
    ]
  },
  {
    id: 'settings', index: '12', title: 'Settings', route: 'settings', kind: 'screen', icon: 'settings',
    summary: 'Playback · access · diagnostics', purpose: 'Holds global listening defaults, accessibility, local storage visibility, and tester-controlled diagnostics.',
    source: 'WhisperbookNavHost.kt · SettingsScreen.kt', bottomNav: true, entry: 'Reached from the persistent Settings tab or the player header.',
    x: 275, y: 360,
    actions: [
      { label: 'Cycle speaking speed', kind: 'local', outcome: 'Changes the global playback rate.' },
      { label: 'Cycle narration phrasing', kind: 'local', outcome: 'Moves between short, natural, and long pacing targets while respecting paragraphs.' },
      { label: 'Cycle sleep timer', kind: 'local', outcome: 'Changes the listening timer.' },
      { label: 'Keep screen awake / Larger text', kind: 'local', outcome: 'Toggles persistent accessibility preferences.' },
      { label: 'Share diagnostic log', kind: 'overlay', target: 'share-sheet', outcome: 'Creates the private on-device log and opens the Android share chooser.' }
    ]
  },
  {
    id: 'bottom-nav', index: 'NAV', title: 'Persistent tabs', route: 'Library · Listen · Settings', kind: 'system', icon: 'tabs',
    summary: 'Library-root saved-state navigation', purpose: 'A shared navigation rail that always anchors Library before restoring a saved top-level tab.',
    source: 'WhisperbookApp.kt · WhisperbookNavigationActions.kt', bottomNav: false, entry: 'Visible on Welcome, Library, Import, Now playing, Book details, Settings, and Current chapter.',
    x: 35, y: 360,
    actions: [
      { label: 'Library tab', kind: 'navigate', target: 'library', outcome: 'Returns to the single Library root.' },
      { label: 'Listen tab', kind: 'conditional', targets: ['library', 'chapter-review', 'parsing', 'narration-setup', 'processing', 'now-playing'], outcome: 'No book → Library; otherwise passes review, parsing, setup, and readiness gates before Listen.' },
      { label: 'Settings tab', kind: 'navigate', target: 'settings', outcome: 'Restores or opens Settings.' }
    ]
  },
  {
    id: 'back-stack', index: 'SYS', title: 'Back stack', route: 'popBackStack() → Library fallback', kind: 'system', icon: 'back',
    summary: 'Previous screen · safe fallback', purpose: 'Explicit Back actions pop the actual prior destination; if none exists, Library is rebuilt as root.',
    source: 'WhisperbookNavHost.kt · backOrLibrary()', bottomNav: false, entry: 'Used by Import, Parsing, Chapter review, Narration setup, Book details, Voice cast, and Current chapter.',
    x: 1475, y: 360,
    actions: [
      { label: 'Pop previous destination', kind: 'conditional', outcome: 'Returns to the real originating screen, which varies with the user’s path.' },
      { label: 'Empty stack fallback', kind: 'navigate', target: 'library', outcome: 'Rebuilds Library as the clean root.' }
    ]
  },
  {
    id: 'file-picker', index: 'S1', title: 'Document picker', route: 'Android OpenDocument', kind: 'overlay', icon: 'picker',
    summary: 'Choose PDF or EPUB', purpose: 'System-owned file selection. Whisperbook takes persistable read permission before importing.',
    source: 'ImportBookScreen.kt', x: 470, y: 690,
    actions: [
      { label: 'Choose a valid file', kind: 'navigate', target: 'parsing', outcome: 'Imports the URI and opens Parsing after success.' },
      { label: 'Cancel picker', kind: 'local', outcome: 'Returns to Import without changing route.' }
    ]
  },
  {
    id: 'share-sheet', index: 'S2', title: 'Share chooser', route: 'Android Sharesheet', kind: 'overlay', icon: 'share',
    summary: 'Tester-controlled log export', purpose: 'Lets the user explicitly choose where the privacy-filtered diagnostic log goes.',
    source: 'SettingsScreen.kt · BetaDiagnostics', x: 230, y: 690,
    actions: [
      { label: 'Choose a share target', kind: 'local', outcome: 'Leaves the app temporarily; Settings remains the underlying route.' },
      { label: 'Dismiss', kind: 'local', outcome: 'Returns to Settings.' }
    ]
  },
  {
    id: 'speaker-picker', index: 'S3', title: 'Speaker picker', route: 'ModalBottomSheet', kind: 'overlay', icon: 'cast',
    summary: 'Who should read this section?', purpose: 'Selects a cast member for a misattributed read-along section.',
    source: 'SpeakerCorrectionDialogs.kt', x: 470, y: 840,
    actions: [
      { label: 'Select a different speaker', kind: 'overlay', target: 'correction-scope', outcome: 'Continues to correction scope.' },
      { label: 'Dismiss', kind: 'local', outcome: 'Returns to Current chapter unchanged.' }
    ]
  },
  {
    id: 'correction-scope', index: 'D1', title: 'Correction scope', route: 'AlertDialog', kind: 'overlay', icon: 'correction',
    summary: 'One section · matching sections', purpose: 'Applies a speaker correction narrowly or to matching text attributed to the same old speaker.',
    source: 'SpeakerCorrectionDialogs.kt', x: 470, y: 990,
    actions: [
      { label: 'Just this section', kind: 'local', outcome: 'Updates only this source section, then returns to Current chapter.' },
      { label: 'All matching sections', kind: 'local', outcome: 'Updates normalized matching sections in this book, then returns.' },
      { label: 'Keep current attribution', kind: 'local', outcome: 'Dismisses without a change.' }
    ]
  },
  {
    id: 'voice-picker', index: 'S4', title: 'Voice picker', route: 'ModalBottomSheet', kind: 'overlay', icon: 'voice',
    summary: 'Test · choose offline voice', purpose: 'Shared picker for Narrator setup and character voice changes.',
    source: 'VoicePickerSheet.kt', x: 710, y: 690,
    actions: [
      { label: 'Test a voice', kind: 'local', outcome: 'Plays an on-device preview without closing the sheet.' },
      { label: 'Choose for Narrator', kind: 'local', outcome: 'Returns to Narration setup with the pending narrator selected.' },
      { label: 'Choose for a character', kind: 'overlay', target: 'voice-scope', outcome: 'Continues to regeneration scope if the voice changed.' },
      { label: 'Dismiss', kind: 'local', outcome: 'Returns to the underlying screen unchanged.' }
    ]
  },
  {
    id: 'voice-scope', index: 'D2', title: 'Regeneration scope', route: 'AlertDialog', kind: 'overlay', icon: 'dialog',
    summary: 'Chapter · onward · whole book', purpose: 'Chooses exactly how far a character voice change should invalidate and rebuild audio.',
    source: 'VoiceRegenerationDialog.kt', x: 710, y: 840,
    actions: [
      { label: 'This chapter only', kind: 'local', outcome: 'Applies the new voice to the active chapter.' },
      { label: 'From this chapter', kind: 'local', outcome: 'Applies through later chapters when available.' },
      { label: 'Whole book', kind: 'local', outcome: 'Applies to every chapter in the book.' },
      { label: 'Keep current voice', kind: 'local', outcome: 'Dismisses without a change.' }
    ]
  },
  {
    id: 'remove-confirm', index: 'D3', title: 'Remove confirmation', route: 'AlertDialog', kind: 'overlay', icon: 'dialog',
    summary: 'Remove app copy · keep original', purpose: 'Makes the destructive boundary explicit before removing a book and generated audio.',
    source: 'RemoveBookDialog.kt', x: 950, y: 690,
    actions: [
      { label: 'Remove book', kind: 'destructive', target: 'library', outcome: 'Deletes Whisperbook’s private copy/audio. From Details, navigation returns to Library.' },
      { label: 'Keep book', kind: 'local', outcome: 'Dismisses and stays on the originating screen.' }
    ]
  },
  {
    id: 'export-picker', index: 'S5', title: 'MP3 destination', route: 'Android CreateDocument', kind: 'overlay', icon: 'export',
    summary: 'Choose where to save', purpose: 'Lets the user name and place one offline MP3; missing narration is prepared first.',
    source: 'BookDetailsScreen.kt · FfmpegBookMp3Exporter.kt', x: 950, y: 840,
    actions: [
      { label: 'Choose destination', kind: 'local', outcome: 'Starts preparation, encoding, and saving while Book details remains the route.' },
      { label: 'Cancel', kind: 'local', outcome: 'Returns to Book details without exporting.' }
    ]
  },
  {
    id: 'chapter-picker', index: 'S6', title: 'Chapter picker', route: 'ModalBottomSheet', kind: 'overlay', icon: 'picker',
    summary: 'Choose available chapter', purpose: 'Shared chapter selector used by the player and synchronized reader.',
    source: 'ChapterPickerSheet.kt', x: 1190, y: 690,
    actions: [
      { label: 'Choose from Now playing', kind: 'navigate', target: 'current-chapter', outcome: 'Selects the chapter and opens read-along.' },
      { label: 'Choose from Current chapter', kind: 'local', outcome: 'Switches chapter and stays in read-along.' },
      { label: 'Dismiss', kind: 'local', outcome: 'Returns to the underlying screen unchanged.' }
    ]
  }
];

const edges = [
  { id: 'welcome-import', from: 'welcome', to: 'import', label: 'Import', category: 'primary', journey: ['first-import'] },
  { id: 'welcome-library', from: 'welcome', to: 'library', label: 'Explore', category: 'primary', journey: ['first-import'] },
  { id: 'library-import', from: 'library', to: 'import', label: 'Add book', category: 'primary', journey: ['first-import'] },
  { id: 'import-picker', from: 'import', to: 'file-picker', label: 'Choose file', category: 'overlay', journey: ['first-import'] },
  { id: 'picker-parsing', from: 'file-picker', to: 'parsing', label: 'Valid file', category: 'overlay', journey: ['first-import'] },
  { id: 'import-parsing', from: 'import', to: 'parsing', label: 'Import succeeds', category: 'primary', journey: ['first-import'] },
  { id: 'parsing-review', from: 'parsing', to: 'chapter-review', label: 'Scan complete', category: 'primary', journey: ['first-import'] },
  { id: 'review-setup', from: 'chapter-review', to: 'narration-setup', label: 'Setup required', category: 'conditional', journey: ['first-import'] },
  { id: 'review-processing', from: 'chapter-review', to: 'processing', label: 'Already confirmed', category: 'conditional' },
  { id: 'setup-processing', from: 'narration-setup', to: 'processing', label: 'Confirm', category: 'primary', journey: ['first-import'] },
  { id: 'processing-listen', from: 'processing', to: 'now-playing', label: 'Playable', category: 'primary', journey: ['first-import', 'returning'] },
  { id: 'library-parsing', from: 'library', to: 'parsing', label: 'Parsing', category: 'conditional', journey: ['returning'] },
  { id: 'library-review', from: 'library', to: 'chapter-review', label: 'Needs chapters', category: 'conditional', journey: ['returning'] },
  { id: 'library-setup', from: 'library', to: 'narration-setup', label: 'Needs setup', category: 'conditional', journey: ['returning'] },
  { id: 'library-processing', from: 'library', to: 'processing', label: 'Preparing', category: 'conditional', journey: ['returning'] },
  { id: 'library-details', from: 'library', to: 'book-details', label: 'Open ready book', category: 'conditional', journey: ['manage'] },
  { id: 'library-listen', from: 'library', to: 'now-playing', label: 'Resume', category: 'conditional', journey: ['returning'] },
  { id: 'processing-library', from: 'processing', to: 'library', label: 'Background', category: 'primary' },
  { id: 'processing-review', from: 'processing', to: 'chapter-review', label: 'Edit chapters', category: 'conditional' },
  { id: 'processing-import', from: 'processing', to: 'import', label: 'Choose another', category: 'conditional' },
  { id: 'listen-details', from: 'now-playing', to: 'book-details', label: 'Book', category: 'primary', journey: ['manage'] },
  { id: 'details-listen', from: 'book-details', to: 'now-playing', label: 'Listen', category: 'primary', journey: ['returning'] },
  { id: 'listen-cast', from: 'now-playing', to: 'voice-cast', label: 'Cast', category: 'primary', journey: ['voices'] },
  { id: 'details-cast', from: 'book-details', to: 'voice-cast', label: 'Voice cast', category: 'primary', journey: ['voices'] },
  { id: 'cast-details', from: 'voice-cast', to: 'book-details', label: 'Apply', category: 'primary', journey: ['voices'] },
  { id: 'listen-reader', from: 'now-playing', to: 'current-chapter', label: 'Read along', category: 'primary', journey: ['reader'] },
  { id: 'reader-cast', from: 'current-chapter', to: 'voice-cast', label: 'Voice cast', category: 'primary', journey: ['reader', 'voices'] },
  { id: 'listen-settings', from: 'now-playing', to: 'settings', label: 'Settings', category: 'primary' },
  { id: 'setup-picker', from: 'narration-setup', to: 'voice-picker', label: 'Choose narrator', category: 'overlay', journey: ['first-import'] },
  { id: 'cast-picker', from: 'voice-cast', to: 'voice-picker', label: 'Change voice', category: 'overlay', journey: ['voices'] },
  { id: 'picker-scope', from: 'voice-picker', to: 'voice-scope', label: 'Character voice', category: 'overlay', journey: ['voices'] },
  { id: 'reader-speaker', from: 'current-chapter', to: 'speaker-picker', label: 'Correct speaker', category: 'overlay', journey: ['reader'] },
  { id: 'speaker-scope', from: 'speaker-picker', to: 'correction-scope', label: 'Choose speaker', category: 'overlay', journey: ['reader'] },
  { id: 'listen-chapters', from: 'now-playing', to: 'chapter-picker', label: 'Chapters', category: 'overlay', journey: ['reader'] },
  { id: 'reader-chapters', from: 'current-chapter', to: 'chapter-picker', label: 'Chapters', category: 'overlay', journey: ['reader'] },
  { id: 'details-export', from: 'book-details', to: 'export-picker', label: 'Export MP3', category: 'overlay', journey: ['manage'] },
  { id: 'details-remove', from: 'book-details', to: 'remove-confirm', label: 'Remove', category: 'overlay', journey: ['manage'] },
  { id: 'library-remove', from: 'library', to: 'remove-confirm', label: 'Remove', category: 'overlay', journey: ['manage'] },
  { id: 'settings-share', from: 'settings', to: 'share-sheet', label: 'Share log', category: 'overlay', journey: ['manage'] },
  { id: 'nav-library', from: 'bottom-nav', to: 'library', label: 'Library', category: 'persistent' },
  { id: 'nav-listen', from: 'bottom-nav', to: 'now-playing', label: 'Listen · gated', category: 'persistent' },
  { id: 'nav-settings', from: 'bottom-nav', to: 'settings', label: 'Settings', category: 'persistent' },
  { id: 'back-library', from: 'back-stack', to: 'library', label: 'Fallback', category: 'back' },
  { id: 'import-back', from: 'import', to: 'back-stack', label: 'Back', category: 'back' },
  { id: 'parsing-back', from: 'parsing', to: 'back-stack', label: 'Back', category: 'back' },
  { id: 'review-back', from: 'chapter-review', to: 'back-stack', label: 'Back', category: 'back' },
  { id: 'setup-back', from: 'narration-setup', to: 'back-stack', label: 'Back', category: 'back' },
  { id: 'details-back', from: 'book-details', to: 'back-stack', label: 'Back', category: 'back' },
  { id: 'cast-back', from: 'voice-cast', to: 'back-stack', label: 'Back', category: 'back' },
  { id: 'reader-back', from: 'current-chapter', to: 'back-stack', label: 'Back', category: 'back' }
];

const journeys = {
  all: {
    title: 'All navigation',
    description: 'No single route is highlighted. Use the connection layers or select any screen to inspect its exact outcomes.',
    steps: ['Select a screen', 'Read its available actions', 'Follow an action to the next screen or surface'],
    nodes: []
  },
  'first-import': {
    title: 'First import to listening',
    description: 'The complete first-run path, including the two persisted confirmation gates added after parsing.',
    steps: ['Welcome → Import', 'Parse and discover chapters', 'Choose chapter plan', 'Confirm language and narrator', 'Prepare first playable chapter', 'Enter Now playing'],
    nodes: ['welcome', 'library', 'import', 'file-picker', 'parsing', 'chapter-review', 'narration-setup', 'voice-picker', 'processing', 'now-playing']
  },
  returning: {
    title: 'Return to a book',
    description: 'Library resolves the book state before opening the screen the user actually needs.',
    steps: ['Open or resume from Library', 'Pass any unfinished gate', 'Open details or start listening'],
    nodes: ['library', 'parsing', 'chapter-review', 'narration-setup', 'processing', 'book-details', 'now-playing']
  },
  voices: {
    title: 'Customize voices',
    description: 'Voice changes are book/chapter aware and always ask for regeneration scope before rewriting audio.',
    steps: ['Open Voice cast', 'Preview and choose a voice', 'Choose regeneration scope', 'Apply and return to Book details'],
    nodes: ['now-playing', 'book-details', 'voice-cast', 'voice-picker', 'voice-scope']
  },
  reader: {
    title: 'Read along and correct attribution',
    description: 'The reader stays synchronized with playback and supports a narrow or matching-section speaker correction.',
    steps: ['Open Current chapter', 'Seek by selecting a passage', 'Choose a corrected speaker', 'Choose correction scope'],
    nodes: ['now-playing', 'current-chapter', 'speaker-picker', 'correction-scope', 'voice-cast', 'chapter-picker']
  },
  manage: {
    title: 'Manage a book and diagnostics',
    description: 'Book export/removal and tester log sharing all hand control to explicit confirmation or Android system surfaces.',
    steps: ['Open Book details', 'Export or remove with an explicit picker/dialog', 'Share diagnostics only from Settings'],
    nodes: ['library', 'book-details', 'export-picker', 'remove-confirm', 'settings', 'share-sheet']
  }
};

const filters = {
  primary: { label: 'Direct routes', color: '#ddb65f', visible: true },
  conditional: { label: 'State gates', color: '#a34f45', visible: true },
  persistent: { label: 'Persistent tabs', color: '#9fb9d6', visible: true },
  overlay: { label: 'Sheets & dialogs', color: '#b7a6d8', visible: false },
  back: { label: 'Back behavior', color: '#8192a5', visible: false }
};

const state = {
  selected: 'library',
  journey: 'all',
  query: '',
  zoom: 0.62,
  panX: 8,
  panY: 18,
  dragging: false,
  dragStart: null
};

const nodeById = new Map(nodes.map((node) => [node.id, node]));
const nodeLayer = document.querySelector('#node-layer');
const edgeLayer = document.querySelector('#edge-layer');
const world = document.querySelector('#diagram-world');
const shell = document.querySelector('#diagram');
const searchInput = document.querySelector('#search-input');
const journeySelect = document.querySelector('#journey-select');
const inspector = document.querySelector('#inspector-content');
const status = document.querySelector('#map-status');

function renderNodes() {
  const template = document.querySelector('#node-template');
  nodeLayer.replaceChildren();
  nodes.forEach((node) => {
    const fragment = template.content.cloneNode(true);
    const button = fragment.querySelector('.map-node');
    button.dataset.id = node.id;
    button.dataset.kind = node.kind;
    button.style.left = `${node.x}px`;
    button.style.top = `${node.y}px`;
    if (node.accent) button.classList.add('is-accent');
    button.querySelector('.node-index').textContent = node.index;
    button.querySelector('.node-kind').textContent = node.kind === 'screen' ? 'screen' : node.kind;
    button.querySelector('.node-icon').innerHTML = svgIcon(node.icon);
    button.querySelector('.node-text strong').textContent = node.title;
    button.querySelector('.node-text small').textContent = node.summary;
    button.querySelector('.node-route').textContent = node.route;
    button.setAttribute('aria-label', `${node.title}. ${node.summary}`);
    button.addEventListener('click', (event) => {
      event.stopPropagation();
      selectNode(node.id, true);
    });
    nodeLayer.appendChild(fragment);
  });
}

function nodeSize(node) {
  return { width: 218, height: node.kind === 'overlay' ? 96 : 122 };
}

function edgePorts(from, to) {
  const a = nodeSize(from);
  const b = nodeSize(to);
  const ac = { x: from.x + a.width / 2, y: from.y + a.height / 2 };
  const bc = { x: to.x + b.width / 2, y: to.y + b.height / 2 };
  const dx = bc.x - ac.x;
  const dy = bc.y - ac.y;
  if (Math.abs(dx) >= Math.abs(dy)) {
    return {
      start: { x: from.x + (dx >= 0 ? a.width : 0), y: ac.y },
      end: { x: to.x + (dx >= 0 ? 0 : b.width), y: bc.y },
      horizontal: true
    };
  }
  return {
    start: { x: ac.x, y: from.y + (dy >= 0 ? a.height : 0) },
    end: { x: bc.x, y: to.y + (dy >= 0 ? 0 : b.height) },
    horizontal: false
  };
}

function edgePath(from, to, edge) {
  const { start, end, horizontal } = edgePorts(from, to);
  const bend = edge.category === 'back' ? 90 : 0;
  if (horizontal) {
    const delta = Math.max(45, Math.abs(end.x - start.x) * 0.48);
    const direction = end.x >= start.x ? 1 : -1;
    return `M ${start.x} ${start.y} C ${start.x + delta * direction} ${start.y + bend}, ${end.x - delta * direction} ${end.y + bend}, ${end.x} ${end.y}`;
  }
  const delta = Math.max(45, Math.abs(end.y - start.y) * 0.48);
  const direction = end.y >= start.y ? 1 : -1;
  return `M ${start.x} ${start.y} C ${start.x} ${start.y + delta * direction}, ${end.x} ${end.y - delta * direction}, ${end.x} ${end.y}`;
}

function renderEdges() {
  edgeLayer.querySelectorAll('.edge-group').forEach((group) => group.remove());
  edges.forEach((edge) => {
    const from = nodeById.get(edge.from);
    const to = nodeById.get(edge.to);
    const group = document.createElementNS('http://www.w3.org/2000/svg', 'g');
    group.classList.add('edge-group', `edge-${edge.category}`);
    group.dataset.id = edge.id;
    group.dataset.category = edge.category;
    const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
    path.classList.add('edge-path');
    path.setAttribute('d', edgePath(from, to, edge));
    group.appendChild(path);
    edgeLayer.appendChild(group);

    const midpoint = path.getPointAtLength(path.getTotalLength() / 2);
    const label = document.createElementNS('http://www.w3.org/2000/svg', 'text');
    label.classList.add('edge-label');
    if (!['primary', 'conditional'].includes(edge.category)) label.classList.add('optional-label');
    label.setAttribute('x', midpoint.x);
    label.setAttribute('y', midpoint.y - 8);
    label.setAttribute('text-anchor', 'middle');
    label.textContent = edge.label;
    group.appendChild(label);
  });
}

function renderJourneys() {
  Object.entries(journeys).forEach(([id, journey]) => {
    const option = document.createElement('option');
    option.value = id;
    option.textContent = journey.title;
    journeySelect.appendChild(option);
  });
  journeySelect.value = state.journey;
  journeySelect.addEventListener('change', () => {
    state.journey = journeySelect.value;
    const journey = journeys[state.journey];
    if (state.journey !== 'all' && journey.nodes.some((id) => nodeById.get(id)?.kind === 'overlay')) {
      filters.overlay.visible = true;
      syncFilterButtons();
    }
    updateJourney();
    updateHighlights();
    if (state.journey !== 'all') focusNodes(journey.nodes.filter((id) => isNodeVisible(nodeById.get(id))));
  });
  updateJourney();
}

function updateJourney() {
  const journey = journeys[state.journey];
  document.querySelector('#journey-title').textContent = journey.title;
  document.querySelector('#journey-description').textContent = journey.description;
  const list = document.querySelector('#journey-steps');
  list.replaceChildren();
  journey.steps.forEach((step) => {
    const item = document.createElement('li');
    item.textContent = step;
    list.appendChild(item);
  });
}

function renderFilters() {
  const list = document.querySelector('#filter-list');
  Object.entries(filters).forEach(([id, filter]) => {
    const count = edges.filter((edge) => edge.category === id).length;
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'filter-toggle';
    button.dataset.filter = id;
    button.style.setProperty('--toggle-color', filter.color);
    button.setAttribute('aria-pressed', String(filter.visible));
    button.innerHTML = `<span class="filter-dot" aria-hidden="true"></span><span>${filter.label}</span><small>${count}</small>`;
    button.addEventListener('click', () => {
      filter.visible = !filter.visible;
      if (id === 'overlay' && !filter.visible && nodeById.get(state.selected)?.kind === 'overlay') selectNode('library');
      syncFilterButtons();
      updateHighlights();
      fitMap();
    });
    list.appendChild(button);
  });
}

function syncFilterButtons() {
  document.querySelectorAll('.filter-toggle').forEach((button) => {
    button.setAttribute('aria-pressed', String(filters[button.dataset.filter].visible));
  });
}

function isNodeVisible(node) {
  return node && (node.kind !== 'overlay' || filters.overlay.visible);
}

function matchingNodes() {
  const query = state.query.trim().toLocaleLowerCase();
  if (!query) return new Set();
  return new Set(nodes.filter((node) => {
    const actionText = node.actions.map((action) => `${action.label} ${action.outcome}`).join(' ');
    return `${node.title} ${node.route} ${node.summary} ${node.purpose} ${actionText}`.toLocaleLowerCase().includes(query);
  }).map((node) => node.id));
}

function updateHighlights() {
  const journey = journeys[state.journey];
  const journeyNodes = new Set(journey.nodes);
  const matches = matchingNodes();
  const hasQuery = state.query.trim().length > 0;
  let visibleMatches = 0;

  document.querySelectorAll('.map-node').forEach((element) => {
    const node = nodeById.get(element.dataset.id);
    const visible = isNodeVisible(node);
    element.hidden = !visible;
    element.classList.toggle('is-selected', node.id === state.selected);
    element.classList.toggle('is-journey', state.journey !== 'all' && journeyNodes.has(node.id));
    element.classList.toggle('is-search-match', hasQuery && matches.has(node.id));
    const mutedByJourney = state.journey !== 'all' && !journeyNodes.has(node.id);
    const mutedBySearch = hasQuery && !matches.has(node.id);
    element.classList.toggle('is-muted', visible && (mutedByJourney || mutedBySearch));
    if (visible && matches.has(node.id)) visibleMatches += 1;
  });

  document.querySelectorAll('.edge-group').forEach((group) => {
    const edge = edges.find((candidate) => candidate.id === group.dataset.id);
    const categoryVisible = filters[edge.category].visible;
    const endpointsVisible = isNodeVisible(nodeById.get(edge.from)) && isNodeVisible(nodeById.get(edge.to));
    group.classList.toggle('is-hidden', !categoryVisible || !endpointsVisible);
    const activeForSelection = edge.from === state.selected || edge.to === state.selected;
    const activeForJourney = state.journey !== 'all' && edge.journey?.includes(state.journey);
    const activeForSearch = hasQuery && matches.has(edge.from) && matches.has(edge.to);
    group.classList.toggle('is-active', activeForSelection || activeForJourney || activeForSearch);
    const muted = (state.journey !== 'all' && !activeForJourney && !activeForSelection) ||
      (hasQuery && !(matches.has(edge.from) && matches.has(edge.to)) && !activeForSelection);
    group.classList.toggle('is-muted', muted);
  });

  document.querySelector('#search-empty').hidden = !(hasQuery && visibleMatches === 0);
  const selected = nodeById.get(state.selected);
  status.textContent = `${nodes.filter((node) => node.kind === 'screen').length} screens · ${nodes.filter((node) => node.kind === 'overlay').length} temporary surfaces · ${selected.title} selected`;
}

function actionBadge(action) {
  const label = {
    navigate: 'route', conditional: 'gated', overlay: 'surface', local: 'in place',
    destructive: 'confirm', automatic: 'automatic'
  }[action.kind] || action.kind;
  return `<span class="action-badge ${action.kind}">${label}</span>`;
}

function renderInspector(node) {
  const navActions = node.actions.filter((action) => ['navigate', 'conditional', 'automatic'].includes(action.kind));
  const localActions = node.actions.filter((action) => !['navigate', 'conditional', 'automatic'].includes(action.kind));
  const bottomFact = node.bottomNav
    ? '<div class="route-fact"><span class="fact-icon">⌑</span><span>Persistent tabs are visible here. Listen still passes every readiness gate.</span></div>'
    : '<div class="route-fact"><span class="fact-icon">×</span><span>Persistent tabs are hidden on this focused task screen.</span></div>';

  inspector.innerHTML = `
    <header class="inspector-cover">
      <div class="inspector-title">
        <span class="inspector-icon" aria-hidden="true">${svgIcon(node.icon)}</span>
        <div>
          <span class="status-pill">${node.kind}</span>
          <h2 id="inspector-heading">${node.title}</h2>
          <p>${node.purpose}</p>
          <code class="inspector-route">${node.route}</code>
        </div>
      </div>
    </header>
    <div class="inspector-body">
      <div class="route-facts">
        <div class="route-fact"><span class="fact-icon">↳</span><span>${node.entry}</span></div>
        ${node.kind === 'screen' ? bottomFact : ''}
      </div>
      ${actionSection('Route-changing actions', navActions, 'Select an action to follow its deterministic destination. Gated actions list every possible outcome.')}
      ${actionSection('In-screen and temporary actions', localActions, 'These mutate state, open a sheet/dialog, or hand off to an Android system surface.')}
      <div class="source-note">Source evidence: ${node.source}</div>
    </div>`;

  inspector.querySelectorAll('button.action-card[data-target]').forEach((button) => {
    button.addEventListener('click', () => {
      const target = button.dataset.target;
      if (nodeById.get(target)?.kind === 'overlay' && !filters.overlay.visible) {
        filters.overlay.visible = true;
        syncFilterButtons();
      }
      selectNode(target, true);
      focusNodes([target]);
    });
  });
}

function actionSection(title, actions, copy) {
  if (!actions.length) return '';
  return `<section class="inspector-section">
    <h3>${title}</h3>
    <p class="inspector-section-copy">${copy}</p>
    <div class="actions">${actions.map((action) => {
      const target = action.target || action.targets?.[0];
      const Tag = target ? 'button' : 'div';
      const targetAttribute = target ? ` data-target="${target}" type="button"` : '';
      const destination = action.target ? ` <span class="action-arrow">→ ${nodeById.get(action.target)?.title || action.target}</span>` : '';
      const alternatives = action.targets ? `<br><em>Possible: ${action.targets.map((id) => nodeById.get(id)?.title || id).join(' · ')}</em>` : '';
      return `<${Tag} class="action-card"${targetAttribute}>
        <span class="action-top"><strong>${action.label}${destination}</strong>${actionBadge(action)}</span>
        <p>${action.outcome}${alternatives}</p>
      </${Tag}>`;
    }).join('')}</div>
  </section>`;
}

function selectNode(id, announce = false) {
  if (!nodeById.has(id)) return;
  state.selected = id;
  history.replaceState(null, '', `#${id}`);
  renderInspector(nodeById.get(id));
  updateHighlights();
  if (announce) status.textContent = `${nodeById.get(id).title} selected. Inspector updated with ${nodeById.get(id).actions.length} actions.`;
}

function applyTransform() {
  world.style.transform = `translate(${state.panX}px, ${state.panY}px) scale(${state.zoom})`;
  document.querySelector('#zoom-level').textContent = `${Math.round(state.zoom * 100)}%`;
}

function visibleBounds(ids = null) {
  const targetNodes = nodes.filter((node) => isNodeVisible(node) && (!ids || ids.includes(node.id)));
  if (!targetNodes.length) return { x: 0, y: 0, width: WORLD.width, height: WORLD.height };
  const left = Math.min(...targetNodes.map((node) => node.x));
  const top = Math.min(...targetNodes.map((node) => node.y));
  const right = Math.max(...targetNodes.map((node) => node.x + nodeSize(node).width));
  const bottom = Math.max(...targetNodes.map((node) => node.y + nodeSize(node).height));
  return { x: left, y: top, width: right - left, height: bottom - top };
}

function focusBounds(bounds, maxZoom = 1.12) {
  const rect = shell.getBoundingClientRect();
  const padding = rect.width < 600 ? 26 : 68;
  const zoom = Math.min(maxZoom, Math.max(0.34, Math.min((rect.width - padding * 2) / bounds.width, (rect.height - padding * 2) / bounds.height)));
  state.zoom = zoom;
  state.panX = (rect.width - bounds.width * zoom) / 2 - bounds.x * zoom;
  state.panY = (rect.height - bounds.height * zoom) / 2 - bounds.y * zoom;
  applyTransform();
}

function focusNodes(ids) {
  const bounds = visibleBounds(ids);
  focusBounds({ x: bounds.x - 40, y: bounds.y - 40, width: bounds.width + 80, height: bounds.height + 80 }, ids.length === 1 ? 1.08 : 0.88);
}

function fitMap() {
  focusBounds(visibleBounds(), 0.75);
}

function setZoom(nextZoom, anchor = null) {
  const previous = state.zoom;
  const zoom = Math.min(1.45, Math.max(0.34, nextZoom));
  const rect = shell.getBoundingClientRect();
  const point = anchor || { x: rect.width / 2, y: rect.height / 2 };
  const worldX = (point.x - state.panX) / previous;
  const worldY = (point.y - state.panY) / previous;
  state.zoom = zoom;
  state.panX = point.x - worldX * zoom;
  state.panY = point.y - worldY * zoom;
  applyTransform();
}

function bindInteractions() {
  document.querySelector('#zoom-in').addEventListener('click', () => setZoom(state.zoom + .12));
  document.querySelector('#zoom-out').addEventListener('click', () => setZoom(state.zoom - .12));
  document.querySelector('#fit-map').addEventListener('click', fitMap);
  document.querySelector('#focus-mode').addEventListener('click', (event) => {
    document.body.classList.toggle('focus-mode');
    const active = document.body.classList.contains('focus-mode');
    event.currentTarget.setAttribute('aria-pressed', String(active));
    event.currentTarget.lastChild.textContent = active ? ' Exit focus' : ' Expand map';
    requestAnimationFrame(fitMap);
  });

  shell.addEventListener('pointerdown', (event) => {
    if (event.target.closest('.map-node')) return;
    state.dragging = true;
    state.dragStart = { x: event.clientX, y: event.clientY, panX: state.panX, panY: state.panY };
    shell.classList.add('is-dragging');
    shell.setPointerCapture(event.pointerId);
  });
  shell.addEventListener('pointermove', (event) => {
    if (!state.dragging) return;
    state.panX = state.dragStart.panX + event.clientX - state.dragStart.x;
    state.panY = state.dragStart.panY + event.clientY - state.dragStart.y;
    applyTransform();
  });
  shell.addEventListener('pointerup', (event) => {
    state.dragging = false;
    shell.classList.remove('is-dragging');
    if (shell.hasPointerCapture(event.pointerId)) shell.releasePointerCapture(event.pointerId);
  });
  shell.addEventListener('wheel', (event) => {
    if (!event.altKey) return;
    event.preventDefault();
    const rect = shell.getBoundingClientRect();
    setZoom(state.zoom + (event.deltaY < 0 ? .08 : -.08), { x: event.clientX - rect.left, y: event.clientY - rect.top });
  }, { passive: false });

  searchInput.addEventListener('input', () => {
    state.query = searchInput.value;
    updateHighlights();
  });
  document.querySelector('#clear-search').addEventListener('click', () => {
    searchInput.value = '';
    state.query = '';
    searchInput.focus();
    updateHighlights();
  });
  document.addEventListener('keydown', (event) => {
    if (event.key === '/' && document.activeElement !== searchInput) {
      event.preventDefault();
      searchInput.focus();
    }
    if (event.key === 'Escape' && state.query) {
      searchInput.value = '';
      state.query = '';
      updateHighlights();
    }
  });
}

function init() {
  renderNodes();
  renderEdges();
  renderJourneys();
  renderFilters();
  bindInteractions();
  const hashNode = location.hash.slice(1);
  selectNode(nodeById.has(hashNode) ? hashNode : state.selected);
  requestAnimationFrame(() => {
    if (nodeById.has(hashNode)) {
      focusNodes([hashNode]);
    } else {
      focusNodes(shell.getBoundingClientRect().width < 600
        ? ['library', 'import']
        : ['welcome', 'library', 'import', 'parsing']);
    }
  });
}

init();
