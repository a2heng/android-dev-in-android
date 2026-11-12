# Adia 路径设计（Termux 前缀 / proot / 真实路径）

本文是 Adia 里**所有路径的唯一说明**。改代码、加脚本、加工具、排查
`libreadline.so.8`、`Permission denied`、`No such file or directory` 之前，先读这一页。

配套文档：[`commands.md`](commands.md) 说明命令分派和所有 `oc-*` 命令。

---

## 1. 一句话结论

> Termux 的二进制把 `/data/data/com.termux/files/usr/lib` **编译进了 ELF 的
> `DT_RUNPATH`**。这个目录只在 proot 沙箱里存在。因此：**任何 Termux ELF 都
> 必须在 proot 里执行**，唯一例外是本文列出的“叶子工具”（见 §7），它们由
> `oc-native-env.sh` 用 `linker64` + `LD_LIBRARY_PATH` 显式启动。

这不是“路径偶尔找不到”，而是**两套路径空间**：

| 空间 | 谁在看 | 前缀在哪里 |
|---|---|---|
| Android 真实空间 | 宿主的 `ProcessBuilder`、Java、opencode 服务器 | `…/files/usr` |
| Termux 沙箱空间 | `bash`、`git`、`python`、`pkg`、`apt`、`clang`… | `/data/data/com.termux/files/usr` |

在真实空间直接执行 `bash`，内核用 ELF 里的 `DT_RUNPATH` 去找
`libreadline.so.8` → 该目录不存在 → `CANNOT LINK EXECUTABLE "bash": library
"libreadline.so.8" not found`。

### 证据（可复现）

```sh
# 在电脑上解包 Termux 的 bash.deb，读动态段
python3 - <<'PY'
# …解包 data.tar.xz，取 bin/bash…
# DT_NEEDED = libandroid-support.so, libreadline.so.8, libiconv.so, libdl.so, libc.so
# DT_RUNPATH = /data/data/com.termux/files/usr/lib
PY
```

`bash` 的 `DT_RUNPATH` 是写死的 Termux 路径，不是相对路径。所以：

- 在 `/data/data/com.a2heng.adia/files/usr` 里放一份 `libreadline.so.8` **没用**；
- 把 `LD_LIBRARY_PATH` 指到真实前缀，只有**显式用 `linker64` 启动**时才有效；
- Android 10+ 还禁止 `untrusted_app` 对应用私有目录做 `execve`，所以“直接运行
  前缀里的 ELF”本身也不可靠；
- **proot 把真实前缀 bind 到 `/data/data/com.termux/files/usr`，两条路径同时成立**，
  这是唯一稳定的解法。

---

## 2. 路径总表（真实空间）

包名：`com.a2heng.adia`（debug 为 `com.a2heng.adia.debug`，beta 为
`com.a2heng.adia.beta`）。**不要在任何脚本里写死包名**，用 `$HOME` / `$PREFIX`。

`HOME` 由 `LocalRuntime.homeDir()` 取 `getFilesDir()/home` 的 **canonical path**，
所以日志里通常是 `/data/data/com.a2heng.adia/files/home`，而不是
`/data/user/0/...`。两者是同一目录，但字符串比较必须用 canonical。

