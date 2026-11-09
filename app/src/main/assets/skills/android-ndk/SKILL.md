---
name: Android NDK
description: Add or debug native C/C++ code in an Android project (CMake/NDK), ABI filters and 16 KB page-size alignment. Use only when a project really needs native code.
---

# Android NDK

## Check what is installed

The offline bundle does **not** include a full NDK. Check:

```sh
ls "$HOME/toolchain/android-sdk/ndk" 2>/dev/null || echo "no NDK"
```

If the project needs native code, the NDK/CMake must be installed separately
(for example with Termux Studio's `studio ndk install`, or a 16 KB-aligned NDK
package). Do not claim NDK support if it is absent.

## Gradle setup

```groovy
android {
    ndkVersion "27.2.12479018"
    defaultConfig {
        ndk { abiFilters 'arm64-v8a' }
    }
    externalNativeBuild {
        cmake { path file('src/main/cpp/CMakeLists.txt') }
    }
}
```

`CMakeLists.txt`:

```cmake
cmake_minimum_required(VERSION 3.22)
project(native-lib C)
add_library(native-lib SHARED native-lib.c)
target_link_libraries(native-lib log)
```

## 16 KB page size

Android 15+ devices may use 16 KB pages. Build native libraries with:

```cmake
target_link_options(native-lib PRIVATE "-Wl,-z,max-page-size=16384")
```

or in `build.gradle`:

```groovy
android { defaultConfig { externalNativeBuild { cmake { arguments "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON" } } } }
```

Verify with `scripts/check-elf-align.py`; all `PT_LOAD` segments must be
`0x4000` aligned.

## Debug native crashes

- `logcat` shows `DEBUG`, `libc`, `signal 11 (SIGSEGV)`, `backtrace`.
- `adb shell debuggerd -b <pid>` may work with adb/wireless debugging.
- Keep `libc++_shared.so` packaging consistent; do not mix STL variants.
- Use `arm64-v8a` only; the app and toolchain are arm64.

## Rules

- Native code is the last resort; prefer Java/Kotlin.
- Do not enable `abiFilters` for ABIs you cannot test.
- Rebuild and reinstall after every native change; Gradle caching can hide stale
  `.so` files.
