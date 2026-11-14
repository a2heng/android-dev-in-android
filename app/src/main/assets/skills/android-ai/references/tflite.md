# TFLite runtime (Termux Python)

Bundled package: `python-tflite-runtime 2.21.0` (Python 3.14), installed at
`$PREFIX/lib/python3.14/site-packages/tflite_runtime`.

This is the **CPU** runtime only. The package contains
`_pywrap_tensorflow_interpreter_wrapper.so` and nothing else — there is **no**
`libtensorflowlite_gpu_delegate.so` / NNAPI delegate shipped with it, so don't
promise GPU/NPU for `.tflite` on this setup.

## Minimal inference

The API below is taken from the exact `interpreter.py` in the package (the same
names exist in TensorFlow's `tf.lite`):

```python
import numpy as np
from tflite_runtime.interpreter import Interpreter

it = Interpreter(
    model_path="/data/data/<pkg>/files/home/models/tflite/foo/model.tflite",
    num_threads=4,                 # big cores, not all 8
)
it.allocate_tensors()

inp = it.get_input_details()[0]
out = it.get_output_details()[0]
print(inp["name"], inp["shape"], inp["dtype"])

x = np.zeros(inp["shape"], dtype=inp["dtype"])
it.set_tensor(inp["index"], x)
it.invoke()
y = it.get_tensor(out["index"])
print(y.shape, y.dtype)
```

Notes:

- The path must be absolute; expand `~` yourself.
- `allocate_tensors()` must be called before `set_tensor`/`invoke`.
- Dynamic shapes: `resize_tensor_input(inp["index"], new_shape)` before
  `allocate_tensors()` (or re-allocate after resizing).
- Multiple inputs/outputs: iterate `get_input_details()` / `get_output_details()`.

## Models

Convert on a PC with the TensorFlow Lite converter (`tf.lite.TFLiteConverter`,
`ai-edge-torch` for PyTorch, or a MediaPipe export), then copy the `.tflite`
into `~/models/tflite/<name>/`.

- Prefer **dynamic-range int8** or **int8 full-integer** quantization for CPU.
- Check `inp["dtype"]`; quantized models need `np.int8`/`np.uint8` and often a
  scale/zero-point (`inp["quantization"]`).
- Keep model files small (<100 MB) and mmap-friendly.

## When to use TFLite instead of ONNX

- The model already exists only as `.tflite` (MediaPipe / mobile exports).
- Extremely small vision/keyword models where the TFLite runtime is leaner.
- Otherwise, prefer ONNX Runtime: it is the same Python workflow and its
  Termux build also has XNNPACK/NNAPI providers.

## Do not

- `pip install tflite-runtime` (no cp314/manylinux wheel; glibc anyway).
- `pip install tensorflow` on the phone (huge, unsupported).
- Expect `load_delegate("libtensorflowlite_gpu_delegate.so")` to work — that
  library is not bundled.
