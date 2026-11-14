#!/data/data/com.termux/files/usr/bin/sh
# Verify the on-device AI stack. Run inside Adia's proot shell:
#   bash ~/.config/opencode/skills/android-ai/scripts/check-ai.sh
set -u

echo "== python =="
python -V 2>&1 || echo "python MISSING"
command -v python || true

echo "== python packages =="
python - <<'PY'
import sys
print("python", sys.version.split()[0])
try:
    import numpy
    print("numpy", numpy.__version__)
except Exception as e:
    print("numpy MISSING:", e)
try:
    import onnxruntime as ort
    print("onnxruntime", ort.__version__, "providers=", ort.get_available_providers())
except Exception as e:
    print("onnxruntime MISSING:", e)
try:
    import tflite_runtime
    print("tflite_runtime", getattr(tflite_runtime, "__version__", "ok"))
except Exception as e:
    print("tflite_runtime MISSING:", e)
PY

echo "== llama.cpp =="
for b in llama-cli llama-server llama-bench llama-quantize; do
    if command -v "$b" >/dev/null 2>&1; then
        echo "$b: $(command -v "$b")"
    else
        echo "$b: MISSING"
    fi
done

echo "== models =="
if [ -d "$HOME/models" ]; then
    du -sh "$HOME/models"/* 2>/dev/null || true
else
    echo "no ~/models yet (create onnx/, tflite/, gguf/ under it)"
fi

echo "== memory =="
free -m 2>/dev/null | head -2 || true
