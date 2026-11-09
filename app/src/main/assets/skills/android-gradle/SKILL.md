---
name: Android Gradle
description: Fix Android Gradle builds on the phone: AGP/Gradle compatibility, SDK/build-tools, memory, daemon, offline mode and common error messages. Use when configuration or plugin resolution fails.
---

# Android Gradle

## Bundled versions

| Component | Version |
|---|---|
| Gradle | 9.8.1 |
| AGP | 8.5.2 (this app); projects may use 8.x |
| compileSdk | 35 |
| build-tools | 35.0.2 |
| JDK | 21 (Termux) |
| Java source/target | 17 |

The build skill launches `GradleMain` directly, because `bin/gradle` breaks
under Android mksh (`Could not find or load main class "-Xmx64m"`).

## Compatibility

- AGP 8.5.x works with Gradle 8.7+; this bundle uses 9.8.1 successfully.
- AGP 9.x may need Gradle 9.x and newer build-tools; do not assume it works.
- If a project pins an older Gradle via its wrapper, set
  `OC_USE_WRAPPER=1` and ensure the distribution is available, or upgrade the
  project to AGP 8.5.2.

## Memory

```properties
org.gradle.jvmargs=-Xmx3g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8
org.gradle.daemon=false
org.gradle.offline=true
```

If the phone has 6 GB RAM or less, use `-Xmx2g`. A killed Gradle daemon often
looks like a random build failure.

## Common errors

| Error | Fix |
|---|---|
| `Failed to find Build Tools revision 34.0.0` | init script forces 35.0.2; add `buildToolsVersion '35.0.2'` to the module |
| `core-lambda-stubs.jar` missing | use toolchain v5+; the jar is bundled |
| `aapt2` format/exec error | use the 16 KB aarch64 aapt2; check ABI |
| `java.io.tmpdir ... doesn't exist` | let `build.sh` use `$HOME/.cache/tmp`; do not create `$PREFIX/tmp` by hand |
| `Could not resolve ...` | dependency not in offline cache; use `android-lib-cache`, not a raw online run |
| `checkDebugAarMetadata` / AndroidX warning | add `android.useAndroidX=true` to `gradle.properties` |
| duplicate `kotlin-stdlib` classes | force one stdlib version (1.8+ merges jdk7/jdk8) |
| `Unsupported class file major version` | JDK too new/old for the AGP/Kotlin version |
| `SDK location not found` | `local.properties` or `ANDROID_HOME` missing |

## Debug a build

```sh
bash ~/.config/opencode/skills/android-build/scripts/build.sh . debug
```

For more detail, run the same Gradle command with `--stacktrace --info` (edit a
copy of build.sh or invoke GradleMain directly). Read the **first** error, not
the last "BUILD FAILED".

## Rules

- Do not edit the bundled Gradle distribution.
- Do not commit `local.properties`, `.gradle/` or `build/`.
- Keep `android.aapt2FromMavenOverride` pointing at the bundled aapt2.