| 逻辑名 | 真实路径 | 谁创建 / 谁用 |
|---|---|---|
| `HOME` | `/data/data/<pkg>/files/home` | opencode 服务器 cwd、agent、终端 |
| `PREFIX`（真实） | `/data/data/<pkg>/files/usr` | toolbox 的 `.deb` 解包目标 |
| `TMPDIR` | `/data/data/<pkg>/cache/tmp` | Java/agent 临时目录；proot 内映射为 `/tmp` |
| `XDG_CONFIG_HOME` | `$HOME/.config` | opencode / skills / MCP |
| `XDG_DATA_HOME` | `$HOME/.local/share` | opencode 数据 |
| `XDG_CACHE_HOME` | `$HOME/.cache` | 缓存、临时构建 |
| `XDG_STATE_HOME` | `$HOME/.local/state` | 状态 |
| `XDG_RUNTIME_DIR` | `$HOME/.run` | 运行时 |
| opencode 配置 | `$HOME/.config/opencode` | 用户配置、`AGENTS.md` |
| Adia 注入配置 | `$HOME/.config/opencode-android` | `opencode.json`、`engine-mode`、skills |
| engine 模式 | `$HOME/.config/opencode-android/engine-mode` | `auto` / `proot` / `fast` |
| 工作区 | `$HOME/workspace` | 服务器默认项目根 |
| 共享工具链 | `$HOME/toolchain` | JDK、SDK、Gradle、Maven 种子 |
| Gradle 用户目录 | `$HOME/toolchain/gradle-home` | `GRADLE_USER_HOME` |
| Maven 本地库 | `$HOME/toolchain/m2` | 可选离线缓存 |
| Android SDK | `$HOME/toolchain/android-sdk` | build-tools / platforms |
| JDK 21 | `$PREFIX/lib/jvm/java-21-openjdk`（toolbox 内置；`build.sh` 自动解析） | 构建 |
| 工具链解压临时 | `$HOME/toolchain.tmp` | 启动清理 |
| 应用日志 | `/data/data/<pkg>/files/logs/adia.log` | 512 KB 轮转 |
| toolbox 工作区 | `$HOME/../toolbox` = `/data/data/<pkg>/files/toolbox` | 索引、下载的 `.deb` |
| toolbox 完成标记 | `$PREFIX/.opencode-toolbox` | 首次安装完成 |
| 包清单 | `$HOME/../toolbox/packages.txt` | 已安装版本 |
| 原生库目录 | `ApplicationInfo.nativeLibraryDir` | **每次升级 APK 都会变** |
| 原生 git 跳板 | `$HOME/.native-bin/git` → `nativeLibraryDir/libgit.so` | 服务器直接调 git |
| 代理配置 | `$HOME/.oc-etc/hosts`、`resolv.conf` | bind 到 `/etc/` |
| 共享存储（可选） | `$HOME/sdcard`（root bind） | 只有 root 且挂载成功时 |
| 真实共享存储 | `/storage/emulated/0` | 不要假设可写 |

> `nativeLibraryDir` 会随每次 APK 升级变化。所有 `oc-*` 脚本必须在**每次启动**
> 重新生成（`CommandResolver.install`），不能把旧路径留在 `$PREFIX/bin` 里。

---

## 3. 路径总表（proot 沙箱内）

proot 由 `LocalRuntime` 启动，bind 表如下（`CommandResolver.prootScript` 与
`LocalRuntime.prootCmd` 必须保持一致）：

| 沙箱路径 | 真实路径 | 作用 |
|---|---|---|
| `/data/data/com.termux/files/usr` | `$PREFIX` | Termux 前缀；所有 ELF 的 RUNPATH |
| `/tmp` | `$TMPDIR` | 临时文件 |
| `/lib/ld-musl-aarch64.so.1` | `nativeLibraryDir/libld_musl.so` | opencode 的 musl loader |
| `/etc/hosts` | `$HOME/.oc-etc/hosts` | 固定 opencode/models 的 IPv4 |
| `/etc/resolv.conf` | `$HOME/.oc-etc/resolv.conf` | DNS |

沙箱里的环境变量：

| 变量 | 值 |
|---|---|
| `HOME` | 真实 `$HOME`（**不**映射成 `/home`） |
| `PREFIX` / `TERMUX_HOME` | `/data/data/com.termux/files/usr` |
| `PATH` | `$PREFIX/bin:/system/bin:/system/xbin:/vendor/bin` |
| `LD_LIBRARY_PATH` | `$PREFIX/lib:$HOME/lib:nativeLibraryDir` |
| `TMPDIR` | 真实 `$TMPDIR`（所以 `/tmp` 与 `$TMPDIR` 是同一批文件） |
| `SHELL` | `/system/bin/sh` |
| `OC_ENGINE` | `proot`（`oc-proot` 用它做幂等，防止嵌套） |

**注意**：`HOME` 在沙箱里仍然是应用私有路径，不是 Termux 的
`/data/data/com.termux/files/home`。这是有意为之：项目、文件页、会话路径只有一套。

---

## 3.1 实际运行审计（2026-11-12，PKB110）

在一次真实会话（cwd `$HOME/workspace/测试`，项目 `DenoiseLab`）里观察到的路径：

