---
name: Android 16 KB
description: Enforce Android 16 KB page-size compatibility for apps and the on-device toolchain. Use when building/shipping native code, choosing aapt2/adb/aidl, or auditing an APK.
---

# Android 16 KB page size

**Policy: follow the official recommendation, not “4 KB is forbidden”.**
16 KB alignment is what Google recommends for new devices and what Play asks
for new apps/updates targeting Android 15+. A 4 KB-aligned app still runs on a
4 KB device (this phone is one). The goal is to be ready for 16 KB devices and
to make the trade-off visible, not to claim 4 KB is broken.

Prefer a 16 KB-aligned tool or native library when one exists. If a 4 KB-only
component is unavoidable, document it and set `ADIA_16KB_STRICT=0` for that
build. Release builds from `android-build` enable `ADIA_16KB_STRICT=1` by
default (strict 64-bit check) and fail only on misaligned **64-bit** libraries;
32-bit ABIs are skipped.

## What must be aligned

Every `PT_LOAD` segment of every `arm64-v8a` ELF that runs on the device:

- the app's `lib*.so` (including dependency AARs)
- native executables packed in `jniLibs` (opencode, adb, aapt2, ...)
- toolchain binaries executed on the phone (aapt2, aidl, adb, zipalign, ...)

Java/JAR/Gradle code does not have this constraint. **32-bit ABIs**
(`armeabi-v7a`, `x86`) are not part of the 16 KB requirement; the checker marks
them `SKIP`, not `4KB/UNALIGNED`. Only 64-bit ELF (`arm64-v8a`, `x86_64`)
failures block a release.

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
| `aapt2` (16 KB build) | 16 KB |
| `aidl` (Termux package, preinstalled) | 16 KB |
| `adb` (Termux `android-tools`, preinstalled) | 16 KB |
| `aapt`, `dexdump`, `split-select`, `zipalign` (lzhiyong static) | 4 KB, optional |

`make-toolchain.py` audits the critical tools (`aapt2`, `aidl`) and the Termux
`adb` before zipping; a regression fails the toolchain build. The four 4 KB
tools above are **not executed by AGP 8.5** in a normal build: AGP uses aapt2,
`zipalign` is manual-only, and `aapt`/`dexdump`/`split-select` are legacy. They
are kept for compatibility. If you need them on a 16 KB device, build them from
AOSP with `-Wl,-z,max-page-size=16384` (aapt2/aidl/adb are already covered).

Never use the 4 KB `aapt2` back; it is the one critical tool.

For a 16 KB-only environment:

- Prefer NDK r28+ with `-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON` and
  `-Wl,-z,max-page-size=16384`.
- Do not ship a 4 KB 64-bit `.so` from a prebuilt AAR; rebuild from source or
  find a 16 KB build. A 32-bit `.so` is allowed but unnecessary on arm64.
- For a 4 KB-only dependency that cannot be replaced, set
  `ADIA_16KB_STRICT=0` and document the limitation.

## Known limits

- The test device is 4 KB (`getconf PAGE_SIZE` = 4096); 16 KB is verified by
  static ELF alignment, not yet by a 16 KB kernel.
- APK zip alignment: AGP handles uncompressed native libs; keep
  `useLegacyPackaging = true` or verify with `zipalign -c -P 16`.
- A 4 KB `aapt2` will fail to load on a 16 KB device; never substitute it back.
