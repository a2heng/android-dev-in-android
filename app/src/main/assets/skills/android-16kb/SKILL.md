---
name: Android 16 KB
description: Enforce Android 16 KB page-size compatibility for apps and the on-device toolchain. Use when building/shipping native code, choosing aapt2/adb/aidl, or auditing an APK.
---

# Android 16 KB page size

**Policy for this project: 16 KB first.** Prefer a 16 KB-aligned tool or native
library over a 4 KB one, even if it is slightly older. If a 4 KB-only component
is unavoidable, document it and do not claim 16 KB support.

Google Play requires 16 KB page-size support for new apps and updates that
target Android 15+ (the requirement started in late 2025). Release builds from
`android-build` therefore enable `ADIA_16KB_STRICT=1` by default and fail if a
native library is 4 KB aligned. Debug builds print the report but do not fail
unless `ADIA_16KB_STRICT=1` is set explicitly.

## What must be aligned

Every `PT_LOAD` segment of every `arm64-v8a` ELF that runs on the device:

- the app's `lib*.so` (including dependency AARs)
- native executables packed in `jniLibs` (opencode, adb, aapt2, ...)
- toolchain binaries executed on the phone (aapt2, aidl, adb, zipalign, ...)

Java/JAR/Gradle code does not have this constraint.

## Audit

For a built APK:

```sh
python3 <skill-dir>/../android-build/scripts/check-16kb.py app/build/outputs/apk/debug/app-debug.apk
```

For a directory of `.so` files:

```sh
python3 <skill-dir>/../android-build/scripts/check-16kb.py app/src/main/jniLibs/arm64-v8a
```

The build skill runs this automatically after `assembleDebug`. Set
`ADIA_16KB_STRICT=1` to turn misalignment into a build failure.

## Current status of the bundled toolchain

| Component | Status |
|---|---|
| opencode 2.2.6 musl | 16 KB |
| musl loader, proot, libc++/libstdc++/libgcc, librg, libandroid-shmem | 16 KB |
| Termux JDK (`libjvm.so`, `libjava.so`) | 16 KB |
| Termux `bash`/`git`/`node`/`python3` | 16 KB |
| `aapt2` (Termux Studio build) | 16 KB |
| `aidl`, `aapt`, `dexdump`, `split-select`, `zipalign`, `adb` | 4 KB (optional tools) |

`aapt2` is the critical build tool and is 16 KB. `aidl` is only used by projects
with `.aidl` files; `adb` is only used by the debug skill; `zipalign`/`aapt`
legacy are rarely executed by AGP 8.5.

For a 16 KB-only environment:

- AIDL: install Termux's `aidl` package (16 KB) or avoid AIDL.
- adb: install Termux `android-tools` (16 KB) inside the prefix, or use the
  bundled 4 KB static adb only for debugging.
- Prefer NDK r28+ with `-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON` and
  `-Wl,-z,max-page-size=16384`.
- Do not ship a 4 KB `.so` from a prebuilt AAR; rebuild it from source or find a
  16 KB build.

## Known limits

- The test device is 4 KB (`getconf PAGE_SIZE` = 4096); 16 KB is verified by
  static ELF alignment, not yet by a 16 KB kernel.
- APK zip alignment: AGP handles uncompressed native libs; keep
  `useLegacyPackaging = true` or verify with `zipalign -c -P 16`.
- A 4 KB `aapt2` will fail to load on a 16 KB device; never substitute it back.
