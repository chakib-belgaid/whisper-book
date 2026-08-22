# Beta diagnostics

Whisperbook keeps a small, rotating diagnostic log in its private app storage. It never uploads
anything automatically. A tester can open **Settings → Beta diagnostics → Share diagnostic log**
and send the generated `.jsonl` report with the Android share sheet.

Every event contains the app version, Android version code, Git commit, and whether the APK was
built from a working tree with local changes. The Settings screen shows the same app version and
commit ID so screenshots and reports can be matched to one exact build.

The report records:

- uncaught crashes, without exception messages that could contain imported file details;
- foreground slow/frozen-frame summaries and memory use;
- book operation, preparation-stage, narration, first-audio, and playback timings;
- technical playback and preparation errors.

It does not record book text, book titles, file names or paths, imported URIs, audio, or voice
samples. Logs are capped at three 512 KiB files (the active log and two archives). Creating a new
share snapshot removes the previous temporary snapshot; it does not erase the private rolling log.

Reports use newline-delimited JSON: one complete event per line. Filter by `level`, `event`,
`version_name`, or `commit` when comparing reports from multiple testers.
