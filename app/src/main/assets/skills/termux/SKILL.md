---
name: Termux Toolbox
description: Use the bundled Termux userland on this phone (bash, git, python, node, clang, cmake, ninja, ssh, pkg/apt, adb) and avoid the Android-vs-Termux path traps. Use when a task needs a Linux tool on-device, when a command says "not found" or "CANNOT LINK EXECUTABLE", or when installing packages.
---

# Termux toolbox on Adia

Adia ships a Termux-style userland inside the APK. It is not the Termux app:
there is no separate Termux install, and you do not need one. The toolbox is on
`PATH`, but **on native mode its binaries still need proot** (see below).

## Native vs proot (read this first)

There are two kinds of binaries on the phone:

1. **Android system binaries** — `/system/bin/sh`, `am`, `pm`, `dumpsys`,
   `logcat`, `input`, `screencap`. They run directly.
2. **Termux/toolchain binaries** — `bash`, `git`, `python`, `node`, `clang`,
   `cmake`, `ninja`, `ssh`, `curl`, `pkg`, `apt`, `adb`… and anything under
   `$HOME/toolchain`. They live in app-private storage; Android returns
   `Permission denied` (EACCES) if a native process tries to `execve` them.

The opencode server may run natively (`oc-engine status` = `auto` or `native`).
The shell wrapper only routes a command to proot when it can see a Termux path
or a known proot command **in the command line**. Variable expansion hides that
path, so this **fails**:

```sh
ADB="$HOME/toolchain/platform-tools/adb"; "$ADB" devices
ADB="$PREFIX/bin/adb"; "$ADB" devices
# Permission denied / inaccessible or not found
```

Put the **whole command inside `oc-proot`**, so the variable expands inside the
sandbox:

```sh
oc-proot 'adb devices'
oc-proot 'ADB="$HOME/toolchain/platform-tools/adb"; "$ADB" version'
oc-proot 'bash "$HOME/project/scripts/build.sh" debug'
```

Rules:

- Check `oc-engine status`:
  - `auto`: wrap commands that need Termux/toolchain binaries in
    `oc-proot '<command>'`.
  - `proot`: every command already runs inside the sandbox; run it normally and
    do **not** nest another `oc-proot`.
  - `native`: measurement-only mode; it skips the proot routing. Run
    `oc-engine auto` first if you need prefix/toolchain binaries.
- `oc-proot '<command>'` — run one command in the sandbox. This is the safe
  default for Termux/toolchain binaries and for scripts that call them.
- A literal prefix path also works (the wrapper sees it):
  `/data/data/com.termux/files/usr/bin/adb devices`.
- `oc-engine proot` switches the whole session to proot (slower; use when many
  commands need it); restore with `oc-engine auto`.
- Prefer the Termux `adb` at `$PREFIX/bin/adb` (matching aarch64 build) over
  `$HOME/toolchain/platform-tools/adb`; both must run inside proot.
- Never `chmod +x` and exec an app-private binary from Java/`ProcessBuilder`;
  Android denies it. Use the shell wrapper or proot.

Do not mix their libraries: an Android binary must not inherit Termux
`LD_LIBRARY_PATH`, and a Termux binary must not be launched with the Android
linker by hand.

## Paths

| What | Path |
|---|---|
| Home seen by the agent | `$HOME` = the app-private home (`/data/data/<pkg>/files/home`; always use `$HOME`) |
| Termux prefix | `$PREFIX` = `/data/data/com.termux/files/usr` |
| Shared toolchain | `$HOME/toolchain` (JDK 21, SDK 36, Gradle 9.9.0) |
| Gradle cache | `$HOME/toolchain/gradle-home` (`GRADLE_USER_HOME`) |
| Maven mirror | `$HOME/toolchain/m2` |
| Temp | `/tmp` (mapped to the app cache) |
| Shared storage | `$HOME/sdcard` only when the phone is rooted and the bind mount exists |

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
- `am`/`pm`/`dumpsys` are Android binaries and run outside the Termux prefix;
  they are already on `PATH`.
- If the phone is rooted, `su -c '<cmd>'` works and `$HOME/sdcard` may be the
  shared storage bind mount.

## Pitfalls

- **`CANNOT LINK EXECUTABLE … library "libX.so" not found`** — the Termux
  binary was launched without its prefix libraries. Run it inside proot:
  `oc-proot '<command>'` (or `oc-engine proot` for a batch).
- **`Permission denied` / `inaccessible or not found` / `exec format error`** —
  a Termux/toolchain ELF or script was executed directly from the app data
  directory by an Android process (often through a shell variable such as
  `ADB="$HOME/..."`). Move the whole command into `oc-proot '...'`; do not
  `chmod +x` and exec it from a Java `ProcessBuilder`.
- **`command not found` for a bundled tool** — check `echo $PATH`; the prefix
  must be first. In a script started by an Android service, source
  `oc-native-env.sh` or run the whole script through `oc-proot`.
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
