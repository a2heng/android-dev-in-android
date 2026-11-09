# Repositories and cache behavior

## Order

`resolve_maven.py` tries repositories in this order:

1. `ADIA_MAVEN_REPOS` (comma-separated, optional)
2. Google Maven — `https://dl.google.com/dl/android/maven2`
   (AndroidX, Material, AGP, Google Play libraries)
3. Maven Central — `https://repo1.maven.org/maven2`
   (Kotlin, OkHttp, Retrofit, Gson, JUnit, most JVM libraries)

Google-first matters for `androidx.*`, `com.android.*`, `com.google.android.*`.
Central usually has no AndroidX artifacts.

A mirror can be added without code changes:

```sh
export ADIA_MAVEN_REPOS="https://maven.aliyun.com/repository/public"
bash scripts/fetch-maven.sh com.squareup.okhttp3:okhttp:4.12.0
```

## Verification

Every downloaded POM/JAR/AAR is recorded in `$ADIA_M2/.adia-index.tsv` with
its SHA-256. When the repository publishes `<file>.sha1`, the script compares
it and warns on a mismatch. A file that already exists is not downloaded
again; delete only the specific version directory if it is corrupt.

## Transitive dependencies

Android projects should use `warm-gradle.sh`: Gradle resolves versions,
BOMs, variant attributes and AAR metadata correctly, then stores everything
in `~/toolchain/gradle-home`. `export-gradle.sh` publishes those files to the
Maven mirror.

`resolve_maven.py` also walks POM `compile`/`runtime` dependencies, but it
does not import BOMs (`scope=import`) or Gradle module metadata. For a BOM
project, warm with Gradle instead of hand-fetching.

## What not to cache

- Do not cache credentials, `settings.xml` or `.netrc` in the mirror.
- Do not commit the mirror; it is runtime state under `HOME`.
- Do not mix a project-local `.gradle` cache with the shared one.
