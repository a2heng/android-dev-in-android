---
name: Android Build
description: Build, sign and locate Android APKs locally on this phone with Gradle, JDK 21 and the on-device Android SDK. Use when the user asks to compile, rebuild, package or install an Android project on the device.
---

# Android Build (on-device)

This phone is the build machine. Everything below runs locally; no PC, no cloud.

**16 KB first:** the build script audits the produced APK and prints a 16 KB
alignment report. Set `ADIA_16KB_STRICT=1` to fail the build when a native
library is only 4 KB aligned. **A Gradle switch or `zipalign -P 16` does not
fix this**: the alignment lives inside each prebuilt 64-bit `.so`. When the
check flags `libonnxruntime`, `libopencv_java4`, `libc++_shared`, etc., upgrade
the dependency to a version that ships 16 KB-aligned Android prebuilts (see
`android-16kb` for the ONNX/OpenCV example).

## When to use

Use this skill when the user wants to:

- build an APK from a Gradle/AGP project on this device
- rebuild after editing Java/Kotlin/XML/resources
- produce a debug or release APK and report its path

## Cache hygiene

The bundled Gradle cache lives in `$HOME/toolchain/gradle-home` and is shared
by every project (`GRADLE_USER_HOME`); the build script prints the `cache`
line so you can confirm it is not using `~/.gradle`. Dependencies are not the
usual bloat: project `build/` and `.gradle/` directories are. Use:

```sh
bash <skill-dir>/scripts/clean-caches.sh            # transient files only
bash <skill-dir>/scripts/clean-caches.sh --builds   # also workspace/*/build + .gradle
bash <skill-dir>/scripts/clean-caches.sh --all      # also shared dependency caches
```

Adia also prunes old temp files, Gradle daemon logs and downloaded `.deb`
files at startup. It never deletes project `build/` directories automatically.

## Prerequisites

- arm64 Android device (the toolchain is arm64 only)
- a Gradle project with a `gradlew` wrapper at its root
- network on the first build (Gradle distribution + Maven dependencies)
- at least ~4 GB free storage and ~2 GB free RAM

## Workflow

> `auto` already sends `bash <skill-dir>/scripts/build.sh ...` to proot. You can
> also be explicit: `bash <skill-dir>/scripts/build.sh /path debug`.
> The scripts start with `#!/data/data/com.termux/files/usr/bin/bash` and must
> not be executed natively from app-private storage. `oc-proot` preserves the
> caller's `$PWD`, so relative project paths work. `fast` is debug-only.

### 1. One-time toolchain setup

Run this once per app install (idempotent):

```sh
bash <skill-dir>/scripts/setup-toolchain.sh
```

If the APK was built with the offline bundle, this only verifies the JDK
(installed from the bundled Termux `.deb`) and the extracted SDK/Gradle tree
under `$HOME/toolchain`. No network is used.

For a source build without the bundle, the script falls back to installing
`openjdk-21` and downloading Android platform 36 plus arm64 `aapt2` (packaged as build-tools 36.0.0).

### 2. Build

```sh
bash <skill-dir>/scripts/build.sh /path/to/project debug
```

The second argument is `debug` or `release` (default `debug`). The script:

- sets `JAVA_HOME`, `ANDROID_HOME`, `ANDROID_SDK_ROOT`, `PATH`
- runs `sh ./gradlew --no-daemon --console=plain assembleDebug`
- passes `-Pandroid.aapt2FromMavenOverride=<arm64 aapt2>` so AGP does not try
  to download an x86_64 aapt2
- prints the resulting APK path as `APK=/absolute/path.apk`

Never execute `./gradlew` directly: project files live on a `noexec` mount.
`sh ./gradlew` reads the script and works.

### 3. Report

Tell the user the APK path and the build result. Do not invent a path: use the
`APK=` line printed by the script.

## Do not bypass the script

The agent shell can be **auto** (`oc-shell` picks) or **proot**. `PREFIX` is
only valid inside proot; in a native shell the real prefix is `$HOME/../usr`.
The script detects this itself. Never export `/data/data/com.termux/files/usr`
by hand in a native shell and never rebuild the Gradle command from scratch:
that path does not exist outside proot and produces the "tmpdir does not exist"
and "`PREFIX/tmp` cannot be created" failures. `oc-proot` now preserves the
caller's cwd, so `$PWD/project` is reliable.

If the script fails, fix the **project** and re-run it. Only use
`OC_USE_WRAPPER=1` or raw Gradle as a last resort, and keep
`GRADLE_USER_HOME=$HOME/toolchain/gradle-home` and
`-Djava.io.tmpdir=$HOME/.cache/tmp`.

## Troubleshooting

| Error | Fix |
|---|---|
| `checkDebugAarMetadata` / "AndroidX dependencies" | add `android.useAndroidX=true` to `gradle.properties`; add `android.enableJetifier=true` only for old support libraries |
| duplicate `kotlin-stdlib` / `kotlin-stdlib-jdk7` classes | force one stdlib version in `configurations.all { resolutionStrategy.force ... }` |
| `java.io.tmpdir` / `PREFIX/tmp` missing | do not create it by hand; the script uses `$HOME/.cache/tmp` |
| `Could not resolve` a library | use `android-lib-cache`; do not switch to a raw online Gradle run |
| `SDK location not found` | the script writes `local.properties`; do not point it at Termux's real prefix |
| `aapt2` download / x86_64 error | the script passes the bundled arm64 aapt2 override |

## Notes and limits

- SDK platform is 36 by default. For `compileSdk 37` projects, install platform
  37 and use a matching aapt2 (see `scripts/setup-toolchain.sh`).
- No NDK/CMake support in this version.
- The first build can take 10+ minutes and heat the phone; later builds are
  incremental.
- Builds default to `--offline` when the bundled Gradle distribution/cache is
  present. If a project needs a dependency not in the bundled cache, set
  `OC_BUILD_ONLINE=1`:
  `OC_BUILD_ONLINE=1 bash <skill-dir>/scripts/build.sh ...`
- To use the project's own wrapper instead of the bundled Gradle:
  `OC_USE_WRAPPER=1 bash <skill-dir>/scripts/build.sh ...`
- If Gradle runs out of memory, lower `org.gradle.jvmargs` in the project's
  `gradle.properties` to `-Xmx2g` or `-Xmx3g`.
- If AGP complains about the SDK, check `$ANDROID_HOME/platforms/android-36`
  contains `android.jar` and `source.properties`.
