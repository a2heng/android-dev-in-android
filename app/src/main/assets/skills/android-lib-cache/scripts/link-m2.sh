#!/data/data/com.termux/files/usr/bin/bash
# Make ~/.m2/repository point at the shared Adia mirror for tools that
# hardcode Maven's default local repository.
set -eu

SKILL_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=env.sh
. "$SKILL_DIR/env.sh"

TARGET="$HOME/.m2/repository"
mkdir -p "$HOME/.m2"
if [ -L "$TARGET" ]; then
  current="$(readlink "$TARGET" || true)"
  if [ "$current" = "$ADIA_M2" ]; then
    echo "[lib-cache] ~/.m2/repository already -> $ADIA_M2"
    exit 0
  fi
  rm -f "$TARGET"
elif [ -e "$TARGET" ]; then
  backup="$TARGET.bak.$(date +%s)"
  echo "[lib-cache] moving existing ~/.m2/repository -> $backup"
  mv "$TARGET" "$backup"
fi
ln -s "$ADIA_M2" "$TARGET"
echo "[lib-cache] ~/.m2/repository -> $ADIA_M2"
