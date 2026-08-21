# FFmpegKit audio runtime

Whisperbook uses `dev.ffmpegkit-maintained:ffmpeg-kit-audio:8.1.7` only for local,
on-device audiobook export from finalized narration WAV segments to MP3.

- Project: <https://github.com/ffmpegkit-maintained/ffmpeg>
- Artifact: `ffmpeg-kit-audio` 8.1.7 from Maven Central
- License declared by the artifact: GNU Lesser General Public License v3.0
- MP3 encoder: LAME (`libmp3lame`), included by the audio artifact
- Support dependency: `com.arthenica:smart-exception-java:0.2.1` (BSD 3-Clause)

The export path does not add network access. Distribution builds must preserve the
applicable notices and satisfy the LGPL requirements for the bundled native runtime.
