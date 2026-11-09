#!/data/data/com.termux/files/usr/bin/bash
# Fetch one or more Maven coordinates into the shared mirror.
# Usage: fetch-maven.sh group:artifact:version[:packaging[:classifier]] [...]
set -eu

SKILL_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=env.sh
. "$SKILL_DIR/env.sh"

if [ "$#" -eq 0 ]; then
  echo "usage: fetch-maven.sh group:artifact:version[:packaging[:classifier]] [...]" >&2
  echo "   e.g. fetch-maven.sh com.squareup.okhttp3:okhttp:4.12.0" >&2
  exit 2
fi

exec python3 "$SKILL_DIR/resolve_maven.py" "$@"
