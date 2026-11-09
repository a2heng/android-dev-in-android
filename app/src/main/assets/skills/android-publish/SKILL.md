---
name: Android Publish
description: Distribute an Android app built on the phone: GitHub Releases, F-Droid, direct APK, signing, checksums and release notes. Use when the user asks to publish or share an APK.
---

# Android Publish

## Where to publish

This project and the apps it builds use sideload-style capabilities
(executing downloaded code, `MANAGE_EXTERNAL_STORAGE`, `REQUEST_INSTALL_PACKAGES`)
and may target an older SDK. **Do not promise Google Play availability.**

Use:

- GitHub Releases (private or public)
- F-Droid / self-hosted F-Droid repo
- direct APK download / local share

## Release checklist

1. Bump `versionCode` and `versionName`.
2. Build release with the `android-release` skill.
3. Install and test the release APK (not only debug).
4. Generate SHA-256:
   ```sh
   sha256sum app-release.apk
   ```
5. Write release notes: features, fixes, known limitations, device/ABI.
6. Upload the APK + checksum. Do not commit the APK to git.

## GitHub Release from the phone

If `gh` is available and authenticated:

```sh
gh release create v1.0.0 app-release.apk \
  --title "v1.0.0" --notes "Release notes..."
```

If not, upload through the GitHub web UI. Private repos produce private
release assets.

## F-Droid

- F-Droid requires reproducible builds and a free/open-source license.
- The app must build from source without proprietary dependencies.
- Submit a merge request to `fdroiddata` with the metadata and build recipe.
- GPL components bundled as binaries need source/notice compliance.

## Versioning

- `versionCode` monotonically increases.
- `versionName` is human-readable (`1.2.3`).
- Tag releases in git (`v1.2.3`).

## Don'ts

- Don't publish a debug-signed APK as a final release.
- Don't upload keystores or `keystore.properties`.
- Don't claim a Play Store listing or a successful F-Droid inclusion without
  evidence.
