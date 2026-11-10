---
name: Android Release
description: Produce a signed release APK/AAB on the phone: keystore, signing config, R8/minify, versioning, verification and checksums. Use when the user asks for a release build or a distributable APK.
---

# Android Release

## 1. Create a keystore (once)

Run inside the app sandbox:

```sh
keytool -genkeypair -v \
  -keystore "$HOME/keys/release.jks" \
  -alias release -keyalg RSA -keysize 4096 -validity 10000 \
  -storepass "$STORE_PASS" -keypass "$KEY_PASS" \
  -dname "CN=MyApp, OU=Mobile, O=Example, L=City, S=State, C=CN"
```

Never commit the keystore or passwords. Keep them in `$HOME/keys` and a local
`keystore.properties` that is gitignored.

## 2. Signing config

`app/build.gradle`:

```groovy
def props = new Properties()
def f = rootProject.file('keystore.properties')
if (f.exists()) props.load(new FileInputStream(f))
android {
    signingConfigs {
        release {
            storeFile rootProject.file(props['storeFile'])
            storePassword props['storePassword']
            keyAlias props['keyAlias']
            keyPassword props['keyPassword']
        }
    }
    buildTypes {
        release {
            minifyEnabled true
            shrinkResources true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'),
                          'proguard-rules.pro'
            signingConfig signingConfigs.release
        }
    }
}
```

`keystore.properties`:

```properties
storeFile=../keys/release.jks
storePassword=...
keyAlias=release
keyPassword=...
```

## 3. Build and verify

```sh
bash ~/.config/opencode/skills/android-build/scripts/build.sh . release
```

Then:

```sh
APK=app/build/outputs/apk/release/app-release.apk
$HOME/toolchain/android-sdk/build-tools/36.0.0/apksigner verify --print-certs "$APK" || true
sha256sum "$APK"
```

`apksigner` may not be present in the minimal build-tools bundle; if so, use
Gradle's signing output and report the SHA-256 instead of inventing a verify
result.

## 4. Rules

- Bump `versionCode` and `versionName` for every release.
- Keep debug and release signing keys separate.
- Test the release APK, not only debug: install it and run the `android-debug`
  skill.
- AAB is only useful for stores; this project distributes APKs directly.
- Do not promise Google Play availability: this app/toolchain uses
  sideload-style capabilities and older target-SDK behaviors.
