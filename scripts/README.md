# Offline bundle scripts

These scripts generate the large, gitignored assets that make the APK usable
without network:

```text
app/src/main/jniLibs/arm64-v8a/libopencode2_bin.so   # fetch-opencode.py
app/src/main/assets/toolbox/*.deb                    # fetch-toolbox.py
app/src/main/assets/toolchain/toolchain.zip          # make-toolchain.py
```

Run from the repository root:

```sh
python scripts/fetch-opencode.py --version 1.19.5
python scripts/fetch-toolbox.py
python scripts/make-toolchain.py
./gradlew :app:assembleDebug
```

## Mirrors

`fetch-toolbox.py` defaults to the TUNA Termux mirror and falls back to USTC,
BFSU and the official `packages.termux.dev`. Override it with:

```sh
python scripts/fetch-toolbox.py --repo https://mirrors.ustc.edu.cn/termux/apt/termux-main
```

For GitHub downloads on a restricted network, set a proxy in the environment
(Python's `urllib` honors `HTTPS_PROXY`):

```sh
HTTPS_PROXY=http://127.0.0.1:10808 python scripts/fetch-opencode.py
```

## Notes

- `fetch-toolbox.py` resolves dependencies from the Termux `Packages.gz`
  index, downloads every `.deb` as `<package>.deb`, and writes
  `bootstrap.txt` + `packages.txt` for the app's offline installer.
- `openjdk-21` pulls audio/X11 dependencies; they are included so `dpkg`
  can configure the JDK. If you need a smaller build, remove `openjdk-21`
  from `DEFAULT_SET` and install it later, but then the APK is no longer
  fully offline.
- `make-toolchain.py` bundles Android platform 35, aarch64 aapt2 35.0.2,
  Gradle 9.8.1 and the local Gradle dependency cache. The cache is optional;
  without it Gradle will need network unless the project's dependencies are
  already cached.
- The generated directories are ignored by git. Do not commit GPL binaries
  into the MIT source tree.
