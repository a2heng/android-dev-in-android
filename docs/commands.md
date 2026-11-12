# Adia 命令与 proot 分派（所有 `oc-*` 命令）

本文是 Adia 里**命令怎么执行**的唯一说明。路径细节见 [`paths.md`](paths.md)。

---

## 1. `oc-` 是什么意思

`oc-` = **OpenCode / Adia 的沙箱控制命令**。它们不是 Termux 包，也不是 Android
系统命令，而是 Adia 在 `$PREFIX/bin` 里生成的脚本，用来决定“这条命令在哪跑”。

所有 `oc-*` 命令都**显式写全名**，不使用别名或缩写：

| 命令 | 全名含义 | 在哪运行 | 作用 |
|---|---|---|---|
| `oc-shell` | OpenCode shell wrapper | 宿主 `/system/bin/sh` | **内部**：opencode 的 `shell` 配置指向它；不要手写调用 |
| `oc-proot` | OpenCode proot runner | 宿主 → proot | 把**一整条命令**放进 Termux 沙箱执行 |
| `oc-engine` | OpenCode engine | 沙箱（幂等） | 查看/切换命令引擎：`auto` / `proot`；`fast` 仅 debug |
| `oc-doctor` | OpenCode doctor | 沙箱 | 诊断路径、必需文件、proot、Java 状态 |
| `oc-repair` | OpenCode repair | 沙箱 | 从 APK 离线补齐缺失的 bash/readline 等 |
| `oc-native-env.sh` | OpenCode native environment | 被 `oc-shell` source | 定义叶子工具的 `linker64` 函数；不要直接执行 |
| `oc-proot` 的配套 | — | — | `oc-engine` / `oc-doctor` / `oc-repair` 都由 `CommandResolver` 生成 |

> **不要**创建 `oc-*` 之外的短别名（如 `ocp`、`ocr`、`eng`）。文档、skill、脚本
> 一律用全名，避免“这个缩写到底是干嘛的”。

---

## 2. 引擎模式（`oc-engine`）

### 2.1 用法

```sh
oc-engine status     # 输出一个词：auto / proot（debug 还可能是 fast）
oc-engine auto       # 默认、推荐
oc-engine proot      # 和 auto 等价，只是显式写出来
oc-engine fast       # 仅 debug 构建；release 会拒绝并保持 auto
oc-engine help       # 显示完整说明
```

`native` 是旧名字，仍接受，但会写成 `fast`。安装新版本时，磁盘里的
`engine-mode=native` 会**自动迁移成 `auto`**。

### 2.2 两种正式模式

| 模式 | 行为 | 适用 |
|---|---|---|
| `auto`（默认） | **整条命令进入 proot**。不做 token 分类，不维护“哪些命令走 proot”的名单，agent 不需要知道命令背后是什么 | 日常、agent、构建、终端 |
| `proot` | 与 `auto` 完全相同，显式写法 | 排障、脚本里想写清楚 |
| `fast` | 仅 debug 测速；整个命令原生执行 | 开发测量，release 不可用 |

**设计原则**：命令对使用者透明。用户/agent 只写命令；`oc-shell` 无条件把
整条命令交给 `oc-proot`。分类、前缀、linker、LD_LIBRARY_PATH 都由机制内部处理，
不要求调用方记住“这个命令该不该包 oc-proot”。

`fast` 会复现 `library "libreadline.so.8" not found`；它只保留给开发测量。
release 构建里 `ALLOW_FAST = BuildConfig.DEBUG`，因此：

- 就算模式文件里残留 `fast`，`oc-shell` 也按 `auto` 执行；
- `oc-engine fast` 返回 2，不写模式文件。

---

## 3. `oc-shell` 的行为（透明分派）

`oc-shell` 由 opencode 作为 `shell` 调用（参数形如 `-c "<command>"`）。逻辑只有：

1. 解析 `-c` / `-lc`，拿到完整命令字符串；
2. 读 `engine-mode`（缺省 `auto`）；
3. 如果是 `fast`/`native` **且**当前是 debug 构建 → 原生 `eval`（测量用）；
4. 其他情况（`auto`、`proot`，以及 release 下的 `fast`）→
   `exec /system/bin/sh "$OWN/oc-proot" "$CMD"`。

没有 token 扫描、没有命令白名单、没有“看起来像 Termux 路径”的判断。
因此下面这些命令都不需要手动包 `oc-proot`：

