# 前端集成：内置 `client/`（无子模块）

Adia 的 Compose 前端现在**直接内置在仓库里**：

```groovy
// settings.gradle
include(":client")
project(":client").projectDir = file("client")
```

`client/` 是从原来的 `opencode-mobile` 客户端复制出来的一份独立副本，包含
数据层、全部 Compose 界面、终端、文件和设置页。Adia 的 `app` 通过
`implementation project(':client')` 使用它。

## 两个项目的关系

- **Adia**：本机 opencode 后端 + 内置前端，打包成 `com.a2heng.adia` 单 APK。
- **OpenCode 安卓 UI**：独立的局域网客户端，
  [`a2heng/opencode-android-ui`](https://github.com/a2heng/opencode-android-ui)，
  直连电脑上的 `http://<电脑>:42000`。
- 两者不再通过 git submodule 关联，也不要求对方在同一台机器/同一个 APK 里。
  代码曾经同源，之后各自演进；公共修复需要手动 cherry-pick/复制。

## 共用代码的同步约定（2026-11-14）

两边的 `client/` 只有**品牌/宿主差异**允许不同：

- 登录页标题、设置页「关于」、更新仓库地址（`UpdateClient.API`）和下载文件名。
- Adia 文件页不提供 `/`、`/tmp/opencode` 胶囊（应用沙箱读不到真实根目录）；
  路径标签用 `~` 表示 HOME。独立客户端可以浏览真实 `/`。
- 新建会话对话框 Adia 用「HOME / 项目区」，独立客户端用「~ / /」。

其余行为必须保持一致，改了一边要同步另一边：

| 能力 | 位置 | 说明 |
| --- | --- | --- |
| 默认模型 | `SettingsScreen.kt` + `AppModel.updateDefaultModel` | 选择页必须放在滚动 Column **外面**（用 `Box`），否则 `fillMaxSize` 被压成 0 高度，表现为「点默认模型没反应」；新建会话后调 `POST /api/session/{id}/model` 真正落到服务端 |
| 保存连接 | `ConnectScreen.kt` + `AppModel.savedConnections` | 只有 ping 通过才写入；地址栏右侧箭头下拉、回填密码、可删除；改地址/密码或取消立即 `cancelConnect()` |
| 连接重试 | `AppModel.connect` | 登录页第一次连不上停在原地（`hadConnected=false`），用过之后掉线才退避重连；`ON_RESUME` 补一次 |
| 后台保活 | `ConnectionService.kt` + `OpenCodeRoot.kt` | 连上后前台服务保持 SSE；`ON_RESUME` 调 `ensureKeepAlive()` 补启动（宿主在 `onCreate` 自动连接时可能被系统拒绝 FGS） |
| 路径统一 | `Models.kt` → `normPath` / `isAbsolutePath` / `joinPath` | 服务端返回的 `\` 统一成 `/`；`C:` 的上一级是 `C:/` |
| 相对路径 | `AppModel.resolvePath` / `Markdown.resolveFileRef` | 不以 `/` 开头的路径按「会话目录 → HOME → 文件页根」补全，避免 `/api/fs/read` 按服务端 cwd 解析（404/打开错文件） |
| 文件页上一级 | `FilesScreen.kt` / `NewSessionDialog.kt` / `FileMentionDialog.kt` | 根路径（`/`、`C:/`）不再往上；相对 `fsDir` 先补到基准根再切父目录 |
| Git「上一次提交」 | `AppModel.loadGit` | committed 模式传 `base=HEAD~1`；工作区干净时自动切过去 |
| 会话列表去重 | `SessionsScreen.kt` | 时间视图必须用 `groupedRest`（`recent` 已从 `rest` 取走前 3 条），否则同一会话出现两次 |
| PTY | `AppModel.createPty` | 远端终端先让服务端用自己的默认 shell（Windows 上是 Git Bash/cmd），旧服务端再退回 `bash`；**Adia 本机终端不走这里**，由宿主注入 Termux/proot shell |


## 构建

```sh
./gradlew :app:assembleDebug
# 不再需要 git submodule update
```

前端在 `client/`，后端/宿主在 `app/`；`client` 的 namespace 仍是
`com.a2heng.opencode.mobile`（代码包名），Adia 的 applicationId 仍是
`com.a2heng.adia(.debug)`。

## 已废弃的旧方案

以前用过 `mobile/opencode-mobile` 子模块、`LocalServerInfoProvider`
ContentProvider 自动握手和 mobile 仓库里的 `:client` 模块。现在这些都不再是
Adia 的构建依赖；本文件只保留当前结构说明。
