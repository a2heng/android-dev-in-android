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
| `oc-engine` | OpenCode engine | 沙箱（幂等） | 查看/切换命令引擎：`auto` / `proot` / `fast` |
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
oc-engine status     # 输出一个词：auto / proot / fast
oc-engine auto       # 安全模式（默认、推荐）
oc-engine proot      # 整条命令都进沙箱
oc-engine fast       # 旧的分派方式，只用于测速
oc-engine help       # 显示完整说明
```

`native` 是旧名字，仍接受，但会写成 `fast`。安装新版本时，磁盘里的
`engine-mode=native` 会**自动迁移成 `auto`**，避免旧设置把 bug 带回来。

### 2.2 三种模式

| 模式 | 行为 | 适用 |
|---|---|---|
| `auto`（默认） | **拒绝优先**：命令里出现任何 Termux 前缀命令（除叶子工具）就整条进 proot；只有系统命令和叶子工具原生执行 | 日常、agent、构建、终端 |
| `proot` | 每条命令都进 proot | 兼容性优先、批量脚本、排障 |
| `fast` | 旧版按命令名分派；可能把 `bash`/`python` 等漏在 proot 外 | **只用于测速**，不要用于工作 |

`fast` 会复现 `library "libreadline.so.8" not found`，这是保留它作为对照的原因，
不是推荐用法。

---

## 3. `oc-shell` 的 `auto` 分派算法（逐条）

`oc-shell` 由 opencode 作为 `shell` 调用（参数形如 `-c "<command>"`）。它先
`unset LD_LIBRARY_PATH LD_PRELOAD`，再按以下顺序判断：

1. **模式**：
   - `proot` → 整条命令交给 `oc-proot`，结束。
   - `fast` / `native` → source `oc-native-env.sh`，原生 `eval`，结束。
   - `auto` → 继续。
2. 把命令按空白切成 token，去掉引号、括号、`; | &`。
3. 任意 token 命中 **`PROOT_NAMES`**（前缀里除叶子工具外的命令）→ 整条进 proot。
4. 任意 token 含**字面量 Termux 前缀**、`$APP_FILES`、`$HOME` → 整条进 proot。
5. 任意 token 含 **`$` 或反引号** → 整条进 proot（变量可能藏住二进制路径）。
6. 任意 token 形如 `./x`、`../x`、`*.sh`、`*.bash`、`*.py`、`*.js`、`*.mjs`、
   `*.cjs`、`*.ts` → 整条进 proot（脚本可能再启动前缀工具）。
7. 否则：source `oc-native-env.sh`，原生 `eval`。
8. 原生返回 **126**（Android 拒绝 exec）或 **127**（找不到）→ 自动重试 `oc-proot`。

### 3.1 例子

| 输入 | 结果 | 原因 |
|---|---|---|
| `grep foo file` | 原生 | 叶子工具 |
| `cat file \| grep foo` | 原生 | `cat` 是系统、`grep` 是叶子 |
| `ls -la /sdcard` | 原生 | 系统 toybox |
| `sed -i s/a/b/ f` | 原生 | 叶子工具 |
| `git status` | proot | `git` 在前缀，且会启动 helper |
| `bash script.sh` | proot | `bash` 在前缀 |
| `sh -c 'bash x'` | proot | token `bash` |
| `python3 x.py` | proot | `python3` 在前缀 |
| `node app.js` | proot | `node` 在前缀 |
| `npm install` | proot | `npm` 是前缀脚本 |
| `pkg install jq` | proot | `pkg` 是前缀脚本 |
| `adb devices` | proot | `adb` 在前缀 |
| `./gradlew assembleDebug` | proot | `./` 路径 |
| `bash ~/.config/.../build.sh` | proot | `bash` + `$HOME` |
| `ADB="$HOME/x"; "$ADB" devices` | proot | token 含 `$` |
| `echo git` | proot | 过度路由，安全但慢；可接受 |
| `am start -n a/b` | 原生 | 系统命令；若被误判为前缀会重试 |

> 过度路由（例如 `echo git` 进 proot）只损失速度；**漏路由会直接坏**。所以
> 判断宁可偏保守。

### 3.2 `PROOT_NAMES` 怎么生成

`CommandResolver.scan()` 在每次启动时扫描 `$PREFIX/bin`：

- 跳过 `.`、`oc-*` 和非法名字；
- 加入固定的 `PROOT_COMMANDS`（见 §4）；
- 加入**只存在于前缀、且不在 `SAFE_NATIVE`、不是 shell builtin** 的命令；
- 同时给所有可执行的 ELF 生成 `oc-native-env.sh` 函数（`fast` 模式仍可用）。

因此新装的包（`pkg install`）在**下次启动**后会自动被扫描到，不需要改代码。

---

## 4. 固定的 proot 命令表（`PROOT_COMMANDS`）

这些命令无论是否在前缀里都强制进 proot：

```text
pkg apt apt-get apt-cache apt-key dpkg dpkg-deb dpkg-query dpkg-trigger
termux-change-repo termux-setup-storage termux-reload-settings termux-services
proot proot-distro sshd service xargs
```

另外固定加入：

```text
oc-doctor oc-repair oc-engine
```

原因：

- `pkg/apt/dpkg` 必须看到 Termux 前缀和 dpkg 数据库；
- `termux-*` 脚本内部会调用前缀工具；
- `xargs` 会 spawn 子命令，原生子命令会绕过函数；
- `oc-doctor`/`oc-repair` 检查的是沙箱路径，在沙箱里运行最准确；
- `oc-engine` 写的是 bind 进来的真实文件，在沙箱里写同样生效。

`git` 的所有子命令（clone/fetch/pull/push/submodule/commit/merge/rebase/
cherry-pick/revert/am/send-email/difftool/mergetool/credential/maintenance/gc/
repack/prune）在旧版里还要单独判断；新版 `auto` 直接把 `git` 整体送 proot，
不再维护这张子命令表。

---

## 5. 叶子工具（唯一允许原生执行的前缀程序）

`SAFE_NATIVE`：

```text
find grep sed awk diff less cmp
gzip bzip2 xz zstd
sort uniq cut tr head tail wc stat du
md5sum sha1sum sha256sum base64
```

规则：

1. 只允许**不会 exec 其他程序**的工具；
2. 必须通过 `linker64` + `LD_LIBRARY_PATH=$PREFIX/lib:...` 启动；
3. 新增工具若会启动子进程（编译器、解释器、构建工具、包管理器、git 辅助），
   **不得**加入此表；
4. 加之前先问：它会不会 `exec`？会 → 不进。

`oc-native-env.sh` 为每个叶子工具生成：

```sh
sed() { LD_LIBRARY_PATH="$OC_NATIVE_LIB_PATH" /system/bin/linker64 \
  '/data/data/<pkg>/files/usr/bin/sed' "$@"; }
