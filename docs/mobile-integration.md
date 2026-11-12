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
