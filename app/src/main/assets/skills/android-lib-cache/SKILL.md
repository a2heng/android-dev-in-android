---
name: Android Library Cache
description: Resolve, download and cache Maven/Gradle libraries that are not in the bundled offline cache, in a shared cache that other projects and tools on this phone can reuse. Use when a build says "Could not resolve", "No cached version", or a library is missing offline.
---

# Android Library Cache

The APK bundles a **seeded** Gradle/Maven cache. It is not a universal
mirror: Compose, Hilt, Room, Retrofit, Kotlin plugins and test libraries may
be absent. This skill downloads a missing library **once** into a shared
location so every later project and tool on the phone works offline.

## Shared locations (do not use project-local caches)

| Path | What it is | Who reuses it |
|---|---|---|
| `~/toolchain/gradle-home` | `GRADLE_USER_HOME` with `caches/modules-2` | every Gradle project |
| `~/toolchain/m2` | Maven repository layout (`group/artifact/version`) | Maven, sbt, javac, kotlinc, scripts, Gradle |
| `~/toolchain/m2/.adia-index.tsv` | coordinate, file, sha256 | audit / dedupe |
| `~/.m2/repository` | symlink to `~/toolchain/m2` | tools that hardcode `~/.m2` |

`HOME` is the app-private root (`/data/user/0/com.a2heng.adia/files/home`),
so these caches survive app updates and are shared by all projects.

What is automatic vs opt-in:

- `~/toolchain/gradle-home` is used automatically by
  `android-build/scripts/build.sh` (`GRADLE_USER_HOME`); it is the real shared
  cache for Gradle downloads.
- `~/toolchain/m2` is only populated when you run `export-gradle.sh` or
  `fetch-maven.sh`; `~/.m2/repository` only exists after `link-m2.sh`. They
  are for Maven/sbt/javac, not required for Gradle.
- Project `build/` and `.gradle/` are **not** caches; they are the main growth.
  Remove them with `android-build/scripts/clean-caches.sh --builds`.

Rule: never download into `./.gradle`, `./build`, or a per-project
`mavenLocal()`. Always use the shared paths above, otherwise the next project
downloads the same 40 MB again.

## Workflow

### 1. Confirm what is missing

```sh
bash <skill-dir>/scripts/env.sh
bash ~/.config/opencode/skills/android-build/scripts/build.sh . debug
```

Read the first `Could not resolve <group:artifact:version>` line.

### 2. Warm the shared Gradle cache (best for Android projects)

```sh
bash <skill-dir>/scripts/warm-gradle.sh /path/to/project assembleDebug
```

This runs the normal `android-build` script **online once**, with
`GRADLE_USER_HOME=~/toolchain/gradle-home`, then immediately rebuilds
**offline** to prove the cache is complete. Transitive dependencies are
resolved by Gradle, which is more reliable than hand-fetching POMs.

Ask the user before using mobile data. After this, every Gradle project that
uses the same coordinates builds offline.

### 3. Publish the Gradle cache to the Maven mirror (for non-Gradle tools)

```sh
bash <skill-dir>/scripts/export-gradle.sh
```

It copies `modules-2` jars/aars/poms into `~/toolchain/m2` so Maven, sbt,
`javac -cp`, Kotlin scripts and other software can consume the same files.

### 4. Fetch one coordinate for another tool

```sh
bash <skill-dir>/scripts/fetch-maven.sh com.squareup.okhttp3:okhttp:4.12.0
bash <skill-dir>/scripts/fetch-maven.sh androidx.room:room-runtime:2.6.1 --no-deps
```

The script tries Google Maven and Maven Central, stores the POM/JAR/AAR in
Maven layout, verifies SHA-1 when the repository publishes one, and appends
to `.adia-index.tsv`. Use `--no-deps` for a single file; transitive
resolution is best done with Gradle (step 2).

### 5. Use the mirror

Gradle — add this to the build (or pass it as an init script):

```groovy
maven { url = uri("${System.getenv('HOME')}/toolchain/m2") }
```

Maven / sbt / scripts:

```sh
export MAVEN_OPTS="-Dmaven.repo.local=$HOME/toolchain/m2"
mvn -o ...
```

One-time symlink for tools that only know `~/.m2`:

```sh
bash <skill-dir>/scripts/link-m2.sh
```

Dependency-free checking with `javac`:

```sh
javac -cp "$HOME/toolchain/m2/com/squareup/okhttp3/okhttp/4.12.0/okhttp-4.12.0.jar" App.java
```

## Environment

`scripts/env.sh` exports the canonical values; source it in ad-hoc shells:

```sh
. <skill-dir>/scripts/env.sh
echo "$ADIA_M2 $GRADLE_USER_HOME"
```

Variables:

- `ADIA_M2` — Maven mirror (default `$HOME/toolchain/m2`)
- `ADIA_GRADLE_HOME` — shared Gradle home (default `$HOME/toolchain/gradle-home`)
- `ADIA_MAVEN_REPOS` — comma-separated extra repository base URLs

## Rules

- One copy per coordinate. If the file exists and the checksum matches, do not
  download again.
- Keep caches outside the project. `scripts/make-toolchain.py` owns the
  bundled seed; the user cache is everything added at runtime.
- Do not delete `~/toolchain/gradle-home` or `~/toolchain/m2` to "clean" a
  build. Delete only the specific version directory if it is corrupt.
- Never commit `~/toolchain`, `~/toolchain/m2` or a `.aar` into git.
- Prefer explicit versions. `+` and `latest.release` make the cache ambiguous
  and break offline builds later.
- On a metered connection, ask first, then download in one batch.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `No cached version of ... available for offline mode` | step 2 with `OC_BUILD_ONLINE=1` |
| `Could not resolve` for a BOM-managed version | warm with Gradle; do not hand-fetch the BOM |
| `Failed to find target with hash string android-36` | install platform 36, see `android-build` |
| `Duplicate class` after adding a mirror | remove the old per-project cache and rebuild |
| Maven ignores the mirror | pass `-Dmaven.repo.local=$HOME/toolchain/m2` |
| Gradle still uses `~/.gradle` | `export GRADLE_USER_HOME=$HOME/toolchain/gradle-home` |

## References

- `references/repositories.md` — repository order, Google vs Central, SHA-1.
- `android-build` — the offline build entry point.
- `android-deps` — version conflicts and dependency insight.
