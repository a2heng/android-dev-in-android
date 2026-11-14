---
name: Android AI
description: Run AI models locally on the phone with the bundled Termux toolchain — ONNX Runtime, TFLite and llama.cpp on CPU, with model storage, quantization, threading, memory/thermal limits and an honest NPU/MNN status. Use when the task mentions local inference, on-device model, ONNX, TFLite, GGUF, llama.cpp, MNN, embeddings, ASR, OCR or NPU.
---

# Android AI (on-device inference)

Adia ships a small, CPU-first AI stack inside the proot toolbox. It is meant for
real on-device work (VAD/ASR, OCR, embeddings, small chat models), not for
pretending a phone can host a 7B model.

**Each engine is documented separately — their APIs and model formats are not
interchangeable:**

| Task | Engine | Reference |
|---|---|---|
| `.onnx` model (ASR, OCR, VAD, embeddings, classic CV/NLP) | ONNX Runtime (Python) | [`references/onnxruntime.md`](references/onnxruntime.md) |
| `.tflite` model (MobileNet, small detectors, MediaPipe exports) | TFLite runtime (Python) | [`references/tflite.md`](references/tflite.md) |
| Chat / completion / local LLM | llama.cpp (GGUF, CLI + OpenAI server) | [`references/llama-cpp.md`](references/llama-cpp.md) |
| One engine for vision + LLM, or vendor NPU | MNN — **not bundled yet**, native integration required | [`references/mnn.md`](references/mnn.md) |

## What is installed

The toolbox (`$PREFIX`, real path `/data/data/<pkg>/files/usr`) includes:

| Package | Version | Where |
|---|---|---|
| `python` | 3.14 | `$PREFIX/bin/python` |
| `python-numpy` | 2.5.4 | `$PREFIX/lib/python3.14/site-packages/numpy` |
| `python-onnxruntime` | 1.31.1 | `.../site-packages/onnxruntime` + `$PREFIX/lib/libonnxruntime.so` |
| `python-tflite-runtime` | 2.21.0 | `.../site-packages/tflite_runtime` |
| `llama-cpp` | 1.0.0 | `$PREFIX/bin/llama-cli`, `llama-server`, `llama-quantize`, … |

Verify (inside Adia's local terminal or any proot shell):

```sh
bash ~/.config/opencode/skills/android-ai/scripts/check-ai.sh
```

Do **not** install these with `pip install onnxruntime` / `pip install
tflite-runtime`: PyPI wheels are manylinux (glibc) and will fail on Android
bionic. Use the bundled Termux packages (`pkg install …`).

## Model storage

Keep weights outside the repo and out of `$TMPDIR`:

```text
~/models/
├── onnx/<name>/*.onnx
├── tflite/<name>/*.tflite
└── gguf/<name>/*.gguf
```

`~/models` is on the same private storage as `$HOME` and survives APK updates.
Never commit weights; never put them in `cache/` (the system clears it).

## Performance rules (phone reality)

- **CPU only is the portable path.** Most phones have no usable, documented
  NPU API through Termux; NNAPI may silently fall back or fail. Treat NPU as
  experimental and always keep a CPU path.
- **Use int8/fp16 quantization.** FP32 LLMs are pointless on a phone.
- **Threads:** use the big cores, not all cores. Start with `-t 4` (llama.cpp)
  and `intra_op_num_threads=4` (ONNX); measure with `/proc/cpuinfo` and
  `taskset` if needed.
- **Memory:** a Q4 0.5B model needs ~0.5–0.8 GB; 1.5B ~1.2–1.8 GB; 3B ~2.5 GB.
  Leave headroom for Android, opencode and the Gradle daemon.
- **Do not run a model while Gradle is building.** Both are CPU-bound and the
  phone will throttle; run `clean-caches.sh`/stop daemons first.
- **Keep models mmap-able** (GGUF/ONNX external data) instead of copying them
  into RAM at startup.
- **Time-box experiments:** load → one inference → print latency → exit. Do not
  leave `llama-server` running after the task unless the user asked for it.

## Choosing an engine

1. Model is `.onnx` → ONNX Runtime. Start with CPU; try
   `NnapiExecutionProvider` only after CPU works.
2. Model is `.tflite` → TFLite runtime (CPU; no GPU/NNAPI delegate `.so` is
   bundled).
3. Chat/LLM or a `.gguf` file → llama.cpp. Prefer `llama-cli` for one-shot
   tests and `llama-server` only when another client needs an HTTP API.
4. Need NPU or one runtime for both vision and LLM → see MNN notes; do not
   promise it until it has been built and tested on this device.

## Common failures

| Symptom | Cause | Fix |
|---|---|---|
| `ModuleNotFoundError: onnxruntime` | Ran outside proot or toolbox not installed | `oc-engine status` / `bash ~/.config/.../check-ai.sh` |
| `ImportError: libonnxruntime.so` | `LD_LIBRARY_PATH` missing `$PREFIX/lib` | run through `proot`/`oc-*`, do not call the ELF directly |
| ONNX shape error | Model has static input dims | read `sess.get_inputs()[0].shape`, feed exactly that |
| NNAPI provider error | Vendor driver/op coverage | fall back to `CPUExecutionProvider` |
| `llama-cli: model not found` | GGUF path wrong / model not downloaded | use `~/models/gguf/...`, check `ls -lh` |
| OOM killed while loading GGUF | model too large / mmap disabled | smaller quant (Q4_K_M), `--no-mmap` off, close other apps |
| `pip install onnxruntime` fails | glibc wheel on bionic | use the Termux package |

## Licensing

- ONNX Runtime: MIT. TFLite runtime: Apache-2.0. llama.cpp: MIT. MNN: Apache-2.0.
- Model licenses are separate: check the model card before redistributing
  weights or bundling them into an APK.
