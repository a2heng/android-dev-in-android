# Adia 交接文档 / Handover

最后更新：2026-11-09  
仓库：<https://github.com/a2heng/android-dev-in-android>  
当前版本目标：**v0.2.0**（签名 Release）

---

## 1. 一句话现状

Adia 是一个把 opencode 2.2.6 和完整 Android 构建工具链装进 APK 的应用：
在手机本地运行 opencode、proot、Termux 工具、JDK 21、Android SDK 35、
Gradle 9.8.1，离线优先，项目放在应用私有目录。

当前 0.2.0 的代码目标是：

- 只发布**签名 release**，不再回退 debug 预览包；
- 修复新建项目时 `~` / `/` 路径死路；
- 新增 `android-lib-cache`，未预装库只下载一次并共享给其他项目/工具；
- 16 KB 按官方建议做兼容检查，明确 4 KB 设备仍可运行；
- 预装 16 KB 的 `aapt2`、Termux `aidl`、Termux `adb`。

---

## 2. 目录与路径（非常重要）

应用包名：

- release：`com.a2heng.adia`
- debug：`com.a2heng.adia.debug`
- beta：`com.a2heng.adia.beta`

应用私有根（真正的 `HOME`）：

```text
/data/user/0/com.a2heng.adia/files/home
（等价路径 /data/data/com.a2heng.adia/files/home）
```

内部结构：

```text
$HOME/
├─ workspace/                 # 规范项目根，新项目都放这里
├─ toolchain/                 # 离线 SDK / Gradle / Maven 缓存
│  ├─ android-sdk/
│  ├─ gradle/gradle-9.8.1/
│  ├─ gradle-home/            # 共享 GRADLE_USER_HOME
│  ├─ m2/                     # 共享 Maven 本地仓库（新）
│  └─ platform-tools/adb      # 静态 adb 兜底
├─ .config/opencode/          # opencode 配置 + skills
├─ .config/opencode-android/  # 原生配置目录（skill 也会复制到这里）
├─ sdcard/                    # 有 root 时 bind mount 到 /storage/emulated/0
├─ .cache/tmp/                # Gradle/JVM 临时目录
└─ .local/share/              # opencode 数据
```

原则：

- 不申请 `MANAGE_EXTERNAL_STORAGE`，不申请旧存储权限。
- 项目、工具链、skills、配置全部在应用私有目录内。
- 新项目不要建在 `$HOME` 根，也不要建在 `/`；统一放 `$HOME/workspace/<Name>`。

---

## 3. 关键代码位置

| 文件 | 作用 |
|---|---|
| `app/src/main/java/com/opencode/launcher/MainActivity.java` | WebView、v2 配对、`oc-early.js` 注入、UI shim |
| `app/src/main/java/com/opencode/launcher/LocalRuntime.java` | 启动 opencode、环境变量、workspace、认证 |
| `app/src/main/assets/oc-early.js` | 在 SPA 前注入：provider 缓存、路径迁移、项目路径修复 |
| `app/src/main/assets/skills/` | 21 个内置 skill |
| `app/src/main/assets/skills/android-build/scripts/build.sh` | 设备内离线构建入口 |
| `app/src/main/assets/skills/android-build/scripts/check-16kb.py` | 16 KB ELF 检查 |
| `app/src/main/assets/skills/android-lib-cache/` | 新：未预装库共享缓存 |
| `scripts/fetch-opencode.py` | 下载 opencode 2.2.6 |
| `scripts/fetch-toolbox.py` | 生成 Termux toolbox（现在含 aidl/android-tools） |
| `scripts/make-toolchain.py` | 生成离线 toolchain.zip，并做 16 KB 审计 |
| `.github/workflows/release.yml` | 只构建签名 release |
| `.github/workflows/ci.yml` | 轻量 CI + 16 KB |
| `RELEASE_NOTES.md` | v0.2.0 中英文发布说明 |

---

## 4. v2 登录与 `oc-early.js` 的坑（重点）

opencode v2 需要：

- Basic Auth：用户名 `opencode`，密码来自 `OPENCODE_SERVER_PASSWORD`；
- 配对流程：`POST /api/pair` 拿 code，再访问 `/auth/connect/<code>` 建立 cookie。

WebView 首次主文档是 `/auth/connect/<code>`，**不能拦截**，否则 cookie 建不起来。
但 WebView 对它的 302 目标（`/`）不一定再次调用
`shouldInterceptRequest`，所以 `oc-early.js` 可能根本没注入。