| 路径 | 大小 | 作用 | 判定 |
|---|---:|---|---|
| `$HOME/workspace/测试` | 24 MB | 会话 cwd / 项目父目录 | 真路径 |
| `$HOME/workspace/测试/DenoiseLab` | 24 MB | 实际 Gradle 项目 | 真路径（嵌套是用户选的目录结构） |
| `$HOME/toolchain/android-sdk` | 160 MB | SDK 36 / build-tools 36 | 真路径 |
| `$HOME/toolchain/gradle` | 147 MB | Gradle 9.9.0 发行版 | 真路径 |
| `$HOME/toolchain/gradle-home` | 1.1 GB | `GRADLE_USER_HOME`，共享依赖缓存 | 真路径（可清理，见下） |
| `$PREFIX` | 1.1 GB | Termux 用户态 + JDK 21 | 真路径 |
| `$HOME/.local/share/opencode` | 16 MB | DB、日志、shell 记录 | 真路径 |
| `$HOME/.config/opencode` | 321 KB | 默认配置 + skills + tools | 真路径（proot/默认） |
| `$HOME/.config/opencode-android` | 302 KB | 原生服务端配置 + skills 副本 | 设计上的副本 |
| `$HOME/bin` | 0 | 旧 `oc-*` 包装脚本 | **已清理**（曾经是冗余） |
| `$HOME/lib` | 2.9 MB | 原生 linker 用的 libstdc++/libgcc | 真路径 |
| `$PREFIX/bin/adb` + `$HOME/toolchain/platform-tools/adb` | 3.5 MB + 7.4 MB | 两个 adb | 有意保留的 fallback |

结论：

- **`files/usr` 和 `files/home` 不是重复**：前者是 Termux 前缀（真实路径），后者是
  用户 HOME；proot 只是把前者 bind 到 `/data/data/com.termux/files/usr`。
- **`toolchain/` 和 `$PREFIX` 不是重复**：`toolchain` 是 Android SDK/Gradle 缓存，
  `$PREFIX` 是 Termux 用户态和 JDK。两者都由 `build.sh` 显式使用。
- **两个 `.config` 目录是设计上的重复**：原生 opencode 的 `OPENCODE_CONFIG_DIR`
  指向 `opencode-android`，默认目录 `opencode` 仍给 proot/兼容路径用。skills
  因此有两份（约 600 KB）；opencode v2 不列 symlink，所以没有用软链合并。
- **`$HOME/bin` 曾经是 bug**：首次启动时 toolbox 还没装，包装脚本生成在这里；
  装好后又在 `$PREFIX/bin` 生成一份，旧的 `$HOME/bin/oc-*` 成了死副本。现在
  启动时会自动删除这 6 个旧文件。
- **`workspace/测试/DenoiseLab` 不是重复**：会话目录是 `测试`，项目是它下面的
  `DenoiseLab`；如果希望路径更短，新建会话时直接选 `DenoiseLab`。
- **`/` 和 `/tmp/opencode` 不是真路径**：`/` 是 Android 根，应用无权读；
  `/tmp` 在 proot 里映射到 `$TMPDIR`（`cache/tmp`），没有 `opencode` 子目录。
  文件页已不再显示这两个入口。

---

`LocalToolbox.ESSENTIALS` 是唯一的“不可缺失”清单：

| 文件 | 包 | 用途 |
|---|---|---|
| `bin/bash` | `bash` | 终端、agent shell |
| `lib/libreadline.so.8` | `readline` | **bash 的 DT_NEEDED** |
| `lib/libhistory.so.8` | `readline` | bash 历史 |
| `lib/libncursesw.so.6` | `ncurses` | readline 依赖 |
| `lib/libiconv.so` | `libiconv` | bash 依赖 |
| `lib/libandroid-support.so` | `libandroid-support` | Termux 兼容层 |
| `bin/pkg` | `termux-tools` | 包管理器入口 |

启动顺序（`LocalRuntime.prepareToolbox`）：

1. `LocalToolbox.repairEssentials()`：逐个 `stat` 清单；缺哪个就从 APK 的
   `assets/toolbox/<pkg>.deb` **只补缺失文件**（`mergeTree(..., onlyMissing)`），
   不覆盖用户用 `pkg upgrade` 装的新版本。
