# Adia v0.1.0（预览版）

Android Dev In Android —— 在手机上运行 opencode 和完整的 Android 构建工具链，离线优先。

## 亮点

- 应用名 **Adia**，包名 `com.a2heng.adia`。
- **opencode 2.2.6**（linux-arm64-musl）直接运行在 APK 内。
- **v2 自动登录**：通过一次性 `/auth/connect/<code>` 链接建立会话，不再手动输入密码。
- **离线工具链**：Termux JDK 21、Android platform 35、aarch64 aapt2、Gradle 9.8.1、
  Maven 缓存、设备内 adb。
- **20 个内置 skill**：build、debug、project、UI、test、release、install、
  performance、NDK、dependencies、i18n、security、Gradle、publish、
  architecture、networking、database、background、16 KB、skill-installer。
- **skill 可自行安装**：支持 git / zip / 本地目录。
- **16 KB 优先**：aapt2 已换成 16 KB 对齐版本；构建后自动审计 APK，release
  构建遇到 4 KB native 库会失败。
- **不再申请“所有文件访问”**：workspace、toolchain、配置和 skills 全部位于
  应用私有根目录 `/data/user/0/com.a2heng.adia/files/home`。
- **原生 UI 汉化**：选项、状态、分享、通知均为中文；opencode Web UI 也支持中文。

## 真机验证

- OPPO PKB110，Android 17（SDK 37），arm64，4 KB 内核。
- 全新安装 → 本地解压 toolbox + toolchain，不下载工具链。
- 手机内构建 Hello World：`BUILD SUCCESSFUL`（增量约 26 秒）。
- 免费模型 `opencode/deepseek-v4.1-flash-free` 在手机内生成并构建应用，
  产物安装后正常启动。
- 对 app、opencode、proot、JDK、Termux 工具和 aapt2 做了 16 KB 静态对齐检查。
  测试机是 4 KB 内核（`ro.boot.hardware.cpu.pagesize=4096`），暂无 16 KB 内核可测。

## 已知限制

- 本预览版 APK 使用 **debug 签名**，不要用于正式升级。
- 16 KB 目前是静态检查；正式声明前需要在 16 KB 内核/设备上验证。
- `aidl`、legacy `aapt`、`dexdump`、`split-select`、`zipalign` 和捆绑的静态
  `adb` 仍是 4 KB 对齐；它们是可选工具。release 构建只在**随包分发的 native
  库**为 4 KB 时失败。
- 没有完整 NDK/CMake bundle；见 `android-ndk` skill。
- 离线 Maven 缓存来自本项目，不是通用镜像。
- 外部项目目前通过应用私有目录导入；SAF 导入/导出是下一步。
- 由于执行/下载代码和侧载能力，不能上 Google Play。

## 安装

```sh
adb install -r adia-debug.apk
```

打开 **Adia**，等待离线 toolbox/toolchain 安装完成，然后新建会话。

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

# Adia v0.1.0 (preview) — English

Android Dev In Android: run opencode and a full Android build toolchain on the
phone, offline-first.

## Highlights

- App name **Adia**, package `com.a2heng.adia`.
- **opencode 2.2.6** (`linux-arm64-musl`) running natively inside the APK.
- **Automatic v2 login** via a one-time `/auth/connect/<code>` link; no manual
  password entry.
- **Offline toolchain**: Termux JDK 21, Android platform 35, aarch64 aapt2,
  Gradle 9.8.1, Maven cache and on-device adb.
- **20 bundled skills**: build, debug, project, UI, test, release, install,
  performance, NDK, dependencies, i18n, security, Gradle, publish,
  architecture, networking, database, background, 16 KB, skill-installer.
- **Self-installable skills** from git, zip or a local directory.
- **16 KB first**: the bundled aapt2 is 16 KB aligned; the build skill audits
  the APK and fails release builds on 4 KB native libraries.
- **No `MANAGE_EXTERNAL_STORAGE`**: workspace, toolchain, config and skills live
  under the app-private root
  `/data/user/0/com.a2heng.adia/files/home`.
- **Chinese native UI** plus the opencode Web UI.

## Verified on-device

- OPPO PKB110, Android 17 (SDK 37), arm64, 4 KB kernel.
- Fresh install → offline toolbox + toolchain extraction (no downloads).
- On-device Hello World build: `BUILD SUCCESSFUL` (~26 s incremental).
- The free model `opencode/deepseek-v4.1-flash-free` generated and built an app
  on the phone; the APK installed and launched.
- 16 KB static alignment checked for the app, opencode, proot, JDK, Termux tools
  and aapt2. The test device has a 4 KB kernel
  (`ro.boot.hardware.cpu.pagesize=4096`), so no 16 KB kernel test is available.

## Known limitations

- This preview APK is **debug-signed**. Do not use it for production updates.
- The 16 KB check is static on this device; verify on a 16 KB kernel/device
  before claiming full hardware support.
- `aidl`, legacy `aapt`, `dexdump`, `split-select`, `zipalign` and the bundled
  static `adb` are still 4 KB aligned. They are optional; release builds fail
  only if a **shipped** native library is 4 KB.
- No full NDK/CMake bundle; see the `android-ndk` skill.
- The offline Maven cache is seeded from this project, not a universal mirror.
- External projects are imported into app-private storage; SAF import/export is
  the next step.
- Not eligible for Google Play due to executing/downloading code and sideload
  capabilities.

## Install

```sh
adb install -r adia-debug.apk
```

Open **Adia**, wait for the offline toolbox/toolchain, then start a session.

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