```sh
pwd
ls -la
git status
python3 app.py
bash scripts/build.sh
adb devices
pkg install -y jq
am start -n com.example/.MainActivity
```

`oc-proot` 仍然保留，用于：

- 想显式声明“整条命令在沙箱里”；
- 在非 Adia 的 native 上下文里手动进入沙箱；
- 写复杂 recipe 时让读者一眼看出边界。

但它**不再是 agent 必须掌握的知识**。这是本版的设计目标。

### 3.1 cwd

`oc-proot` 用调用者的 `$PWD` 作为 `proot -w`；只有目录不存在时才回退
`$HOME`。所以在 `$HOME/workspace/测试` 里执行 `pwd`、`git status`、
`bash "$PWD/DenoiseLab"` 都指向会话目录，不会突然跑到 HOME。

---

## 4. 为什么不再维护 `PROOT_COMMANDS`

旧版维护了一张“这些命令必须进 proot”的表（`pkg/apt/git/xargs/...`），
再加规则扫描前缀。问题是：**任何列表都会漏**。漏一个命令，用户就会看到
`libreadline.so.8` 或 `Permission denied`；而用户不应该为这个列表负责。

现在：

- 所有命令统一进 proot，表只作为**文档/原理记录**；
- 需要知道“某个命令为什么要 proot”时，查 §4.1 的原理表；
- 不再有 `PROOT_COMMANDS`、`SAFE_NATIVE`、`SAFE_NATIVE` 运行时判断。

### 4.1 原理表（记录用，不是分派依据）

| 类别 | 例子 | 为什么必须 proot |
|---|---|---|
| Termux 用户态 | `bash` `git` `python` `node` `pkg` `apt` `dpkg` `ssh` `curl` `clang` `adb` | ELF 的 `DT_RUNPATH` 写死 `/data/data/com.termux/files/usr/lib`；只有 proot 里这个路径存在 |
| 前缀脚本 | `npm` `pip` `termux-*` `build.sh` | shebang 指向前缀的 bash，必须在挂载了前缀的命名空间里执行 |
| 构建链 | `gradle` `java` `aapt2` `aidl` | JDK/Gradle 会再启动前缀里的子进程 |
| 包管理器 | `pkg` `apt` `dpkg` | 需要完整的 dpkg DB、`/var/lib/dpkg`、`/tmp` 映射 |
| 设备 API | `termux-battery-status` 等 | 前缀脚本 + 本地 HTTP |
| 系统命令 | `am` `pm` `dumpsys` `logcat` | 也能在 proot 里跑；Adia 在 `$PREFIX/bin` 放了包装，先 `unset LD_LIBRARY_PATH` 再 exec `/system/bin/*` |

> 这张表解释“为什么安全”，不是让 agent 用来判断“这条命令要不要包
> oc-proot”。判断已经由机制统一做完了。

---

## 5. 前缀工具在哪里运行

- **auto / proot**：全部在 proot 内。`$PREFIX` 是
  `/data/data/com.termux/files/usr`，`$HOME` 仍是真实 HOME，`$PWD` 继承调用者。
- **fast（仅 debug）**：全部原生；`oc-native-env.sh` 为 ELF 生成
  `linker64` 函数，`LD_LIBRARY_PATH` 只在那次调用里生效。它只用于测量，
  不进入 release。

这意味着 agent 不需要记住“grep 原生、git 走 proot、xargs 走 proot”这类规则。
命令写出来，`auto` 统一进沙箱。

---

## 6. 全部 `oc-*` 命令的精确接口

### 6.1 `oc-proot`

```sh
oc-proot '<整条命令>'
```

- 只接受**一个参数**（整条命令字符串）；内部 `CMD="${1:-exec /system/bin/sh}"`。
- 正确：`oc-proot 'adb devices'`
- 正确：`oc-proot 'ADB="$HOME/toolchain/platform-tools/adb"; "$ADB" devices'`
- 正确：`oc-proot 'cd ~/workspace/app && ./gradlew assembleDebug'`
- 错误：`oc-proot adb devices`（只取到 `adb`）
- 幂等：已经 `OC_ENGINE=proot` 时直接 `sh -c`，不会嵌套 proot。
- **继承调用者的当前目录**：`oc-proot` 用 `$PWD` 作为 proot 的 `-w`；只有
  目录不存在时才回退到 `$HOME`。所以在 `workspace/测试` 里执行
  `oc-proot 'pwd'` 得到的是 `workspace/测试`，不是 HOME。
