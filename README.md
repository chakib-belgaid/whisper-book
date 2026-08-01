# Whisperbook for Android

Whisperbook turns a local PDF or EPUB into a chapter-based audiobook without an account, upload, or network connection. It extracts the publication on-device, finds quoted dialogue with deterministic heuristics, assigns stable character colors and embedded voices, synthesizes local WAV segments, and keeps the active passage synchronized with Media3 playback.

![Whisperbook welcome screen](design-system/screens/01-welcome.png)

## What is implemented

- Private Storage Access Framework import with byte-signature validation and SHA-256 integrity checks.
- EPUB metadata, reading-order, navigation, and chapter extraction.
- Text-layer PDF extraction plus bundled ML Kit OCR for image-only PDFs.
- Explainable regex/heuristic dialogue attribution with character aliases and confidence evidence.
- Eight embedded KittenTTS Nano voices through sherpa-onnx; no model download is required.
- WorkManager preparation chain that survives backgrounding and process restarts.
- Room persistence for books, chapters, passages, characters, assignments, audio, jobs, and playback checkpoints.
- App-private, bounded, atomic WAV cache and Media3 foreground playback.
- Chapter picker, automatic next-chapter prefetch and continuation, 15-second transport, playback speed, sleep timer, audio focus, and headphone-disconnect handling.
- Synchronized read-along with detected character names, color rails, an explicit “Now speaking” state, live passage progress, and optional auto-scroll.
- Native Compose implementations of all nine screens in the blue Woodland Paper Theatre design.
- No `INTERNET` or `ACCESS_NETWORK_STATE` permission in the built APK; backup and device transfer are disabled for private books and audio.

The complete screen inventory and navigation artifact are in [design-system/IMPLEMENTATION_MAP.md](design-system/IMPLEMENTATION_MAP.md) and [design-system/navigation/navigation-graph.svg](design-system/navigation/navigation-graph.svg).

## Build

Requirements: Android Studio/SDK 36, Android build-tools 36, and JDK 17.

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug
./gradlew connectedDebugAndroidTest
./gradlew :app:assembleRelease :app:bundleRelease
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Without release credentials, Gradle intentionally produces an unsigned release artifact.

For a signed release, set all four variables before building:

```bash
export WHISPERBOOK_KEYSTORE_PATH=/absolute/path/to/release.jks
export WHISPERBOOK_KEYSTORE_PASSWORD='...'
export WHISPERBOOK_KEY_ALIAS='...'
export WHISPERBOOK_KEY_PASSWORD='...'
./gradlew :app:bundleRelease
```

Secrets and keystores are never committed.

## Test coverage

JVM tests cover format signatures, EPUB parsing, chapter detection, paragraph normalization, dialogue attribution, voice mapping, PCM conversion, WAV integrity, cache invalidation/LRU behavior, preparation checkpoints, settings, persistence mapping, ViewModel actions, sleep timing, and navigation contracts.

Connected Android tests cover:

- Native KittenTTS model loading and real PCM synthesis on arm64.
- EPUB parsing with Android's platform XML implementation.
- Text-layer PDF extraction.
- Image-only PDF recognition with bundled offline OCR.
- Compose navigation, semantics, minimum touch targets, and the read-along active state.

An end-to-end emulator run also imports `qa-fixtures/tiny-story.epub`, prepares all five worker stages, synthesizes opening passages, plays the chapter through the foreground Media3 service, and displays the attributed Elara/Rowan/Narrator passages.

## Supported input

Whisperbook accepts valid, readable PDF and EPUB documents. Password-protected/encrypted, corrupt, DRM-protected, or textless pages that cannot be recognized are reported as local import errors; no cloud fallback is attempted. Very large books are prepared progressively, and chapter audio beyond the opening prefetch is synthesized locally on demand.

See [PRIVACY.md](PRIVACY.md) and [docs/licenses/TTS_ARTIFACTS.md](docs/licenses/TTS_ARTIFACTS.md) for the privacy and third-party artifact record.
