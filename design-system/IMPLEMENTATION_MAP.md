# Whisperbook implementation map

The selected direction is the blue **Woodland Paper Theatre** system. The visual pack contains nine portrait mobile screens plus a deterministic navigation graph.

## Shared visual contract

- Deep midnight-blue layered paper for global surfaces.
- Warm cream paper for readable content and controls.
- Terracotta curtains, tan wood, and restrained brass as supporting accents.
- Tactile papercraft depth with gentle contact shadows; controls remain visually conventional and large enough for touch.
- Storybook serif for titles, readable sans serif for actions and metadata.
- Persistent bottom navigation: **Library**, **Listen**, **Settings**.
- Offline and on-device processing are always communicated as reassurance, never as a warning.

## Screen inventory

| ID | Screen | Primary purpose | Main destinations |
| --- | --- | --- | --- |
| 01 | Welcome | Explain the private, offline promise and start first import | Library, Import book |
| 02 | Library | Browse books, resume listening, or add a file | Import book, Book details, Now playing, Settings |
| 03 | Import book | Select a PDF or EPUB and confirm local processing | Processing, Library |
| 04 | Processing | Show extraction, character detection, and voice-assignment progress | Book details, Library |
| 05 | Now playing | Listen, seek, change chapter, set sleep controls, or open synchronized reading | Book details, Voice cast, Current chapter, Library, Settings |
| 06 | Book details | Show progress and chapters; start or resume playback | Now playing, Voice cast, Library |
| 07 | Voice cast | Preview and override automatic character-to-voice assignments | Book details, Now playing |
| 08 | Settings | Manage local voices, storage, listening defaults, and accessibility | Library, Now playing |
| 09 | Current chapter | Read along with speaker-colored passages synchronized to narration | Now playing, Voice cast, Library, Settings |

## State and implementation notes

- Import and processing should survive app backgrounding and expose resumable state.
- Processing stages: chapter extraction, dialogue/character detection, voice assignment, and audio preparation.
- The voice-cast screen must expose automatic assignments without implying perfect detection; every assignment is editable.
- The player streams chapter audio from locally generated/cached segments and preloads the next segment.
- The current-chapter screen follows the same segment timeline as audio playback and scrolls the active passage into view.
- Speaker identity uses a text label and colored rail in addition to tint, so the distinction never depends on color alone.
- Passage colors remain stable across playback and voice editing: Narrator blue, Elara burgundy, Fox orange.
- The Library and Book Details screens own durable reading/listening progress.
- All network-dependent controls are intentionally absent from the core flow.

## Assets

- `screens/01-welcome.png`
- `screens/02-library.png`
- `screens/03-import-book.png`
- `screens/04-processing.png`
- `screens/05-now-playing.png`
- `screens/06-book-details.png`
- `screens/07-voice-cast.png`
- `screens/08-settings.png`
- `screens/09-current-chapter.png`
- `navigation/navigation-graph.svg`
- `navigation/navigation-graph.png`
