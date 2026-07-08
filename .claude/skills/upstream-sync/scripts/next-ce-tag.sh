#!/usr/bin/env bash
# Prints the next CE release tag (v<base>-ce.<N>) for findroid-ce.
#
# Logic:
#   base = APP_NAME in buildSrc/src/main/kotlin/Versions.kt (tracks upstream verbatim)
#   latest = most recent existing v*-ce.* tag
#   same base  -> increment N
#   new base   -> N = 0
#
# Run from the repo root, AFTER the upstream merge (so Versions.kt is current).
set -euo pipefail

versions_file="buildSrc/src/main/kotlin/Versions.kt"
[[ -f "$versions_file" ]] || { echo "error: $versions_file not found (run from repo root)" >&2; exit 1; }

base=$(sed -n 's/.*APP_NAME[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' "$versions_file")
[[ -n "$base" ]] || { echo "error: could not parse APP_NAME from $versions_file" >&2; exit 1; }

latest=$(git tag -l 'v*-ce.*' --sort=-creatordate | head -1)

if [[ -z "$latest" ]]; then
  echo "v${base}-ce.0"
  exit 0
fi

latest_base=${latest#v}; latest_base=${latest_base%-ce.*}
latest_n=${latest##*-ce.}

if [[ "$latest_base" == "$base" ]]; then
  echo "v${base}-ce.$((latest_n + 1))"
else
  echo "v${base}-ce.0"
fi
