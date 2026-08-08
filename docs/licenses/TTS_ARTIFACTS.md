# Offline TTS artifacts

Whisperbook bundles its Android speech runtime and multilingual model files so the installed app never downloads model weights. English is enabled by default; the optional French (`fr`) and Arabic (`ar`) language-pack controls activate the corresponding Supertonic language code against those same local weights. The app code pins the combined inference identity as `supertonic-3-int8-2026-05-11+sherpa-onnx-1.13.4`; changing either artifact must also change that identity so incompatible cached audio is regenerated.

## sherpa-onnx Android runtime

- Version: `1.13.4`
- Source: <https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.4/sherpa-onnx-1.13.4.aar>
- Bundled file: `app/libs/sherpa-onnx-1.13.4.aar`
- SHA-256: `03f9c4df965f21c71269365a7951a7f23b5696fddd093fa318c80d65550ab780`
- License copy: `docs/licenses/sherpa-onnx-1.13.4-LICENSE`

## Supertonic 3 English INT8 model

- Release: `sherpa-onnx-supertonic-3-tts-int8-2026-05-11`
- Source archive: <https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/sherpa-onnx-supertonic-3-tts-int8-2026-05-11.tar.bz2>
- Bundled directory: `app/src/main/assets/tts/sherpa-onnx-supertonic-3-tts-int8-2026-05-11`
- Expected output sample rate: `44,100 Hz`
- Embedded presets used by the app: eight (`Bella`, `Jasper`, `Luna`, `Bruno`, `Rosie`, `Hugo`, `Kiki`, `Leo`)
- Bundled upstream notices: `LICENSE` and `README.md` inside the model directory

### Bundled file checksums

| File | SHA-256 |
| --- | --- |
| `duration_predictor.int8.onnx` | `c3eb91414d5ff8a7a239b7fe9e34e7e2bf8a8140d8375ffb14718b1c639325db` |
| `text_encoder.int8.onnx` | `c7befd5ea8c3119769e8a6c1486c4edc6a3bc8365c67621c881bbb774b9902ff` |
| `vector_estimator.int8.onnx` | `20cd86fa5c6effedfda0e7cffe5b0569ca401c440a0c3a1d72bf39286c0db3fd` |
| `vocoder.int8.onnx` | `e923d60f53f95eb1ce235f1dc33ec56d9c057823c96fa6f8acf98f32b0da6152` |
| `tts.json` | `42078d3aef1cd43ab43021f3c54f47d2d75ceb4e75f627f118890128b06a0d09` |
| `unicode_indexer.bin` | `8402ca48e5189a8950138580b0fff64db6f072f24ac07cd54ba8b2fbb9883b30` |
| `voice.bin` | `67d5209b0ee8ce6c74105ffbe12fe6a7628aea3b4ba2fcb308a4a67938a93ce8` |
| `LICENSE` | `0dfe0d0ba84416fe3879d9a34f4909d8d0137c78d1e95834177b0414ac096fa2` |
| `README.md` | `a96c347945f7c8bc1673bea3525b1ac8d36fdde556e1e0a6a186052429caf863` |

These hashes describe the files committed in this repository. Recompute and review the artifact record whenever the runtime or model directory changes.
