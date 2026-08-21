# FFmpegKit audio runtime

Whisperbook uses `dev.ffmpegkit-maintained:ffmpeg-kit-audio:8.1.7` only for local,
on-device audiobook export from finalized narration WAV segments to MP3.

- Project: <https://github.com/ffmpegkit-maintained/ffmpeg>
- Artifact: `ffmpeg-kit-audio` 8.1.7 from Maven Central
- License declared by the artifact: GNU Lesser General Public License v3.0
- MP3 encoder: LAME (`libmp3lame`), included by the audio artifact
- Support dependency: `com.arthenica:smart-exception-java:0.2.1` (BSD 3-Clause)
- Bundled license/source records: the AAR packages `res/raw/license*.txt` and
  `res/raw/source.txt` for FFmpegKit and its included native libraries

> [!WARNING]
> The `8.1.7` AAR's embedded `source.txt` still points to the retired Arthenica
> source page and does not identify the exact maintained fork source commit or
> build recipe. Do not rely on that file alone for a commercial distribution.

The export path does not add network access. Distribution builds must preserve the
applicable notices and satisfy the LGPL requirements for the bundled native runtime.
Commercial distribution is not prohibited, but it is conditional on compliance.
For each final artifact, follow the [FFmpeg project's LGPL checklist](https://ffmpeg.org/legal.html),
make the exact corresponding source and build information available, preserve the
right to replace/debug the LGPL libraries, and verify that no GPL or nonfree build
option is present. Archive or publish the exact `8.1.7` corresponding source and
configuration used for the shipped binaries. See the repository-wide
[third-party notices](../../THIRD_PARTY_NOTICES.md).
