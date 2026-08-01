# Whisperbook privacy

Whisperbook is intentionally offline-only.

- Imported books are copied into this app's private Android storage.
- Extracted text, character attribution, voice assignments, synthesized audio, settings, and playback checkpoints remain in private on-device storage.
- The shipped APK declares neither `android.permission.INTERNET` nor `android.permission.ACCESS_NETWORK_STATE`.
- The TTS model, pronunciation data, and OCR model are bundled with the application; there is no runtime model download.
- Android cloud backup and device-to-device transfer are disabled for all app data domains.
- There are no accounts, analytics, advertising SDKs, telemetry endpoints, or cloud-processing fallbacks.

The system document picker grants read access only to files the user explicitly selects. Whisperbook creates a private copy so continued playback does not depend on the original provider. Removing application data or uninstalling the app removes its private library and generated audio. Password-protected or DRM-protected publications are not bypassed.
