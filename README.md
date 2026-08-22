<p align="center">
  <img src="./assets/readme/hero.svg" width="100%" alt="Whisperbook turns local EPUB and PDF books into synchronized multi-voice audiobooks entirely on Android">
</p>

<p align="center">
  <strong>Android 8.0+</strong> · <strong>arm64</strong> · <strong>offline runtime</strong> · <strong>no account</strong> · <strong>no cloud upload</strong>
</p>

Whisperbook is an offline-first Android reader that converts a local EPUB or PDF into a chapter-based audiobook. It extracts text, identifies dialogue, assigns stable character voices, synthesizes speech, and keeps the visible passage synchronized with playback—without sending the book off the device.

> [!IMPORTANT]
> The app runtime deliberately has no `INTERNET` or `ACCESS_NETWORK_STATE` permission. A first development build still needs network access to resolve Gradle dependencies.

## Download

Download the installable APK and its SHA-256 checksum from the [latest GitHub release](https://github.com/chakib-belgaid/whisper-book/releases/latest). The initial `v0.1` artifact is debug-signed and installs as `com.whisperbook.app.debug`; it is intended for direct testing rather than Play Store distribution.

## See it working

<table>
  <tr>
    <td align="center" width="33%">
      <a href="./assets/readme/screenshots/welcome.webp"><img src="./assets/readme/screenshots/welcome.webp" width="100%" alt="Whisperbook welcome screen with a moonlit paper theatre and local book import action"></a><br>
      <strong>Welcome</strong><br><sub>Import EPUB or PDF without an account.</sub>
    </td>
    <td align="center" width="33%">
      <a href="./assets/readme/screenshots/library.webp"><img src="./assets/readme/screenshots/library.webp" width="100%" alt="Whisperbook library showing continue listening and more local books"></a><br>
      <strong>Library</strong><br><sub>Resume each book from its own saved state.</sub>
    </td>
    <td align="center" width="33%">
      <a href="./assets/readme/screenshots/book-details.webp"><img src="./assets/readme/screenshots/book-details.webp" width="100%" alt="Whisperbook book details with continue listening, MP3 export, chapters, and voice cast controls"></a><br>
      <strong>Book details</strong><br><sub>Browse chapters, edit the cast, or export MP3.</sub>
    </td>
  </tr>
  <tr>
    <td align="center" width="33%">
      <a href="./assets/readme/screenshots/now-playing.webp"><img src="./assets/readme/screenshots/now-playing.webp" width="100%" alt="Whisperbook now playing screen with speaker, playback, seek, speed, sleep timer, cast, and chapter controls"></a><br>
      <strong>Now playing</strong><br><sub>Control playback while the active speaker stays visible.</sub>
    </td>
    <td align="center" width="33%">
      <a href="./assets/readme/screenshots/voice-cast.webp"><img src="./assets/readme/screenshots/voice-cast.webp" width="100%" alt="Whisperbook voice cast screen with book language and per-character voice assignments"></a><br>
      <strong>Voice cast</strong><br><sub>Preview and override voices for the selected book.</sub>
    </td>
    <td align="center" width="33%">
      <a href="./assets/readme/screenshots/read-along.webp"><img src="./assets/readme/screenshots/read-along.webp" width="100%" alt="Whisperbook synchronized chapter view with speaker-labelled passages, correction controls, and playback"></a><br>
      <strong>Read along</strong><br><sub>Follow, hear, and correct speaker-labelled passages.</sub>
    </td>
  </tr>
</table>

These are API 36 emulator captures of the production Compose surfaces using deterministic QA data, not static interface mockups. Select any screen to open it at full resolution. The complete journey and source mapping live in the [implementation map](design-system/IMPLEMENTATION_MAP.md).

## What it does

| Capability | Implementation |
| --- | --- |
| Private import | Android Storage Access Framework, byte-signature validation, SHA-256 duplicate detection, and an app-private source copy |
| Publication extraction | EPUB metadata/reading-order parsing, PDF text extraction, and bundled ML Kit OCR for image-only pages |
| Character voices | Explainable dialogue heuristics, first-person narrator detection, confidence-bearing age/gender cues, profile-aware casting across eight embedded Supertonic 3 voices, and per-book or per-chapter overrides |
| Editable attribution | Every read-along passage exposes its detected speaker; corrections can affect one phrase or matching phrases without changing the original publication |
| Book language | English is ready by default; French and Arabic can be activated per book from Voice Cast while synthesis remains private and on-device |
| Durable preparation | A staged WorkManager pipeline with persisted progress, restart recovery, chapter-scoped character discovery, opening-audio priority, and sequential prefetch |
| Local audio | sherpa-onnx inference, 44.1 kHz WAV output, atomic writes, cache validation, retention, and bounded cleanup |
| Playback | Media3 foreground service, chapter queueing, automatic continuation, 15-second seek, speed control, sleep timer, and audio-focus handling |
| Read-along | Active-passage tracking, speaker labels and portraits, live progress, optional auto-scroll, and playback checkpoints |
| MP3 export | A cancellable, progress-reporting offline export that completes missing narration, reuses finalized audio, and writes one book-level MP3 through Android's document picker |
| Privacy | No runtime networking permission, no accounts, no analytics, no cloud fallback, and Android backup/transfer disabled |

## How a book becomes audio

<p align="center">
  <img src="./docs/architecture/diagrams/offline-pipeline.svg" width="100%" alt="Seven-stage offline pipeline from local book selection to checkpointed audiobook playback">
</p>

The opening chapter is attributed, cast, and streamed first, so listening can start without scanning every chapter for characters. Later chapters are analyzed and generated sequentially. A versioned `characters.json` mirror in app-private storage records each completed chapter's character contribution for restart-safe, idempotent progress; Room remains authoritative for characters and voice choices. MP3 export reuses these finalized chapter segments and generates only missing narration before encoding. The editable diagrams.net source is [offline-pipeline.drawio](docs/architecture/diagrams/offline-pipeline.drawio).

## Architecture

<p align="center">
  <img src="./docs/architecture/diagrams/system-architecture.svg" width="100%" alt="Whisperbook layered Android architecture and private data boundary">
</p>

The application uses one Android module with package boundaries for presentation, orchestration, domain contracts, processing engines, persistence, and playback. `WhisperbookAppContainer` is the process-scoped composition root; `WhisperbookViewModel` translates UI intent into repository, preparation, and playback operations.

Read the [architecture guide](docs/architecture/README.md) for responsibilities, dependency direction, runtime flows, storage boundaries, and change guidance. The editable diagrams.net source is [system-architecture.drawio](docs/architecture/diagrams/system-architecture.drawio).

## Repository map

```text
whisper-book/
├── app/
│   ├── schemas/                  Room schema history
│   └── src/
│       ├── main/
│       │   ├── assets/tts/       Bundled Supertonic 3 model
│       │   ├── java/.../data/    Room, DataStore, repositories
│       │   ├── java/.../domain/  Models and ports
│       │   ├── java/.../engine/  Import, extraction, attribution, TTS, export
│       │   ├── java/.../playback/Media3 service and queue
│       │   └── java/.../ui/      Compose screens and design system
│       ├── test/                 JVM tests
│       └── androidTest/          Device/emulator tests
├── art/                          Source art and emulator evidence
├── assets/readme/                README visual identity
├── design-system/                Approved screens and navigation map
├── docs/BETA_DIAGNOSTICS.md      Tester log collection and privacy boundary
├── docs/architecture/            Architecture guide and editable diagrams
├── docs/licenses/                Third-party artifact records
├── LICENSE                       PolyForm Noncommercial 1.0.0 terms
├── THIRD_PARTY_NOTICES.md        Bundled dependency rights and obligations
└── qa-fixtures/                  Deterministic EPUB test fixture
```

## Build and install

### Requirements

- JDK 17
- Android SDK 36 and build-tools 36
- An arm64 Android device or emulator running Android 8.0 / API 26 or newer

The TTS runtime and multilingual model are committed under `app/libs/` and `app/src/main/assets/tts/`; no first-launch model download is required. English is enabled by default. French and Arabic can be activated from each book's **Voice Cast** screen and reuse the same local multilingual model without adding a network permission.

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Open the app, choose a readable `.epub` or `.pdf` in the Android document picker, and wait for the opening audio to finish preparing.

## Verification

Run the local quality gate:

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
```

Run connected tests with an arm64 device or emulator available:

```bash
./gradlew connectedDebugAndroidTest
```

JVM tests cover document parsing, attribution, persistence mapping, cache behavior, WAV generation, preparation, ViewModel actions, settings, navigation contracts, and playback utilities. Connected tests exercise real model loading/synthesis, Android EPUB/PDF extraction, local OCR, Compose semantics, navigation, voice preview, library persistence, and chapter continuation.

CI runs unit tests, lint, and an unsigned release build on every push and pull request.

## Release builds

Release builds are minified and resource-shrunk. Provide all four signing values to generate a signed artifact:

```bash
export WHISPERBOOK_KEYSTORE_PATH=/absolute/path/to/release.jks
export WHISPERBOOK_KEYSTORE_PASSWORD='...'
export WHISPERBOOK_KEY_ALIAS='...'
export WHISPERBOOK_KEY_PASSWORD='...'
./gradlew :app:bundleRelease
```

Without these variables, Gradle intentionally creates an unsigned release artifact. Keystores and signing secrets are ignored by Git.

## Supported input and boundaries

- EPUB and PDF are supported; corrupt, encrypted, password-protected, and DRM-protected publications are rejected.
- Image-only PDF pages use bundled on-device OCR. Pages with no recognizable text remain an import error; there is no cloud fallback.
- The current native runtime is packaged for `arm64-v8a` only.
- Large books are prepared progressively; they are not fully synthesized before playback starts.
- Age, gender, and first-person identity detection is a conservative English-language heuristic. Ambiguous or conflicting evidence stays unknown and uses the normal automatic/default voice; users can always override the cast.
- Imported sources, extracted content, generated audio, settings, and checkpoints live in app-private storage. Uninstalling the app or clearing its data removes that private library, not the user's original external file.

## Project documents

- [Architecture](docs/architecture/README.md) — component responsibilities, flows, boundaries, and editable diagrams
- [Beta diagnostics](docs/BETA_DIAGNOSTICS.md) — versioned crash/performance logs and tester sharing workflow
- [Product contract](PRODUCT.md) — product goals, non-goals, and acceptance criteria
- [Design system](DESIGN.md) — Woodland Paper Theatre principles and UI tokens
- [Implementation map](design-system/IMPLEMENTATION_MAP.md) — screen-by-screen source and asset mapping
- [Privacy](PRIVACY.md) — on-device data handling and permission policy
- [TTS artifacts](docs/licenses/TTS_ARTIFACTS.md) — bundled runtime/model provenance and checksums
- [FFmpegKit audio runtime](docs/licenses/FFMPEGKIT.md) — offline MP3 export runtime and license
- [Third-party notices](THIRD_PARTY_NOTICES.md) — commercial-use boundaries and release obligations

## Licensing

Whisperbook's original source and first-party project materials are licensed
under the [PolyForm Noncommercial License 1.0.0](LICENSE), with the required
copyright notice in [NOTICE](NOTICE). Personal, educational, research, and
other qualifying noncommercial uses are permitted under those terms.

Commercial use by anyone other than the copyright holder requires a separate
commercial license. The copyright holder remains free to use the original work
commercially and to grant separate permissions. PolyForm Noncommercial is a
source-available license with a commercial-use restriction; it is not an
Apache, GNU, or OSI-approved open-source license.

Third-party runtimes, model assets, fonts, and libraries are not covered by
Whisperbook's PolyForm license. They retain their own terms under
[docs/licenses](docs/licenses) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
