---
name: Android Test
description: Write and run Android unit tests and on-device instrumentation/UI tests. Use when the user asks for tests, coverage, or to verify a screen/flow on the phone.
---

# Android Test

## Unit tests (JVM, no device)

Put tests in `app/src/test/java/...` and run:

```sh
bash ~/.config/opencode/skills/android-build/scripts/build.sh . debug testDebugUnitTest
```

JUnit 4 is usually already resolvable; if not, add:

```groovy
testImplementation 'junit:junit:4.13.2'
```

Prefer pure JVM tests for logic, parsing, and ViewModels with no Android
dependencies.

## Instrumentation tests (on-device)

Put tests in `app/src/androidTest/java/...` and run:

```sh
bash ~/.config/opencode/skills/android-build/scripts/build.sh . debug connectedDebugAndroidTest
```

This requires a connected adb device. On this phone, use the `android-debug`
skill to pair Wireless debugging and connect `adb` to `127.0.0.1`.

Dependencies:

```groovy
androidTestImplementation 'androidx.test.ext:junit:1.2.1'
androidTestImplementation 'androidx.test.espresso:espresso-core:3.6.1'
```

These artifacts may not be in the offline cache; check with a build before
promising a test run.

## UI checks without writing tests

- Launch: `adb shell monkey -p <pkg> -c android.intent.category.LAUNCHER 1`
- Screenshot: `adb exec-out screencap -p > /tmp/screen.png`
- Crash/ANR: `android-debug/scripts/crash.sh <pkg>`
- UI hierarchy: `adb shell uiautomator dump /sdcard/window.xml` then pull it

## Rules

- Do not claim tests passed without the Gradle task output and the test report.
- Reports: `app/build/reports/tests/testDebugUnitTest/index.html` and
  `app/build/reports/androidTests/connected/`.
- Keep instrumentation tests small; the phone is slower than a desktop.
