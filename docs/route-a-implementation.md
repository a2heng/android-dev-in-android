# 路线 A 实施记录：Dajeovv fork + 端侧构建链

分支：`feat/on-device-build`
基线：`Dajeovv/opencode-android` @ `007dae2`（MIT）

## 1. 架构决定

```
APK (targetSdk 34, MIT)
├─ opencode (musl, linux-arm64)          ← 原生执行，无 proot
│   ├─ ld-musl-aarch64.so.1              ← jniLibs
│   └─ WebView → http://127.0.0.1:4096
├─ 构建链                                 ← 在 proot 内执行
│   ├─ openjdk-21                         ← Termux 包
│   ├─ Android platform 35 (android.jar)  ← Google 官方 platform-35_r02.zip
│   ├─ aapt2 35.0.2 (aarch64)             ← lzhiyong/android-sdk-tools
│   └─ Gradle wrapper + Maven 依赖         ← 首次构建联网下载
└─ 项目目录：应用私有目录 / /sdcard
```

为什么构建链走 proot：

- 本项目 保持 `targetSdk 34`，Android 10+ 不允许 `execve()` 应用私有目录里的文件；
- JDK/Gradle/aapt2 是大量动态 ELF，逐个放进 `jniLibs` 不现实；
- proot 已经在 Dajeovv 里用于 Termux toolbox，复用它执行构建链，避免 `targetSdk 28` 或 linker hack；
- opencode 自身仍走原生 musl（快、省电），只有构建命令进 proot。

代价：Gradle 构建的 I/O 会经过 proot 的 syscall 翻译，比 CoGo 的纯原生方案慢一些。先用正确性换性能，后续再评估 APKLF/nativeLibraryDir。

## 2. 已实现的改动

### 2.1 新增 `assets/skills/android-build/`

- `SKILL.md`：告诉 opencode 何时、如何在本机构建 APK。
- `scripts/setup-toolchain.sh`：优先使用 APK 内置的离线工具链；源码构建时才
  在线安装 JDK、platform 35、aapt2 35.0.2。
- `scripts/build.sh`：设置环境并执行 Gradle，打印 `APK=...`。有离线包时默认
  `--offline`，并使用内置的 Gradle 9.8.1 和依赖缓存。

### 2.2 `LocalRuntime`：安装 skill

- `installTools()` 现在额外调用 `installSkills()`，把 `assets/skills` 递归复制到
  `~/.config/opencode/skills`；
- 原生模式下还复制到 `~/.config/opencode-android/skills`，因为服务端设置了
  `OPENCODE_CONFIG_DIR=~/.config/opencode-android`；
- 每次启动覆盖 `android-build` 的文件，保证 APK 升级后 skill 也更新。

### 2.3 `CommandResolver`：构建脚本路由到 proot

在生成的 `oc-shell` 里新增：

```sh
*android-build/scripts/*)
  exec /system/bin/sh "$OWN/oc-proot" "$CMD" ;;
```

这样 agent 执行
`bash .../skills/android-build/scripts/build.sh ...` 时，整条命令在 proot 内运行，
`java`、`gradle`、`aapt2`、`pkg` 都能正常 exec。

### 2.4 `LocalToolbox` / `ToolboxService`：源码构建的回退

上游 release APK 内置 `assets/toolbox/*.deb`，但源码仓库里没有这些文件。
现在如果检测不到 bundled assets，就自动改为从 Termux 仓库在线安装
`DEFAULT_SET`，避免首次启动永远装不上 toolbox。

## 3. 工具链版本与来源

| 组件 | 版本/来源 | 备注 |
|---|---|---|
| JDK | Termux `openjdk-21` 21.0.13 | `pkg install -y openjdk-21` |
| Android platform | Google `platform-35_r02.zip`（62 MB） | 解压出 `android-35/android.jar` |
| aapt2 | `lzhiyong/android-sdk-tools` 35.0.2 aarch64 | 14 MB zip，取 `build-tools/aapt2` |
| Gradle | 项目自带 `gradlew` 指定的版本 | 首次构建下载到 `~/.gradle` |
| D8/apksigner | AGP 自带（JVM 内运行） | 不需要 aarch64 二进制 |
| Kotlin | AGP/Kotlin 插件从 Maven 下载 | 不需要 Termux `kotlin` 包 |

