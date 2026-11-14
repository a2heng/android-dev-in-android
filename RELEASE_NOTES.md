# Adia v0.4.1

本文件只写当前版本相对上一版的改动；CI 直接把本文件作为 GitHub Release 正文，历史版本看 git tag 对应的提交。

## 本版：客户端与 OpenCode 安卓 UI 对齐

### 默认模型

- 设置页的选择页原来放在滚动 `Column` 里，被无限高度压成 0，表现为「点默认模型没反应」；现在放在 `Box` 里。
- 选择结果照旧通过 `POST /api/session/{id}/model` 落到服务端会话，新建会话立即生效。

### 登录与连接

- **保存连接**：只有真正连通（探测成功）的地址才保存；地址栏右侧下拉可选、可删除、自动回填密码。
- **取消连接**：连接中按钮变成「取消连接」；改地址/密码或点取消会立即打断，停在登录页。
- **不再空转**：第一次连不上就停在登录页，只有已经连上过再掉线才退避重连。
- **后台保活**：连上后 `ConnectionService` 前台通知保持 SSE；回到前台补一次 `ensureKeepAlive()`，并在 Compose 宿主里补申请通知权限（Android 13+）。

### 路径

- 服务端路径统一 `/`（`normPath`），Windows 的 `D:\a\b` 不再让「上一级」原地踏步。
- **不以 `/` 开头的相对路径**按「会话目录 → HOME → 文件页根」补全（`resolvePath` / `resolveFileRef`），不再被服务端按自己的工作目录解析成 404 或错文件。
- 文件页、新建会话、`@` 文件引用里的「上一级」能正确处理 `/` 和 Windows 盘符根（`C:` → `C:/`）。
- **Adia 专属保持**：文件页仍以应用 HOME 为根（不提供 `/`、`/tmp/opencode` 胶囊），路径标签显示 `~`；本机终端仍是宿主注入的 Termux/proot shell。

### Git 与终端

- Git「上一次提交」改用 `HEAD~1 → HEAD`；工作区干净时打开 Git 自动切过去。
- 远端 PTY 先让服务端用自己的默认 shell（Windows 上是 Git Bash / cmd.exe），旧服务端再退回 `bash`；Adia 本机终端不受影响。

### 同步回 OpenCode 安卓 UI

- 会话列表时间视图改用 `groupedRest`，修复同一会话出现两次（数量 1、列表两行）。
- 版本比较容忍 `-rc` / `-beta` 后缀。
- 相对路径补全、文件页「上一级」根处理、`ensureKeepAlive()` 同步过去。

清单与两边允许的差异见 [`docs/mobile-integration.md`](docs/mobile-integration.md)。
