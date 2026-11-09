# Adia

**Android Dev In Android** — 在 Android 手机上直接开发 Android，并在同一个 APK
内启动 [opencode](https://github.com/anomalyco/opencode) 作为 AI 开发代理。

- 应用名：**Adia**
- 包名：`com.a2heng.adia`
- opencode：**2.2.6**（musl arm64，原生运行）
- 目标：把「opencode + JDK + Android SDK + Gradle + 调试工具」全部塞进一个可离线
  使用的 APK，不依赖电脑、不依赖 Termux 外部应用、首次启动不下载工具链。

## 已验证（2026-11-09）

- 设备：OPPO PKB110，Android 17，arm64，4 KB 页大小，158 GB 可用存储。
- 完整离线 APK 安装成功；首次启动本地安装 toolbox + 解压 toolchain，没有下载工具链。
- 手机内用内置 JDK 21 + Gradle 9.8.1 + SDK 35 + aapt2 构建 Hello World：
  `BUILD SUCCESSFUL in 26s`（增量），产物安装后正常启动。
- 使用免费模型 `opencode/deepseek-v4.1-flash-free`，让手机内的 opencode 自己生成
  并构建 `AgentHello`，安装后显示 “Hello from opencode on-device”。
- 内置技能：`android-build`、`android-debug`、`skill-installer`，以及项目/UI/测试/
  发布/性能/NDK/依赖/国际化/安全/Gradle/发布/架构/网络/数据库/后台等 19 个 skill。

## 16 KB 页大小

已用 `scripts/check-elf-align.py` 检查过当前包：

- opencode、musl loader、proot、libc++/libstdc++/libgcc、librg、
  libandroid-shmem：**16 KB aligned**
- Termux 的 `libjvm.so`、`libjava.so`、`bash`、`git`、`node`、`python3`：
  **16 KB aligned**
- 原 `lzhiyong` 的 `aapt2` / `aidl`：4 KB aligned

已把 `aapt2` 换成 Termux Studio 的 16 KB 对齐版本（支持 SDK 35/36/37），
重新打包后手机内构建仍成功。`aidl` 仍是 4 KB；只有使用 AIDL 的项目会受影响。

当前测试机是 4 KB 页（`getconf PAGE_SIZE` = 4096），所以还没有 16 KB 真机验证。
建议下一步用 16 KB 页的 Android 15/16 模拟器或真机做一次完整构建。

检查命令：

```sh
python scripts/check-elf-align.py app/src/main/jniLibs/arm64-v8a/*.so
```

## 现在能做什么

- **opencode 本地运行**：官方 `linux-arm64-musl` 单文件作为 native library 随 APK
  分发，WebView 打开本地 Web UI。不需要 Termux、不需要 root。
- **端侧构建 Android**：Termux 的 `openjdk-21` + Android platform 35 +
  aarch64 `aapt2` + Gradle，直接在手机上执行 `assembleDebug`。
- **离线优先**：工具链、JDK 的 `.deb`、Gradle 发行版和常用 Maven 缓存都可以打进
  APK；首次启动只做本地解压/安装。
- **opencode skill**：`android-build` skill 告诉 agent 如何初始化工具链、构建项目、
  找到产物 APK。
- **可扩展**：保留上游的 Termux 风格 toolbox、`pkg/apt`、设备 API（剪贴板、Toast、
  振动、Wi-Fi 信息等）。

## 目录结构

```text
.
├─ app/                       # Android 应用（Java + WebView）
│  └─ src/main/
│     ├─ assets/skills/       # opencode skills（android-build）
│     ├─ assets/toolbox/      # 离线 Termux .deb（构建产物，gitignored）
│     ├─ assets/toolchain/    # 离线 SDK/Gradle/cache zip（构建产物，gitignored）
│     └─ jniLibs/             # opencode/proot/musl 等预编译库
├─ docs/                      # 调研、实施记录、构建说明
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
ADIA_HOME = /data/user/0/com.a2heng.adia/files/home
├─ workspace/                 # 所有用户项目；opencode 的 cwd
├─ toolchain/                 # JDK、SDK 35、aapt2、Gradle、Maven 缓存、adb
│  ├─ android-sdk/
│  ├─ gradle/
│  ├─ gradle-home/
│  └─ platform-tools/adb
├─ .config/opencode/          # opencode 配置、skills、tools
├─ .config/opencode-android/  # 原生服务端配置 + skills 副本
└─ .cache/                    # 临时文件（java.io.tmpdir 也指向这里）
```

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

需要 JDK 17、Android SDK Platform 34、Build-Tools 34。

```sh
# 1) 补 opencode 运行时（仓库不提交 208 MB 的二进制）
curl -L -o /tmp/oc.tar.gz \
  https://github.com/anomalyco/opencode/releases/download/v2.2.6/opencode-linux-arm64-musl.tar.gz
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
python scripts/make-toolchain.py

./gradlew :app:assembleDebug
```

产物会包含：

- `assets/toolbox/*.deb`：Termux 工具链 + `openjdk-21` 及其依赖
- `assets/toolchain/toolchain.zip`：Android platform 35、aapt2、Gradle 9.8.1、
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
