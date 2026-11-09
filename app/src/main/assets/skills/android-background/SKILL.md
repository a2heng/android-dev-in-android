---
name: Android Background
description: Run work in the background correctly on Android: WorkManager, foreground services, alarms, wake locks and battery/Doze constraints. Use for sync, uploads, long builds or notifications.
---

# Android Background

## Choose the right mechanism

| Need | Use |
|---|---|
| Deferrable, guaranteed sync | WorkManager |
| Long user-visible task (build, download) | Foreground service |
| Exact time (alarm clock) | `AlarmManager` exact alarm |
| Periodic lightweight check | WorkManager periodic (min 15 min) |
| Immediate short work while app visible | coroutine/`lifecycleScope` |

Do not use a bare background thread or `Service` for work that must survive;
Android will kill it.

## WorkManager

```groovy
implementation 'androidx.work:work-runtime:2.9.1'
```

```kotlin
val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).build()
WorkManager.getInstance(context).enqueueUniquePeriodicWork(
    "sync", ExistingPeriodicWorkPolicy.KEEP, request)
```

Constraints: network, battery not low, charging. Workers must be idempotent.

## Foreground service

- Declare the service and a `foregroundServiceType` (e.g. `dataSync`,
  `specialUse`) in the manifest.
- Start with `ContextCompat.startForegroundService`.
- Call `startForeground` within a few seconds.
- Post a clear, ongoing notification with a stop action.
- Request `POST_NOTIFICATIONS` on Android 13+.
- Release wake locks in `finally`.

## Doze and OEM killers

- Do not fight Doze with exact alarms unless the feature truly needs it.
- Ask the user to disable battery optimization for long-running dev tools.
- OPPO/ColorOS, Xiaomi/HyperOS and others kill background processes
  aggressively; test on the real device and document the whitelist step.

## This project

The opencode server uses a foreground service and a watchdog. If it stops in
the background, check the notification, battery optimization, and the
watchdog interval in Options.
