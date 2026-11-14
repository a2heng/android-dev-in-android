#!/usr/bin/env python3
"""Self-test for the bundled ONNX Runtime.

Uses `mul_1.onnx` that ships inside the `onnxruntime` Python package, so it
needs no download. Run inside Adia's proot shell:

    python ~/.config/opencode/skills/android-ai/scripts/onnx_smoke.py

Expected result for x = [[1,2],[3,4],[5,6]]: [[1,4],[9,16],[25,36]].
"""
import os
import sys

import numpy as np
import onnxruntime as ort


def main() -> int:
    model = os.path.join(os.path.dirname(ort.__file__), "datasets", "mul_1.onnx")
    if not os.path.isfile(model):
        print("bundled mul_1.onnx not found:", model, file=sys.stderr)
        return 1

    print("onnxruntime", ort.__version__)
    print("providers  ", ort.get_available_providers())
    # Deterministic self-test: CPU always exists. Try NNAPI only after this works.
    sess = ort.InferenceSession(model, providers=["CPUExecutionProvider"])
    inp = sess.get_inputs()[0]
    print("model      ", model)
    print("input      ", inp.name, inp.shape, inp.type)

    shape = [d if isinstance(d, int) and d > 0 else 1 for d in inp.shape]
    x = np.arange(1, int(np.prod(shape)) + 1, dtype=np.float32).reshape(shape)
    y = sess.run(None, {inp.name: x})[0]
    print("input data ", x.tolist())
    print("output     ", y.tolist())
    print("OK" if np.allclose(y, x * x) else "UNEXPECTED OUTPUT")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
