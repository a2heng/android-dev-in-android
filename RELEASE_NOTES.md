# Adia v0.2.0

Android Dev In Android —— 在手机上运行 opencode 和完整的 Android 构建工具链，离线优先。

## 本版重点

- **正式签名版**：Release 只构建 `assembleRelease`，CI 强制要求
  `ADIA_KEYSTORE_BASE64` / `ADIA_KEY_ALIAS` / `ADIA_KEYSTORE_PASS` /
  `ADIA_KEY_PASS`，不再回退到 debug 预览包。
- **项目路径选择器修复**：新建项目时点 `~` 或 `/` 不再进入无法读取的
  Android 根目录。两者都指向应用管理的规范项目根
  `~/workspace`，后续逐级浏览也都在这个目录内完成。
- **新 skill `android-lib-cache`**：未预装的库只下载一次，写入共享缓存
  `~/toolchain/gradle-home` 和 Maven 布局镜像 `~/toolchain/m2`；本机其他
  Gradle 项目、Maven、sbt、`javac`、脚本都能复用。
- **`android-build` 更稳**：自动识别 native/proot 下的 `PREFIX`，
  Gradle 临时目录固定到 `~/.cache/tmp`，不再手写 Termux 真实路径；
  AndroidX/Jetifier、Kotlin stdlib 冲突、AAR metadata 等常见错误已写入
  troubleshooting。
- **16 KB 检查修正**：`armeabi-v7a` / `x86` 等 32 位库标记为 `SKIP`，
  不再误报 `4KB/UNALIGNED`。只有 64 位库未对齐才会让 release 失败。
- **预装 16 KB 工具**：`aapt2`、Termux `aidl` 和 Termux `adb` 均为 16 KB
  对齐；`aapt`/`dexdump`/`split-select`/`zipalign` 是可选 legacy 工具，
  AGP 8.5 正常构建不会执行。16 KB 是官方建议，4 KB 设备仍可运行。
- **21 个内置 skill**：新增 `android-lib-cache`，并按手机上的真实构建会话
  补强 build、project、gradle、deps、16 KB 等 skill。

## 仍然保持

- 应用名 **Adia**，包名 `com.a2heng.adia`，opencode **2.2.6**。
- **v2 自动登录**：一次性 `/auth/connect/<code>` 建立会话，不手输密码。
- **离线工具链**：Termux JDK 21、Android platform 35、aarch64 aapt2、
  Gradle 9.8.1、Maven 种子缓存、设备内 adb。
- **16 KB 优先**：aapt2 为 16 KB 对齐；release 构建遇到 64 位 native 库
  4 KB 对齐会失败。
- **无“所有文件访问”权限**：workspace、toolchain、配置和 skills 全部位于
  `/data/user/0/com.a2heng.adia/files/home`。
- **中文原生 UI**，opencode Web UI 支持中文。

## 安装

```sh
adb install -r adia-0.2.0.apk
```

打开 **Adia**，等待离线 toolbox/toolchain 就绪，然后新建会话。
新项目建议放在 `~/workspace/<项目名>`。

## 已知限制

- 16 KB 仍是静态对齐检查；测试机为 4 KB 内核
  （`ro.boot.hardware.cpu.pagesize=4096`），尚无 16 KB 内核可做硬件验证。
  16 KB 是官方建议，不是 4 KB 不可用。
- 可选 legacy `aapt`、`dexdump`、`split-select`、`zipalign` 仍是 4 KB；
  AGP 8.5 正常构建不会执行它们。关键工具 `aapt2`、`aidl`、`adb` 已是
  16 KB。
- 没有完整 NDK/CMake bundle；见 `android-ndk` skill。
- 离线 Maven 种子缓存来自本项目，不是通用镜像；缺失库用
  `android-lib-cache` 下载并共享。
- 由于执行/下载代码和侧载能力，不能上 Google Play。

## 致谢与许可

