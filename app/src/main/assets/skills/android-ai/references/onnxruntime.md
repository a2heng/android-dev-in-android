# ONNX Runtime (Termux Python)

Bundled package: `python-onnxruntime 1.31.1` + `python-numpy 2.5.4`
(Python 3.14). Installed in `$PREFIX/lib/python3.14/site-packages/onnxruntime`;
the C++ runtime is `$PREFIX/lib/libonnxruntime.so`.

## Verified smoke test

The Python package ships two tiny models (`mul_1.onnx`, `logreg_iris.onnx`), so
you can verify the runtime without downloading anything:

```sh
python ~/.config/opencode/skills/android-ai/scripts/onnx_smoke.py
```

Expected (tested):

```text
onnxruntime 1.31.1
providers   [...]
input       X [3, 2] tensor(float)
input data  [[1.0, 2.0], [3.0, 4.0], [5.0, 6.0]]
output      [[1.0, 4.0], [9.0, 16.0], [25.0, 36.0]]
OK
```

## Minimal inference

```python
import numpy as np
import onnxruntime as ort

sess = ort.InferenceSession("~/models/onnx/foo/model.onnx",
                            providers=["CPUExecutionProvider"])
for i in sess.get_inputs():
    print(i.name, i.shape, i.type)          # static dims must match exactly

x = np.zeros((1, 3, 224, 224), dtype=np.float32)
y = sess.run(None, {sess.get_inputs()[0].name: x})[0]
print(y.shape, y.dtype)
```

## Execution providers

The Termux build is compiled with **CPU**, **XNNPACK** and **NNAPI** providers
(confirmed in `libonnxruntime.so`). What actually works depends on the phone:

```python
print(ort.get_available_providers())
```

- Start with `CPUExecutionProvider`.
- `XnnpackExecutionProvider` is available in this build; add it explicitly
  (`providers=["XnnpackExecutionProvider", "CPUExecutionProvider"]`) when the
  CPU path is too slow and the model is float.
- `NnapiExecutionProvider` may use a vendor NPU, but op coverage is limited and
  drivers vary. Always keep `CPUExecutionProvider` in the list so unsupported
  nodes can fall back:

```python
sess = ort.InferenceSession(model, providers=[
    "NnapiExecutionProvider", "CPUExecutionProvider",
])
```

If creation or the first `run()` fails, drop NNAPI and use CPU.

## Threads and memory

```python
so = ort.SessionOptions()
so.intra_op_num_threads = 4       # big cores; do not use all 8
so.inter_op_num_threads = 1
so.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL
sess = ort.InferenceSession(model, so, providers=["CPUExecutionProvider"])
```

- Prefer int8-quantized `.onnx` for CPU; fp16 is not faster on most phone CPUs.
- Run `onnxruntime_test` (shipped in `$PREFIX/bin`) only for diagnostics.
- The C++ library is 22 MB; models are separate and belong in `~/models/onnx/`.

## Converting models

Conversion is normally done on a PC (`torch.onnx.export` / `onnxsim`), not on the
phone. Copy the resulting `.onnx` (and external-data files) into
`~/models/onnx/<name>/`. Keep opset ≤ the runtime version (1.31 supports recent
opsets; check `onnxruntime.__version__`).

## Do not

- `pip install onnxruntime` (manylinux/glibc wheel — fails on bionic).
- Pass `~` to `InferenceSession`; expand it first (`os.path.expanduser`).
- Feed a different shape than `get_inputs()[0].shape` for static models.
