# Bundled skills

These skills are copied by the launcher to:

```text
~/.config/opencode/skills/
~/.config/opencode-android/skills/
```

Start a new session (or restart the server) after an APK update so OpenCode
re-discovers them.

## Core workflow

| Skill | Use |
|---|---|
| `android-build` | Build an APK offline with JDK 21/Gradle 9.8.1/SDK 35, includes the 16 KB audit |
| `android-project` | Scaffold or repair a Gradle project |
| `android-install` | Install/launch/uninstall via bundled adb |
| `android-debug` | logcat, crash/ANR, install-and-run |
| `android-16kb` | 16 KB page-size policy and audit |

## Code quality

| Skill | Use |
|---|---|
| `android-ui` | Material 3, Compose/XML, responsive layouts, accessibility |
| `android-architecture` | MVVM, ViewModel/StateFlow, repositories, navigation |
| `android-test` | JVM unit tests and on-device instrumentation tests |
| `android-i18n` | Chinese/English/Spanish resources, plurals, RTL |
| `android-security` | permissions, exported components, secrets, WebView |

## Platform

| Skill | Use |
|---|---|
| `android-gradle` | AGP/Gradle/SDK compatibility and build errors |
| `android-deps` | dependency resolution, offline cache, conflicts |
| `android-lib-cache` | download once, cache missing libraries in a shared Gradle/Maven cache |
| `android-ndk` | CMake/NDK, ABI filters, 16 KB native builds |
| `android-database` | Room/SQLite/DataStore, migrations |
| `android-networking` | OkHttp/Retrofit, timeouts, offline behavior |
| `android-background` | WorkManager, foreground services, Doze |
| `android-performance` | startup, jank, memory, baseline profiles |

## Release

| Skill | Use |
|---|---|
| `android-release` | keystore, signing, R8, release APK |
| `android-publish` | GitHub Releases / F-Droid / direct APK |

## Meta

| Skill | Use |
|---|---|
| `skill-installer` | Install/list/remove skills from git, zip or a local dir |

## Adding a skill

Ask the agent to use `skill-installer`, or run:

```sh
bash ~/.config/opencode/skills/skill-installer/scripts/install.sh <source> [id]
```

Only install skills from sources you trust: a skill is instructions the agent
will follow.
