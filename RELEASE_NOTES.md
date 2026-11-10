# Adia v0.3.1

Android Dev In Android —— 在手机上运行 opencode 和完整的 Android 构建工具链，离线优先。

## 本版重点

- **修复旧 WebView 打不开（Android 13 的「出了点问题」）**：opencode 的 Web UI
  用到 `URL.canParse`（Chromium 120）、`Promise.try`（128）、`Map.groupBy`（117）、
  `Array.prototype.toSorted`（110）等新 API。出厂后没更新过 Android System WebView
  的 Android 13 自带 Chromium 108，一进应用就抛
  `TypeError: URL.canParse is not a function`，整个界面起不来。
  现在 APK 在 SPA 之前注入两个脚本：
  - `oc-corejs.js`：core-js 3.51 的 24 个模块（31 KB），补齐上面这些 API；
  - `oc-early.js`：只留 `AbortSignal.timeout/any` 这类 core-js 不管的 DOM 补丁，
    外加原有的 `/provider` 缓存和目录选择器修正。
- **内核版本进日志**：启动时记一条 `webview Chrome/xxx`，再遇到「出了点问题」
  一看日志就知道是不是系统 WebView 太旧。
- **302 之后补注入并重载一次**：v2 首次登录走 `/auth/connect/<code>`，WebView
  不会对 302 的目标再走拦截器，脚本只能晚到。当检测到内核确实缺这些 API 时，
  用原认证头重载一次，让脚本进 `<head>` 后再跑 bundle；最多一次，不会循环。
  内核本来就新的设备不做重载，不会看到闪一下。
- **文档不再写死本机代理端口**：拉取二进制的示例改成占位符。

## 仍然保持

- 应用名 **Adia**，包名 `com.a2heng.adia`，opencode **2.2.6**。
- **v2 自动登录**：一次性 `/auth/connect/<code>` 建立会话，不手输密码。
- **离线工具链**：Termux JDK 21、Android platform 35、aarch64 aapt2、
  Gradle 9.8.1、Maven 种子缓存、设备内 adb。
- **22 个内置 skill** 与「无『所有文件访问』权限」的沙箱布局。

# Adia v0.3.0

Android Dev In Android —— 在手机上运行 opencode 和完整的 Android 构建工具链，离线优先。

## 本版重点

- **新增文件系统（悬浮菜单）**：悬浮按钮里可以直接浏览 `~`（应用私有目录）
  下的所有文件与文件夹。文件可以分享给其他应用，也可以「保存到手机」
  （系统文件选择器，存到下载/文档）；文件夹会先打包成 ZIP 再分享或保存，
  方便把一个项目或一整个目录发给别人。
- **悬浮菜单重做**：改成带图标和分组的下拉面板（Servidor / Archivos /
  Sistema），每项都有标题和一句说明；图标全部换成统一的矢量图标，不再用
  系统自带的杂项图标。
- **日志真正可用**：应用和 opencode 运行时的输出都写入
  `files/logs/adia.log`（512 KB 轮转），「Registro」可以查看、复制、分享、
  清空；不再依赖那个关掉应用就清空的文本框。
- **报告问题重做**：不再塞一大段看不懂的模板。现在只有一个「发生了什么」
  输入框和一个「附带设备信息与日志」开关，点「Abrir en GitHub」直接打开
  Adia 仓库的新 issue，内容干净、可读。
- **用量修复**：改用 v2 的 `/api/session`（带 Basic 认证），正确解析
  `{data:[...]}`，并显示缓存读取 token；删除会话也改用
  `DELETE /api/session/<id>`。
- **检查更新修复**：检查的是本仓库的 `v0.x` release（之前在看 v4/v5
  分支，永远查不到）。
- **移除 `/` 与 `~` 的路径劫持**：不再拦截 `fs/list` 改写路径。之前把
  `/`、`~` 和 workspace 外的路径强行映射到 `~/workspace`，反而让项目选择器
  出现新的异常；现在保持 opencode 原本的行为，只保留项目 git 初始化和
  sdcard 目录补全。

## 仍然保持

