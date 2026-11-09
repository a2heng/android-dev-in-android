# 在 Android APK 内做 Android 开发 + 内置 opencode
## 可行性与现有项目调研（2026-11-09）

> 结论先说：**技术可行，而且已经被别人做出来了**。这不再是“能不能”的研究题，而是“选哪条路线、许可是否干净、体验能否接受”的工程题。
>
> 调研方式：GitHub API + 参考项目 submodule（`reference/`）+ 官方文档。文中标注“已确认”的条目来自源码/文档，“待验证”的需要在真机上实测。

---

## 0. TL;DR

| 问题 | 结论 |
|---|---|
| APK 内能否运行 opencode？ | **能**。官方提供 `opencode-linux-arm64-musl` 单文件（约 75 MB），配 musl loader 可直接在 Android 用户态跑，不需要 root、不需要 proot。已有两个 APK 这么做。 |
| APK 内能否编译 Android APK？ | **能**。JDK 21 + Android SDK build-tools(aarch64) + Gradle/AGP 已有成熟方案（AndroidIDE / Code on the Go / Termux Studio）。 |
| 两者能否放进同一个 APK？ | **能**，且已有项目同时内置（Zar-Manah 的 opencode-android-apk：JDK 21 + SDK 35 + Gradle + opencode）。 |
| 最大障碍是什么？ | 不是编译，而是 **Android 的 W^X 执行限制、后台进程存活、内存/散热/存储、以及无法上 Google Play**。 |
| 推荐路线 | 以 MIT 的 `Dajeovv/opencode-android` 为壳，移植成熟构建链；或直接 fork GPLv3 的 `CodeOnTheGo` 快速得到完整 IDE。详见第 3 节。 |
| 设备门槛 | arm64、**8 GB RAM 起**（4 GB 勉强）、**16 GB 空闲存储**、Android 12+ 体验较好。 |
| 分发方式 | 只能 GitHub / F-Droid / 直接 APK 侧载。**不能上 Google Play**（原因见 5.1）。 |

---

## 1. 需求拆解与技术门槛

目标产品 = 一个 APK，内部具备：

1. 文件/项目管理 + 代码编辑（或直接复用 opencode 的 Web UI）
2. 内置 opencode 服务，可对话、改代码、跑命令
3. 内置 Android 构建链：JDK、Android SDK、aapt2/d8/apksigner、Gradle/AGP、Kotlin
4. 构建完成后安装/启动自家 APK（可选 Shizuku 静默安装）
5. 全程离线可用（首次装依赖除外）

逐项可行性：

| 能力 | 现实情况 | 结论 |
|---|---|---|
| 在 APK 沙箱内执行 ELF 二进制 | Android 10+ 对 `targetSdk>=29` 禁止 `execve()` 应用私有目录文件（W^X）。规避方式见 2.1 | 可行，有 4 种路径 |
| 运行 opencode | 官方 `opencode-linux-arm64-musl.tar.gz` 是 PIE + musl，可配 `ld-musl-aarch64.so.1` 直接运行；DNS 需走本地 CONNECT 代理或 resolv.conf shim | **已确认**（Dajeovv 源码、官方 release 资产） |
| 运行 JDK | Termux 的 `openjdk-17/21` 是 bionic 构建；AndroidIDE/CoGo 已内置 | 可行 |
| Gradle + AGP | CoGo 用 Gradle Tooling API 在独立进程跑真实 Gradle；AndroidIDE 同源 | 可行，但慢、吃内存 |
| aapt2 / d8 / apksigner | `lzhiyong/android-sdk-tools` 提供 aarch64 静态构建（Apache-2.0）；Termux Studio 还自编译了支持 SDK 35+ 的 aapt2 | 可行 |
| Kotlin 编译 | CoGo 移植了 Kotlin 编译器；AndroidIDE 也支持 | 可行，首次/冷编译慢 |
| 构建后安装 APK | `PackageInstaller` + `REQUEST_INSTALL_PACKAGES`；Shizuku / 无线 ADB 可静默装 | 可行 |
| 后台长时间构建 | 需要前台服务 + WakeLock + 关闭电池优化；国产 ROM 仍可能杀 | 可行，需适配 |
| 16 KB 页大小（Android 15+ 新机） | 内置的预编译 .so / 二进制需 16 KB 对齐，否则崩溃 | **待验证**，高风险项 |
| Google Play 上架 | targetSdk 28 不满足 2026 年要求；且“下载可执行代码”违反政策 | **不可上架** |

