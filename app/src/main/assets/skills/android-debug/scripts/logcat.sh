#!/data/data/com.termux/files/usr/bin/bash
# Capture logcat for a package after launching it.
# Usage: logcat.sh <package> [seconds] [activity]
set -eu
# Everything runs inside proot; no wrapper/re-exec is needed.
: "${HOME:?HOME is not set}"
: "${OC_ENGINE:=proot}"
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
