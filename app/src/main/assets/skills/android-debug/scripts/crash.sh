#!/data/data/com.termux/files/usr/bin/bash
# Print crash / ANR entries for a package from the crash and main buffers.
# Usage: crash.sh <package>
set -eu
: "${HOME:?HOME is not set}"
PKG="${1:?usage: crash.sh <package>}"
ADB="$HOME/toolchain/platform-tools/adb"
[ -x "$ADB" ] || { echo "adb not found at $ADB" >&2; exit 2; }

"$ADB" devices | grep -q "device$" || {
  echo "no adb device. Enable Wireless debugging, pair and connect first." >&2
  exit 3
}

echo "===== crash buffer ====="
"$ADB" logcat -d -b crash -v threadtime | tail -n 200 || true
echo
echo "===== main: FATAL / ANR / $PKG ====="
"$ADB" logcat -d -v threadtime | grep -E "FATAL EXCEPTION|AndroidRuntime|ANR in|$PKG|Caused by|signal [0-9]+" | tail -n 200 || true
