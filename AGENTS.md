# Adia repository rules (read before changing paths or commands)

1. **Read [`docs/paths.md`](docs/paths.md) and [`docs/commands.md`](docs/commands.md)
   first.** They are the contract for paths, proot mounts and command routing.
2. **Never execute a Termux ELF outside proot.** The only exceptions are the
   leaf tools in `CommandResolver.SAFE_NATIVE` (they are launched with
   `linker64` + `LD_LIBRARY_PATH`). If in doubt, route to proot.
3. **Do not add a command to `SAFE_NATIVE`** unless it never `exec`s another
   program. Interpreters, build tools, package managers and git helpers stay out.
4. **Every startup-essential file** must be in `LocalToolbox.ESSENTIALS`,
   `ESSENTIAL_ROOTS` (fetch script), `bootstrap.txt`, and `docs/paths.md`.
5. **`bootstrap.txt` must contain `bash` and `readline`** so a failed dpkg
   cannot leave a prefix that says `library "libreadline.so.8" not found`.
6. **Never hardcode** `com.a2heng.adia`, `com.termux`, `nativeLibraryDir` or a
   JDK/SDK path in shell scripts. Use `$HOME`, `$PREFIX`, `$TMPDIR`; generate
   `nativeLibraryDir` paths at runtime.
7. **`oc-proot` must stay idempotent** (`OC_ENGINE=proot` does not nest).
8. **Build/verify locally with the pinned toolchain:**

   ```sh
   source /d/AndroidDevToolchain/env.sh
   cd /d/android-dev-in-android
   python scripts/verify-toolbox.py
   ./gradlew :app:assembleDebug
   ```

9. **Before tagging a release:** `python scripts/verify-toolbox.py` must pass;
   release CI runs it too. Do not tag if it fails.
10. **Update both docs** when changing behavior. The 60-second acceptance test
    is in `docs/paths.md`; run it on a real device (`oc-engine status`,
    `oc-doctor`, `bash -lc 'git --version'`, `python3 --version`,
    `pkg install -y jq`).

The Windows toolchain lives at `D:\AndroidDevToolchain`; its README explains
the single JDK/SDK/PATH setup.
