#!/data/data/com.termux/files/usr/bin/bash
# Copy Gradle's modules-2 cache into the shared Maven-layout mirror so that
# Maven, sbt, javac, kotlinc and scripts can reuse the same artifacts.
# Usage: export-gradle.sh [modules-2 dir]
set -eu

SKILL_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=env.sh
. "$SKILL_DIR/env.sh"

SRC="${1:-$ADIA_GRADLE_HOME/caches/modules-2/files-2.1}"
if [ ! -d "$SRC" ]; then
  echo "[lib-cache] Gradle cache not found: $SRC" >&2
  echo "[lib-cache] build something online first (warm-gradle.sh)" >&2
  exit 2
fi

INDEX="$ADIA_M2/.adia-index.tsv"
[ -f "$INDEX" ] || printf 'coordinate\tfile\tsha256\n' > "$INDEX"

copied=0
skipped=0
# Layout: <group>/<artifact>/<version>/<sha1>/<file>
while IFS= read -r f; do
  rel="${f#"$SRC"/}"
  group="${rel%%/*}"
  rest="${rel#*/}"
  artifact="${rest%%/*}"
  rest="${rest#*/}"
  version="${rest%%/*}"
  base="$(basename "$f")"
  case "$base" in
    *.jar|*.aar|*.pom|*.zip) ;;
    *) continue ;;
  esac
  gpath="$(printf '%s' "$group" | tr '.' '/')"
  dest="$ADIA_M2/$gpath/$artifact/$version"
  mkdir -p "$dest"
  if [ -f "$dest/$base" ]; then
    skipped=$((skipped + 1))
    continue
  fi
  cp -n "$f" "$dest/$base"
  coord="$group:$artifact:$version"
  sum="$(python3 - "$dest/$base" <<'PY'
import hashlib,sys
h=hashlib.sha256()
with open(sys.argv[1],'rb') as f:
    for b in iter(lambda:f.read(1<<20),b''):
        h.update(b)
print(h.hexdigest())
PY
)"
  printf '%s\t%s\t%s\n' "$coord" "$dest/$base" "$sum" >> "$INDEX"
  copied=$((copied + 1))
done <<EOF
$(find "$SRC" -mindepth 4 -maxdepth 4 -type f \( -name '*.jar' -o -name '*.aar' -o -name '*.pom' -o -name '*.zip' \) 2>/dev/null)
EOF

echo "[lib-cache] exported $copied file(s), $skipped already present"
echo "[lib-cache] mirror: $ADIA_M2"
du -sh "$ADIA_M2" 2>/dev/null || true
