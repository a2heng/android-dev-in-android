---
name: Android Install
description: Install, launch, uninstall or grant permissions to an Android app on this phone using the bundled adb, PackageInstaller or Shizuku. Use after a build, or when the user asks to run an APK.
---

# Android Install

## Install with adb

```sh
adb install -r -t /path/to/app-debug.apk
```

Both adb binaries (Termux `$PREFIX/bin/adb` and
`$HOME/toolchain/platform-tools/adb`) work normally: `auto` runs the whole
command inside proot. No `oc-proot` wrapper is needed.

`-r` replaces an existing install; `-t` allows test-only APKs. For a signature
mismatch: uninstall first, then install:

```sh
adb uninstall com.example.app
adb install /path/to/app.apk
```

## Launch

```sh
adb shell monkey -p com.example.app -c android.intent.category.LAUNCHER 1
# or, if the activity is known:
adb shell am start -n com.example.app/.MainActivity
```

Get the launchable activity:

```sh
adb shell cmd package resolve-activity --brief com.example.app | tail -n 1
```

## Permissions

```sh
adb shell pm grant com.example.app android.permission.POST_NOTIFICATIONS
adb shell appops set com.example.app MANAGE_EXTERNAL_STORAGE allow
```

On some OEM ROMs (OPPO/ColorOS, Xiaomi/HyperOS) `pm grant`/`appops` from the
shell user is restricted. Do not treat a failure as an app bug; guide the user
to grant it in Settings.

## Shizuku / PackageInstaller

If adb is not available, the app can use Android's PackageInstaller intent or
Shizuku (if installed). These usually show a confirmation dialog; the user must
tap Install.

## Verify

```sh
adb shell dumpsys package com.example.app | grep -E "versionName|versionCode|firstInstallTime"
adb shell pidof com.example.app
```

## Notes

- The APK must be signed (debug signing is fine).
- Do not install over an app with a different signature; uninstall first.
- On the same phone, adb needs Wireless debugging pairing; see `android-debug`.