## 4. 使用方式

### 4.1 首次

在 opencode 里让 agent 执行（或用户自己在终端执行）：

```sh
bash ~/.config/opencode/skills/android-build/scripts/setup-toolchain.sh
```

会下载约 80 MB（platform + aapt2），并安装约 250 MB 的 JDK。

### 4.2 构建项目

```sh
bash ~/.config/opencode/skills/android-build/scripts/build.sh /path/to/project debug
```

或直接对 agent 说：“用 android-build skill 构建这个项目”。

### 4.3 前置条件

- 项目根目录有 `gradlew`；
- `compileSdk` 35（或自行补 platform 36）；
- 首次构建联网下载 Gradle 与 Maven 依赖；
- 建议 8 GB RAM，`-Xmx3g`。

## 5. 本机（Windows）构建 APK

本次已在开发机上完成一次真实构建（2026-11-09）：

- 临时工具链放在 `.cache/toolchain`（JDK 17.0.21 + Android SDK 34 +
  Gradle 9.8.1，未提交到仓库）；
- 从官方 release 取 `opencode-linux-arm64-musl.tar.gz`，解压后把 `opencode`
  复制为 `app/src/main/jniLibs/arm64-v8a/libopencode2_bin.so`；
- 生成完整离线包：`assets/toolbox/*.deb`（含 openjdk-21）+
  `assets/toolchain/toolchain.zip`（platform 35、aapt2、Gradle 9.8.1、Maven 缓存）；
- `gradle :app:assembleDebug --no-daemon` → **BUILD SUCCESSFUL**；
- 产物 `dist/app-debug-offline.apk` 约 **564 MB**；
- 已确认 APK 内包含 `assets/skills/android-build/*`、
  `assets/toolbox/openjdk-21_*.deb`、`assets/toolchain/toolchain.zip` 与
  `lib/arm64-v8a/libopencode2_bin.so`。

详细步骤见 `docs/build-apk.md`。注意：opencode 二进制、Termux `.deb` 和
toolchain zip 都被 `.gitignore` 排除，源码构建必须用 `scripts/` 补运行时，
否则 APK 只有十几 MB 且启动失败。

也可以在 GitHub Actions 里构建（仓库已有 `.github/workflows/ci.yml`，可参考）。

## 6. 待办（按优先级）

1. **真机验证**：安装 fork 的 debug APK，跑通 setup + 一个 Hello World 的 `assembleDebug`；记录耗时、峰值内存、温度。
2. **安装产物**：把 `APK=` 路径接到 `PackageInstaller` 或 Dajeovv 的 `DeviceApi`，让 agent 能“构建 → 安装 → 启动”。
3. **构建 UI**：在 opencode Web UI 里注入“Build”按钮，或增加原生菜单项；显示 Gradle 进度与日志。
4. **离线化**：预置 Gradle 发行版与常用 Maven 缓存（Compose/AndroidX），减少首次构建时间。
5. **SDK 36**：支持 `platform-36` 与对应 aapt2；处理 AGP 9 / Gradle 9 兼容。
6. **targetSdk 迁移**：评估 APKLF（可执行文件放 `jniLibs`）或保持 28 的取舍；目前保持 34 + proot。
7. **16 KB 页大小**：检查 aapt2/JDK/Gradle 依赖的 native 库是否 16 KB 对齐。
8. **许可**：Termux 包（GPL 等）按需下载而非打包；JDK/SDK 的再分发条款需确认。

## 7. 已知风险

- `pkg install openjdk-21` 在部分设备/镜像上可能很慢或失败；需要重试/换镜像。
- proot 下 Gradle 的增量构建和文件监听可能不如原生稳定；先 `--no-daemon`。
- 首次 Gradle/Maven 下载可能超过 1 GB；流量与存储要提前告知用户。
- `aapt2 35.0.2` 对 `compileSdk 36` 的资源可能不兼容。
- Dajeovv 上游仍在快速迭代，merge 冲突会持续存在；建议把改动做成独立 commit，便于 rebase。
