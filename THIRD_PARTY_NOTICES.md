# Third-party notices

Whisperbook's [PolyForm Noncommercial License 1.0.0](LICENSE) applies only to
the original work made available by the Whisperbook copyright holder. It does
not replace, restrict, or relicense third-party software, models, fonts, or
other materials bundled with or used by the application.

Third-party licenses generally allow commercial distribution when their
conditions are followed. They do not make the underlying third-party
components commercially exclusive to Whisperbook or its copyright holder.

## Direct bundled components

| Component | License or terms | Commercial distribution notes |
| --- | --- | --- |
| sherpa-onnx 1.13.4 Android runtime | [Apache License 2.0](docs/licenses/sherpa-onnx-1.13.4-LICENSE) | Commercial use is permitted subject to the license, attribution, notice, modification, and patent terms. |
| Supertonic 3 model files | [MIT License](app/src/main/assets/tts/sherpa-onnx-supertonic-3-tts-int8-2026-05-11/LICENSE) | Commercial use is permitted; retain the copyright and license notice. See the [artifact record](docs/licenses/TTS_ARTIFACTS.md). |
| FFmpegKit Audio 8.1.7 and its native audio libraries | GNU LGPL v3.0 and component-specific licenses embedded by the AAR under `res/raw/` | Commercial use is possible, but the final distribution must satisfy the LGPL and each bundled codec's terms. See the [FFmpegKit record](docs/licenses/FFMPEGKIT.md). |
| smart-exception-java 0.2.1 | BSD 3-Clause | Commercial use is permitted; retain the copyright, conditions, and disclaimer. |
| Inter | [SIL Open Font License 1.1](docs/licenses/fonts/Inter-OFL.txt) | Commercial embedding is permitted subject to the OFL and reserved-name rules. |
| Cormorant Garamond | [SIL Open Font License 1.1](docs/licenses/fonts/CormorantGaramond-OFL.txt) | Commercial embedding is permitted subject to the OFL and reserved-name rules. |
| Libre Baskerville | [SIL Open Font License 1.1](docs/licenses/fonts/LibreBaskerville-OFL.txt) | Commercial embedding is permitted subject to the OFL and its reserved font name. |
| Google ML Kit text recognition | [ML Kit Terms of Service](https://developers.google.com/ml-kit/terms) | Use is governed by Google's terms. Review the current terms and required store privacy disclosures for every commercial release. |

Whisperbook also depends on AndroidX, Jetpack Compose, Kotlin, kotlinx,
jsoup, PDFBox Android, and their transitive dependencies. Their upstream
licenses remain in force. This file is a focused record of the direct bundled
components with the greatest distribution impact; it is not an exhaustive
software bill of materials or legal opinion.

## Commercial release checklist

Before selling or publishing a store build:

1. Generate and review a complete dependency and license inventory for the
   exact release artifact.
2. Preserve all copyright, license, attribution, modification, and patent
   notices required by the versions actually shipped.
3. For FFmpegKit and its codecs, provide the applicable GPL/LGPL texts,
   corresponding source and build information, prominent attribution, and
   the modification/relinking and reverse-engineering permissions required by
   the LGPL. Confirm that no GPL or nonfree FFmpeg option entered the build.
   The current AAR's inherited `source.txt` is not sufficient on its own
   because it points to the retired upstream source page rather than an exact
   maintained `8.1.7` source snapshot and build recipe.
4. Surface third-party notices in the distributed product or alongside every
   download, not only in the source repository.
5. Review current ML Kit terms and complete the applicable store data-safety
   disclosures.
6. Obtain jurisdiction-specific legal review before treating the product as
   commercially cleared, especially for codec patent exposure.
