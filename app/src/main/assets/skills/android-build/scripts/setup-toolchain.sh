#!/data/data/com.termux/files/usr/bin/bash
# On-device Android build toolchain setup.
# Prefers the offline bundle extracted from assets; falls back to network.
set -eu

: "${PREFIX:?PREFIX is not set; run this inside the app sandbox}"
: "${HOME:?HOME is not set}"

BUNDLED="$HOME/toolchain"
SDK="$BUNDLED/android-sdk"
BT_VERSION="35.0.2"
BT="$SDK/build-tools/$BT_VERSION"
CACHE="$HOME/.cache/oc-toolchain"
PLATFORM_ZIP_URL="https://dl.google.com/android/repository/platform-35_r02.zip"
AAPT2_ZIP_URL="https://github.com/lzhiyong/android-sdk-tools/releases/download/35.0.2/android-sdk-tools-static-aarch64.zip"

say() { printf '\n[android-build] %s\n' "$*"; }

# The APK's ToolchainInstaller runs in parallel with the server. Give it up
# to ~3 minutes before deciding that this is a source build without a bundle.
n=0
while [ ! -f "$BUNDLED/.installed" ] && [ "$n" -lt 90 ]; do
  sleep 2
  n=$((n + 1))
done

JAVA_HOME="$PREFIX/lib/jvm/java-21-openjdk"
if [ ! -x "$JAVA_HOME/bin/java" ]; then
  JAVA_HOME="$(ls -d "$PREFIX"/lib/jvm/java-21* 2>/dev/null | head -n 1 || true)"
fi

if [ -f "$BUNDLED/.installed" ]; then
  say "Using bundled offline SDK/Gradle."
  if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
    # The toolbox normally installs openjdk-21 from the bundled .deb.
    say "JDK missing; installing openjdk-21 from the bundled/online toolbox."
    pkg install -y openjdk-21 >/dev/null
    JAVA_HOME="$PREFIX/lib/jvm/java-21-openjdk"
  fi
else
  say "No offline toolchain bundle; falling back to network."
  mkdir -p "$SDK/platforms" "$BT" "$CACHE" "$HOME/.android"
  pkg install -y openjdk-21 curl python >/dev/null

  if [ ! -f "$SDK/platforms/android-35/android.jar" ]; then
    say "Downloading Android platform 35 (~64 MB)..."
    curl -fL --retry 3 --retry-delay 2 -o "$CACHE/platform-35_r02.zip" "$PLATFORM_ZIP_URL"
    python3 -m zipfile -e "$CACHE/platform-35_r02.zip" "$SDK/platforms/"
    rm -f "$CACHE/platform-35_r02.zip"
  fi

  if [ ! -x "$BT/aapt2" ]; then
    say "Downloading arm64 build-tools (aapt2 35.0.2)..."
    curl -fL --retry 3 --retry-delay 2 -o "$CACHE/android-sdk-tools.zip" "$AAPT2_ZIP_URL"
    rm -rf "$CACHE/android-sdk-tools"
    mkdir -p "$CACHE/android-sdk-tools"
    python3 -m zipfile -e "$CACHE/android-sdk-tools.zip" "$CACHE/android-sdk-tools/"
    cp "$CACHE/android-sdk-tools/build-tools/aapt2" "$BT/aapt2"
    cp "$CACHE/android-sdk-tools/build-tools/zipalign" "$BT/zipalign" 2>/dev/null || true
    chmod 755 "$BT/aapt2" "$BT/zipalign" 2>/dev/null || true
    rm -rf "$CACHE/android-sdk-tools" "$CACHE/android-sdk-tools.zip"
  fi
fi

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
  echo "[android-build] ERROR: openjdk-21 not found under $PREFIX/lib/jvm" >&2
  exit 2
fi
if [ ! -x "$BT/aapt2" ]; then
  echo "[android-build] ERROR: aapt2 not found at $BT/aapt2" >&2
  exit 2
fi
if [ ! -f "$SDK/platforms/android-35/android.jar" ]; then
  echo "[android-build] ERROR: android.jar (platform 35) not found under $SDK/platforms" >&2
  exit 2
fi

say "Toolchain ready:"
echo "  JAVA_HOME=$JAVA_HOME"
echo "  ANDROID_HOME=$SDK"
echo "  AAPT2=$BT/aapt2"
"$JAVA_HOME/bin/java" -version 2>&1 | sed 's/^/  /'
"$BT/aapt2" version 2>&1 | sed 's/^/  /' || true
