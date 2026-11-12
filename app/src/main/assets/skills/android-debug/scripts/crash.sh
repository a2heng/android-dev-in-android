#!/data/data/com.termux/files/usr/bin/bash
# Print crash / ANR entries for a package from the crash and main buffers.
# Usage: crash.sh <package>
set -eu
# Run it normally: auto executes the whole command in proot.
# If called from a native shell (debug fast), it re-execs itself below.
: "${HOME:?HOME is not set}"

# Fallback for a native/debug shell: re-exec in proot. When already inside
# proot, OC_ENGINE=proot and oc-proot is idempotent, so this is safe.
if [ "${OC_ENGINE:-}" != "proot" ]; then
  P="${PREFIX:-$HOME/../usr}"
  exec /system/bin/sh "$P/bin/oc-proot" "bash '$0' $*"
fi
PKG="${1:?usage: crash.sh <package>}"
ADB="${PREFIX:-$HOME/../usr}/bin/adb"
[ -x "$ADB" ] || ADB="$HOME/toolchain/platform-tools/adb"
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
