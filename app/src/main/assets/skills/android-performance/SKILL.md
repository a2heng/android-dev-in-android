---
name: Android Performance
description: Measure and improve Android app performance on the phone: startup time, jank, memory, baseline profiles, R8 and main-thread work. Use when the app feels slow or the user asks for optimization.
---

# Android Performance

## Measure first

```sh
ADB=$HOME/toolchain/platform-tools/adb
$ADB shell am force-stop com.example.app
$ADB shell am start -W -n com.example.app/.MainActivity
```

`TotalTime`/`WaitTime` are the startup numbers. Run 3 times, report the median,
not the best run.

Jank and frames:

```sh
$ADB shell dumpsys gfxinfo com.example.app framestats
$ADB shell dumpsys gfxinfo com.example.app | grep -E "Janky|percentile"
```

Memory:

```sh
$ADB shell dumpsys meminfo com.example.app
```

## Common fixes

- Move disk/network/JSON work off the main thread.
- Avoid allocating in `onDraw`/composition; cache objects.
- Use `RecyclerView`/`LazyColumn` for lists, not nested scroll views.
- Reduce overdraw; avoid large opaque backgrounds behind images.
- Enable R8 and resource shrinking for release builds.
- Compose: stable parameters, `key` in lists, avoid reading state in
  composition when it changes often.
- Add a baseline profile for startup-critical code (requires the
  `androidx.profileinstaller` dependency and a benchmark module).

## Baseline profiles (optional)

Add `androidx.profileinstaller:profileinstaller` and a `baseline-prof.txt`.
This needs dependencies; check the offline cache first, otherwise ask before
using `OC_BUILD_ONLINE=1`.

## Rules

- Do not optimize without a measurement before/after.
- Report the device, build type (debug/release), and exact command.
- Debug builds are much slower; never compare debug startup with release.
- Thermal throttling on a phone is real: let the device cool between runs.
