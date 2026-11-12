# Adia

**Android Dev In Android** — 在 Android 手机上直接开发 Android，并在同一个 APK
内启动 [opencode](https://github.com/anomalyco/opencode) 作为 AI 开发代理。

- 应用名：**Adia**
- 包名：`com.a2heng.adia`
- opencode：**2.2.7**（musl arm64，原生运行）
- 目标：把「opencode + JDK + Android SDK + Gradle + 调试工具」全部塞进一个可离线
  使用的 APK，不依赖电脑、不依赖 Termux 外部应用、首次启动不下载工具链。

## 已验证（2026-11-09）

- 设备：OPPO PKB110，Android 17，arm64，4 KB 页大小，158 GB 可用存储。
- 完整离线 APK 安装成功；首次启动本地安装 toolbox + 解压 toolchain，没有下载工具链。
- 手机内用内置 JDK 21 + Gradle 9.9.0 + SDK 36 + aapt2 构建 Hello World：
  `BUILD SUCCESSFUL in 26s`（增量），产物安装后正常启动。
- 使用免费模型 `opencode/deepseek-v4.1-flash-free`，让手机内的 opencode 自己生成
  并构建 `AgentHello`，安装后显示 “Hello from opencode on-device”。
- 内置技能 22 个：构建、调试、项目、UI、测试、发布、安装、性能、NDK、依赖、
  共享库缓存、国际化、安全、Gradle、发布、架构、网络、数据库、后台、16 KB、
  Termux 工具链、skill-installer。

## 路径与命令（先读这两篇）

Termux 的 ELF 把 `/data/data/com.termux/files/usr/lib` 编译进了
`DT_RUNPATH`；只有 proot 沙箱里这个路径才存在。Adia 的命令分派和前缀
自愈就是围绕这一条设计的：

- **[`docs/paths.md`](docs/paths.md)** — 所有真实路径、沙箱路径、bind 表、
  必需文件、自愈流程、`libreadline.so.8` 的根因与排查。
- **[`docs/commands.md`](docs/commands.md)** — 所有 `oc-*` 命令的全名与用法、
  **`auto` = 整条命令无条件进 proot**（`fast` 仅 debug）、命令↔路径矩阵、
  原理解释表、系统命令包装、迭代检查清单。

日常自检：

```sh
oc-engine status   # auto / proot（fast 仅 debug）
oc-doctor          # 路径、必需文件、proot 实测
oc-repair          # 缺 bash/readline 时离线补齐
```

## 16 KB 页大小（官方建议，不是 4 KB 不可用）

16 KB 对齐是 Google 对新设备和 Play 新应用的**建议/要求方向**，不是“4 KB
设备不能运行”。Adia 自身仍可在 4 KB 内核上正常构建和运行；这里做的是提前
兼容未来的 16 KB 设备，并让 release 构建给出明确报告。

已检查当前包：

- opencode、musl loader、proot、libc++/libstdc++/libgcc、librg、
  libandroid-shmem：**16 KB aligned**
- Termux 的 `libjvm.so`、`libjava.so`、`bash`、`git`、`node`、`python3`：
  **16 KB aligned**
- 关键构建工具：`aapt2` 为 16 KB；`aidl` 和 `adb` 预装 Termux 的
  aarch64 版本，已复检为 **16 KB aligned**
- 可选 legacy 工具：`aapt`、`dexdump`、`split-select`、`zipalign` 仍是
  4 KB。AGP 8.5 正常构建不会执行它们（使用 aapt2 / zipflinger），只有在
  16 KB 设备上手动使用这些工具时才需要从 AOSP 重新编译。

release 构建默认对**随包分发的 64 位 native 库**做严格检查；32 位
（`armeabi-v7a`/`x86`）会标记为 `SKIP`，不参与 16 KB 判定。遇到无法替换的
4 KB 依赖时，可以设置 `ADIA_16KB_STRICT=0` 继续构建，并在发布说明中记录。
这只是提前兼容 16 KB 设备；4 KB 设备仍然可以正常构建和运行。

当前测试机是 4 KB 页（`getconf PAGE_SIZE` = 4096），所以 16 KB 仍是静态
对齐检查；建议后续用 16 KB 页的 Android 15/16 真机做一次完整构建。

检查命令：

```sh
python3 app/src/main/assets/skills/android-build/scripts/check-16kb.py \
  app/src/main/jniLibs/arm64-v8a
```

## 与 OpenCode 安卓 UI 的关系

Adia 内置自己的 Compose 前端副本（`client/`），**不再使用 git submodule**：

- `client/` 是 Adia 仓库内的独立模块，直接 `include(":client")`，构建不需要初始化任何子模块。
- 独立的局域网客户端已经改名为 **OpenCode 安卓 UI**：
  [`a2heng/opencode-android-ui`](https://github.com/a2heng/opencode-android-ui)。
- 两边只是共享过一套界面代码，之后各自演进；改 Adia 不会再碰 mobile 仓库，反之亦然。
- **服务端进程**模式仍是 `auto / native / proot`（这是 opencode 服务器本身，
  不是命令引擎；命令引擎见 `oc-engine`），编译期可用 `-PadiaRuntime=proot`
  固定成 Termux 风格的 proot 沙箱。

细节见 [`docs/mobile-integration.md`](docs/mobile-integration.md)。

## 现在能做什么

- **opencode 本地运行**：官方 `linux-arm64-musl` 单文件作为 native library 随 APK
  分发，WebView 打开本地 Web UI。不需要 Termux、不需要 root。
- **文件分享（0.3.0）**：悬浮菜单 → **Archivos** 浏览 `~` 下的文件和文件夹；
  文件可分享给其他应用或保存到手机，文件夹会先打成 ZIP 再分享/保存。
- **默认 Home 项目**：打开就是应用私有 HOME，不预置示例项目，也不会自动
  创建 git 仓库；需要 Android 项目时让 agent 用 `android-project` skill 生成。
- **端侧构建 Android**：Termux 的 `openjdk-21` + Android platform 36 +
  aarch64 `aapt2` + Gradle，直接在手机上执行 `assembleDebug`。
- **离线优先**：工具链、JDK 的 `.deb`、Gradle 发行版和常用 Maven 缓存都可以打进
  APK；首次启动只做本地解压/安装。
- **opencode skill**：`android-build` skill 告诉 agent 如何初始化工具链、构建项目、
  找到产物 APK。
- **可扩展**：保留上游的 Termux 风格 toolbox、`pkg/apt`、设备 API（剪贴板、Toast、
  振动、Wi-Fi 信息等）。

## 存储与清理

- APK 约 742 MB，其中 `toolchain.zip`（326 MB）和 toolbox 的 `.deb`（279 MB）
  本身已经是压缩格式，再压收益很小。首次启动会把它们展开成工作副本：
  `~/toolchain` 约 443 MB（清理掉误建的 `.git` 后）、`files/usr` 约 1.1 GB。
  APK 里没有第二份 zip 副本；`ToolchainInstaller` 直接解到 `toolchain.tmp`，
  成功后改名并写 `.installed`，不会重复解压。
- **Gradle 共享缓存**：`~/toolchain/gradle-home` 就是 `GRADLE_USER_HOME`，
  所有项目共用；不会用到 `~/.gradle`。`~/toolchain/m2` 只在运行
  `export-gradle.sh` / `fetch-maven.sh` 后才会有内容，`~/.m2` 也只是
  `link-m2.sh` 的可选软链。
- **主要膨胀来源**是每个项目的 `build/` 和 `.gradle/`，以及旧的 Gradle
  daemon 日志。用内置脚本清理：

  ```sh
  bash ~/.config/opencode/skills/android-build/scripts/clean-caches.sh
  bash ~/.config/opencode/skills/android-build/scripts/clean-caches.sh --builds
  bash ~/.config/opencode/skills/android-build/scripts/clean-caches.sh --all
  ```

  不带参数只删临时文件；`--builds` 再删 `~/workspace/*/build` 和 `.gradle`；
  `--all` 连共享依赖缓存一起删（下次构建重新下载）。Adia 启动时也会自动
  清理旧的临时文件、Gradle daemon 日志和已下载的 `.deb`。

## 目录结构

```text
.
├─ app/                       # Android 应用（Java + WebView）
│  └─ src/main/
│     ├─ assets/skills/       # opencode skills（android-build）
│     ├─ assets/toolbox/      # 离线 Termux .deb（构建产物，gitignored）
│     ├─ assets/toolchain/    # 离线 SDK/Gradle/cache zip（构建产物，gitignored）
│     └─ jniLibs/             # opencode/proot/musl 等预编译库
├─ docs/                      # paths.md / commands.md 必读，另有调研与构建说明
├─ reference/                 # 只读参考项目（git submodules）
├─ scripts/                   # 离线包生成脚本
├─ dist/                      # 本地构建的 APK（gitignored）
├─ .gitmodules
├─ THIRD_PARTY_LICENSES.md
└─ LICENSE                    # MIT
```

## 根目录布局（全部在 Android 应用权限内）

运行时根目录：

```text
HOME = /data/data/com.a2heng.adia/files/home   # canonical，日志里不会是 /data/user/0
├─ workspace/                 # 所有用户项目；opencode 的 cwd
├─ toolchain/                 # SDK、aapt2、Gradle、Maven 缓存、adb（JDK 在 $PREFIX）
│  ├─ android-sdk/
│  ├─ gradle/
│  ├─ gradle-home/
│  └─ platform-tools/adb
├─ .config/opencode/          # opencode 配置、skills、tools
├─ .config/opencode-android/  # 服务端配置、engine-mode、skills 副本
├─ .native-bin/git            # 指向 nativeLibraryDir/libgit.so 的跳板
├─ .cache/                    # 临时文件（TMPDIR 也指向这里）
└─ .oc-etc/                   # hosts / resolv.conf（bind 到 proot /etc）

PREFIX（真实） = /data/data/com.a2heng.adia/files/usr
PREFIX（沙箱） = /data/data/com.termux/files/usr   # proot bind
```

完整表格（包括 `$TMPDIR`、XDG、nativeLibraryDir、toolbox 工作区）见
[`docs/paths.md`](docs/paths.md)。

- 不使用 `MANAGE_EXTERNAL_STORAGE`，不读写 `/sdcard`。
- opencode 以 `workspace/` 为 cwd；项目都放在这里。
- 工具链固定在 `toolchain/`，agent 和构建脚本使用同一路径。
- 外部项目通过系统文件选择器导入到 `workspace/`（SAF），或先 `adb push` 到应用私有目录。

## 快速开始

### 直接安装

从 `dist/` 或 GitHub Releases 取 debug/release APK：

```sh
adb install -r dist/app-debug-offline.apk
```

首次启动后：

1. 等 toolbox（Termux 包）安装完成；
2. 等 `$HOME/toolchain` 解压完成（APK 内置离线包时）；
3. 在 opencode 里执行：

```sh
bash ~/.config/opencode/skills/android-build/scripts/setup-toolchain.sh
bash ~/.config/opencode/skills/android-build/scripts/build.sh /path/to/project debug
```

### 从源码构建

需要 JDK 17（CI 固定）或 JDK 21（`D:\AndroidDevToolchain`），Android SDK
Platform 37.0 / 36，Build-Tools 36.0.0。

```sh
# 1) 补 opencode 运行时（仓库不提交 208 MB 的二进制）
curl -L -o /tmp/oc.tar.gz \
  https://github.com/anomalyco/opencode/releases/download/v2.2.7/opencode-linux-arm64-musl.tar.gz
tar -xzf /tmp/oc.tar.gz -C /tmp
cp /tmp/opencode app/src/main/jniLibs/arm64-v8a/libopencode2_bin.so
chmod 755 app/src/main/jniLibs/arm64-v8a/libopencode2_bin.so

# 2) 构建
./gradlew :app:assembleDebug
```

这只是「瘦 APK」；要得到内置工具链的大 APK，继续看下一节。

## 生成完整离线 APK

离线包由 `scripts/` 生成（脚本会下载 Termux 包、Android platform、aapt2、
Gradle 和 Maven 缓存，然后放到 `app/src/main/assets/`）：

```sh
python scripts/fetch-opencode.py
python scripts/fetch-toolbox.py
python scripts/verify-toolbox.py   # 必须通过：bash/readline/符号链接/DT_NEEDED
python scripts/make-toolchain.py

./gradlew :app:assembleDebug
```

产物会包含：

- `assets/toolbox/*.deb`：Termux 工具链 + `openjdk-21` 及其依赖
- `assets/toolchain/toolchain.zip`：Android platform 36、aapt2、Gradle 9.9.0、
  Gradle 依赖缓存
- `lib/arm64-v8a/libopencode2_bin.so`：opencode musl 运行时

APK 会比较大（当前约 560 MB），这是刻意为之：换取完全离线、开箱即用。

## 参考项目与致谢

本项目的底座是 [Dajeovv/opencode-android](https://github.com/Dajeovv/opencode-android)
（MIT）。感谢作者把「opencode 原生跑在 Android APK 里」这条路走通。

本项目基于其 MIT 代码继续开发，但仓库是**独立的初始提交**，不保留 fork 历史，
也不会向上游提交 PR；上游作为参考/来源记录保留在 `reference/`。

其他参考项目以 **git submodule** 的形式放在 `reference/`，只读、不参与构建：

| 项目 | 许可 | 参考点 |
|---|---|---|
| [Dajeovv/opencode-android](https://github.com/Dajeovv/opencode-android) | MIT | 底座：musl opencode、proot toolbox、WebView |
| [anomalyco/opencode](https://github.com/anomalyco/opencode) | MIT | opencode 本体与 HTTP API |
| [appdevforall/CodeOnTheGo](https://github.com/appdevforall/CodeOnTheGo) | GPL-3.0 | 端侧 Gradle/Kotlin IDE 的成熟实现 |
| [AndroidIDEOfficial/AndroidIDE](https://github.com/AndroidIDEOfficial/AndroidIDE) | GPL-3.0 | 初代端侧 Gradle IDE |
| [termux/termux-app](https://github.com/termux/termux-app) | GPL-3.0* | 终端、bootstrap、包管理 |
| [termux/proot-distro](https://github.com/termux/proot-distro) | GPL-3.0 | 用户态 rootfs / 容器参考 |
| [CrimsonCurious/vexel](https://github.com/CrimsonCurious/vexel) | MIT | 无 Gradle 的轻量 APK builder |
| [poordevcode/termux-android-studio](https://github.com/poordevcode/termux-android-studio) | Apache-2.0 | aarch64 aapt2、Gradle-on-Termux 脚本 |
| [Thr45hx/opencode-termux-native](https://github.com/Thr45hx/opencode-termux-native) | MIT | glibc/patchelf 原生运行配方 |
| [Zar-Manah/opencode-android-apk](https://github.com/Zar-Manah/opencode-android-apk) | GPL-3.0 | 最接近的成品：opencode + JDK + SDK + Gradle |
| [lzhiyong/android-sdk-tools](https://github.com/lzhiyong/android-sdk-tools) | Apache-2.0 | aarch64 build-tools（aapt2） |

\* `termux-app` 主体 GPLv3，`terminal-view` / `terminal-emulator` 为 Apache-2.0
例外；详见其 `LICENSE.md`。

初始化参考项目（按需，浅克隆）：

```sh
git submodule update --init --depth 1 reference/opencode-android
git submodule update --init --depth 1 reference/vexel
```

**许可边界**：我们自己的代码是 MIT。GPL 项目只以 submodule 形式存在，不复制进
主源码树。预编译并随 APK 分发的 Termux 包、proot、JDK 等各自保留原许可，详见
[`THIRD_PARTY_LICENSES.md`](THIRD_PARTY_LICENSES.md)。

## 分发限制

- **不能上 Google Play**：应用需要执行下载/解压的代码、使用
  `MANAGE_EXTERNAL_STORAGE` 和 `REQUEST_INSTALL_PACKAGES`，且部分工具链依赖
  `targetSdk` 兼容行为。
- 建议通过 GitHub Releases / F-Droid 自建仓库 / 直接 APK 侧载分发。
- 安装前请自行审查代码；这是一个可以读写项目文件并执行命令的开发工具。

## 许可

本项目代码：MIT，见 [`LICENSE`](LICENSE)。
第三方组件、完整许可证文本和 GPL 源码获取方式：见
[`THIRD_PARTY_LICENSES.md`](THIRD_PARTY_LICENSES.md)、[`NOTICE`](NOTICE) 和
[`LICENSES/`](LICENSES/)。

opencode 是 Anomaly 的项目；本项目与 opencode 官方无隶属关系。
