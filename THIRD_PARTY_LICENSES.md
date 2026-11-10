# Third-party licenses and notices

This repository's own source code is MIT (see `LICENSE`). The app also contains
or builds against third-party software. This file is a summary, not legal advice. Full license texts are in
`LICENSES/`; see `NOTICE` for the GPL/LGPL source offer.

## Source code in this repository

| Component | License | Notes |
|---|---|---|
| Base app (`app/`, originally Dajeovv/opencode-android) | MIT | Copyright is retained in `LICENSE`; see upstream `AUTHORSHIP.md` |
| Our additions (skills, toolchain installer, mirror support) | MIT | Same `LICENSE` |
| `reference/**` submodules | upstream licenses | Not linked into the app; see `reference/README.md` |

No GPL source code from `reference/` is copied into `app/` or `scripts/`.

## Bundled JavaScript

| Component | License | Notes |
|---|---|---|
| core-js 3.51.0 segment (`app/src/main/assets/oc-corejs.js`, generated) | MIT | https://github.com/zloirock/core-js — only the modules listed in `scripts/make-webview-shims.js`; regenerated with that script, header kept in the file |

## Prebuilt binaries shipped in the APK

| Component | License | Source / notice |
|---|---|---|
| opencode (`libopencode2_bin.so`) | MIT | https://github.com/anomalyco/opencode |
| musl libc loader (`libld_musl.so`) | MIT | https://musl.libc.org/ |
| proot (`libproot.so`, `libproot_loader.so`) | GPL-2.0 | https://github.com/termux/proot |
| talloc (`libtalloc.so`) | LGPL-3.0 (or later) | https://talloc.samba.org/ |
| ripgrep (`librg.so`) | MIT / Unlicense | https://github.com/BurntSushi/ripgrep |
| libc++ (`libc++_shared.so`) | Apache-2.0 with LLVM exception | https://libcxx.llvm.org/ |
| libstdc++ / libgcc (`libstdcxx.so`, `libgcc_s.so`) | GPL-3.0 with GCC runtime exception | https://gcc.gnu.org/ |
| Termux `.deb` packages (`assets/toolbox/`, generated) | per package (GPL/LGPL/Apache/MIT/…) | https://github.com/termux/termux-packages, https://packages.termux.dev |
| OpenJDK 21 (`openjdk-21` Termux package) | GPL-2.0 with Classpath Exception | https://openjdk.org/ |
| Android platform 35 (`android.jar`, generated asset) | Android SDK License | https://developer.android.com/studio/terms |
| aapt2 (`build-tools/35.0.2/aapt2`, generated asset) | Apache-2.0 | https://github.com/lzhiyong/android-sdk-tools |
| Gradle 9.8.1 distribution (generated asset) | Apache-2.0 | https://gradle.org/ |
| Gradle dependency cache (generated asset) | per artifact | Maven metadata retains licenses |

The generated assets are **not committed** to this repository
(`app/src/main/assets/toolbox/` and `app/src/main/assets/toolchain/` are
gitignored). They are produced by the scripts in `scripts/` for local builds.

## GPL / LGPL compliance

Several bundled binaries are GPL or LGPL (Termux packages, proot, OpenJDK).
Distributing an APK that contains them means you must:

- keep the copyright and license notices;
- provide the corresponding source code, or a written offer to provide it;
- not impose additional restrictions on the GPL parts.

The easiest path is to point users at the exact upstream sources and versions:

- Termux packages: `https://github.com/termux/termux-packages` and
  `https://packages.termux.dev` (the `.deb` metadata contains the version);
- proot: `https://github.com/termux/proot`;
- OpenJDK: `https://github.com/termux/termux-packages/tree/master/packages/openjdk-21`;
- GCC runtime: `https://gcc.gnu.org/`.

If you publish a release APK, include a `NOTICE`/source-offer page next to the
APK. Do not relicense GPL components under MIT.

## Android SDK

The Android SDK platform/build-tools are subject to Google's Android SDK
License. Redistribution of SDK components may require additional permission.
For a public release, review the current SDK terms or have users provide their
own SDK components.

## Trademarks

Android is a trademark of Google LLC. Termux is a project of the Termux
community. opencode is a project of Anomaly. This project is unofficial and is
not endorsed by any of them.
