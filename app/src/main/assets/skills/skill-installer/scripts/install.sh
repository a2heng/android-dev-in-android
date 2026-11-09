#!/data/data/com.termux/files/usr/bin/bash
# Install an opencode skill from a git repo, zip URL or local directory.
# Usage: install.sh <source> [id] [--force]
set -eu

: "${HOME:?HOME is not set}"
SRC="${1:-}"
ID="${2:-}"
FORCE=0
for a in "$@"; do [ "$a" = "--force" ] && FORCE=1; done
if [ -z "$SRC" ]; then
  echo "usage: install.sh <git-url|zip-url|local-dir> [id] [--force]" >&2
  exit 2
fi
[ "$ID" = "--force" ] && ID=""

CACHE="$HOME/.cache/skill-install"
rm -rf "$CACHE"
mkdir -p "$CACHE"
ROOT=""

case "$SRC" in
  *.zip)
    echo "[skill] downloading $SRC"
    curl -fL --retry 3 -o "$CACHE/pkg.zip" "$SRC"
    mkdir -p "$CACHE/unzip"
    python3 -m zipfile -e "$CACHE/pkg.zip" "$CACHE/unzip/"
    ROOT="$CACHE/unzip"
    ;;
  https://github.com/*/tree/*)
    # https://github.com/user/repo/tree/branch/sub/dir
    REST="${SRC#https://github.com/}"
    REPO="${REST%%/tree/*}"
    AFTER="${REST#*/tree/}"
    BRANCH="${AFTER%%/*}"
    SUB="${AFTER#*/}"
    [ "$SUB" = "$AFTER" ] && SUB=""
    echo "[skill] cloning https://github.com/$REPO ($BRANCH)"
    git clone --depth 1 --branch "$BRANCH" "https://github.com/$REPO.git" "$CACHE/repo"
    ROOT="$CACHE/repo/$SUB"
    ;;
  *.git|git@*|https://*)
    echo "[skill] cloning $SRC"
    git clone --depth 1 "$SRC" "$CACHE/repo"
    ROOT="$CACHE/repo"
    ;;
  *)
    ROOT="$SRC"
    ;;
esac

if [ ! -d "$ROOT" ]; then
  echo "[skill] source not found: $ROOT" >&2
  exit 3
fi

SKILL="$(find "$ROOT" -maxdepth 5 -name SKILL.md -type f 2>/dev/null | head -n 1 || true)"
if [ -z "$SKILL" ]; then
  echo "[skill] no SKILL.md found under $ROOT" >&2
  exit 4
fi
DIR="$(cd "$(dirname "$SKILL")" && pwd)"

NAME="$(sed -n 's/^name:[[:space:]]*//p' "$SKILL" | head -n 1)"
DESC="$(sed -n 's/^description:[[:space:]]*//p' "$SKILL" | head -n 1)"
[ -n "$ID" ] && NAME="$ID"
if [ -z "$NAME" ]; then
  NAME="$(basename "$DIR")"
fi
# Safe id: lowercase, digits, dash.
NAME="$(printf '%s' "$NAME" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9-' '-')"
NAME="${NAME#-}"; NAME="${NAME%-}"
if [ -z "$NAME" ]; then
  echo "[skill] could not derive a safe id" >&2
  exit 5
fi
if [ -z "$DESC" ]; then
  echo "[skill] warning: $SKILL has no description; the model may not discover it" >&2
fi

for BASE in "$HOME/.config/opencode/skills" "$HOME/.config/opencode-android/skills"; do
  mkdir -p "$BASE"
  DEST="$BASE/$NAME"
  if [ -e "$DEST" ] && [ "$FORCE" != "1" ]; then
    echo "[skill] already exists: $DEST (use --force to replace)" >&2
    exit 6
  fi
  if [ -e "$DEST" ]; then
    rm -rf "$DEST.bak"
    mv "$DEST" "$DEST.bak"
  fi
  cp -a "$DIR" "$DEST"
  echo "[skill] installed $NAME -> $DEST"
done

echo "[skill] name: $NAME"
echo "[skill] description: ${DESC:-<none>}"
echo "[skill] start a new session (or restart the server) to re-discover skills."
