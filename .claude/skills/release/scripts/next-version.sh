#!/usr/bin/env bash
# Prints the next Ephyra release version (X.Y.Z, no "v") and, on stderr, why.
#
#   .claude/skills/release/scripts/next-version.sh
#
# Run from the repo root on an up-to-date main.
#
# Ephyra's own versions start at 2.0.0. Tags below v2 (v0.x, v1.x) are Findroid's, inherited with
# the fork, and are ignored.
#
# Logic (semver from conventional commits since the last vX.Y.Z tag, X >= 2):
#   no vX.Y.Z tag yet          -> NAME in EphyraVersion.kt as it is (the first release, 2.0.0)
#   a breaking commit          -> major ("type!:" in the subject or "BREAKING CHANGE" in the body)
#   a feat commit              -> minor
#   anything else              -> patch
#   no commits since the tag   -> error, nothing to release
set -euo pipefail

version_file="buildSrc/src/main/kotlin/EphyraVersion.kt"
[[ -f "$version_file" ]] || { echo "error: $version_file not found (run from repo root)" >&2; exit 1; }

name=$(sed -n 's/.*const val NAME = "\([0-9]*\.[0-9]*\.[0-9]*\)".*/\1/p' "$version_file")
[[ -n "$name" ]] || { echo "error: could not parse NAME from $version_file" >&2; exit 1; }

last=$(git tag -l 'v*' --sort=-v:refname | grep -E '^v([2-9]|[1-9][0-9]+)\.[0-9]+\.[0-9]+$' | head -1 || true)
if [[ -z "$last" ]]; then
  echo "no vX.Y.Z tag yet: first release, $name from $version_file" >&2
  echo "$name"
  exit 0
fi

range="$last..HEAD"
# Merge commits are skipped: their subjects are "Merge pull request ...", the PR's commits count.
subjects=$(git log --no-merges --format='%s' "$range")
[[ -n "$subjects" ]] || { echo "error: no commits since $last, nothing to release" >&2; exit 1; }

IFS=. read -r major minor patch <<< "${last#v}"
if grep -qE '^[a-z]+(\([^)]*\))?!:' <<< "$subjects" \
  || git log --no-merges --format='%b' "$range" | grep -q 'BREAKING CHANGE'; then
  echo "breaking change since $last: major" >&2
  echo "$((major + 1)).0.0"
elif grep -qE '^feat(\([^)]*\))?:' <<< "$subjects"; then
  echo "feat since $last: minor" >&2
  echo "$major.$((minor + 1)).0"
else
  echo "only fixes, docs and chores since $last: patch" >&2
  echo "$major.$minor.$((patch + 1))"
fi
