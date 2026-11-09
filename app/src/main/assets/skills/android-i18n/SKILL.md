---
name: Android i18n
description: Localize an Android app: string resources, Chinese/English/Spanish, plurals, RTL and locale testing. Use when the user asks for Chinese UI or multiple languages.
---

# Android i18n

## Put every user-visible string in resources

`app/src/main/res/values/strings.xml`:

```xml
<resources>
    <string name="app_name">My App</string>
    <string name="welcome">Welcome</string>
</resources>
```

`app/src/main/res/values-zh/strings.xml`:

```xml
<resources>
    <string name="app_name">我的应用</string>
    <string name="welcome">欢迎</string>
</resources>
```

Use `@string/welcome` in XML and `getString(R.string.welcome)` in code.
Never hard-code Chinese/English in layouts.

## Plurals and formatting

```xml
<plurals name="files">
    <item quantity="one">%d file</item>
    <item quantity="other">%d files</item>
</plurals>
```

`getResources().getQuantityString(R.plurals.files, n, n)`.

## RTL

- Use `start`/`end` instead of `left`/`right`.
- Use `android:layout_marginStart`, `paddingStart`.
- Test with a pseudo-RTL locale if the app supports Arabic/Hebrew.

## Locale testing

```sh
ADB=$HOME/toolchain/platform-tools/adb
$ADB shell am start -a android.settings.LOCALE_SETTINGS
```

Or use per-app languages on Android 13+ (Settings → Apps → My App → Language).
After changing the locale, relaunch the app and take a screenshot.

## Chinese specifics

- Use `values-zh` for Simplified/Traditional fallback; add `values-zh-rCN` /
  `values-zh-rTW` if the wording differs.
- Keep punctuation Chinese (，。！？) in Chinese strings; do not reuse English
  punctuation.
- Avoid translating brand names, file paths, model IDs, code identifiers.
- This launcher's native UI uses `Zh.java`; the opencode Web UI follows the
  browser/server language.

## Verify

1. Build with `android-build`.
2. Install/launch with `android-debug/scripts/install-run.sh`.
3. Screenshot at least Chinese and English.
4. Check for clipped text and missing translations (`MissingTranslation` lint).