---

## 2. 关键技术点（决定架构的 5 件事）

### 2.1 在 APK 内执行二进制的 4 条路

| 方案 | 做法 | 优点 | 缺点 |
|---|---|---|---|
| **A. nativeLibraryDir（APKLF 思路）** | 把可执行文件命名成 `lib*.so` 放进 `jniLibs`，安装后位于 `/data/app/.../lib/arm64/`（`apk_data_file`），该目录允许执行 | targetSdk 可保持最新；Google 认可的方向 | 只能一层目录；需要把二进制 symlink/包装进 rootfs；升级要重装 APK |
| **B. system linker 跳板** | 可执行文件放应用私有目录，用 `/system/bin/linker64 <elf>` 启动 | 简单，targetSdk 可较高 | 利用 SELinux 漏洞，随时可能被修；不合规；Dajeovv 用它跑 git/shell |
| **C. proot** | proot 自身放 nativeLibraryDir，用它启动 rootfs 内任意 ELF | 兼容性最好，可跑 apt/dpkg | ptrace 开销、I/O 慢、后台易被杀；GPLv2 |
| **D. targetSdk=28** | 直接 exec 私有目录 | 最简单，Termux/CoGo/Zar 都用 | 2026 年仍可安装（Android 17 最低 target 24），但首次安装有“旧应用”警告、静默更新受限、**永久不能上 Play** |

> 现实组合：**opencode 用 A（musl loader 直接从 nativeLibraryDir 执行）**；**构建工具链用 A + D 或 C**；**git/shell 用 B 过渡**。Dajeovv 的 targetSdk 是 34，证明 A+B 组合在 Android 14 上可用。

### 2.2 opencode 的运行形态

- 官方 release 同时提供 glibc 与 **musl** 的 `linux-arm64` 包。
- musl 单文件 + `ld-musl-aarch64.so.1` 即可在 Android 内核上跑（无需 proot）。
- 没有 `/etc/resolv.conf`：Dajeovv 起了一个本地 CONNECT 代理，让 Bun 通过 `netd` 解析 DNS；`opencode-termux-native` 则用 `LD_PRELOAD` shim 重定向 `resolv.conf`。
- opencode 2.x 自带 **Web UI**，APK 直接用 WebView 加载 `http://127.0.0.1:4096` 即可，省掉自研聊天界面。Dajeovv、Zar、一堆 mobile client 都是这个思路。
- 自动更新会替换二进制：需要校验和 + 启动失败回滚（Dajeovv 已实现）。

### 2.3 构建链的最小集合

```
JDK 21 (bionic)
  └─ Gradle 8.x + AGP 8.x
       ├─ aapt2 (aarch64, 支持 SDK 35/36 资源)
       ├─ d8 / apksigner (aarch64 或 Java)
       ├─ kotlinc (Android 移植版)
       └─ Maven 依赖缓存（首次联网下载，之后离线）
Android SDK: platform android-35/36 + build-tools 35/36
```

- **Termux 官方仓库已经有完整 JVM 链**（2026-11 实测索引）：
  - `openjdk-21` 21.0.13（约 250 MB 安装量）
  - `gradle` 9.9.0、`kotlin` 2.4.21
  - `d8` 37.0.0、`apksigner` 37.0.0、`aapt2` 16.0.0.4（aapt2 偏旧，现代资源需替换）
  - 也就是说 **JDK/Gradle/Kotlin/D8/apksigner 可以直接用 Termux 包管理器装**，不需要自己编译。
