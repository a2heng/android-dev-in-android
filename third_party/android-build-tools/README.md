# Android build-tools helper jar

`core-lambda-stubs.jar` is taken from Android SDK Build-Tools 34.0.0
(`$ANDROID_HOME/build-tools/34.0.0/core-lambda-stubs.jar`).

Android Gradle Plugin requires this jar when desugaring Java lambdas. The
aarch64 build-tools package from `lzhiyong/android-sdk-tools` ships only the
native binaries, so we vendor this small platform-independent jar and add it to
the offline `build-tools/35.0.2` directory.

It is part of the Android SDK, distributed under the Android SDK License /
Apache-2.0 terms. See the upstream `NOTICE.txt` in the Android SDK build-tools
package.

`aapt2-aarch64-16kb` is the self-built aarch64 `aapt2` from
[poordevcode/termux-android-studio](https://github.com/poordevcode/termux-android-studio)
(Apache-2.0). It supports Android SDK 35/36/37 resources and is linked with
16 KB page-size alignment, unlike the minimal `aapt2` in
`lzhiyong/android-sdk-tools` (4 KB aligned).