- 会 unset `BASH_ENV`/`ENV`/`LD_PRELOAD`，防止宿主的原生函数污染沙箱。

### 6.2 `oc-engine`

```sh
oc-engine status
oc-engine auto
oc-engine proot
oc-engine fast
oc-engine help
```

- `status` 只输出一个词，方便脚本判断：`[ "$(oc-engine status)" = auto ]`。
- 写文件 `$HOME/.config/opencode-android/engine-mode`；下一条命令立即生效，
  不需要重启服务器。
- `fast` / `native` 仅 debug：会输出警告到 stderr。release 下 `oc-engine fast`
  直接报错并保持 `auto`，不会写出 `fast` 到模式文件。

### 6.3 `oc-doctor`

```sh
oc-doctor
```

输出：

1. `engine`、`OC_ENGINE`、`HOME`、`PREFIX`、`PWD`、`PATH`；
2. 7 个必需文件的 `OK` / `MISSING`；
3. 在 proot 里实测：
   - `echo proot-ok`
   - `echo PWD=$PWD`（应与外层 cwd 一致）
   - `echo PREFIX=$PREFIX`
   - `bash -c "echo bash-ok"`
   - `git --version`
4. 向 `http://127.0.0.1:<SERVER_PORT+100>/doctor` POST，返回 JSON：
   - `prefix`、`installed`、`complete`
   - `missing`（7 个必需文件的缺失列表）
   - `engine`、`runtime`、`proot_available`、`native_available`
   - `sandbox`（一次实际命令输出）

退出码：脚本本身总是 0；看 `MISSING` 和 JSON 的 `missing` 判断。

### 6.4 `oc-repair`

```sh
oc-repair
```

- 调 `/repair`，Java 从 APK 的 `assets/toolbox/*.deb` **只补缺失文件**；
- 不联网、不覆盖用户升级过的包；
- 完成后自动跑 `oc-doctor`；
- 如果 `OPENCODE_TOOLBOX_TOKEN` 缺失（例如从非 Adia shell 调用）会退出 2。

### 6.5 `oc-shell`（内部）

- opencode 的 `shell` 配置指向 `libocshell.so` 跳板，跳板再执行
  `$TERMUX_HOME/bin/oc-shell`；
- 参数形如 `-c "<command>"`、`-lc "<command>"`；也接受 `-l`/`--login`；
- `auto`/`proot` 下无条件 `exec oc-proot "$CMD"`；`fast` 仅在 debug 走原生；
- **不是给人手动调用的**；排障时用 `oc-engine` / `oc-doctor`。

### 6.6 `oc-native-env.sh`（内部，仅 fast/debug）

- 只有 `fast`/`native`（debug）由 `oc-shell` source；
- 为前缀 ELF 生成 `linker64` 函数，供测速使用；
- 设置 `OC_NATIVE_LIB_PATH=$PREFIX/lib:$HOME/lib:nativeLibraryDir`；
- `auto`/`proot` 不 source 它；不要直接执行它。

---

## 7. Android 系统命令（原生）

这些由 `/system/bin` 提供，`auto` 直接原生执行，不继承 Termux 的
`LD_LIBRARY_PATH`：

```text
sh am pm cmd settings getprop logcat dumpsys input monkey screencap
uiautomator wm svc appops content ip ss ifconfig netstat mount
ls cat cp mv rm mkdir chmod chown ln date sleep env nohup setsid
tar toybox
```

Adia 还会在 `$PREFIX/bin` 写一批**包装脚本**，避免系统命令误载入 Termux 库：

| 包装 | 行为 |
|---|---|
| `am` / `pm` / `dumpsys` / `input` / `monkey` / `screencap` / `svc` / `uiautomator` / `wm` / `appops` / `content` / `ip` / `ss` / `ifconfig` / `netstat` / `mount` | `unset LD_LIBRARY_PATH LD_PRELOAD; PATH=/system/bin exec /system/bin/<cmd> "$@"` |
| `su` | 转给 `opencode-root`（Magisk 的真实 su） |
| `sudo` | 同 `su` |
| `opencode-root` | HTTP `/root`，由 App 调真实 su |
| `termux-battery-status` 等 | HTTP `/device/*`，由 App 执行 |

设备 API 命令全表：