已在 `MainActivity` 加两层兜底：

1. `fetchIndexDoc` 带上 `CookieManager` 的 cookie（v2 只带 Basic 不够）；
2. 首次 `onPageFinished` 后检查 `window.__ocEarlyV1`；
3. 如果不存在，直接用 `evaluateJavascript(earlyScript())` **延迟注入**。
   这是目前最可靠的路径：不依赖 WebView 对 302 目标再次触发
   `shouldInterceptRequest`；
4. `earlyReloadTried` 防止重复注入。

延迟注入的代价是：SPA 已经加载完，最早的 provider 请求可能没赶上缓存；
但项目选择器是在之后才打开，`fetch` 包装仍然生效，路径修复可用。

验证方法（debug 包）：

```sh
adb logcat -d | grep -E "main intercept|early missing|early html"
```

期望看到：

```text
main intercept /auth/connect/...
early missing; injecting late
```

然后在 WebView 控制台确认：

```js
window.__ocEarlyV1 === true
```

如果 `early` 仍是 false，项目路径修复、provider 缓存、git 初始化都不会生效。

验证方法（debug 包）：

```sh
adb logcat -d | grep -E "main intercept|early missing|early html"
```

期望看到：

```text
main intercept /auth/connect/...
main intercept /
early missing; reloading root
```

然后在 WebView 控制台确认：

```js
window.__ocEarlyV1 === true
```

如果 `early` 仍是 false，项目路径修复、provider 缓存、git 初始化都不会生效。

---

## 5. 项目路径选择器修复

问题：

- v2 项目选择器的 `~` 按钮取 `sync.data.path.home`；
- 对 `/data/data/...` 这种路径，`inferHomeDirectory()` 返回空字符串；
- `~` 于是退化成 `/`；
- `/` 指向 Android 根目录，`fs.list` 有 containment 检查，直接空白；
- 用户只能“上一步”。

修复在 `app/src/main/assets/oc-early.js`：

- 新增 `ocConfig.workspaceDir()` 桥；
- `canonicalProjectPath()` 把 `/`、`~`、HOME、以及 workspace 外的绝对路径
  统一映射到 `$HOME/workspace`；
- 拦截 `GET /file`（v1）和 `GET /api/fs/list`（v2），只重写 `path`；
- `/api/fs/find` 的目录搜索仍走 location，不动。

效果：

- 点 `~` → workspace；
- 点 `/` → workspace；
- 再点子目录 → `workspace/<dir>`；
- 选中的项目路径始终在 workspace 内。

验证：

```sh
# 页面内执行（需要 WebView 调试）
fetch('/api/fs/list?location[directory]=<workspace>&path=%2F')
  .then(r => r.text()).then(console.log)
```

应该返回 workspace 的目录列表，而不是 500 或空。

---

## 6. 共享库缓存（android-lib-cache）

目标：未预装的库只下载一次，别的项目、别的工具都能用。

共享路径：

| 路径 | 用途 |
|---|---|
| `$HOME/toolchain/gradle-home` | 所有 Gradle 项目的 `GRADLE_USER_HOME` |
| `$HOME/toolchain/m2` | Maven 布局镜像，给 Maven/sbt/javac/脚本用 |
| `$HOME/toolchain/m2/.adia-index.tsv` | 坐标、文件、SHA-256 |
| `$HOME/.m2/repository` | 可选的软链，兼容只认 `~/.m2` 的工具 |

脚本：

```sh
# 1. 在线预热一次，然后自动离线复验
bash ~/.config/opencode/skills/android-lib-cache/scripts/warm-gradle.sh . assembleDebug

# 2. 把 Gradle 缓存导出到 Maven 布局
bash ~/.config/opencode/skills/android-lib-cache/scripts/export-gradle.sh

# 3. 单个坐标（给非 Gradle 工具）
bash ~/.config/opencode/skills/android-lib-cache/scripts/fetch-maven.sh \
  com.squareup.okhttp3:okhttp:4.12.0

# 4. 让 ~/.m2 指向共享镜像
bash ~/.config/opencode/skills/android-lib-cache/scripts/link-m2.sh
```

规则：