2. `CommandResolver.install()`：重新生成所有 `oc-*` 脚本，写入当前
   `nativeLibraryDir` 和当前 `$PREFIX`。
3. 再选择 native / proot 启动服务器。

首次安装（`ToolboxService.installBundled`）：

1. **Phase 1**：Java 先解包 `bootstrap.txt`。该文件现在**必须包含**
   `bash`、`readline`、`ncurses`、`libiconv`、`libandroid-support`、
   `termux-tools`、`util-linux`。这样即使 dpkg 中途失败，shell 也能启动。
2. **Phase 2**：真 `dpkg --unpack` + `--configure`，让 apt/dpkg 数据库正确。
3. `repairEssentials()` 再兜底。
4. 写 `.opencode-toolbox` 标记，重启服务器。

> 旧版只把 apt/dpkg 的依赖放进 Phase 1，dpkg 一失败就留下“bash 在、readline
> 不在”的前缀，且 `.opencode-toolbox` 仍然写入 → 每次启动都坏。这就是复发的根因。

---

## 5. 为什么不能靠“补一个 LD_LIBRARY_PATH”

| 做法 | 结果 |
|---|---|
| 在真实空间 `export LD_LIBRARY_PATH=$PREFIX/lib` 后直接 `bash` | Android 拒绝 `execve` 应用私有文件（126/Permission denied）；即使放行，RUNPATH 仍指向不存在的 Termux 目录 |
| `linker64 $PREFIX/bin/bash` + `LD_LIBRARY_PATH` | **能启动叶子工具**；但 bash 内部再 `exec` 的 `git`/`python`/脚本仍会走真实路径，继续失败 |
| 把 `$PREFIX` 软链到 `/data/data/com.termux/files/usr` | 需要 root 且要改别的应用的私有目录；普通安装不可行 |
| 给每个命令写 shell function | 只覆盖“直接输入的命令”；`env bash`、`find -exec`、脚本内部 `exec` 会绕过 |
| **proot + bind 前缀** | 所有路径、shebang、RUNPATH、`pkg/apt` 同时成立；**唯一完整解** |

---

## 6. 不变量（改代码前必须保持）

1. **任何 Termux ELF/脚本不得在真实空间直接执行**，除 §7 的叶子工具。
2. `$PREFIX` 必须始终能解析出 §4 的 7 个文件；每次启动都检查。
3. `bootstrap.txt` 必须包含 §4 的包，先于 `dpkg` 解包。
4. `nativeLibraryDir` 相关的路径只能在运行时生成，不得写死到脚本或文档示例。
5. 沙箱内的 `PREFIX` 必须是 `/data/data/com.termux/files/usr`；真实前缀只用于
   Java 解包和 `linker64` 的 `LD_LIBRARY_PATH`。
6. **proot 必须继承调用者的 cwd**：`oc-proot` 用 `$PWD` 做 `-w`，不能写死
   `$HOME`。否则 `pwd`/`$PWD/project` 会在 HOME 下找项目，出现“文件明明
   写了却 build 找不到”的假路径。
7. `oc-proot` 必须幂等：`OC_ENGINE=proot` 时不得再次调用 proot。
8. 新增“可能 exec 子进程”的工具时，不得加入 `SAFE_NATIVE`。

违反任一条，`libreadline.so.8` / `Permission denied` 就会回来。

---

## 7. 唯一允许的原生例外：叶子工具

`CommandResolver.SAFE_NATIVE`：

```
find grep sed awk diff less cmp
gzip bzip2 xz zstd
sort uniq cut tr head tail wc stat du md5sum sha1sum sha256sum base64
```

它们由 `oc-native-env.sh` 生成同名函数：

```sh
name() { LD_LIBRARY_PATH="$OC_NATIVE_LIB_PATH" /system/bin/linker64 '<前缀里的真实路径>' "$@"; }
```

- 用 `linker64`（不是直接 exec），绕开 Android 的 `execve` 限制；
- 显式给 `$PREFIX/lib`，绕开写死的 `DT_RUNPATH`；
- **只选不 exec 子进程的工具**；`xargs`、`make`、`git`、`bash`、`python` 都**不在**列表里。

新增工具时：如果它会启动别的程序（编译器、包管理器、脚本解释器、git 辅助
进程），**不要**加进 `SAFE_NATIVE`，让 `auto` 把它送进 proot。