```text
termux-battery-status
termux-clipboard-get
termux-clipboard-set
termux-toast
termux-vibrate
termux-brightness
termux-volume
termux-wifi-connectioninfo
termux-sensor
termux-sensor-list
```

---

## 8. Termux 工具分类（默认离线包）

| 类别 | 命令 | 执行位置 |
|---|---|---|
| shell | `bash` `sh`（前缀版） | proot |
| 包管理 | `pkg` `apt` `apt-get` `dpkg` | proot |
| VCS | `git` `ssh` `ssh-keygen` | proot |
| 语言 | `python` `python3` `pip` `node` `npm` `npx` | proot |
| 构建 | `make` `cmake` `ninja` `clang` `clang++` `ld.lld` `pkg-config` | proot |
| Java | `java` `javac` `keytool` | proot |
| 网络 | `curl` `wget` | proot |
| Android | `adb` `aidl` `aapt2`（toolchain） | proot / 构建脚本 |
| 文本/搜索 | `grep` `sed` `awk` `find` `sort` `uniq` `cut` `tr` `head` `tail` `wc` `diff` `less` `cmp` | proot |
| 压缩 | `gzip` `bzip2` `xz` `zstd` | proot |
| 校验 | `md5sum` `sha1sum` `sha256sum` `base64` | proot |
| 其他前缀命令 | `rg` `jq` `tree` `htop` `gdb` `strace` … | proot |

> 新装的任何前缀命令也自动 proot；没有需要 agent 维护的白名单。表中所有类别
> 都由 `oc-shell` 统一送进沙箱。

---

## 8.1 命令 ↔ 路径矩阵（真正用到的组合）

| 命令 / 场景 | 真实二进制 | 执行方式 | cwd | 关键路径 |
|---|---|---|---|---|
| `bash -lc '...'` | `$PREFIX/bin/bash` | proot | `$PWD`（会话目录） | `LD_LIBRARY_PATH=$PREFIX/lib` |
| `git ...` | `$PREFIX/bin/git` | proot | `$PWD` | `$PREFIX/libexec/git-core` |
| `python3 ...` | `$PREFIX/bin/python3` | proot | `$PWD` | `$PREFIX/lib/python3.*` |
| `node/npm ...` | `$PREFIX/bin/node` | proot | `$PWD` | `$PREFIX/lib/node_modules` |
| `pkg/apt/dpkg ...` | `$PREFIX/bin/pkg` 等 | proot | `$PWD` | dpkg DB `$PREFIX/var/lib/dpkg` |
| `adb ...` | `$PREFIX/bin/adb` | proot | `$PWD` | 备选 `$HOME/toolchain/platform-tools/adb` |
| `./gradlew ...` | 项目脚本 | proot | `$PWD` | JDK `$PREFIX/lib/jvm/java-21-openjdk` |
| `build.sh <项目>` | skill 脚本 | proot | `$PWD` | SDK `$HOME/toolchain/android-sdk`、Gradle `$HOME/toolchain/gradle`、缓存 `$HOME/toolchain/gradle-home` |
| `am/pm/settings/dumpsys` | `/system/bin/...`（经 `$PREFIX/bin` 包装） | proot；包装先 unset 污染变量 | `$PWD` | 不继承 Termux `LD_LIBRARY_PATH` |
| `grep/sed/find/sort...` | `$PREFIX/bin/...` | proot | `$PWD` | `$PREFIX/lib`（proot 的 LD_LIBRARY_PATH） |
| `oc-proot '...'` | `oc-proot` | 宿主 → proot | **调用者 `$PWD`** | 前缀 bind 到 Termux 路径 |
| `oc-doctor` / `oc-repair` | `$PREFIX/bin/oc-*` | proot | `$PWD` | HTTP `127.0.0.1:SERVER_PORT+100` |
| 本机终端 | `$PREFIX/bin/bash` | proot + `script` PTY | 启动时 `$HOME` | `$PREFIX/bin/script` |
| 文件页 | opencode `/api/fs` | 原生 Java/Kotlin | 根 `$HOME` | 不暴露真实 `/` |
| 新建会话 | opencode `/api/fs/list` | 原生 Java/Kotlin | 选中的项目目录 | `~`/`HOME`、`项目区`=`$HOME/workspace` |

规则：

- 所有 shell 命令统一在 proot 里跑；agent 不需要判断。
- 系统命令在 proot 里通过 `$PREFIX/bin` 的包装先清理
  `LD_LIBRARY_PATH`/`LD_PRELOAD`，再 exec `/system/bin/*`。