本项目基于 MIT 许可的
[Dajeovv/opencode-android](https://github.com/Dajeovv/opencode-android)。
感谢作者提供 opencode-in-APK 的原生架构、proot toolbox 和 WebView launcher。

同时感谢以下参考项目：

- [anomalyco/opencode](https://github.com/anomalyco/opencode)（MIT）
- [appdevforall/CodeOnTheGo](https://github.com/appdevforall/CodeOnTheGo)（GPL-3.0）
- [AndroidIDEOfficial/AndroidIDE](https://github.com/AndroidIDEOfficial/AndroidIDE)（GPL-3.0）
- [termux/termux-app](https://github.com/termux/termux-app)（GPL-3.0；terminal-view/emulator 为 Apache-2.0）
- [termux/proot-distro](https://github.com/termux/proot-distro)（GPL-3.0）
- [CrimsonCurious/vexel](https://github.com/CrimsonCurious/vexel)（MIT）
- [poordevcode/termux-android-studio](https://github.com/poordevcode/termux-android-studio)（Apache-2.0）
- [Thr45hx/opencode-termux-native](https://github.com/Thr45hx/opencode-termux-native)（MIT）
- [Zar-Manah/opencode-android-apk](https://github.com/Zar-Manah/opencode-android-apk)（GPL-3.0）
- [lzhiyong/android-sdk-tools](https://github.com/lzhiyong/android-sdk-tools)（Apache-2.0）

随包分发的二进制及其许可见 [`THIRD_PARTY_LICENSES.md`](THIRD_PARTY_LICENSES.md)。

---

# Adia v0.2.0 — English

Android Dev In Android: run opencode and a full Android build toolchain on the
phone, offline-first.

## Highlights

- **Signed release only**: CI builds `assembleRelease` and requires
  `ADIA_KEYSTORE_BASE64`, `ADIA_KEY_ALIAS`, `ADIA_KEYSTORE_PASS` and
  `ADIA_KEY_PASS`. There is no debug-preview fallback.
- **Project picker fixed**: `~` and `/` no longer open the unreadable Android
  root. Both resolve to the app-managed project root `~/workspace`, and every
  deeper folder stays inside it.
- **New `android-lib-cache` skill**: a missing library is downloaded once into
  the shared `~/toolchain/gradle-home` cache and a Maven-layout mirror at
  `~/toolchain/m2`. Other Gradle projects, Maven, sbt, `javac` and scripts on
  the phone reuse the same files.
- **More reliable `android-build`**: detects `PREFIX` in native and proot
  shells, pins Gradle's temp dir to `~/.cache/tmp`, and documents AndroidX/
  Jetifier, Kotlin stdlib conflicts and AAR metadata errors.
- **16 KB checker fixed**: 32-bit ABIs (`armeabi-v7a`, `x86`) are reported as
  `SKIP`, not `4KB/UNALIGNED`. Only misaligned 64-bit libraries fail a release.
- **16 KB tools preinstalled**: `aapt2`, Termux `aidl` and Termux `adb` are
  16 KB aligned. `aapt`/`dexdump`/`split-select`/`zipalign` are optional legacy
  tools that AGP 8.5 does not execute. 16 KB is the official recommendation;
  4 KB devices still run.
- **21 bundled skills**: `android-lib-cache` added; build, project, gradle,
  deps and 16 KB skills improved from a real on-device build session.

## Kept from 0.1.0

- App name **Adia**, package `com.a2heng.adia`, opencode **2.2.6**.
- **Automatic v2 login** via a one-time `/auth/connect/<code>` link.
- **Offline toolchain**: Termux JDK 21, Android platform 35, aarch64 aapt2,
  Gradle 9.8.1, Maven seed cache and on-device adb.
- **16 KB first**: 16 KB-aligned aapt2; release builds fail on misaligned
  64-bit native libraries.
- **No `MANAGE_EXTERNAL_STORAGE`**: workspace, toolchain, config and skills
  live under `/data/user/0/com.a2heng.adia/files/home`.
- **Chinese native UI** plus the opencode Web UI.

## Install

```sh
adb install -r adia-0.2.0.apk
```

Open **Adia**, wait for the offline toolbox/toolchain, then start a session.
Put new projects under `~/workspace/<name>`.

## Known limitations

- The 16 KB check is static alignment; the test device has a 4 KB kernel
  (`ro.boot.hardware.cpu.pagesize=4096`), so no 16 KB kernel test is available.
  16 KB is the official recommendation; 4 KB devices still work.
- Optional legacy `aapt`, `dexdump`, `split-select` and `zipalign` are still
  4 KB; AGP 8.5 does not execute them. The critical `aapt2`, `aidl` and `adb`
  are 16 KB.
- No full NDK/CMake bundle; see the `android-ndk` skill.
- The offline Maven seed is not a universal mirror; use `android-lib-cache`
  for missing libraries.
- Not eligible for Google Play due to executing/downloading code and sideload
  capabilities.

## Credits and licenses

Built on the MIT-licensed
[Dajeovv/opencode-android](https://github.com/Dajeovv/opencode-android).
Thanks to its author for the native opencode-in-APK architecture, the proot
toolbox and the WebView launcher.

Thanks also to:

- [anomalyco/opencode](https://github.com/anomalyco/opencode) (MIT)
- [appdevforall/CodeOnTheGo](https://github.com/appdevforall/CodeOnTheGo) (GPL-3.0)
- [AndroidIDEOfficial/AndroidIDE](https://github.com/AndroidIDEOfficial/AndroidIDE) (GPL-3.0)
- [termux/termux-app](https://github.com/termux/termux-app) (GPL-3.0; terminal-view/emulator Apache-2.0)
- [termux/proot-distro](https://github.com/termux/proot-distro) (GPL-3.0)
- [CrimsonCurious/vexel](https://github.com/CrimsonCurious/vexel) (MIT)
- [poordevcode/termux-android-studio](https://github.com/poordevcode/termux-android-studio) (Apache-2.0)
- [Thr45hx/opencode-termux-native](https://github.com/Thr45hx/opencode-termux-native) (MIT)
- [Zar-Manah/opencode-android-apk](https://github.com/Zar-Manah/opencode-android-apk) (GPL-3.0)
- [lzhiyong/android-sdk-tools](https://github.com/lzhiyong/android-sdk-tools) (Apache-2.0)

See [`THIRD_PARTY_LICENSES.md`](THIRD_PARTY_LICENSES.md) for bundled binaries
and their licenses.
