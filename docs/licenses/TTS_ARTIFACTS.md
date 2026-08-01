# Offline TTS artifacts

The Android app bundles the following official runtime and unmodified model
files.

## sherpa-onnx Android runtime

- Version: `1.13.4`
- Source: <https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.4/sherpa-onnx-1.13.4.aar>
- Bundled file: `app/libs/sherpa-onnx-1.13.4.aar`
- SHA-256: `03f9c4df965f21c71269365a7951a7f23b5696fddd093fa318c80d65550ab780`
- License: `docs/licenses/sherpa-onnx-1.13.4-LICENSE`

## Kitten Nano English v0.8 INT8 model

- Source: <https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kitten-nano-en-v0_8-int8.tar.bz2>
- Source archive SHA-256: `6fa5be852612ce761094ba74ee6123b4fc4acfefa79bf64dc63acae4a83af2fd`
- Bundled assets: `app/src/main/assets/tts/kitten-nano-en-v0_8-int8`
- License: `docs/licenses/kitten-nano-en-v0_8-LICENSE`

The model asset directory contains only the runtime model, voice embeddings,
tokens, complete eSpeak NG data, and the upstream license. The upstream README
is intentionally not packaged in the APK.
