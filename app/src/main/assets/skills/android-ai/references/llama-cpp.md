# llama.cpp / GGUF (Termux, CPU)

Bundled package: `llama-cpp 1.0.0`. Binaries live in `$PREFIX/bin`, the real
code in `$PREFIX/lib/libllama*.so`, `libggml*.so`. Model format: **GGUF**.

CPU backend is always available (ARM NEON + int8 dot products). The
`llama-cpp-backend-opencl` / `llama-cpp-backend-vulkan` packages are **not**
bundled; install them only if you explicitly want GPU offload:

```sh
pkg install llama-cpp-backend-opencl     # Adreno / Mali, experimental
pkg install llama-cpp-backend-vulkan
```

## One-shot chat (recommended for tests)

```sh
llama-cli \
  -m ~/models/gguf/Qwen2.5-0.5B-Instruct-Q4_K_M.gguf \
  -p "用一句话解释什么是 proot" \
  -n 128 -t 4 -c 2048 --jinja
```

- `-m` model, `-p` prompt, `-n` max new tokens, `-t` threads, `-c` context.
- `--jinja` uses the model's chat template (required for instruct models).
- Add `-no-cnv` / `--single-turn` for a non-interactive answer if the build
  supports it; otherwise pipe the prompt and let `-n` stop it.
- Time-box: a 0.5B Q4 model should answer in a few seconds on big cores; a 3B
  model is several times slower and heats the phone.

## OpenAI-compatible server

```sh
llama-server \
  -m ~/models/gguf/Qwen2.5-0.5B-Instruct-Q4_K_M.gguf \
  --host 127.0.0.1 --port 8080 \
  -t 4 -c 4096 --jinja
```

Then:

```sh
curl -s http://127.0.0.1:8080/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -d '{"model":"local","messages":[{"role":"user","content":"hi"}],"max_tokens":64}'
```

This is useful when another app/skill expects the OpenAI API. Bind to
`127.0.0.1`, never `0.0.0.0`, and stop the server when done.

## Model size / memory budget (Q4_K_M)

| Params | File size | Load + context (rough) |
|---|---:|---:|
| 0.5–0.6B | 0.4–0.5 GB | 0.7–0.9 GB |
| 1.5–1.7B | 1.0–1.2 GB | 1.5–2.0 GB |
| 3–4B | 2.0–2.6 GB | 3.0–3.8 GB |
| 7–8B | 4.5–5.0 GB | not recommended on a phone |

Always leave headroom for Android + opencode. If the process is OOM-killed,
drop to a smaller model or a smaller context (`-c 1024`).

## Measuring and converting

```sh
llama-bench -m <model.gguf> -t 4          # prompt/gen tok/s
llama-quantize <in.gguf> <out.gguf> Q4_K_M
```

`llama-quantize` runs on the phone but is CPU/RAM heavy; prefer converting on a
PC when possible.

## Downloading models

Use the model's official Hugging Face repo. In this environment `HF_ENDPOINT`
may point at `https://hf-mirror.com`; use `hf download` (with `hf_transfer`)
rather than `curl` so a broken download resumes. Store under
`~/models/gguf/<name>/`, never in `$TMPDIR` or `cache/`.

## Do not

- Run a 7B model "to see if it works" — it will OOM/throttle and waste time.
- Leave `llama-server` running after the test (it holds CPU/RAM and a port).
- Expect `-ngl` to help without an OpenCL/Vulkan backend package installed.
- Use `.safetensors`/`.bin` directly; convert to GGUF first (`convert_hf_to_gguf.py`
  on a PC).
