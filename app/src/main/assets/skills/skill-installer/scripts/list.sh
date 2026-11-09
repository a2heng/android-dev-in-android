#!/data/data/com.termux/files/usr/bin/bash
# List installed opencode skills.
set -eu
: "${HOME:?HOME is not set}"

found=0
for BASE in "$HOME/.config/opencode/skills" "$HOME/.config/opencode-android/skills"; do
  [ -d "$BASE" ] || continue
  while IFS= read -r f; do
    found=1
    id="$(basename "$(dirname "$f")")"
    name="$(sed -n 's/^name:[[:space:]]*//p' "$f" | head -n 1)"
    desc="$(sed -n 's/^description:[[:space:]]*//p' "$f" | head -n 1)"
    printf '%-22s %s\n' "$id" "${name:-$id}"
    [ -n "$desc" ] && printf '  %s\n' "$desc"
    printf '  %s\n' "$f"
  done < <(find "$BASE" -maxdepth 2 -name SKILL.md -type f 2>/dev/null | sort -u)
done

if [ "$found" = "0" ]; then
  echo "no skills installed under ~/.config/opencode/skills"
fi
