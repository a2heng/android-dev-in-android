#!/data/data/com.termux/files/usr/bin/bash
# Remove an installed opencode skill by id.
set -eu
: "${HOME:?HOME is not set}"

ID="${1:-}"
if [ -z "$ID" ]; then
  echo "usage: remove.sh <skill-id>" >&2
  exit 2
fi
case "$ID" in
  android-build|skill-installer)
    echo "[skill] refusing to remove built-in skill: $ID" >&2
    exit 3
    ;;
  *[!a-z0-9-]*)
    echo "[skill] invalid id: $ID" >&2
    exit 4
    ;;
esac

removed=0
for BASE in "$HOME/.config/opencode/skills" "$HOME/.config/opencode-android/skills"; do
  DEST="$BASE/$ID"
  if [ -d "$DEST" ]; then
    rm -rf "$DEST"
    echo "[skill] removed $DEST"
    removed=1
  fi
done
if [ "$removed" = "0" ]; then
  echo "[skill] not installed: $ID" >&2
  exit 5
fi
echo "[skill] start a new session to refresh the skill list."