```

注意传的是**前缀里的 symlink 路径**，不是 canonical 目标；Termux 的 coreutils
靠 `argv[0]` 选择 applet，传错会报 invalid option。

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
- `fast` / `native` 会输出警告到 stderr：仅测速。

### 6.3 `oc-doctor`

```sh
oc-doctor
```

输出：

1. `engine`、`OC_ENGINE`、`HOME`、`PREFIX`、`PWD`、`PATH`；
2. 7 个必需文件的 `OK` / `MISSING`；
3. 在 proot 里实测：
   - `echo proot-ok`
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
- **不是给人手动调用的**；排障时用 `oc-engine` / `oc-doctor`。

### 6.6 `oc-native-env.sh`（内部）

- 由 `oc-shell` 在原生路径 source；
- 定义 §5 的叶子工具函数；
- 设置 `OC_NATIVE_LIB_PATH=$PREFIX/lib:$HOME/lib:nativeLibraryDir`；
- 不要直接 `sh oc-native-env.sh` 期待它执行命令。

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

| 类别 | 命令 | 默认路由 |
|---|---|---|
| shell | `bash` `sh`（前缀版） | proot |
| 包管理 | `pkg` `apt` `apt-get` `dpkg` | proot |
| VCS | `git` `ssh` `ssh-keygen` | proot |
| 语言 | `python` `python3` `pip` `node` `npm` `npx` | proot |
| 构建 | `make` `cmake` `ninja` `clang` `clang++` `ld.lld` `pkg-config` | proot |
| Java | `java` `javac` `keytool` | proot |
| 网络 | `curl` `wget` | proot |
| Android | `adb` `aidl` `aapt2`（toolchain） | proot / 构建脚本 |
| 文本/搜索 | `grep` `sed` `awk` `find` `sort` `uniq` `cut` `tr` `head` `tail` `wc` `diff` `less` `cmp` | 原生（叶子） |
| 压缩 | `gzip` `bzip2` `xz` `zstd` | 原生（叶子） |
| 校验 | `md5sum` `sha1sum` `sha256sum` `base64` | 原生（叶子） |
| 其他前缀命令 | `rg` `jq` `tree` `htop` `gdb` `strace` … | proot |

> 表的最后一行是重点：**新装的任何前缀命令默认 proot**。只有明确验证过“不会
> spawn 子进程”的叶子工具才可能进 `SAFE_NATIVE`。

---

## 9. 写命令的规则（给 agent 和脚本作者）

1. 优先交给 `auto`；它已经把前缀命令送进 proot。
2. 命令里有**变量**、**整段脚本**、**复合逻辑**时，用 `oc-proot '<整条>'`：
   ```sh
   oc-proot 'ADB="$HOME/toolchain/platform-tools/adb"; "$ADB" devices'
   oc-proot 'cd ~/workspace/App && ./gradlew assembleDebug'
   ```
3. 不要写 `ADB=...; "$ADB"` 到外层再执行；变量会藏住路径。
4. 不要 `chmod +x` 后从 Java/`ProcessBuilder` 直接执行应用私有文件。
5. 脚本 shebang 可以写 `#!/data/data/com.termux/files/usr/bin/bash`，但**整条
   命令必须在 proot 内**；`auto` 会保证这一点，`fast` 不会。
