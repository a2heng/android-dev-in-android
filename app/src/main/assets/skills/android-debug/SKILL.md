---
name: Android Debug
description: Debug Android apps on this phone with logcat, crash/ANR analysis, install-and-launch, and build-failure triage. Use when an app built on-device crashes, shows a black screen, ANRs, or the user asks to debug.
---

# Android Debug (on-device)

Everything runs on the phone. Just run `adb` normally: `auto` executes the
whole command inside proot, where both adb binaries and their libraries work.
You do not need to wrap it or classify the command.

Prefer the 16 KB Termux `adb` (`$PREFIX/bin/adb`); it is the matching aarch64
build. `$HOME/toolchain/platform-tools/adb` is the fallback.

## When to use

- the app installs but crashes or shows a black screen
- ANR / frozen UI
- the build succeeds but the app does not run
- need logcat for a package
- need to install and launch a freshly built APK

## 1. Connect adb to this same phone

The cleanest way is Android's **Wireless debugging**:

1. Developer options → Wireless debugging → on.
2. Tap "Pair device with pairing code".
3. On the phone (in a shell):

```sh
adb pair 127.0.0.1:<pair-port> <6-digit-code>
adb connect 127.0.0.1:<connect-port>
adb devices
```

Variables and compound commands work too; `auto` sends the whole line to proot:

```sh
ADB="$HOME/toolchain/platform-tools/adb"; "$ADB" devices
```

No wrapper is required; the whole shell is already inside proot.

If `adb devices` shows a device, continue. If not, ask the user to repeat the
pairing step; do not guess ports.

## 2. Install and launch

```sh
bash <skill-dir>/scripts/install-run.sh /path/to/app-debug.apk com.example.app
```

The script installs, clears logcat, launches the launcher activity, waits a
few seconds, and prints the filtered log.

## 3. Capture a crash

```sh
bash <skill-dir>/scripts/logcat.sh com.example.app 15
bash <skill-dir>/scripts/crash.sh com.example.app
```

Look for:

- `FATAL EXCEPTION` / `AndroidRuntime`
- `Process: com.example.app`
- `Caused by:` — the real root cause is usually the last `Caused by`
- `ANR in com.example.app`
- native: `DEBUG`, `libc`, `signal 11 (SIGSEGV)`, `tombstone`

## 4. Build failures

Run the build script and read the first error, not the last line:

```sh
bash ~/.config/opencode/skills/android-build/scripts/build.sh <project> debug
```

Common causes in this environment:

- `Failed to find Build Tools revision` → the init script forces 36.0.0; check
  `$HOME/toolchain/android-sdk/build-tools/36.0.0/source.properties`
- `core-lambda-stubs.jar` missing → toolchain bundle is incomplete
- `aapt2` exec/format error → wrong ABI or 4 KB-only aapt2 on a 16 KB device
- `java.io.tmpdir` missing → `$PREFIX/tmp` was not created
- dependency not in the offline cache → retry with `OC_BUILD_ONLINE=1`

## 5. Fix and verify

1. Read the relevant source and the full stack trace.
2. Make the smallest fix.
3. Rebuild with the `android-build` skill.
4. Reinstall and rerun this skill's logcat/crash scripts.
5. Report root cause, fix, and the command used; do not claim success without
   a clean logcat/launch result.

## Notes

- Android 10+ restricts apps from reading other apps' logs. The bundled `adb`
  over Wireless debugging is the supported path; do not try to read
  `/dev/log/*` from the app sandbox.
- Keep logcat captures short and filtered; the full buffer is huge.
- Never paste API keys or tokens from logs. Redact `sk-`, `ghp_`, `Bearer`.
