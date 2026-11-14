---
name: Android 16 KB
description: Understand and fix Android 16 KB page-size compatibility. Use when an APK check reports 4KB/UNALIGNED, when adding native AARs (ONNX, OpenCV, etc.), or when deciding whether a Gradle switch/zipalign is enough.
---

# Android 16 KB page size

**The 16 KB problem is not a build switch. It is the ELF `PT_LOAD` alignment of
the prebuilt 64-bit `.so` files inside the APK.** No Gradle flag, no
`useLegacyPackaging`, and no `zipalign -P 16` can change the alignment inside
an ELF that was compiled for 4 KB pages. The fix is to ship 64-bit native
libraries that upstream already built for 16 KB (or rebuild them with
`-Wl,-z,max-page-size=16384`).

Policy: follow the official recommendation, not “4 KB is forbidden”. A
4 KB-aligned app still runs on a 4 KB device (this phone is one). The goal is
to be ready for 16 KB devices and to make the trade-off visible.

## First: find who is actually misaligned

```sh
python3 <skills>/android-build/scripts/check-16kb.py app/build/outputs/apk/debug/app-debug.apk
```

The report shows `min_align=0x1000` (4 KB, bad) or `0x4000` (16 KB, good) per
64-bit library. Typical offenders come from native AARs:

- `libonnxruntime.so` / `libonnxruntime4j_jni.so` (ONNX Runtime)
- `libopencv_java4.so` (OpenCV)
- `libc++_shared.so` (often repacked by those AARs)

32-bit ABIs (`armeabi-v7a`, `x86`) are `SKIP`; only 64-bit ELF
(`arm64-v8a`, `x86_64`) counts.

## What does NOT fix it

- **`zipalign -P 16` / `useLegacyPackaging`**: these align the `.so` *inside
  the APK zip*. They do not touch the ELF `PT_LOAD` segments. The runtime
  loader still sees 4 KB ELF alignment.
- **`ADIA_16KB_STRICT=0`**: only skips the check. The app will still fail to
  load that library on a 16 KB device.
- **A Gradle “16 KB switch”**: AGP has no switch that recompiles third-party
  prebuilt `.so` files.
- **Dropping to 32-bit (`armeabi-v7a`)**: passes the check because 32-bit is
  skipped, but loses 64-bit and is not a real fix.

## What actually fixes it: upgrade the dependency

Replace the AAR with a version whose Android prebuilts are already 16 KB
aligned. Real example (RapidOCR, from an on-device OCR project):

```gradle
configurations.all {
    resolutionStrategy {
        force 'com.microsoft.onnxruntime:onnxruntime-android:1.23.2'
        force 'org.opencv:opencv:4.12.0'
    }
}

dependencies {
    implementation 'io.github.hzkitty:rapidocr4j-android:1.0.0'
    implementation 'org.opencv:opencv:4.12.0'   // 16 KB-aligned prebuilt
}
```

Why this works:

- ONNX Runtime 1.18.0 (the version RapidOCR 1.0.0 pulls) shipped 4 KB
  `arm64-v8a` libraries. 1.23.2 ships 16 KB ones.
- OpenCV 4.9.0 was 4 KB; 4.12.0 (NDK 27+) is 16 KB aligned, and it also brings
  a 16 KB `libc++_shared.so`.
- No need to rebuild ONNX/OpenCV from source on the phone, and 64-bit stays.

After rebuilding, the check should show all 64-bit libraries as `16KB OK`:

```text
arm64-v8a: 8/8 16KB OK
x86_64:    8/8 16KB OK
armeabi-v7a/x86: SKIP
```

Risk: the wrapper library was tested against the old native versions. Compile
and install are not enough — run the actual feature (e.g. take a photo and run
OCR) before declaring success. If the new ABI misbehaves, pin the wrapper to a
version that officially uses the newer natives, or rebuild it.

## If no 16 KB build exists

- Prefer NDK r28+ and `-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON` +
  `-Wl,-z,max-page-size=16384` when you control the source.
- For a 4 KB-only dependency you cannot replace, document it and set
  `ADIA_16KB_STRICT=0` for that build. This is a known limitation, not a fix.
- Never substitute the 4 KB `aapt2` back: `aapt2` is executed on the device and
  is the one critical tool.

## Bundled toolchain status

| Component | Status |
|---|---|
| opencode 2.2.7 musl | 16 KB |
| musl loader, proot, libc++/libstdc++/libgcc, librg, libandroid-shmem | 16 KB |
| Termux JDK (`libjvm.so`, `libjava.so`) | 16 KB |
| Termux `bash`/`git`/`node`/`python3` | 16 KB |
| `aapt2` (16 KB build) | 16 KB |
| `aidl` (Termux package, preinstalled) | 16 KB |
| `adb` (Termux `android-tools`, preinstalled) | 16 KB |
| `aapt`, `dexdump`, `split-select`, `zipalign` (lzhiyong static) | 4 KB, optional |

`make-toolchain.py` audits the critical tools (`aapt2`, `aidl`) and the Termux
`adb` before zipping; a regression fails the toolchain build. The 4 KB tools above are not executed by AGP 8.5 in a normal build.

## Known limits

- The test device is 4 KB (`getconf PAGE_SIZE` = 4096); 16 KB is verified by
  static ELF alignment, not yet by a 16 KB kernel.
- Always re-run the checker on the final release APK, not only on the debug
  build.