- 应用名 **Adia**，包名 `com.a2heng.adia`，opencode **2.2.6**。
- **v2 自动登录**：一次性 `/auth/connect/<code>` 建立会话，不手输密码。
- **离线工具链**：Termux JDK 21、Android platform 35、aarch64 aapt2、
  Gradle 9.8.1、Maven 种子缓存、设备内 adb。
- **22 个内置 skill**：构建、调试、项目、UI、测试、发布、安装、性能、
  NDK、依赖、共享库缓存、国际化、安全、Gradle、发布、架构、网络、数据库、
  后台、16 KB、Termux 工具链、skill-installer。
- **无「所有文件访问」权限**：workspace、toolchain、配置和 skills 全部位于
  `/data/user/0/com.a2heng.adia/files/home`；文件分享走 FileProvider，
  保存走系统文件选择器。

## 安装

```sh
adb install -r adia-0.3.0.apk
```

打开 **Adia**，等待离线 toolbox/toolchain 就绪，然后新建会话。
新项目建议放在 `~/workspace/<项目名>`；需要导出时点右下角悬浮按钮 →
**Archivos**。

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
- 文件夹导出会完整打包（包括 `.git`、`build` 等），大目录请耐心等待；
  压缩期间不要退出应用。
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

# Adia v0.3.0 — English

Android Dev In Android: run opencode and a full Android build toolchain on the
phone, offline-first.

## Highlights

- **New file system in the floating menu**: browse every file and folder under
  `~` (the app-private home). Files can be shared to other apps or saved to the
  phone through the system file picker (Downloads/Documents). Folders are
  zipped first, then shared or saved, so you can hand a whole project or
  directory to someone else.
- **Redesigned floating menu**: a grouped sheet with proper vector icons
  (Server / Files / System) and a short description per action, replacing the
  old system-icon popup.
- **The log actually works**: app and opencode runtime output go to
  `files/logs/adia.log` (512 KB rotation). "Log" can view, copy, share and
  clear it instead of the old in-memory text box.
- **Report a problem redesigned**: no more cryptic template. One "what
  happened" field, one "attach device info and log" switch, and
  "Open on GitHub" creates a clean, readable issue in the Adia repository.
- **Usage fixed**: uses the v2 `/api/session` endpoint with Basic auth, parses
  `{data:[...]}`, shows cache-read tokens, and deletes sessions through
  `DELETE /api/session/<id>`.
- **Update check fixed**: it now checks this repository's `v0.x` releases
  (it used to look at the v4/v5 channels and never found anything).
- **Removed the `/` and `~` path hijack**: `fs/list` is no longer intercepted
  to rewrite paths. Forcing `/`, `~` and out-of-workspace paths into
  `~/workspace` caused new picker bugs; upstream behaviour is kept, along with
  project git initialisation and sdcard folder completion.

## Kept from 0.2.0

- App name **Adia**, package `com.a2heng.adia`, opencode **2.2.6**.
- **Automatic v2 login** via a one-time `/auth/connect/<code>` link.
- **Offline toolchain**: Termux JDK 21, Android platform 35, aarch64 aapt2,
  Gradle 9.8.1, Maven seed cache and on-device adb.
- **22 bundled skills**, including a Termux toolbox guide.
- **No "All files access" permission**: everything lives in the app-private
  home; sharing uses FileProvider and saving uses the system file picker.

## Install

```sh
adb install -r adia-0.3.0.apk
```

Open **Adia**, wait for the offline toolbox/toolchain, then start a session.
Keep projects in `~/workspace/<name>`; to export, tap the floating button →
**Files**.

## Known limitations

- 16 KB is still a static alignment check; the test device is 4 KB
  (`ro.boot.hardware.cpu.pagesize=4096`). 16 KB is the official direction,
  not a claim that 4 KB is broken.
- Optional legacy `aapt`, `dexdump`, `split-select` and `zipalign` are still
  4 KB; AGP 8.5 does not run them.
- No full NDK/CMake bundle; see the `android-ndk` skill.
- Folder export packs everything (including `.git` and `build`); large folders
  take time. Keep the app open while zipping.
- Cannot be published on Google Play (executes/downloads code and sideloads).
