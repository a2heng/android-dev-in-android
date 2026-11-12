---
name: Termux Toolbox
description: Use the bundled Termux userland on this phone (bash, git, python, node, clang, cmake, ninja, ssh, pkg/apt, adb) and avoid the Android-vs-Termux path traps. Use when a task needs a Linux tool on-device, when a command says "not found" or "CANNOT LINK EXECUTABLE", or when installing packages.
---

# Termux toolbox on Adia

Adia ships a Termux-style userland inside the APK. It is not the Termux app:
there is no separate Termux install, and you do not need one. The toolbox is on
`PATH`, but **its binaries only work inside proot** (the release engine enforces
this; see below).

## Native vs proot (read this first)

There are two kinds of binaries on the phone:

1. **Android system binaries** — `/system/bin/sh`, `am`, `pm`, `dumpsys`,
   `logcat`, `input`, `screencap`. They run directly.
2. **Termux/toolchain binaries** — `bash`, `git`, `python`, `node`, `clang`,
   `cmake`, `ninja`, `ssh`, `curl`, `pkg`, `apt`, `adb`… and anything under
   `$HOME/toolchain`. They live in app-private storage; Android returns
   `Permission denied` (EACCES) if a native process tries to `execve` them.

**Everything already runs inside proot**: the opencode server, this shell, the
terminal and every child process. There are no modes (`auto`/`fast`/`native`)
and no command whitelist. The agent only writes the command; no wrapper is
needed.

```sh
pwd
ls -la
git status
python3 app.py
bash scripts/build.sh
adb devices
pkg install -y jq
am start -n com.example/.MainActivity
```

Variables, `cd`, pipelines and compound recipes also work directly:

```sh
ADB="$HOME/toolchain/platform-tools/adb"; "$ADB" devices
cd "$HOME/workspace/App" && ./gradlew assembleDebug
```

Rules:

- The command cwd is the session directory (`/api/location`), not HOME.
- Prefer the Termux `adb` at `$PREFIX/bin/adb` (matching aarch64 build) over
  `$HOME/toolchain/platform-tools/adb`.
- System commands (`am`, `pm`, `dumpsys`, …) have wrappers in `$PREFIX/bin`
  that unset Termux library variables before execing `/system/bin/*`.
- Never `chmod +x` and exec an app-private binary from Java/`ProcessBuilder`;
  Android denies it. Use the shell.
- If a path/library error appears, run `oc-doctor`, then `oc-repair`.
  Both are documented in `docs/commands.md` and `docs/paths.md`.

Do not mix their libraries: an Android binary must not inherit Termux
`LD_LIBRARY_PATH`, and a Termux binary must not be launched with the Android
linker by hand.

## Paths

| What | Path |
|---|---|
| Home seen by the agent | `$HOME` = the app-private home (`/data/data/<pkg>/files/home`; always use `$HOME`) |
| Project root | `$HOME/workspace`; the session cwd is usually a directory under it |
| Termux prefix | `$PREFIX` = `/data/data/com.termux/files/usr` |
| JDK 21 | `$PREFIX/lib/jvm/java-21-openjdk` (not `$HOME/toolchain`) |
| Android SDK | `$HOME/toolchain/android-sdk` (SDK 36, build-tools 36.0.0) |
| Gradle | `$HOME/toolchain/gradle` (9.9.0 distribution) |
| Gradle cache | `$HOME/toolchain/gradle-home` (`GRADLE_USER_HOME`) |
| Maven mirror | `$HOME/toolchain/m2` |
| Runtime data | `$HOME/.local/share/opencode` (DB, logs, shell records) |
| Default config | `$HOME/.config/opencode` |
| Native config | `$HOME/.config/opencode-android` (skills copied here too) |
| Temp | `/tmp` inside proot, mapped to `$TMPDIR` (`cache/tmp`) |
| Shared storage | `$HOME/sdcard` only when the phone is rooted and the bind mount exists |

`/` and `/tmp/opencode` are **not** available in the Adia file browser: the app
cannot read the Android root, and `/tmp/opencode` is not a real directory.

Rules:

- Always use `$HOME`, `$PREFIX`, `$TMPDIR`; never hardcode
  `/data/data/com.a2heng.adia/...` or `/data/data/com.termux/...` in scripts.
- A script with `#!/data/data/com.termux/files/usr/bin/bash` works because that
  path exists inside the sandbox. `#!/usr/bin/env bash` also works when `bash`
  is on `PATH`.
- Do not write to `/sdcard` unless the user set up storage; it is not mounted
  by default.

## Packages

The APK already contains the common packages (see the list below). Install
more with the Termux package manager:

```sh
pkg update
pkg install <name>
# or
apt-get update && apt-get install -y <name>
```

Notes:

- `pkg` is a shim provided by Adia; it forwards to `apt` with the right
  `PREFIX` and non-interactive flags.
- The first online install needs network. Mirrors are already configured; do
  not rewrite `sources.list` unless a mirror is actually down.
