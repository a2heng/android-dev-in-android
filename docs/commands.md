# Adia 命令与路径（全部在 proot 内）

本文是 Adia 里**命令怎么执行**的唯一说明。路径细节见 [`paths.md`](paths.md)。

## 设计原则

| 原则 | 在代码里的体现 | 反例（不要这样做） |
|---|---|---|
| 机制承担复杂度 | 整个命令进 proot；路径/库/linker 都由机制处理 | 要求 agent 记住“git 要包 oc-proot，grep 不用” |
| 命令透明 | 只写 `git status`、`pkg install`、`./gradlew` | 让用户判断“这条命令属于原生还是 Termux” |
| 单一事实来源 | 一个 proot、一个 `$PREFIX`、一个会话 cwd | 同时维护原生命令表、proot 命令表、fallback 表 |
| 拒绝会腐烂的名单 | 没有 `PROOT_COMMANDS` / `SAFE_NATIVE` 分派 | “先按命令名判断，漏了再补一条” |
| 失败可恢复 | 前缀自愈、缓存自动补、APK 断点续传/取消 | 下载中断就删包、安装取消就重下 |
| 文档即契约 | docs + skills 与代码同步；有验收清单 | 代码改了，文档/技能还教旧做法 |

这些原则比任何一条命令都重要。下面所有“为什么这样设计”都是它们的展开。

---

## 1. 唯一原则：一切都在 proot

Adia 里不再有“原生命令 / proot 命令”的分类，也没有 `auto`、`fast`、`native`
这些模式。启动 opencode 服务器、打开本机终端、执行 agent 命令、跑 Gradle、
装包，全部在**同一个 proot 沙箱**里。

因此：

- 用户/agent 只写命令，不需要知道命令背后是什么；
- 不需要 `oc-proot '<命令>'`，不需要 `oc-shell`；
- 不需要记住“哪些命令要包起来”；
- 没有命令白名单，也没有会被漏掉的分类表。

`oc-` 前缀只保留三个**辅助命令**：

| 命令 | 作用 |
|---|---|
| `oc-doctor` | 诊断路径、7 个必需文件、Java 状态 |
| `oc-repair` | 从 APK 离线补齐缺失的 bash/readline 等 |
| `oc-engine` | 兼容旧脚本：只报告 `proot`，不再切换模式 |

`oc-shell`、`oc-proot`、`oc-native-env.sh` 已删除；`$HOME/bin` 里的旧副本
会在启动时自动清理。

---

## 2. 命令怎么执行

```text
opencode server ──(proot, cwd=$HOME)──> shell tool / bash
                                        └─ cwd = 会话目录
                                        └─ $PREFIX = /data/data/com.termux/files/usr
```

- 服务器进程由 `LocalRuntime.startProot` 启动，固定在 proot 内；
- opencode 的 `shell` 配置在 `$HOME/.config/opencode-android/opencode.json`，
  指向 `$PREFIX/bin/bash`；
- 所以 `git`、`python`、`adb`、`pkg`、`./gradlew`、`build.sh` 天然就在沙箱里；
- 命令的 cwd 是会话目录（`/api/location` 的 directory），不是 HOME；
- 系统命令 `am`/`pm`/`dumpsys` 等由 `$PREFIX/bin` 的包装脚本先
  `unset LD_LIBRARY_PATH LD_PRELOAD`，再 exec `/system/bin/*`。

不需要 `oc-proot`，也不需要判断“这条命令看起来像不像 Termux 路径”。

---

## 3. 所有命令和它们使用的路径

### 3.1 诊断/修复

```sh
oc-doctor
oc-repair
oc-engine status     # 永远输出 proot
oc-engine help
```

| 命令 | 使用的路径 |
|---|---|
| `oc-doctor` | `$PREFIX` 7 个必需文件、`$HOME/.config/opencode-android/engine-mode`（旧文件，存在也不影响） |
| `oc-repair` | `POST 127.0.0.1:SERVER_PORT+100/repair`，Java 从 `assets/toolbox/*.deb` 补 `$PREFIX` |
| `oc-engine` | 只读；不写模式文件 |

### 3.2 Termux 工具（全部 proot）

| 类别 | 命令 | 关键路径 |
|---|---|---|
| shell | `bash` `sh` | `$PREFIX/bin`、`$PREFIX/lib` |
| 包管理 | `pkg` `apt` `apt-get` `dpkg` | `$PREFIX/var/lib/dpkg`、`$PREFIX/var/cache/apt` |
| VCS | `git` `ssh` | `$PREFIX/libexec/git-core`、`$HOME/.ssh` |
| 语言 | `python3` `pip` `node` `npm` | `$PREFIX/lib/python3.*`、`$PREFIX/lib/node_modules` |
| 构建 | `make` `cmake` `ninja` `clang` `java` `javac` | `$PREFIX/bin`、`$PREFIX/lib/jvm/java-21-openjdk` |
| 网络 | `curl` `wget` | `$PREFIX/etc/tls/cert.pem` |
| Android | `adb` `aidl` | `$PREFIX/bin`；备选 `$HOME/toolchain/platform-tools/adb` |
| 文本/搜索 | `grep` `sed` `awk` `find` `sort` … | `$PREFIX/bin`、`$PREFIX/lib` |
| 压缩/校验 | `gzip` `xz` `zstd` `sha256sum` … | `$PREFIX/bin`、`$PREFIX/lib` |
| 其他 | `rg` `jq` `tree` `gdb` `strace` … | `$PREFIX/bin` |

