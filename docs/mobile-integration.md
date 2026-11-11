# 与 OpenCode Mobile 整合（支线 `feat/proot-mobile-ui`）

目标：Adia 提供**本机 Termux 风格后端**（opencode 在 proot 沙箱里跑），
前端不再用 WebView，而是接上我们自己的 Compose 客户端
[`opencode-mobile`](https://github.com/a2heng/opencode-mobile)。
两者用 **git submodule** 统合在同一个仓库里（`mobile/opencode-mobile`）。

## 1. 架构

```text
┌──────────────────────────── 同一台手机 ────────────────────────────┐
│                                                                    │
│  com.a2heng.adia(.debug)                com.a2heng.opencode.mobile │
│  ┌───────────────────────────┐          ┌────────────────────────┐ │
│  │ opencode serve            │          │ Compose 前端           │ │
│  │ 127.0.0.1:4096 / 4098     │◀──HTTP───│ （会话/聊天/文件/终端）│ │
│  │ Basic: opencode:<随机>    │  SSE     │                        │ │
│  └───────────────────────────┘          └────────────────────────┘ │
│               ▲                                    ▲               │
│               │ ContentProvider                    │               │
│               └──── content://…local-server/info ──┘               │
│                      {url, username, password}                     │
└────────────────────────────────────────────────────────────────────┘
```

- **后端**：Adia 的 opencode 只监听回环地址，密码是每次安装随机生成的
  32 字节 Base64URL，存在 Adia 私有 `SharedPreferences` 里。
- **前端**：OpenCode Mobile 启动时先查本机 Adia 的 ContentProvider，
  拿到 URL + 密码后直接连 127.0.0.1，不再要求用户手输服务器地址。
- Adia 没在跑时，前端回退到用户上次保存的服务器（例如电脑的
  `http://192.168.0.105:42000`）。

## 2. 握手协议（ContentProvider）

Adia 侧：`LocalServerInfoProvider`（`app/src/main/java/com/opencode/launcher/`）

```xml
<permission android:name="com.a2heng.adia.permission.LOCAL_SERVER_INFO"
            android:protectionLevel="normal" />
<provider android:name=".LocalServerInfoProvider"
          android:authorities="${applicationId}.local-server"
          android:exported="true"
          android:readPermission="com.a2heng.adia.permission.LOCAL_SERVER_INFO" />
```

- authority：`com.a2heng.adia.local-server`（正式包）、
  `com.a2heng.adia.debug.local-server`（debug 包）。
- 查询：`content://<authority>/info`，单行单列 `info`：

```json
{"url":"http://127.0.0.1:4098","port":4098,"username":"opencode",
 "password":"…","running":true,"pid":12345,"app":"adia"}
```

- 只读；写操作一律抛 `UnsupportedOperationException`。
- 权限是 `normal`，安装即授予，不需要用户在设置里点什么。

前端侧：`LocalServerProbe.kt` + `AppModel.autoConnect()`

1. 查两个 authority，`running=true` 才用；
2. 用拿到的 URL/密码 `ping()` 一次，健康才连（`remember = false`，
   不覆盖用户保存的电脑服务器）；
3. 否则退回保存的服务器；最后才探测 `127.0.0.1:4096/4098`（无密码调试场景）。

清单里必须声明：

```xml
<uses-permission android:name="com.a2heng.adia.permission.LOCAL_SERVER_INFO" />
<queries>
    <package android:name="com.a2heng.adia" />
    <package android:name="com.a2heng.adia.debug" />
</queries>
```

## 3. 运行模式（proot / native）

`LocalRuntime.runtimeMode()` 的优先级：**用户偏好 > 编译期默认值**。

| 值 | 行为 |
|---|---|
| `auto`（默认） | 先试原生 musl，失败或健康检查不过再走 proot |
| `native` | 只用原生 musl |
| `proot` | 只用 proot 沙箱（Termux 风格 userland） |

编译期默认值来自 Gradle 属性：

```sh
./gradlew :app:assembleDebug -PadiaRuntime=proot
```

运行时可用 `LocalRuntime.setRuntimeMode(ctx, "proot")` 覆盖（下次启动生效）。

> 说明：proot 模式会把 syscall 过一遍 ptrace，I/O 慢一些，但命令、环境变量、
> 路径语义更接近 Termux；构建链（JDK/Gradle/aapt2）本来就在 proot 里跑，
> 统一到 proot 可以减少“两套环境”的差异。

## 4. 构建与安装

```sh
# 0) 子模块（只读参考 + 前端源码）
git submodule update --init mobile/opencode-mobile

# 1) Adia（本机后端；debug 包，端口 4098，可与正式版共存）
python scripts/fetch-opencode.py        # 补 jniLibs 里的 opencode（约 75 MB）
./gradlew :app:assembleDebug -PadiaRuntime=proot
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 2) 前端（另一个仓库，也可以用 submodule 里的源码）
cd mobile/opencode-mobile
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

先把 Adia 打开一次，让本地服务起来；再打开 OpenCode Mobile，它会自动
连上 `127.0.0.1:4098`，顶栏标题和会话列表来自 Adia 的 workspace。

## 5. 当前进度

- [x] 分支 `feat/proot-mobile-ui`
- [x] `mobile/opencode-mobile` 子模块
- [x] Adia 侧 ContentProvider + 权限
- [x] 前端启动自动发现本机 Adia 并连接
- [x] 运行模式 `auto/native/proot` + `-PadiaRuntime`
- [x] 前端拆成 `:client` library（opencode-mobile 0.4.0）
- [x] Adia `settings.gradle` include 子模块的 `:client`，**不需要安装 Mobile APK**
- [ ] 用 16 KB 页设备实测 proot 模式

## 5.1 单 APK 融合方案（下一步）

目标：一个 APK 里同时有「后端」和「前端」，不需要两个包、不需要 ContentProvider，
也不需要安装 Mobile APK（Adia 哲学：开箱即用）。前端以 `:client` library 直接编进 Adia。

```text
android-dev-in-android/
├─ app/                      # Adia：入口 Activity + 启动本地 opencode
├─ mobile/opencode-mobile/   # 子模块
│  ├─ client/                # ← 新增：Compose UI + 数据层（library）
│  │  ├─ data/  (OcClient / AppModel / Models / LocalServerProbe)
│  │  └─ ui/    (AppRoot / ChatScreen / FilesScreen / TerminalScreen / …)
│  └─ app/                   # 独立 APK 的薄壳（MainActivity + 分享入口）
└─ docs/
```

1. 前端仓库拆模块：`:client`（library，包含现在 `app/` 里除 `MainActivity` 之外的全部代码和资源）
   + `:app`（只留 `MainActivity`、启动画面、分享 intent）。行为不变，独立 APK 照常发版。
2. Adia 的 `settings.gradle`：

   ```groovy
   include ':app'
   include ':client'
   project(':client').projectDir = file('mobile/opencode-mobile/client')
   ```

3. 版本对齐：AGP 用 Adia 的 9.5.1、Gradle 9.9.0、compileSdk 37；
   Kotlin / Compose 编译插件用前端仓库的版本（2.4.21 / BOM 2026.06.01）。
4. Adia 启动本地服务后直接：

   ```kotlin
   setContent { OpenCodeMobileRoot(url = "http://127.0.0.1:4096", password = LocalRuntime.serverPassword(ctx)) }
   ```

   同进程直接传 URL/密码，`LocalServerProbe` 只在「装成两个 APK」时使用。
5. 替换关系：
   - 文件管理器：`FilesSheet`（WebView 弹层）→ `FilesScreen`
   - 终端：WebView/toolbox 终端 → `TerminalScreen`（libvterm + PTY WebSocket）
   - 会话/聊天/缓存/Git/表单：直接用 `SessionsScreen` / `ChatScreen` / `SessionCacheScreen` / `GitScreen`
   - 删除 WebView shim（`oc-corejs.js` / `oc-early.js`）和仅供网页用的菜单逻辑
6. 保留 Adia 的后端能力：`LocalRuntime`、`LocalToolbox`、`ToolchainInstaller`、`CommandResolver`、
   `LocalNetworkProxy`、`ServerWatch`、`RuntimeUpdateService`。

风险点：AGP/Kotlin 版本跨度、资源合并冲突（主题、启动图标）、`Application` 初始化顺序
（AppModel 依赖 Application context，可在 library 里用 `AndroidViewModel` 保持不变）。


## 6. 已知限制

- 两个 APK 仍需分别安装；单 APK 融合是下一步（见上）。
- Adia 必须至少打开过一次、服务在跑，前端才能自动连上。
- ContentProvider 只返回“本机服务”的信息，不提供任何文件/配置读写能力。
