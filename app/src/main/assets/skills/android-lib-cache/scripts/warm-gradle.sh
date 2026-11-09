#!/data/data/com.termux/files/usr/bin/bash
# Download a project's dependencies once into the shared cache, then prove
# that an offline build works. Usage:
#   warm-gradle.sh <project-dir> [assembleDebug]
set -eu

PROJECT="${1:-$PWD}"
TASK="${2:-assembleDebug}"
SKILL_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=env.sh
. "$SKILL_DIR/env.sh"

if [ ! -d "$PROJECT" ]; then
  echo "[lib-cache] project not found: $PROJECT" >&2
  exit 2
fi

BUILD_SH="${ADIA_BUILD_SH:-$HOME/.config/opencode/skills/android-build/scripts/build.sh}"
if [ ! -f "$BUILD_SH" ]; then
  BUILD_SH="$HOME/.config/opencode-android/skills/android-build/scripts/build.sh"
fi
if [ ! -f "$BUILD_SH" ]; then
  echo "[lib-cache] android-build/scripts/build.sh not found; set ADIA_BUILD_SH" >&2
  exit 3
fi

case "$TASK" in
  *Release*|*release*) VARIANT=release ;;
  *) VARIANT=debug ;;
esac

echo "[lib-cache] warming shared Gradle cache (online, once)"
echo "[lib-cache] project: $PROJECT"
echo "[lib-cache] task:    $TASK"
OC_BUILD_ONLINE=1 ADIA_GRADLE_HOME="$ADIA_GRADLE_HOME" \
  bash "$BUILD_SH" "$PROJECT" "$VARIANT" "$TASK"

echo
echo "[lib-cache] verifying the same task offline"
OC_BUILD_ONLINE=0 ADIA_GRADLE_HOME="$ADIA_GRADLE_HOME" \
  bash "$BUILD_SH" "$PROJECT" "$VARIANT" "$TASK"

echo
echo "[lib-cache] shared cache size:"
du -sh "$ADIA_GRADLE_HOME" 2>/dev/null || true
echo "[lib-cache] next: export it for non-Gradle tools with export-gradle.sh"