- **aapt2 需要单独换**：`lzhiyong/android-sdk-tools` 的 `35.0.2` aarch64 静态包（14 MB，Apache-2.0）提供支持 SDK 35 的 `aapt2`；Termux Studio 还提供支持 35/36/37 的自编译版本。
- **Gradle 在手机上能跑**：CoGo 的实测数据是 Pixel 6 Pro 上“一行改动”增量构建约 2.4 秒，小项目 clean build 比旧版快 24–35%。所以体验不是不能用，而是“大项目会热、会慢”。
- **不要用 `./gradlew` 直接跑**：`/sdcard` 是 `noexec`；Termux Studio 的做法是用系统 Gradle + `aapt2FromMavenOverride` 指向自编译 aapt2。
- **Kotlin/Compose** 是内存大头，建议默认 `-Xmx3g`，并按设备内存动态调。

### 2.4 APK 体积与存储

| 组成 | 大致体积 |
|---|---|
| opencode musl 单文件 | 75 MB（解压后 ~150 MB） |
| Termux 风格 toolbox（git/node/python/bash…） | ~200 MB |
| JDK 21 | ~180–250 MB |
| Android SDK（platform + build-tools） | ~200–400 MB |
| Gradle 发行版 + Maven 缓存 | 300 MB–1.5 GB |
| 项目与构建产物 | 每个项目 50 MB–1 GB |

**APK 本体 150–300 MB（不带构建链）/ 约 700 MB（带 JDK + SDK + Gradle 缓存，Zar-Manah 的 release APK 实测 717 MB），安装后占用 4–8 GB**。CoGo 官方要求 3.5 GB、AndroidIDE 要求 4 GB，可作为下限参考。

### 2.5 进程与生命周期

- opencode server、Gradle daemon 都必须跑在 **前台服务** 里，配 `dataSync`/`specialUse` 类型 + 常驻通知。
- 需要 `WAKE_LOCK`、`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`；国产 ROM 还要引导用户加白名单。
- 构建期间切后台很容易被 LMK 杀，建议：前台服务 + 分阶段落盘 + 可恢复构建。

---

## 3. 现有项目调研

### 3.1 直接命中：把 opencode 塞进 APK 的项目

