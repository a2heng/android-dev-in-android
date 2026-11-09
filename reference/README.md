# Reference projects

These are **read-only references**, registered as git submodules. They are not
linked into the app, not imported by Gradle, and not part of our source tree.
Do not copy code from a GPL project into this repository.

Initialize only the ones you need (shallow):

```sh
git submodule update --init --depth 1 reference/opencode-android
git submodule update --init --depth 1 reference/vexel
```

| Path | Upstream | License | Why it is here |
|---|---|---|---|
| `opencode-android` | [Dajeovv/opencode-android](https://github.com/Dajeovv/opencode-android) | MIT | Base fork / native opencode-in-APK reference |
| `opencode` | [anomalyco/opencode](https://github.com/anomalyco/opencode) | MIT | Official opencode runtime and API |
| `CodeOnTheGo` | [appdevforall/CodeOnTheGo](https://github.com/appdevforall/CodeOnTheGo) | GPL-3.0 | Mature on-device Gradle IDE reference |
| `AndroidIDE` | [AndroidIDEOfficial/AndroidIDE](https://github.com/AndroidIDEOfficial/AndroidIDE) | GPL-3.0 | Archived predecessor of CoGo |
| `termux-app` | [termux/termux-app](https://github.com/termux/termux-app) | GPL-3.0* | Terminal/toolchain bootstrap reference |
| `proot-distro` | [termux/proot-distro](https://github.com/termux/proot-distro) | GPL-3.0 | Container/userland reference |
| `vexel` | [CrimsonCurious/vexel](https://github.com/CrimsonCurious/vexel) | MIT | Non-Gradle APK builder reference |
| `termux-android-studio` | [poordevcode/termux-android-studio](https://github.com/poordevcode/termux-android-studio) | Apache-2.0 | aarch64 aapt2 and Gradle-on-Termux scripts |
| `opencode-termux-native` | [Thr45hx/opencode-termux-native](https://github.com/Thr45hx/opencode-termux-native) | MIT | glibc/patchelf native opencode recipe |
| `opencode-android-apk` | [Zar-Manah/opencode-android-apk](https://github.com/Zar-Manah/opencode-android-apk) | GPL-3.0 | Closest prior art: opencode + JDK + SDK + Gradle |
| `android-sdk-tools` | [lzhiyong/android-sdk-tools](https://github.com/lzhiyong/android-sdk-tools) | Apache-2.0 | Prebuilt aarch64 build-tools (aapt2) |

\* `termux-app` is GPLv3; `terminal-view` / `terminal-emulator` are Apache-2.0
exceptions. Check the upstream `LICENSE.md` before reusing anything.

## License rule for this repository

Our own code is MIT (see `LICENSE`). GPL projects are kept **only** as
submodules under `reference/`. If you need an idea from a GPL project,
re-implement it from the documented behavior, not by copying source.

Prebuilt binaries that are bundled into the APK (Termux packages, proot, JDK,
etc.) have their own licenses; see `THIRD_PARTY_LICENSES.md`.