- Installed files go under `$PREFIX` and survive app updates (they are in the
  app-private home).
- Use `pkg search <name>` / `apt-cache search <name>` before guessing a
  package name.

Bundled highlights: `bash`, `coreutils`, `findutils`, `grep`, `sed`, `gawk`,
`diffutils`, `tar`, `gzip`, `bzip2`, `xz-utils`, `zstd`, `curl`, `wget`,
`git`, `openssh`, `python`, `python-pip`, `nodejs`, `clang`, `lld`, `llvm`,
`cmake`, `ninja`, `make`, `pkg-config`, `binutils`, `gdb`, `procps`, `psmisc`,
`util-linux`, `termux-tools`, `termux-am`, `pulseaudio`, `openjdk-21`,
`android-tools` (adb), `aidl`.

## Python

```sh
python --version
python -m venv .venv
. .venv/bin/activate
pip install <pkg>
```

- `pip` uses the configured index; if it is slow, use the project's
  `android-lib-cache`/mirror conventions instead of adding random indexes.
- For native wheels, prefer `manylinux aarch64`; building from source needs
  `clang`/`cmake`/`ninja` and can be slow on the phone.
- Keep virtualenvs inside the project (`./.venv`), not in `$HOME` root.

## Node

```sh
node --version
npm --version
npm install
```

- `nodejs` includes npm. Use a project-local `node_modules`; do not install
  global packages unless the task needs a CLI.
- If `npm` is missing, `pkg install nodejs` again or use `corepack`.

## C/C++ and NDK

```sh
clang --version
cmake --version
ninja --version
```

- The NDK sysroot and Android platform libraries are under the bundled
  toolchain; use the project's Gradle/CMake setup instead of hand-written
  cross-compile flags.
- For 16 KB page-size compatibility, compile with
  `-Wl,-z,max-page-size=16384` (NDK r28+ also supports
  `-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON`). See the `android-16kb` skill:
  prebuilt `.so` files are the usual culprit, not a Gradle switch.

## Git and SSH

```sh
git status
git clone <url>
ssh-keygen -t ed25519
```

- Git works in the sandbox; HTTPS uses the bundled CA file. If a corporate
  proxy is needed, export `https_proxy`/`http_proxy` for that command.
- `ssh`/`sftp-server` are available. Keys live in `$HOME/.ssh` and survive app
  updates.
- Do not run `git` helpers that need a GUI; use CLI only.

## Android integration

```sh
adb devices                 # adb is preinstalled (Termux android-tools)
adb shell am start -n ...
am start -n ...             # termux-am / Android am
termux-am start ...
```

- For UI automation, prefer `adb shell input`, `uiautomator dump`,
  `screencap` (system binaries) rather than Termux GUI packages.
- `am`/`pm`/`dumpsys` are Android binaries; Adia wraps them in `$PREFIX/bin`
  so they work inside proot without inheriting Termux libraries.
- If the phone is rooted, `su -c '<cmd>'` works and `$HOME/sdcard` may be the
  shared storage bind mount.

## Pitfalls

- **`CANNOT LINK EXECUTABLE … library "libX.so" not found`** — the prefix is
  missing a library (classically `libreadline.so.8`). Run `oc-doctor`, then
  `oc-repair`. Everything in Adia is already inside proot; do not bypass it.
- **`Permission denied` / `inaccessible or not found` / `exec format error`** —
  an ELF or script was executed directly from app-private storage by an
  Android process, bypassing Adia. Use the Adia terminal/agent; do not
  `chmod +x` and exec it from a Java `ProcessBuilder`.
- **`command not found` for a bundled tool** — check `echo $PATH`; the prefix
  must be first. Everything runs in proot, so no extra wrapper is needed.
- **`/tmp` is not the Android `/tmp`** — it is bound to the app cache. Files
  there are disposable; do not put results there.
- **Long-running jobs** — background them with `setsid`/`nohup` and write a log
  under `$HOME` (not `/tmp` if you need to keep it). The app may kill the
  sandbox when the server restarts.
- **Storage permissions** — Adia does not request `MANAGE_EXTERNAL_STORAGE`.
  To read the shared storage, use the app's file picker/share flow or a rooted
  bind mount; do not assume `/sdcard` is writable.
- **Package scripts that assume the Termux app** — `termux-setup-storage`,
  `termux-reload-settings`, `termux-services` exist as shims but some need the
  real Termux app. Prefer plain files and `nohup`.

## Quick recipes

```sh
# Check the environment
echo "$HOME"; echo "$PREFIX"; command -v bash git python node clang

# Install a missing CLI
pkg install -y jq

# Python one-off
python - <<'PY'
print("hello from the phone")
PY

# Build a native project (CMake)
cmake -S . -B build -G Ninja -DCMAKE_BUILD_TYPE=Release
cmake --build build -j"$(nproc)"

# Keep a job alive and log it
setsid nohup ./job.sh >"$HOME/job.log" 2>&1 &
tail -f "$HOME/job.log"
```
