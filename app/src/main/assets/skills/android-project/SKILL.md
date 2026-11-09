---
name: Android Project
description: Create or repair an Android Gradle project on this phone: modules, AGP/Gradle versions, manifest, package name, and a first build. Use when starting a new app or when the project structure is broken.
---

# Android Project

## Defaults for this device

- AGP **8.5.2**, Gradle **9.8.1** (bundled)
- `compileSdk 35`, `buildToolsVersion "35.0.2"`
- `minSdk 24`, `targetSdk 34`
- Java 17
- Arm64 only

The build skill forces `buildToolsVersion 35.0.2` through an init script, but
writing it in `app/build.gradle` makes the project portable.

## Where projects live

Create projects under `~/workspace/<Name>`, never directly in `~` and never in
the Android filesystem root. `~/workspace` is the app-managed project root;
the project picker's `~` and `/` buttons both resolve there.

```sh
mkdir -p "$HOME/workspace/MyApp"
```

## Minimal structure

```text
~/workspace/MyApp/
├─ settings.gradle
├─ build.gradle
├─ gradlew                  # empty placeholder is enough for the build skill
└─ app/
   ├─ build.gradle
   └─ src/main/
      ├─ AndroidManifest.xml
      ├─ java/com/example/myapp/MainActivity.java
      └─ res/values/styles.xml
```

`settings.gradle`:

```groovy
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
rootProject.name = "MyApp"
include ":app"
```

Root `build.gradle`:

```groovy
plugins { id 'com.android.application' version '8.5.2' apply false }
```

`gradle.properties` (required for any AndroidX dependency):

```properties
android.useAndroidX=true
android.enableJetifier=true
org.gradle.jvmargs=-Xmx3g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8
org.gradle.daemon=false
```

`app/build.gradle`:

```groovy
plugins { id 'com.android.application' }
android {
    namespace 'com.example.myapp'
    compileSdk 35
    buildToolsVersion '35.0.2'
    defaultConfig {
        applicationId 'com.example.myapp'
        minSdk 24
        targetSdk 34
        versionCode 1
        versionName '1.0'
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
}
```

## Workflow

1. Create the directories and files above.
2. Build with the `android-build` skill:
   `bash ~/.config/opencode/skills/android-build/scripts/build.sh . debug`
3. If Gradle asks for dependencies not in the cache, use
   `android-lib-cache` (`warm-gradle.sh`) so the download is reused by every
   project. Ask before using mobile data.
4. Install with the `android-install` or `android-debug` skill.

## Rules

- Never run `./gradlew` directly on `/sdcard` (`noexec`); the build skill uses
  `sh` and the bundled Gradle launcher.
- Use a unique `applicationId`; do not reuse `com.example.hello` for real work.
- Do not add Compose dependencies unless the offline cache has them; see
  `android-deps`.
- Add a `.gitignore` with `.gradle/`, `build/`, `local.properties`, `*.apk`.
