#!/data/data/com.termux/files/usr/bin/bash
# Build an Android project on-device.
# Usage: build.sh <project-dir> [debug|release] [gradle-task]
set -eu

PROJECT="${1:-$PWD}"
VARIANT="${2:-debug}"
TASK="${3:-}"

: "${HOME:?HOME is not set}"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Resolve PREFIX instead of trusting the caller. Inside proot the sandbox
# prefix is mounted at the Termux path; in a native shell it is the sibling
# "usr" directory of HOME (files/home -> files/usr). Do not export the real
# Termux path by hand in a native shell: it does not exist there.
if [ -z "${PREFIX:-}" ] || [ ! -d "$PREFIX" ]; then
  if [ -d /data/data/com.termux/files/usr ]; then
    PREFIX=/data/data/com.termux/files/usr
  elif [ -d "$HOME/../usr" ]; then
    PREFIX="$(cd "$HOME/../usr" && pwd)"
  else
    echo "[android-build] cannot find the toolchain prefix (PREFIX/HOME/../usr)." >&2
    echo "[android-build] run inside the app sandbox, or set PREFIX explicitly." >&2
    exit 2
  fi
fi
export PREFIX

# The Termux JDK defaults java.io.tmpdir to $PREFIX/tmp. That directory can be
# awkward to create inside some proot setups, so point Gradle's JVMs at the
# app cache tmp instead (which always exists and is writable).
GRADLE_TMP="$HOME/.cache/tmp"
mkdir -p "$GRADLE_TMP" "$HOME/.cache" 2>/dev/null || true
mkdir -p "$PREFIX/tmp" "$PREFIX/var/tmp" 2>/dev/null || true
export TMPDIR="$GRADLE_TMP"

BUNDLED="$HOME/toolchain"
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$BUNDLED/android-sdk}}"
BT_VERSION="35.0.2"
AAPT2="$SDK/build-tools/$BT_VERSION/aapt2"

JAVA_HOME="${JAVA_HOME:-$PREFIX/lib/jvm/java-21-openjdk}"
if [ ! -x "$JAVA_HOME/bin/java" ]; then
  JAVA_HOME="$(ls -d "$PREFIX"/lib/jvm/java-21* 2>/dev/null | head -n 1 || true)"
fi
if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
  echo "[android-build] JDK 21 not installed. Run scripts/setup-toolchain.sh first." >&2
  exit 2
fi
if [ ! -x "$AAPT2" ]; then
  echo "[android-build] aapt2 not installed at $AAPT2. Run scripts/setup-toolchain.sh first." >&2
  exit 2
fi
if [ ! -f "$SDK/platforms/android-35/android.jar" ]; then
  echo "[android-build] Android platform 35 missing under $SDK/platforms." >&2
  exit 2
fi

cd "$PROJECT"
if [ ! -f ./gradlew ]; then
  echo "[android-build] $PWD does not contain a gradlew wrapper." >&2
  exit 3
fi

# AGP only needs this when the project has no local.properties of its own.
if [ ! -f ./local.properties ]; then
  printf 'sdk.dir=%s\n' "$SDK" > ./local.properties
fi

if [ -z "$TASK" ]; then
  case "$VARIANT" in
    debug|Debug) TASK="assembleDebug" ;;
    release|Release) TASK="assembleRelease" ;;
    *) TASK="assemble$(printf '%s' "$VARIANT" | cut -c1 | tr '[:lower:]' '[:upper:]')$(printf '%s' "$VARIANT" | cut -c2-)" ;;
  esac
fi

export JAVA_HOME
export ANDROID_HOME="$SDK"
export ANDROID_SDK_ROOT="$SDK"
export PATH="$SDK/platform-tools:$SDK/build-tools/$BT_VERSION:$JAVA_HOME/bin:$PATH"

# Prefer the Gradle distribution and dependency cache shipped in the APK.
# Do not use bin/gradle: its quoting breaks under Android's mksh when it
# builds the JVM argument list. Launch GradleMain directly instead.
GRADLE_HOME="$BUNDLED/gradle/gradle-9.8.1"
GRADLE_MAIN="$GRADLE_HOME/lib/gradle-gradle-cli-main-9.8.1.jar"
if [ -f "$GRADLE_MAIN" ] && [ "${OC_USE_WRAPPER:-0}" != "1" ]; then
  GRADLE_CMD=("$JAVA_HOME/bin/java" "-Djava.io.tmpdir=$GRADLE_TMP"
    -classpath "$GRADLE_MAIN" org.gradle.launcher.GradleMain)
  if [ -d "$BUNDLED/gradle-home/caches/modules-2" ]; then
    export GRADLE_USER_HOME="$BUNDLED/gradle-home"
  fi
  OFFLINE="--offline"
  if [ "${OC_BUILD_ONLINE:-0}" = "1" ]; then
    OFFLINE=""
  fi
else
  GRADLE_CMD=(sh ./gradlew)
  OFFLINE=""
fi

# AGP 8.5 defaults to build-tools 34.0.0, which is not in the bundle.
# Force the bundled 35.0.2 for every Android module without touching the
# project's build files.
INIT="$BUNDLED/init.gradle"
cat > "$INIT" <<'EOF'
allprojects { p ->
    ['com.android.application', 'com.android.library'].each { id ->
        p.plugins.withId(id) {
            def a = p.extensions.findByName('android')
            if (a != null) {
                a.buildToolsVersion = '35.0.2'
            }
        }
    }
}
EOF

echo "[android-build] project : $PWD"
echo "[android-build] task    : $TASK"
echo "[android-build] java    : $JAVA_HOME"
echo "[android-build] sdk     : $SDK"
echo "[android-build] aapt2   : $AAPT2"
echo "[android-build] gradle  : ${GRADLE_CMD[*]} ${OFFLINE:-online}"

# Project files live on a noexec mount: never run ./gradlew directly.
"${GRADLE_CMD[@]}" --no-daemon --console=plain $OFFLINE \
  -I "$INIT" \
  -Dorg.gradle.jvmargs="-Xmx3g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8 -Djava.io.tmpdir=$GRADLE_TMP" \
  -Pandroid.suppressUnsupportedCompileSdk=35 \
  -Pandroid.aapt2FromMavenOverride="$AAPT2" \
  "$TASK"

# Find the newest APK produced by this task.
APK="$(find . -path "*/build/outputs/apk/*" -name '*.apk' -type f 2>/dev/null | sort | tail -n 1 || true)"
if [ -z "$APK" ]; then
  APK="$(find . -path "*/build/outputs/*" -name '*.apk' -type f 2>/dev/null | sort | tail -n 1 || true)"
fi
if [ -n "$APK" ]; then
  echo
  echo "APK=$(cd "$(dirname "$APK")" && pwd)/$(basename "$APK")"
  echo
  echo "[android-build] 16 KB page-size check:"
  STRICT="${ADIA_16KB_STRICT:-0}"
  case "$VARIANT" in release|Release) [ -z "${ADIA_16KB_STRICT:-}" ] && STRICT=1 ;; esac
  case "$TASK" in assembleRelease|bundleRelease) [ -z "${ADIA_16KB_STRICT:-}" ] && STRICT=1 ;; esac
  if [ "$STRICT" = "1" ]; then
    python3 "$SCRIPT_DIR/check-16kb.py" "$APK"
  else
    python3 "$SCRIPT_DIR/check-16kb.py" "$APK" || true
  fi
else
  echo "[android-build] build finished but no APK was found." >&2
  exit 4
fi
