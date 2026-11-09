#!/data/data/com.termux/files/usr/bin/bash
# Audit and clean Adia caches.
#   clean-caches.sh            safe transient files only
#   clean-caches.sh --builds   also remove workspace/*/build and .gradle
#   clean-caches.sh --all      also remove the shared Gradle/Maven caches
set -eu

: "${HOME:?HOME is not set}"
BUILDS=0
ALL=0
for arg in "$@"; do
  case "$arg" in
    --builds) BUILDS=1 ;;
    --all) ALL=1; BUILDS=1 ;;
    -h|--help)
      echo "usage: $0 [--builds] [--all]"
      exit 0
      ;;
    *) echo "unknown option: $arg" >&2; exit 2 ;;
  esac
done

PREFIX_DIR="$(cd "$HOME/../usr" 2>/dev/null && pwd || true)"

show() {
  echo "== sizes"
  for p in "$HOME/toolchain" "$PREFIX_DIR" "$HOME/.local/share/opencode" "$HOME/workspace"; do
    [ -n "$p" ] && [ -e "$p" ] && du -sh "$p" 2>/dev/null || true
  done
}

show

# Transient files: safe to remove while nothing is building.
rm -rf "$HOME/.cache/tmp"/* 2>/dev/null || true
rm -rf "$HOME/toolchain/gradle-home/daemon" 2>/dev/null || true
rm -rf "$HOME/toolchain/gradle-home/.tmp" 2>/dev/null || true
rm -rf "$HOME/toolchain.tmp" 2>/dev/null || true
if [ -n "$PREFIX_DIR" ]; then
  rm -f "$PREFIX_DIR/var/cache/apt/archives"/*.deb 2>/dev/null || true
  rm -f "$PREFIX_DIR/var/cache/apt/pkgcache.bin" \
        "$PREFIX_DIR/var/cache/apt/srcpkgcache.bin" 2>/dev/null || true
fi

# Accidental git repos in managed directories (never user projects).
for d in "$HOME/toolchain" "$HOME/.config" "$HOME/.local"; do
  if [ -d "$d/.git" ]; then
    rm -rf "$d/.git"
    echo "removed $d/.git"
  fi
done
if [ -n "$PREFIX_DIR" ] && [ -d "$PREFIX_DIR/.git" ]; then
  rm -rf "$PREFIX_DIR/.git"
  echo "removed $PREFIX_DIR/.git"
fi

if [ "$BUILDS" = 1 ]; then
  echo "== removing project build outputs under $HOME/workspace"
  find "$HOME/workspace" -maxdepth 4 -type d \( -name build -o -name .gradle \) \
    -prune -exec rm -rf {} + 2>/dev/null || true
fi

if [ "$ALL" = 1 ]; then
  echo "== removing shared dependency caches (they will be re-downloaded)"
  rm -rf "$HOME/toolchain/gradle-home/caches" 2>/dev/null || true
  rm -rf "$HOME/toolchain/m2" 2>/dev/null || true
fi

echo
show
