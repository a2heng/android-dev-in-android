---
name: Android UI
description: Build or improve Android UI on the phone (Compose or XML/Views), Material 3 theming, responsive layouts, edge-to-edge, dark mode and accessibility. Use for screens, layouts, themes and visual polish.
---

# Android UI

## Choose the stack

- **XML/Views + Material Components**: safest offline; the Gradle cache already
  has `appcompat` and `material` from this app's own build.
- **Jetpack Compose**: nicer, but needs Compose artifacts in the Gradle cache.
  Check with a small build; if resolution fails, use `OC_BUILD_ONLINE=1` only
  with the user's permission, or stay on XML.

## Material 3 theme

`res/values/themes.xml`:

```xml
<style name="Theme.MyApp" parent="Theme.Material3.DayNight.NoActionBar">
    <item name="colorPrimary">#4F46E5</item>
    <item name="colorSecondary">#0EA5E9</item>
</style>
```

Use `?attr/colorSurface`, `?attr/colorOnSurface`, `?attr/colorPrimary` instead
of hard-coded colors so dark mode works.

## Layout rules for a phone

- One primary action per screen; 48dp minimum touch target.
- Use `ConstraintLayout` or `LinearLayout` with weights; avoid fixed widths.
- Insets: apply `android:fitsSystemWindows="true"` or Compose
  `WindowInsets.safeDrawing` so content is not under the status/navigation bar.
- Long text: `maxLines`, `ellipsize`, and scroll containers.
- Landscape/tablet: use `-land` resources or a `SlidingPaneLayout`; do not
  assume portrait.

## Accessibility

- Every `ImageView` needs `contentDescription` (or `importantForAccessibility="no"`).
- Contrast >= 4.5:1 for body text.
- Support font scaling: use `sp`, not `dp`, for text.
- Test with TalkBack when possible.

## Compose notes

- Hoist state; keep composables small.
- Use `rememberSaveable` for UI state.
- Avoid heavy work in composition; use `LaunchedEffect`.
- Preview with `@Preview` but remember previews do not run on-device.
- Compose compiler/Kotlin versions must match the AGP/Kotlin plugin.

## Verify

1. Build with the `android-build` skill.
2. Install/launch with `android-debug/scripts/install-run.sh`.
3. Screenshot with `adb exec-out screencap -p > /tmp/screen.png`.
4. Check portrait, landscape, dark mode, and large font.
