# 构建 APK

## 前置

- JDK 17
- Android SDK Platform 34 + Build-Tools 34.0.0
- Gradle 9.9.0（或能访问 `services.gradle.org` 的 `gradlew`）

## 瘦 APK（只含 opencode，不含工具链）

仓库不提交 208 MB 的 opencode 二进制：

```sh
python scripts/fetch-opencode.py --version 2.3.0
./gradlew :app:assembleDebug
```

产物约 94 MB。首次启动会在线安装 Termux toolbox，构建时再下载 SDK/Gradle。

## 完整离线 APK（推荐）

生成三个被 `.gitignore` 排除的大资源：

```sh
# opencode musl 运行时
python scripts/fetch-opencode.py --version 2.3.0

# Termux 包（含 openjdk-21 及其依赖）
python scripts/fetch-toolbox.py

# Android platform 36 + aapt2 + Gradle 9.9.0 + Maven 缓存
python scripts/make-toolchain.py

./gradlew :app:assembleDebug
```

产物会包含：

- `assets/toolbox/*.deb`：Termux 工具链 + JDK
- `assets/toolchain/toolchain.zip`：SDK/Gradle/缓存
- `lib/arm64-v8a/libopencode2_bin.so`：opencode

当前完整包约 **564 MB**（这是刻意的：换离线、开箱即用）。

`fetch-toolbox.py` 默认使用清华 Termux 镜像，失败会自动切 USTC/BFSU/官方。
GitHub 下载需要代理时（换成自己的代理地址）：

```sh
HTTPS_PROXY=http://127.0.0.1:<port> python scripts/fetch-opencode.py
```

## 构建结果（2026-11-09）

- JDK 17.0.21 + Android SDK 34 + Gradle 9.9.0
- `BUILD SUCCESSFUL`
- 完整 debug APK：`dist/app-debug-offline.apk`，约 564 MB
- 已确认 APK 内包含：
  - `assets/skills/android-build/*`
  - `assets/toolbox/openjdk-21_*.deb`
  - `assets/toolchain/toolchain.zip`
  - `lib/arm64-v8a/libopencode2_bin.so`

## 安装与首次启动

```sh
adb install -r dist/app-debug-offline.apk
```

首次启动：

1. `ToolboxService` 从 `assets/toolbox` 安装 `.deb`（含 JDK），不联网；
2. `ToolchainInstaller` 解压 `assets/toolchain/toolchain.zip` 到
   `$HOME/toolchain`；
3. 启动 opencode（debug 变体端口 `4098`）；
4. 把 `android-build` skill 复制到
   `~/.config/opencode/skills` 和 `~/.config/opencode-android/skills`。

然后在 opencode 里执行：

```sh
bash ~/.config/opencode/skills/android-build/scripts/setup-toolchain.sh
```

再对一个 Gradle 项目执行：

```sh
bash ~/.config/opencode/skills/android-build/scripts/build.sh /path/to/project debug
```

默认 `--offline` 使用内置 Gradle 和缓存；如果项目缺依赖：

```sh
OC_BUILD_ONLINE=1 bash ~/.config/opencode/skills/android-build/scripts/build.sh ...
```

## 注意

- `scripts/fetch-toolbox.py` 会把 `openjdk-21` 的音频/X11 依赖一起下载，
  否则 `dpkg` 无法完整 configure。它们会增大 APK。
- `make-toolchain.py` 的 Gradle 缓存来自本机 `~/.gradle`，只覆盖已经构建过的
  依赖；新项目缺依赖时需联网或补缓存。
- 生成的 `assets/toolbox/`、`assets/toolchain/`、`dist/` 都不进 git。