---

## 8. 排查手册

在 Adia 终端或 agent 里按顺序执行：

```sh
oc-engine status          # auto / proot / fast
oc-doctor                 # 路径、7 个必需文件、proot 实测、Java 状态
oc-repair                 # 缺文件时从 APK 离线补齐
```

| 现象 | 原因 | 处理 |
|---|---|---|
| `library "libreadline.so.8" not found` | Termux ELF 在 proot 外执行，或前缀缺 `readline` | `oc-repair`；确认 `oc-engine` 不是 `fast`；不要直接执行 `$PREFIX/bin/bash` |
| `Permission denied` / `inaccessible or not found` | 直接 exec 应用私有目录的 ELF/脚本 | 用 `oc-proot '<整条命令>'`，或用 `auto` 让包装器处理 |
| `pkg: command not found` | `termux-tools` 缺失 | `oc-repair` |
| `bash: ...: No such file or directory` | shebang 指向的前缀不存在（在 proot 外） | 整条命令放 `oc-proot` |
| `proot: la caja de herramientas aun se esta instalando` | 首次安装未完成 | 等通知，或看 `files/logs/adia.log` |
| `java.io.tmpdir doesn't exist` | `$PREFIX/tmp` 不存在 | 构建脚本已改用 `$HOME/.cache/tmp`，不要手建 `$PREFIX/tmp` |
| Gradle 找不到 SDK | `local.properties` 指错 | 用 `android-build` skill 的 `build.sh`，它会写真实路径 |

日志：`/data/data/<pkg>/files/logs/adia.log`，过滤 `OpenCodeLocal`、
`OpenCodeToolbox`、`runtime`。

### 安装后 60 秒验收（每次发版都做）

在 Adia 的本机终端里依次执行：

```sh
oc-engine status        # 期望：auto
oc-doctor               # 期望：没有 MISSING，proot-ok/bash-ok/git 版本都出现
bash -lc 'git --version'
python3 --version
pkg install -y jq
```

四条都通过才算“路径问题已解决”。任何一条失败：

1. `oc-repair`；
2. 再看 `oc-doctor` 的 `missing`；
3. 仍然失败就抓 `files/logs/adia.log` 里 `OpenCodeToolbox` / `OpenCodeLocal` 的行。

---

## 9. 加新工具 / 新包的标准流程

1. 在 `scripts/fetch-toolbox.py` 的 `DEFAULT_SET` 加包名（或运行时 `pkg install`）。
2. 如果它是“启动 shell 必需”的，再加入 `ESSENTIAL_ROOTS` 和
   `LocalToolbox.ESSENTIALS`。
3. 跑 `python3 scripts/fetch-toolbox.py` 和 `python3 scripts/verify-toolbox.py`。
4. 如果在 `bin/` 里新增一个**会 exec 子进程**的命令，不要动 `SAFE_NATIVE`；
   `auto` 会在下次启动扫描到它并送 proot。
5. 如果是叶子工具，才可以加入 `SAFE_NATIVE`，并在文档里注明“不会 spawn”。
6. 更新 `docs/commands.md` 的命令表。
7. 真机验证：`oc-doctor`、`oc-engine status`、`bash -lc 'git --version'`、
   `python3 --version`、`pkg install -y jq`。

---

## 10. 相关代码位置

| 责任 | 文件 |
|---|---|
| 前缀、`.deb` 解包、自愈 | `app/src/main/java/com/opencode/launcher/LocalToolbox.java` |
| 引擎模式、proot/原生切换、路径挂载 | `app/src/main/java/com/opencode/launcher/LocalRuntime.java` |
| 命令分派、`oc-*` 脚本生成 | `app/src/main/java/com/opencode/launcher/CommandResolver.java` |
| `/doctor`、`/repair` HTTP | `app/src/main/java/com/opencode/launcher/ToolboxServer.java` |
| 首次安装 / 自测 | `app/src/main/java/com/opencode/launcher/ToolboxService.java` |
| 离线包清单 | `scripts/fetch-toolbox.py` |
| 打包前校验 | `scripts/verify-toolbox.py` |
| agent 规则 | `app/src/main/assets/skills/termux/SKILL.md` |