- 构建类命令必须让 `build.sh` 解析 JDK/SDK/Gradle，不要手写路径。
- `oc-proot` 的 cwd 继承调用者；相对路径 `$PWD/项目` 是可靠的。

## 9. 写命令的规则（给 agent 和脚本作者）

1. **直接写命令**。`auto` 无条件把整条命令送进 proot，不需要判断命令类型。
2. 变量、`cd`、复合逻辑都直接写；`oc-proot '<整条>'` 是可选的显式写法，
   不是必须的前置知识：
   ```sh
   ADB="$HOME/toolchain/platform-tools/adb"; "$ADB" devices
   cd "$HOME/workspace/App" && ./gradlew assembleDebug
   ```
3. 脚本 shebang 可以写 `#!/data/data/com.termux/files/usr/bin/bash`；整条命令
   已经在 proot 内。
4. 构建链一律走 `android-build/scripts/build.sh`，它内部解析 JDK/SDK/Gradle。
5. 不要在脚本里写死 `com.a2heng.adia` 或 `com.termux` 的**应用**路径；用
   `$HOME`、`$PREFIX`、`$TMPDIR`、`$PWD`。
6. 只有调试引擎（`fast`，仅 debug）才需要知道“原生 vs proot”；正式运行不需要。

---

## 10. 报错对照表

| 报错 | 真实原因 | 解决 |
|---|---|---|
| `library "libreadline.so.8" not found` | 前缀缺 `readline`，或有人绕过 `auto` 直接 exec | `oc-repair`；确认不是 debug `fast` |
| `CANNOT LINK EXECUTABLE ... library "lib*.so" not found` | 同上，任意 Termux 库 | 同上 |
| `Permission denied` / `inaccessible or not found` | Android 拒绝 exec 应用私有文件 | 正常 `auto` 不会发生；不要绕过 `oc-shell` |
| `exec format error` | 用错 ABI / 直接 exec 脚本 | 用 proot；检查 aarch64 |
| `pkg: command not found` | `termux-tools` 缺失 | `oc-repair` |
| `proot: la caja de herramientas aun se esta instalando` | 首次安装未完成 | 等通知；看日志 |
| `engine=fast` | 旧测速模式（release 应已忽略） | `oc-engine auto`；release 下 fast 不可写 |
| `doctor-http-failed` | 服务器未启动 / token 缺失 | 从 App 终端运行；重启服务 |
| `library "libandroid-support.so" not found` | bash 依赖缺失 | `oc-repair`（会补 `libandroid-support`） |

---

## 11. 迭代开发检查清单（防止走偏）

改命令/路径相关代码时，逐条打勾：

- [ ] `auto` 仍然无条件把整条命令交给 `oc-proot`，没有重新引入 token 白名单。
- [ ] release 构建仍然忽略 `fast`。
- [ ] 新增“启动必需”文件同时加入 `LocalToolbox.ESSENTIALS`、
      `ESSENTIAL_ROOTS`、`docs/paths.md`。
- [ ] `bootstrap.txt` 仍包含 `bash` 和 `readline`。
- [ ] `oc-*` 命令文档有全名，没有模糊缩写。
- [ ] `nativeLibraryDir` 路径仍是运行时生成，没有写死。
- [ ] `oc-proot` 仍然幂等，并且用 `$PWD` 做 `-w`。
- [ ] 跑过 `python3 scripts/verify-toolbox.py`。
- [ ] 跑过 `./gradlew :app:compileDebugJavaWithJavac`。
- [ ] 真机跑过 `oc-doctor`，`missing` 为空。
- [ ] 真机跑过 `bash -lc 'git --version'`、`python3 --version`、`pkg install -y jq`。
- [ ] `docs/paths.md` 和 `docs/commands.md` 与代码一致。

---

## 12. 代码位置

| 功能 | 文件 |
|---|---|
| 生成 `oc-shell` / `oc-proot` / `oc-engine` / `oc-doctor` / `oc-repair` | `CommandResolver.java` |
| 引擎模式文件、启动 native/proot | `LocalRuntime.java` |
| `/doctor`、`/repair` | `ToolboxServer.java` |
| 前缀自愈、`.deb` 解包 | `LocalToolbox.java` |
| 设备 API / root 包装 | `LocalToolbox.writeDeviceScripts` |
| agent 规则 | `app/src/main/assets/skills/termux/SKILL.md` |