### 3.3 设备命令

| 命令 | 行为 | 路径 |
|---|---|---|
| `am` `pm` `settings` `getprop` `logcat` `dumpsys` `input` `monkey` `screencap` `svc` `uiautomator` `wm` `appops` `content` `ip` `ss` `ifconfig` `netstat` `mount` | 包装后 exec `/system/bin/*` | `$PREFIX/bin/<name>` |
| `su` `sudo` | 转给 `opencode-root`，用 Magisk 的真实 su | `$PREFIX/bin/opencode-root` |
| `termux-battery-status` 等 | HTTP `/device/*`，由 App 执行 | `$PREFIX/bin/termux-*` |

### 3.4 构建/项目

| 命令 | 使用的路径 |
|---|---|
| `bash .../android-build/scripts/build.sh <project>` | JDK `$PREFIX/lib/jvm/java-21-openjdk`；SDK `$HOME/toolchain/android-sdk`；Gradle `$HOME/toolchain/gradle`；缓存 `$HOME/toolchain/gradle-home` |
| `./gradlew` | 项目目录；`local.properties` 的 `sdk.dir` |
| `android-debug/scripts/*.sh` | `$PREFIX/bin/adb`，备选 `$HOME/toolchain/platform-tools/adb` |
| 文件页 | Java/Kotlin 直接读写 `$HOME`，根为 `~` |
| 新建会话 | `/api/fs/list`，根为 `HOME`；`项目区` = `$HOME/workspace` |

---

## 4. 为什么不会再有 libreadline 错误

`bash` 的 ELF 把 `DT_RUNPATH=/data/data/com.termux/files/usr/lib` 编译进去。
Adia 把真实前缀 `files/usr` bind 到这个路径；服务器、shell、所有子进程都在
同一个 proot 命名空间里。所以：

- `libreadline.so.8` 永远在 `/data/data/com.termux/files/usr/lib`；
- `bash` 不会再在“真实空间”里找它；
- 没有“这条命令漏了 proot”的可能，因为根本不存在非 proot 的执行路径。

前缀本身仍会在每次启动时自愈（7 个必需文件），防止安装中断。

---

## 5. 报错对照

| 报错 | 原因 | 处理 |
|---|---|---|
| `library "libreadline.so.8" not found` | 前缀缺 `readline`，或有人绕过 Adia 直接 exec | `oc-repair`；确认不是从外部 shell 直接跑 |
| `Permission denied` / `inaccessible or not found` | 绕过 App 直接 exec 应用私有文件 | 从 Adia 的终端/agent 运行 |
| `pkg: command not found` | `termux-tools` 缺失 | `oc-repair` |
| `proot: ...` | 首次安装未完成 / 前缀损坏 | 等安装完成，`oc-repair` |
| `java.io.tmpdir doesn't exist` | `$PREFIX/tmp` 缺失 | `build.sh` 已改用 `$HOME/.cache/tmp`，不要手建 |
| `doctor-http-failed` | 服务器未启动 / token 缺失 | 从 App 终端运行；重启服务 |

---

## 6. 迭代检查清单（防止走偏）

- [ ] 不重新引入 `auto`/`fast`/`native` 或命令白名单。
- [ ] 不重新引入 `oc-shell`/`oc-proot`/`oc-native-env.sh`。
- [ ] 服务器、终端、构建、agent 命令都在同一个 proot 命名空间。
- [ ] 新增系统命令必须同时加包装（`unset LD_LIBRARY_PATH LD_PRELOAD`）。
- [ ] 新增“启动必需”文件同时加入 `LocalToolbox.ESSENTIALS`、
      `ESSENTIAL_ROOTS`、`docs/paths.md`。
- [ ] `bootstrap.txt` 仍包含 `bash` 和 `readline`。
- [ ] `nativeLibraryDir` 路径只在运行时生成。
- [ ] `oc-doctor` 的 `missing` 为空。
- [ ] 真机跑过 `pwd`、`git --version`、`python3 --version`、`build.sh`。
- [ ] `docs/paths.md`、`docs/commands.md` 与 skills 一致。

---

## 7. 代码位置

| 功能 | 文件 |
|---|---|
| 唯一 proot 启动、server 配置 | `LocalRuntime.java` |
| 生成 `oc-doctor` / `oc-repair` / `oc-engine` | `CommandResolver.java` |
| `/doctor`、`/repair` | `ToolboxServer.java` |
| 前缀自愈、`.deb`、系统命令包装 | `LocalToolbox.java` |
| agent 规则 | `app/src/main/assets/skills/termux/SKILL.md` |