| 项目 | 星 | 许可 | 状态 | 它做了什么 | 对我们的价值 |
|---|---:|---|---|---|---|
| [Dajeovv/opencode-android](https://github.com/Dajeovv/opencode-android) | 34 | **MIT** | 活跃（2026-11） | 单 APK 内置 opencode 2.2.0/1.19.4；musl loader 原生执行；Termux 风格 `apt/dpkg/pkg` toolbox；WebView 跑 opencode Web UI；前台服务/看门狗/自更新；无线 ADB 设备控制 | **最佳底座**：许可干净、targetSdk 34、已经解决最难的执行与 DNS |
| [Zar-Manah/opencode-android-apk](https://github.com/Zar-Manah/opencode-android-apk) | 18 | **GPLv3** | 活跃（2026-11） | opencode + **JDK 21 + Android SDK 35 + Gradle** + `pc` 设备桥（截图/点击/安装）+ 持久记忆 | **最接近目标形态**的参考实现；但 targetSdk 28、资产未入仓、仓库里提交了 debug keystore，不适合直接产品化 |
| [Thr45hx/opencode-termux-native](https://github.com/Thr45hx/opencode-termux-native) | 7 | MIT | 活跃 | 在 Termux 里用 glibc repo + patchelf 改解释器 + resolv shim 原生跑 opencode | 另一套“无 proot”配方，可借鉴 DNS/loader 处理 |
| [Charlie6F/opencode_termux_alpine_aarch64](https://github.com/Charlie6F/opencode_termux_alpine_aarch64) | 5 | MIT | 2025 | opencode 的 Alpine/aarch64 fork + proot-distro 包装 | 仅作参考，proot 路线 |
| [dzianisv/opencode-mobile](https://github.com/dzianisv/opencode-mobile) | 295 | MIT | 活跃 | 连接远程 opencode server 的 Android 客户端（Play/F-Droid） | 如果做“瘦客户端 + 远程服务”路线的现成 UI |

### 3.2 端侧 Android IDE / 构建链

| 项目 | 星 | 许可 | 状态 | 关键点 |
|---|---:|---|---|---|
| [appdevforall/CodeOnTheGo](https://github.com/appdevforall/CodeOnTheGo) | 586 | **GPLv3** | 活跃（周更） | AndroidIDE 的正统继任者；内嵌 Termux 工具链；真实 Gradle Tooling API；Kotlin/Java LSP；插件系统；AI agent；`TARGET_SDK=28`（架构文档明确说是为了 W^X 硬需求）；3.5 GB 存储 / 1.5–4 GB RAM |
| [AndroidIDEOfficial/AndroidIDE](https://github.com/AndroidIDEOfficial/AndroidIDE) | 3.3k | GPLv3 | **已归档（2024-10）** | 第一代端侧 Gradle IDE；代码仍是最好的参考；不要作为底座 |
| [CrimsonCurious/vexel](https://github.com/CrimsonCurious/vexel) | 5 | MIT | 活跃 | 不用 Gradle 的轻量 APK builder：aapt2 + javac + kotlinc + D8 + zipalign + apksigner；支持 Java/Kotlin/C++；Termux CLI | 适合做“快速构建后端”，不适合复杂 AGP 项目 |
| [poordevcode/termux-android-studio](https://github.com/poordevcode/termux-android-studio) | 30 | **Apache-2.0** | 活跃 | 自编译 aarch64 aapt2（支持 SDK 35/36/37）+ `studio` CLI/TUI；Gradle/JDK/NDK 管理；Shizuku 静默安装 | **构建脚本与 aapt2 的最佳来源**（许可友好） |
| [Skykai521/VibeApp](https://github.com/Skykai521/VibeApp) | 33 | GPLv3 | 活跃 | 自然语言生成 App + 端侧 aapt2/javac/D8/签名 + 自动修错循环 | 验证了“AI + 端侧构建”的产品形态 |
| [Nirbhay-Kumar-Developer/mkapk](https://github.com/Nirbhay-Kumar-Developer/mkapk) | 3 | 未标注 | 活跃 | Termux 上的增量构建/混淆/多架构 | 参考价值有限 |
| [wmartinmimi/apk-devtool](https://github.com/wmartinmimi/apk-devtool) | 6 | GPLv3 | 2023 停更 | 无 Gradle 的 apk 构建脚本 | 仅历史参考 |
| [BuildAPKs/buildAPKs](https://github.com/BuildAPKs/buildAPKs) | 667 | GPLv3 | 2022 停更 | 在 Termux 里批量构建上千个示例 APK | 历史参考 |

### 3.3 运行时/容器积木

| 项目 | 许可 | 用途 | 注意 |
|---|---|---|---|
| [termux/termux-app](https://github.com/termux/termux-app) | **GPLv3**（terminal-view/emulator 为 Apache-2.0 例外） | 终端模拟器、bootstrap、包管理 | 整体嵌入会传染 GPL；可以只用 Apache-2.0 的终端 view |
| [termux/proot-distro](https://github.com/termux/proot-distro) | GPLv3 | 在 Termux 里装 Ubuntu/Debian/Alpine | 仅在需要完整 Linux 时使用 |
| proot | GPLv2 | 无 root 的系统调用翻译 | 性能开销；与闭源产品隔离困难 |
| [lzhiyong/android-sdk-tools](https://github.com/lzhiyong/android-sdk-tools) | **Apache-2.0** | aarch64 的 build-tools（aapt2/d8/apksigner 等） | 版本覆盖到 35/36，首选 |
| Shizuku | Apache-2.0 | 无 root 静默安装/提权 API | 需用户装 Shizuku 并授权 |

### 3.4 已确认的实现细节（源码级）

- **Dajeovv**：`targetSdk 34`、`minSdk 26`；opencode 二进制、musl loader、proot 全部放 `jniLibs`；`LocalRuntime.startNative()` 直接用 musl loader 启动；DNS 走本地 CONNECT 代理；toolbox 走 proot；`libgit.so`/`libocshell.so` 是 `/system/bin/sh` 跳板脚本。
- **Zar-Manah**：`targetSdk 28`；`gradle.properties` 里 `targetSdkVersion=28`；资产中包含 `ld-musl-aarch64.so.1`、JDK 21、SDK 35/34、`gradle-oc`；首次启动从官方 release 下载 opencode musl 包；`wrap/java` 通过 `LD_LIBRARY_PATH` 启动 `java.real`。
- **CoGo**：`ARCHITECTURE.md` 明确写 `TARGET_SDK=28` 是硬需求（W^X），并说明 Gradle 在独立进程通过 Tooling API 跑。

---

## 4. 三条可选路线

### 路线 A（推荐）：以 MIT 的 Dajeovv 为壳，补构建链

```
APK (targetSdk 34, MIT)
├─ Java/Kotlin 宿主
│   ├─ WebView → opencode Web UI (127.0.0.1:4096)
│   ├─ 前台服务 / 看门狗 / 通知
│   └─ 项目选择器、文件分享、安装器
├─ jniLibs (可执行)
│   ├─ opencode (musl) + ld-musl + libstdc++/libgcc
│   ├─ proot + loader（可选，跑 toolbox）
│   └─ git/shell 跳板
├─ assets（首次解压）
│   ├─ JDK 21
│   ├─ Android SDK platform 35 + build-tools 35/36
│   ├─ Gradle 8.x + 预置 Maven 缓存
│   └─ aapt2 (aarch64, 支持新资源)
└─ workspace: 应用私有目录 + /sdcard（MANAGE_EXTERNAL_STORAGE）
```

- **优点**：许可干净（MIT）、targetSdk 现代、最难的两件事（opencode 原生执行 + DNS）已经解决。
- **缺点**：需要自己补 JDK/SDK/Gradle 资产与构建调度；项目较年轻（34 星），需要读懂代码。
- **工作量**：MVP 2–4 周（熟悉代码 3–5 天 + 构建链接入 1–2 周 + 打磨 1 周）。

### 路线 B（最快得到完整 IDE）：fork CodeOnTheGo，把 opencode 接进去

```
CoGo (GPLv3, targetSdk 28)
├─ 现成：编辑器 / Gradle / Kotlin LSP / 终端 / 插件 / AI agent
├─ 新增：opencode musl 二进制 + WebView 或终端集成
└─ 复用：构建、安装、日志、项目模型
```

- **优点**：构建链、编辑器、终端、插件、安装器全部现成；周更、社区活跃；最接近“现在就能用”。
- **缺点**：**GPLv3**（衍生作品必须开源）；仓库 1.1 GB，编译/理解成本高；targetSdk 28 的长期风险由上游承担；把 opencode 嵌进它的终端需要适配它的执行环境。
- **工作量**：1–2 周可跑通，但产品化受许可与上游架构约束。

### 路线 C：完全自研（Termux 库 + proot Ubuntu）

- **优点**：完全可控；能跑完整 Linux 生态（apt、Node、Python、Playwright 等）。
- **缺点**：Termux app GPLv3；proot 性能差、后台易被杀；需要自己做终端、文件、构建、安装；工作量 2–3 个月起。
- **不建议**，除非目标用户明确需要“完整 Linux”。

### 路线 D（非目标，但可做 MVP/兜底）：瘦客户端 + 远程 opencode

- PC 上跑 `opencode serve --hostname 0.0.0.0`，APK 只做 WebView/客户端（现成项目一堆）。
- 1 天可完成，但不满足“本地开发”的核心诉求，可作为过渡或高端用户的补充模式。

### 对比

| 维度 | A: Dajeovv + 构建链 | B: CoGo + opencode | C: 自研 Termux/proot | D: 远程客户端 |
|---|---|---|---|---|
| 许可 | **MIT** | GPLv3 | GPLv3 为主 | MIT |
| 上手速度 | 中 | **快** | 慢 | **最快** |
| 构建链成熟度 | 需自建 | **现成** | 需自建 | 不涉及 |
| targetSdk 风险 | **低（34）** | 高（28） | 高（28） | 低 |
| 性能 | 好（musl 原生） | 好（bionic） | 差（proot） | 取决于网络 |
| 可维护性 | 中 | 中（上游大） | 低 | 高 |
| 适合 | **产品化** | 快速验证/内部工具 | 极客/完整 Linux | 过渡 |

**建议**：先用半天做路线 0（真机装现成 APK 验证），再决定 A 或 B。若目标是“自己做的东西能长期维护、许可干净”，选 A；若目标是“两周内自己天天用”，选 B。

---

## 5. 风险清单与对策

### 5.1 分发与合规（最高优先级）

- **不能上 Google Play**：
  - 2026-08-31 起新应用/更新必须 targetSdk 36；CoGo/Zar 的 28 无法满足。
  - Play 政策禁止“从 Play 以外下载可执行代码”（即使技术上用 nativeLibraryDir 规避）。
  - `MANAGE_EXTERNAL_STORAGE`、`REQUEST_INSTALL_PACKAGES` 也受严格审查。
  - → 分发渠道：GitHub Releases、F-Droid 自建仓库、直接 APK。文档里要写清楚。
- **开源许可**：
  - 选 A（MIT）最省事；选 B 必须 GPLv3 开源。
  - 若在 APK 里**预置** Termux 包（bash/git 等 GPL 程序），需要履行 GPL 源码提供义务；更稳妥的是首次运行从 Termux 官方仓库下载，而不是打包进 APK。
  - 预置 Android SDK 组件需确认 Android SDK EULA 是否允许再分发（AndroidIDE/CoGo 都这么做，但产品化前应让法务确认）。
  - OpenJDK 是 GPLv2+CE，需附源码获取方式。
- **签名与更新**：自更新 APK 要用固定签名；opencode 自更新要校验 checksum 并在启动失败时回滚。

### 5.2 技术风险

| 风险 | 影响 | 对策 |
|---|---|---|
| Android 17+ 收紧 exec / 最低 targetSdk | 应用可能无法安装或无法执行 | 优先 nativeLibraryDir（APKLF），避免长期依赖 targetSdk 28 / linker hack |
| 16 KB 页大小 | 内置 .so 崩溃 | 所有 native 库按 16 KB 对齐；真机 `getconf PAGE_SIZE` 验证 |
| 内存不足 / LMK | Gradle daemon 或 opencode 被杀 | 8 GB RAM 起步；`-Xmx3g`；前台服务；分阶段构建；可恢复 |
| 散热/降频 | 大项目构建 10 分钟+ | 限制并发；提示充电/散热；增量构建 |
| 存储膨胀 | 安装后 5–8 GB | 提供清理缓存；SDK/依赖按需下载 |
| 国产 ROM 后台限制 | 服务被杀 | 引导加白名单、关闭电池优化、常驻通知 |
| opencode 更新破坏兼容 | server 起不来 | 固定版本 + 校验 + 回滚；提供“禁用自动更新” |
| WebView 与 opencode Web UI 兼容 | 触摸/键盘/窄屏体验差 | Dajeovv 的注入脚本可参考；必要时做原生壳 |
| 首次启动下载失败 | 无法使用 | 内置最小可用运行时；断点续传；镜像源 |

### 5.3 体验风险

- 手机屏幕小：opencode Web UI 的侧栏/设置弹窗需要注入 CSS/JS 适配（Dajeovv 已做）。
- 虚拟键盘遮挡输入框：`adjustResize` + WebView 适配。
- 构建时手机发热、不能锁屏：需要明确的产品引导。
- 大项目（Compose 多模块）在手机上可能 10 分钟级，需设定预期。

---

## 6. 建议的 MVP 与验证计划

### Phase 0：真机验证（0.5–1 天，强烈建议先做）

1. 在目标手机安装并试用：
   - `Dajeovv/opencode-android` v2（175 MB）：验证 opencode 本地运行、Web UI、终端、toolbox。
   - `Zar-Manah/opencode-android-apk`：验证“opencode + JDK + SDK + Gradle”能否真的构建一个 Hello World。
2. 记录：
   - 冷启动时间、空闲内存占用、连续对话 30 分钟是否被杀。
   - `assembleDebug` 一个空 Compose 项目的耗时、峰值内存、温度。
   - `getconf PAGE_SIZE`（4 KB 还是 16 KB）。
   - 是否能安装构建产物、Shizuku 是否可用。
3. 产出：一张“设备能力基线表”，决定后续是 A 还是 B。

### Phase 1：路线 A 的最小闭环（1–2 周）

- fork Dajeovv，改包名/签名，保留 MIT 声明。
- 把 JDK 21、SDK platform 35、build-tools 35、aapt2、Gradle 发行版作为 assets 首次解压。
- 实现 `BuildManager`：调 Gradle（或先接 Vexel 的非 Gradle 快速构建），输出 APK 到私有目录。
- 用 `PackageInstaller` 安装；可选接 Shizuku。
- 验收：在手机上对内置模板执行 `assembleDebug`，安装并启动成功。

### Phase 2：产品化（1–2 周）

- 项目选择器、文件导入/导出、构建日志、失败重试。
- opencode 会话与项目绑定；diff 查看；权限确认。
- 前台服务、看门狗、崩溃恢复、更新检查。
- 窄屏 UI 适配、键盘适配。
- 验收：连续 1 小时“改代码 → 构建 → 安装 → 对话”不崩。

### Phase 3：长期化

- 迁移到 nativeLibraryDir/APKLF，摆脱 targetSdk 28 与 linker hack。
- 16 KB 对齐、Android 17 兼容。
- 多 ABI（先 arm64）、NDK 支持（可选）。
- 插件/MCP、远程模式（可选）。

---

## 7. 待验证问题（决定“买 vs 造”）

1. 目标机型上，Zar-Manah 的 JDK+SDK+Gradle 是否真能完成一次 Compose 构建？耗时/内存多少？
2. Dajeovv 的 opencode 在 30–60 分钟连续使用下是否稳定？WebView 体验是否可接受？
3. 目标机型页大小是否 16 KB？内置二进制是否兼容？
4. Android 16/17 上，targetSdk 28 的 CoGo/Zar 是否仍可安装并执行？（当前资料：最低 target 24，仍可装，但有警告）
5. Shizuku 在目标机型上的可用性与授权成本。
6. 预置 JDK/SDK 的再分发许可是否允许。
7. 团队更在意“许可干净/长期维护”还是“两周内可用”——这直接决定 A/B。

---

## 8. 当前落地进展（路线 A，2026-11-09）

项目已经移到仓库根目录，并完成第一版脚手架。仓库使用独立初始提交（不保留
fork 历史），Dajeovv/opencode-android 作为 MIT 来源记录在 README 与
`reference/` 中：

- 新增 `assets/skills/android-build/`：包含 `SKILL.md`、`setup-toolchain.sh`、
  `build.sh`，让 opencode 能在本机执行 `assembleDebug` 并打印 `APK=...`。
- `LocalRuntime` 启动时把 `assets/skills` 复制到
  `~/.config/opencode/skills` 和 `~/.config/opencode-android/skills`。
- `CommandResolver` 把 `android-build/scripts/*` 路由到 proot，保证 JDK/Gradle/
  aapt2 能执行。
- `LocalToolbox` 增加 bundled-assets 检测；源码构建没有 `assets/toolbox/*.deb`
  时自动改为在线安装 `DEFAULT_SET`。
- 工具链方案：Termux `openjdk-21` + Google `platform-35_r02.zip` +
  `lzhiyong` aapt2 35.0.2 + 内置 Gradle 9.8.1 与 Maven 缓存。
- **已实现完整离线打包**：`assets/toolbox/*.deb`（含 JDK）+
  `assets/toolchain/toolchain.zip`（SDK/aapt2/Gradle/缓存）+
  `libopencode2_bin.so`。首次启动只做本地解压/安装，不下载工具链。
- **已完成一次真实构建验证**（Windows，JDK 17 + SDK 34 + Gradle 9.8.1）：
  `BUILD SUCCESSFUL`，完整离线 debug APK 约 **564 MB**。
- 参考项目已改为 `reference/` 下的 git submodules，只读、不参与构建；
  主源码树保持 MIT，GPL 代码不复制进来，第三方许可见
  `THIRD_PARTY_LICENSES.md`。
- 详细实施记录见 `docs/route-a-implementation.md`，构建步骤见
  `docs/build-apk.md`。

下一步需要在真机上验证：安装 fork 的 debug APK → 运行 setup → 构建一个
Hello World → 记录耗时/内存/温度；再决定是否保持 proot 构建或迁移到
nativeLibraryDir/APKLF。

---

## 9. 参考链接

- opencode 官方：https://github.com/anomalyco/opencode （MIT，最新 release 含 `opencode-linux-arm64-musl.tar.gz`）
- Dajeovv/opencode-android：https://github.com/Dajeovv/opencode-android
- Zar-Manah/opencode-android-apk：https://github.com/Zar-Manah/opencode-android-apk
- CodeOnTheGo：https://github.com/appdevforall/CodeOnTheGo
- AndroidIDE：https://github.com/AndroidIDEOfficial/AndroidIDE
- Vexel：https://github.com/CrimsonCurious/vexel
- Termux Studio：https://github.com/poordevcode/termux-android-studio
- Termux：https://github.com/termux/termux-app
- proot-distro：https://github.com/termux/proot-distro
- android-sdk-tools（aarch64）：https://github.com/lzhiyong/android-sdk-tools
- opencode-termux-native（glibc 配方）：https://github.com/Thr45hx/opencode-termux-native
- Android W^X 说明：https://developer.android.com/about/versions/10/behavior-changes-10#execute-permission
- Termux 与 Android 10：https://github.com/termux/termux-packages/wiki/Termux-and-Android-10
- Play targetSdk 要求：https://developer.android.com/google/play/requirements/target-sdk

---

## 附：本地已克隆的参考仓库

位于 `reference/`（git submodules，浅克隆，仅用于阅读源码；不参与构建）：

| 目录 | 上游 | 许可 |
|---|---|---|
| `opencode-android/` | Dajeovv/opencode-android | MIT |
| `opencode/` | anomalyco/opencode | MIT |
| `CodeOnTheGo/` | appdevforall/CodeOnTheGo | GPLv3 |
| `AndroidIDE/` | AndroidIDEOfficial/AndroidIDE | GPLv3 |
| `termux-app/` | termux/termux-app | GPLv3（terminal-view 例外 Apache-2.0） |
| `proot-distro/` | termux/proot-distro | GPLv3 |
| `vexel/` | CrimsonCurious/vexel | MIT |
| `termux-android-studio/` | poordevcode/termux-android-studio | Apache-2.0 |
| `opencode-termux-native/` | Thr45hx/opencode-termux-native | MIT |
| `opencode-android-apk/` | Zar-Manah/opencode-android-apk | GPLv3 |
| `android-sdk-tools/` | lzhiyong/android-sdk-tools | Apache-2.0 |

> 注：只添加了 submodule 指针，尚未初始化大仓库（CodeOnTheGo 约 1.1 GB）。
> `git submodule update --init --depth 1 reference/<name>` 按需拉取。
