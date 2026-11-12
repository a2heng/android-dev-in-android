#!/data/data/com.termux/files/usr/bin/bash
# Install and launch an APK, then print the first log lines for its package.
# Usage: install-run.sh <apk> <package> [activity]
set -eu
# IMPORTANT: run through proot, e.g.
#   oc-proot 'bash <this-script> <args>'
# adb lives in app-private storage; native exec gives Permission denied.
: "${HOME:?HOME is not set}"

# Re-exec inside proot if launched natively. adb is in app-private storage;
# native exec returns Permission denied. When already inside proot, OC_ENGINE=proot
# and oc-proot itself is idempotent, so this is safe.
if [ "${OC_ENGINE:-}" != "proot" ]; then
  P="${PREFIX:-$HOME/../usr}"
  exec /system/bin/sh "$P/bin/oc-proot" "bash '$0' $*"
fi
APK="${1:?usage: install-run.sh <apk> <package> [activity]}"
PKG="${2:?usage: install-run.sh <apk> <package> [activity]}"
ACT="${3:-}"
ADB="${PREFIX:-$HOME/../usr}/bin/adb"
[ -x "$ADB" ] || ADB="$HOME/toolchain/platform-tools/adb"
[ -x "$ADB" ] || { echo "adb not found at $ADB" >&2; exit 2; }

"$ADB" devices | grep -q "device$" || {
  echo "no adb device. Enable Wireless debugging, pair and connect first." >&2
  echo "  $ADB pair 127.0.0.1:<pair-port> <code>" >&2
  echo "  $ADB connect 127.0.0.1:<port>" >&2
  exit 3
}

echo "[debug] installing $APK"
"$ADB" install -r -t "$APK"
"$ADB" logcat -c
if [ -n "$ACT" ]; then
  "$ADB" shell am start -n "$ACT"
else
  "$ADB" shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null
fi
sleep 4
echo "[debug] logcat for $PKG"
"$ADB" logcat -d -v threadtime | grep -E "$PKG|AndroidRuntime|FATAL|ANR in|libc|DEBUG" | tail -n 120 || true