- 不要下载到项目内 `./.gradle`。
- 不要用 `~/.gradle` 作为共享缓存。
- 不要提交 `toolchain/`、`m2/`、`*.aar`。
- BOM 管理的版本优先用 Gradle 预热，不要手工解析。

---

## 7. 16 KB 策略（已修正）

**16 KB 是官方建议/Play 新应用要求的方向，不是“4 KB 设备不能用”。**
Adia 自己就在 4 KB 内核上运行和构建。

当前状态：

| 工具 | 对齐 |
|---|---|
| `aapt2` | 16 KB |
| Termux `aidl` | 16 KB（预装） |
| Termux `adb` | 16 KB（预装） |
| `aapt`、`dexdump`、`split-select`、`zipalign` | 4 KB（可选 legacy） |

说明：

- AGP 8.5 正常构建只用 `aapt2`，不会执行上面 4 个 legacy 工具；
- `zipalign` 只在手动对齐时用；
- 32 位 ABI（`armeabi-v7a`/`x86`）在检查器中标记 `SKIP`，不参与 16 KB 判定；
- 只有 64 位库未对齐才会让 release 严格检查失败；
- 遇到无法替换的 4 KB 依赖，设置 `ADIA_16KB_STRICT=0` 继续构建，并在发布说明记录。

测试机：

- OPPO PKB110，Android 17（SDK 37），arm64；
- `getconf PAGE_SIZE` = 4096；
- `ro.boot.hardware.cpu.pagesize` = 4096；
- 还没有 16 KB 内核真机验证。

---

## 8. 本地构建与安装

本机（Windows）开发：

```sh
cd D:/android-dev-in-android

export JAVA_HOME="D:/android-dev-in-android/.cache/toolchain/jdk-17.0.21+9"
export ANDROID_HOME="D:/android-dev-in-android/.cache/toolchain/sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"

".cache/toolchain/gradle-9.8.1/bin/gradle.bat" :app:assembleDebug \
  --no-daemon --console=plain
```

安装：

```sh
ADB=.cache/toolchain/sdk/platform-tools/adb.exe
S=UKTW9TK7I7CM8X4P
P=com.a2heng.adia.debug

"$ADB" -s $S install -r app/build/outputs/apk/debug/app-debug.apk
"$ADB" -s $S shell am force-stop $P
"$ADB" -s $S shell am start -n $P/com.opencode.launcher.MainActivity
```

注意：

- 本地 APK 约 **708 MB**（clean build）。不要提交、不要上传到普通 git。
- 增量构建后如果 APK 异常变大（例如 900 MB），是旧资源残留；
  执行 `rm -rf app/build` 后 clean build。
- `dist/` 下是测试 APK，已被 `.gitignore` 忽略。

---

## 9. 发版流程（只签名 Release）

目标：GitHub Actions 在 CI 构建并发布签名 APK，本机不传 612/708 MB 大文件。

### 9.1 前置 secrets

仓库 Secrets 已配置：

```text
ADIA_KEYSTORE_BASE64
ADIA_KEY_ALIAS
ADIA_KEYSTORE_PASS
ADIA_KEY_PASS
```

本地 keystore 在：

```text
.cache/adia-signing/release.jks
.cache/adia-signing/signing.properties
```

已 gitignore，不能提交。

### 9.2 发版

```sh
git add -A
git commit -m "feat: ..."
git push origin main

# 删除旧 tag（如果还在）
git push origin :refs/tags/v0.1.0

# 创建并推送新 tag
git tag -a v0.2.0 -m "Adia v0.2.0"
git push origin v0.2.0
```

CI 会：

1. 拉取 opencode 2.2.6；
2. 拉取 Termux toolbox（含 aidl/android-tools）；
3. 预热 Gradle 缓存；
4. 生成 `toolchain.zip` 并审计 16 KB；
5. `assembleRelease` + `-PadiaRequireSigning=1`；
6. 16 KB 检查、apksigner 验证；
7. 上传 `adia-0.2.0.apk` + `.sha256`；
8. 用 `RELEASE_NOTES.md` 创建 GitHub Release。

### 9.3 如果 tag 已经推了但代码要改

Release 还没完成时：

```sh
git tag -d v0.2.0
git push origin :refs/tags/v0.2.0
git tag -a v0.2.0 -m "Adia v0.2.0"
git push origin v0.2.0
```

如果 Release 已经发布，建议改 `v0.2.1`，不要覆盖已发布资产。

---

## 10. 当前 Git / 发布状态

