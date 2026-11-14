# MNN (native Android) — status: **not bundled**

MNN is Alibaba's inference engine (Apache-2.0). It is a good fit when you want
one engine for vision + LLM + NPU experiments, but **it is not a Termux
package**, so it cannot be used with `pkg install` or `pip install`.

## Size

Official release `3.6.2` ships:

```text
mnn_3.6.2_android_armv7_armv8_cpu_opencl_vulkan.zip   6,746,981 bytes (~6.4 MiB)
```

That zip covers **two ABIs** (armv7 + armv8) and three backends (CPU, OpenCL,
Vulkan), so the arm64-only subset is smaller than 6.4 MiB. **APK size is not the
blocker**; the blocker is native integration and device testing.

## Why it is not in the toolbox

- Termux packages are built for Android bionic and installed with `pkg`; MNN
  ships prebuilt native libraries and headers, not a Termux package.
- Using it requires either:
  1. **JNI/AAR in Adia** — copy the arm64 `libMNN*.so` into
     `app/src/main/jniLibs/arm64-v8a/`, compile a small JNI wrapper with the
     NDK, and expose it to the UI/agent (e.g. a localhost-only HTTP endpoint);
     or
  2. **A cross-compiled CLI** — build a tiny `mnn` runner with the NDK and put
     it in `$PREFIX/bin`, then call it from a proot shell.
- Models must be converted to `.mnn` on a PC with `MNNConvert` (or exported by
  the MNN toolchain). `.onnx`/`.tflite`/`.gguf` are not loaded directly.

## NPU / GPU reality

- MNN has an **NNAPI** backend, but whether an Android phone exposes a usable
  NPU through NNAPI depends on the vendor driver and op coverage. NNAPI itself
  is deprecated on newer Android versions; do not promise NPU speedups.
- MNN's **OpenCL** backend can use the phone GPU (Adreno/Mali) on some devices;
  **Vulkan** is another option. Both need real-device benchmarking.
- The **CPU** backend (ARM82/NEON, int8) is the only predictable path. Compare
  it against llama.cpp (LLM) or ONNX Runtime (small models) before adding a
  second engine.

## If we decide to add it

1. Download the Android zip, take only `arm64-v8a` libs + headers.
2. Run `python app/src/main/assets/skills/android-build/scripts/check-16kb.py`
   on every `.so`; MNN releases are recent but verify, because `jniLibs` is
   packaged with `extractNativeLibs=true`.
3. Build the smallest possible runner:
   - vision: MobileNet/ResNet `.mnn` → one image → top-5;
   - LLM: MNN-LLM demo with a 0.5B–1.5B quantized model.
4. Benchmark on the **actual phone**: load time, first token, tok/s, RAM, heat.
5. Only bundle it if it beats the existing path (otherwise it is dead weight).

## When MNN is the right choice

- You need one runtime for **vision + LLM + audio** and are willing to write
  native code.
- You explicitly target a vendor NPU and can test it on that device.
- You already have `.mnn` models from a PC pipeline.

## When not to

- A `.onnx` model works — use ONNX Runtime (already bundled).
- A `.gguf` LLM works — use llama.cpp (already bundled, simpler).
- You only need CPU inference for a small model — the bundled stack is enough.

## Licensing

MNN is Apache-2.0. Model weights have their own licenses; check them before
shipping an APK with weights inside.
