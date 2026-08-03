# Whisperbook architecture

Whisperbook is an offline-first Android application organized as a single Gradle module with explicit package boundaries. The architecture optimizes for three product invariants:

1. A selected book and every derived artifact remain on the device.
2. Playback can start after the opening audio is ready, without waiting for the whole book.
3. Background work, process recreation, and chapter transitions do not lose durable progress.

## System view

![Whisperbook system architecture](diagrams/system-architecture.svg)

Editable diagrams.net source: [system-architecture.drawio](diagrams/system-architecture.drawio).

## Package responsibilities

| Package | Responsibility | Primary entry points |
| --- | --- | --- |
| `ui` | Compose screens, navigation, design tokens, accessibility semantics, and user-facing state | `WhisperbookApp`, `WhisperbookNavHost`, `WhisperbookAppState` |
| `integration` | UI orchestration and process-level dependency composition | `WhisperbookViewModel`, `WhisperbookAppContainer`, `WhisperbookServices` |
| `domain` | Stable data models and ports used across storage, preparation, and playback | `Models.kt`, `Ports.kt` |
| `engine.document` | SAF import, byte-signature validation, EPUB parsing, PDF extraction, and offline OCR | `SafBookImporter`, `OfflinePublicationExtractor`, `AndroidPdfOcrHook` |
| `engine.attribution` | Dialogue scanning, speaker attribution, alias handling, and confidence evidence | `DialogueScanner`, `HeuristicSpeakerAttributor` |
| `engine.preparation` | Durable, staged background preparation and chapter prefetch | `ProductionPreparationCoordinator`, `PreparationWorker`, `SequentialChapterAudioPreparer` |
| `engine.tts` | Embedded model validation and local speech generation | `SherpaKittenTtsEngine`, `SupertonicAssets` |
| `engine.audio` | WAV persistence, cache keys, on-demand generation, and voice previews | `AppPrivateAudioSegmentStore`, `LocalAudioGenerationCoordinator`, `LocalVoicePreviewPlayer` |
| `data` | Room/DataStore persistence and repository implementations | `WhisperBookDatabase`, `RoomLibraryRepository`, `DataStoreSettingsRepository` |
| `playback` | Media3 service/session, queue construction, checkpoints, and sleep timer | `WhisperPlaybackService`, `ControllerBackedPlaybackGateway`, `PlaybackRuntime` |

`SherpaKittenTtsEngine` is a historical internal class name; the implementation and bundled assets use Supertonic 3.

## Dependency direction

The project does not enforce these boundaries with separate Gradle modules, so they are architectural rules rather than compiler-enforced rules:

```text
Compose UI
    ↓
ViewModel / integration
    ↓
domain ports and models
    ↓
data, preparation, audio, and playback implementations
    ↓
Android platform + Room + WorkManager + Media3 + sherpa-onnx
```

- UI code should consume screen state and actions rather than open databases, files, or media sessions directly.
- Domain models should not depend on Compose, Room entities, WorkManager, or Media3 types.
- The app container is the composition root. Avoid constructing alternate process-scoped dependency graphs in screens or workers.
- Background workers resolve installed `PreparationDependencies` and persist checkpoints after each durable stage.
- Playback reads prepared segments through `PlaybackQueueSource`; it should not know how a publication was parsed or attributed.

## Runtime flow

![Whisperbook offline processing pipeline](diagrams/offline-pipeline.svg)

Editable diagrams.net source: [offline-pipeline.drawio](diagrams/offline-pipeline.drawio).

### Import and preparation

