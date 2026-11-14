# Adia v0.4.2

本文件只写当前版本相对上一版的改动；CI 直接把本文件作为 GitHub Release 正文，历史版本看 git tag 对应的提交。

## 本版：本地 AI（`android-ai` skill）

### 内置 AI 运行时

- toolbox 新增 4 个 Termux 包（全部为 bionic 构建，**不要**换成 PyPI 的 manylinux 轮子）：
  `python-numpy 2.5.4`、`python-onnxruntime 1.31.1`、`python-tflite-runtime 2.21.0`、`llama-cpp 1.0.0`。
- APK 约 +26.6 MB `.deb`，首次安装后约 +119 MB（`$PREFIX`）。
- ONNX Runtime 的 Termux 构建带 **CPU / XNNPACK / NNAPI** provider；
  TFLite 只有 CPU（没有 GPU/NNAPI delegate `.so`）；
  llama.cpp 提供 `llama-cli` / `llama-server` / `llama-quantize` / `llama-bench`，CPU 走 ARM NEON。
- 模型统一放 `~/models/{onnx,tflite,gguf}/`，不要放 `cache/` 或 `/tmp`。

### 新 skill：`android-ai`

四个库**分开写**，各自的 API、模型格式和限制不混在一起：

| 文件 | 内容 |
| --- | --- |
| `SKILL.md` | 引擎选择、模型目录、CPU 线程/内存/量化规则、常见错误、NPU 现状 |
| `references/onnxruntime.md` | `InferenceSession`、provider 顺序、NNAPI 回退、线程数 |
| `references/tflite.md` | `tflite_runtime.interpreter` 的完整调用序列、量化输入 |
| `references/llama-cpp.md` | GGUF 大小预算、`llama-cli` / `llama-server`、OpenAI 兼容接口 |
| `references/mnn.md` | 为什么暂不内置、官方 Android 包大小、JNI/CLI 接入条件 |

自检（proot 内）：

```sh
bash ~/.config/opencode/skills/android-ai/scripts/check-ai.sh
python ~/.config/opencode/skills/android-ai/scripts/onnx_smoke.py
```

`onnx_smoke.py` 用 onnxruntime 包自带的 `mul_1.onnx`，实测输出
`[[1,4],[9,16],[25,36]]`，不需要下载模型。

### 聊天：键盘跟随滚动

- 往下翻看更早的消息时，输入框的软键盘跟着收起；往回翻或回到底部时再弹出。
- 只对已经聚焦的输入框生效，不会在看历史时强行弹出键盘；`imeHiddenByScroll`
  只在「是我们收起的」情况下才重新 `show()`。
- Android 的 IME 高度和动画由系统控制，只能 `show()`/`hide()`，**不能逐像素跟手**；
  要真正跟手需要自绘输入法（不做）。

### MNN（未内置）

- 官方 `3.6.2` 的 Android 预编译包 `mnn_..._android_armv7_armv8_cpu_opencl_vulkan.zip`
  只有 **6.4 MiB**（含两个 ABI、CPU/OpenCL/Vulkan 三个后端），arm64 子集更小。
- 但它不是 Termux 包：要走 JNI/NDK 或交叉编译 CLI，模型还要在 PC 上转成 `.mnn`；
  NPU 只能通过厂商驱动/NNAPI，不能承诺。
- 结论：先把 ONNX/TFLite/llama.cpp 用起来，MNN 在真机跑通并证明比现有路径更快后再内置。