- 仓库公开：`a2heng/android-dev-in-android`
- 分支：`main`
- 历史：
  - `e95034c` Initial commit: Adia v0.1.0
  - `5411205` feat: Adia 0.2.0 signed release, picker fix and shared library cache
- 旧 tag `v0.1.0` 已删除
- `v0.2.0` 已创建过一次；**随后又修了 `oc-early.js` 注入问题，
  交接时可能需要把 `v0.2.0` 重新指向最新 commit 并重跑 Release。**
- 本地安装的是 `0.2.0-adia-debug`（versionCode 2）。

未提交/待确认（交接时检查）：

- `MainActivity.java` 的 cookie + root reload 兜底；
- 如果 Release 用的是旧 tag commit，则 Release APK 的 picker 修复不会生效；
- 需要重新跑一次本地 CDP 验证 `window.__ocEarlyV1 === true`。

---

## 11. 已知问题 / 下一步

1. **确认 `oc-early.js` 真正注入**
   - 用 WebView CDP 检查 `window.__ocEarlyV1`；
   - 如果仍 false，检查 `onPageFinished` 的 root reload 是否触发，
     以及 `shouldInterceptRequest` 是否对 `/` 调用。

2. **confirm 16 KB aidl 在 proot 内可执行**
   - `aidl` 是 Termux 动态二进制，依赖 `libgtest.so`、`libfmt.so`、
     `libc++_shared.so`；
   - toolbox 已包含这些库；正常 AGP 构建在 proot 内运行；
   - 需要用一个真实 AIDL 项目做一次构建验证。

3. **APK 体积**
   - clean build 约 708 MB；
   - 主要来自 opencode 253 MB、toolchain 327 MB、toolbox 266 MB；
   - 如需减小，可评估移除 `clang/llvm/cmake/ninja/lld`（约 90 MB），
     但会削弱 NDK 能力。

4. **zipalign/aapt/dexdump/split-select 仍是 4 KB**
   - 目前不影响 AGP 8.5；
   - 如未来必须 16 KB，需要从 AOSP 源码用
     `-Wl,-z,max-page-size=16384` 重新编译。

5. **SAF 导入/导出**
   - 目前外部项目仍走应用私有目录；
   - SAF 是下一步。

6. **Release 体积/上传**
   - GitHub Release 单文件上限 2 GB，708 MB 可上传；
   - Actions artifact 也需确认额度。

---

## 12. 常用命令速查

```sh
# 设备
ADB=.cache/toolchain/sdk/platform-tools/adb.exe
S=UKTW9TK7I7CM8X4P
P=com.a2heng.adia.debug

# 日志
"$ADB" -s $S logcat -d | grep -E "main intercept|early|OpenCode"

# 服务器健康
"$ADB" -s $S forward tcp:4098 tcp:4098
curl -u "opencode:<password>" http://127.0.0.1:4098/api/info

# 查看密码（debug 包可 run-as）
"$ADB" -s $S shell run-as com.a2heng.adia.debug \
  cat shared_prefs/app.xml | grep local_server_password

# 技能列表
curl -u "opencode:<password>" http://127.0.0.1:4098/api/skill

# 16 KB 检查
python3 app/src/main/assets/skills/android-build/scripts/check-16kb.py <apk>
```

---

## 13. English summary

Adia packages opencode 2.2.6 and an offline Android toolchain into an APK.
The app-private root is `$HOME` (`.../files/home`); projects belong in
`$HOME/workspace`.

v0.2.0 focuses on:

- signed-only GitHub Releases;
- fixing the project picker so `~` and `/` resolve to `~/workspace`;
- a shared `android-lib-cache` (Gradle cache + Maven mirror);
- 16 KB compatibility as an official recommendation, not a claim that 4 KB
  is broken;
- preinstalled 16 KB `aapt2`, Termux `aidl` and Termux `adb`.

Important handover points:

- `/auth/connect/<code>` must not be intercepted; the SPA root is reloaded once
  so `/oc-early.js` can be injected.
- The release tag may need to be recreated after the cookie/root-reload fix.
- Local APKs are ~708 MB; never commit them.
- `aapt`, `dexdump`, `split-select` and `zipalign` are optional 4 KB legacy
  tools; AGP 8.5 does not execute them.
- Next: verify `window.__ocEarlyV1`, test a real AIDL build, and consider SAF
  import/export.