1. `SafBookImporter` reads the user-selected URI, validates the actual file signature, hashes the content, and creates an app-private copy.
2. `RoomLibraryRepository` persists the book record and prevents duplicate sources from becoming separate library entries.
3. `OfflinePublicationExtractor` parses EPUB reading order or extracts PDF pages. `AndroidPdfOcrHook` recognizes pages without a usable text layer.
4. The preparation worker normalizes passages, detects chapters, attributes dialogue, creates stable character/voice assignments, and commits each stage to Room.
5. Supertonic synthesis generates PCM on one low-priority inference lane. `AppPrivateAudioSegmentStore` validates and atomically commits WAV files.
6. Opening passages become playable first. Remaining passages are generated sequentially, with next-chapter work prefetched rather than competing for CPU in parallel.

### Playback and read-along

1. `ControllerBackedPlaybackGateway` connects the application layer to the Media3 session service.
2. `LocalPlaybackQueueSource` resolves cached segments and may coordinate missing local audio generation.
3. `WhisperPlaybackService` owns the player and foreground media session.
4. Media transitions update `PlaybackRuntime`; the installed checkpoint sink writes passage and chapter progress to Room.
5. UI state combines library data, current queue state, and checkpoints to highlight the active passage and continue across chapters.

## Persistence boundaries

| Store | Contents | Lifecycle |
| --- | --- | --- |
| Room | Books, chapters, passages, characters, voice assignments, audio metadata, preparation jobs, checkpoints | App-private; schema history is committed under `app/schemas/` |
| DataStore | Playback and presentation preferences | App-private |
| Imported source files | Private copies of user-selected EPUB/PDF files | Deleted with the library entry or app data; the external original is untouched |
| Audio cache | Synthesized segments and retained voice-change rollback audio | App-private, validated, bounded, and cleaned according to retention rules |
| Voice preview cache | Short generated samples for the embedded voice picker | App-private and invalidated by model version/sample-rate changes |

## Privacy and trust boundary

The Android manifest explicitly removes `INTERNET` and `ACCESS_NETWORK_STATE`, including declarations that could arrive through transitive manifests. Cleartext traffic is disabled, backup and device transfer are disabled, services are not exported, and selected documents are copied into private app storage.

Build tooling is outside this runtime boundary: Gradle may access configured artifact repositories while resolving dependencies. The installed app itself has no networking permission.

See [PRIVACY.md](../../PRIVACY.md) for the user-facing privacy contract.

## Concurrency and failure handling

- CPU-heavy speech inference is intentionally serialized to protect Compose and audio I/O responsiveness.
- WorkManager provides durable background execution, retry behavior, and foreground-service integration for preparation.
- Preparation stage progress is persisted, so work can resume after process recreation instead of starting from zero.
- WAV output uses temporary files plus validation and atomic promotion to avoid exposing partial audio.
- Cache keys include content, voice, speed, and model version so incompatible audio is regenerated.
- Chapter selection and queue handoff follow a latest-request-wins contract to prevent stale asynchronous queues replacing a newer choice.

## Safe change guide

| Change | Start here | Verify with |
| --- | --- | --- |
| Add a screen or route | `ui/navigation`, `ui/screens`, `WhisperbookUiState` | Navigation and Compose semantics tests |
| Change stored book data | `data/local/db`, mappers, repository | Room schema export, migration test, repository tests |
| Add a preparation stage | `PreparationWorker`, `ProductionPreparationCoordinator` | Stage-resume, retry, and notification tests |
| Change voice behavior | `engine/tts`, `engine/audio`, voice assignments | Model asset, PCM/WAV, preview, cache-key, and regeneration tests |
| Change playback sequencing | `integration/LocalPlaybackQueueSource`, `playback` | Queue, chapter continuation, checkpoint, audio-focus tests |
| Change import support | `engine/document`, repository | Signature, parser, OCR, encrypted/corrupt input tests |

## Diagram maintenance

The `.drawio` files are uncompressed diagrams.net XML and are the editable sources. Open them in [diagrams.net](https://app.diagrams.net/), keep existing cell IDs where practical, and export the matching SVG next to the source. README embeds point to the SVG exports so diagrams render directly on GitHub.

Whenever a boundary, dependency, or pipeline stage changes, update both the XML source and exported SVG in the same change.
