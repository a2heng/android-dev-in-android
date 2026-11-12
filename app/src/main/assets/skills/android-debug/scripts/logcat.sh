#!/data/data/com.termux/files/usr/bin/bash
# Capture logcat for a package after launching it.
# Usage: logcat.sh <package> [seconds] [activity]
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
PKG="${1:?usage: logcat.sh <package> [seconds] [activity]}"
SECS="${2:-12}"
ACT="${3:-}"
ADB="${PREFIX:-$HOME/../usr}/bin/adb"
[ -x "$ADB" ] || ADB="$HOME/toolchain/platform-tools/adb"
[ -x "$ADB" ] || { echo "adb not found at $ADB" >&2; exit 2; }

"$ADB" devices | grep -q "device$" || {
  echo "no adb device. Enable Wireless debugging, pair and connect first." >&2
  exit 3
}

"$ADB" logcat -c
if [ -n "$ACT" ]; then
  "$ADB" shell am start -n "$ACT" >/dev/null
else
  "$ADB" shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null
fi
echo "[debug] capturing ${SECS}s for $PKG..."
sleep "$SECS"
"$ADB" logcat -d -v threadtime | grep -E "$PKG|AndroidRuntime|FATAL|ANR in|libc|DEBUG|Choreographer" | tail -n 200 || true