6. 构建链一律走 `android-build/scripts/build.sh`，它内部用 `$PREFIX` 的 JDK/
   Gradle/aapt2，并自动 re-exec 到 proot。
7. 不要在脚本里写死 `com.a2heng.adia` 或 `com.termux` 的**应用**路径；用
   `$HOME`、`$PREFIX`、`$TMPDIR`。

---

## 10. 报错对照表

| 报错 | 真实原因 | 解决 |
|---|---|---|
| `library "libreadline.so.8" not found` | 前缀缺 `readline`，或 ELF 在 proot 外跑 | `oc-repair`；`oc-engine auto`；用 `oc-proot` |
| `CANNOT LINK EXECUTABLE ... library "lib*.so" not found` | 同上，任意 Termux 库 | 同上 |
| `Permission denied` / `inaccessible or not found` | Android 拒绝 exec 应用私有文件 | 不要直接执行；`auto` 或 `oc-proot` |
| `exec format error` | 用错 ABI / 直接 exec 脚本 | 用 proot；检查 aarch64 |
| `pkg: command not found` | `termux-tools` 缺失 | `oc-repair` |
| `proot: la caja de herramientas aun se esta instalando` | 首次安装未完成 | 等通知；看日志 |
| `engine=fast` | 旧测速模式 | `oc-engine auto` |
| `doctor-http-failed` | 服务器未启动 / token 缺失 | 从 App 终端运行；重启服务 |
| `library "libandroid-support.so" not found` | bash 依赖缺失 | `oc-repair`（会补 `libandroid-support`） |

---

## 11. 迭代开发检查清单（防止走偏）

改命令/路径相关代码时，逐条打勾：

- [ ] 新增前缀命令没有进 `SAFE_NATIVE`，除非确认它不 spawn。
- [ ] 新增“启动必需”文件同时加入 `LocalToolbox.ESSENTIALS`、
      `ESSENTIAL_ROOTS`、`docs/paths.md`。
- [ ] `bootstrap.txt` 仍包含 `bash` 和 `readline`。
- [ ] `oc-*` 命令在 `PROOT_NAMES` 或显式分支里，且文档有全名。
- [ ] `nativeLibraryDir` 路径仍是运行时生成，没有写死。
- [ ] `oc-proot` 仍然幂等。
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
