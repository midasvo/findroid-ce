---
name: release
description: >-
  Cut and publish an Ephyra release (vX.Y.Z, semver from conventional commits): pick the next
  version, write the changelog section, bump EphyraVersion.kt, tag and publish. Use this whenever
  the user says "release", "do a release", "cut/tag/publish a version", "ship it to stable",
  or asks only for part of it (just the changelog, just the version number, just the tag), so
  the conventions stay the same. Syncing with Findroid is a separate skill (upstream-sync).
---

# Release Playbook

Goal: turn what is on `main` into a stable release `vX.Y.Z`. A release never merges anything
from Findroid; that happens beforehand, in its own PR (see the `upstream-sync` skill).

## Background

- **Repo:** `origin` = `midasvo/findroid-ce`. The app is **Ephyra** (formerly Findroid CE).
- **Version:** `NAME` in `buildSrc/src/main/kotlin/EphyraVersion.kt` is the one source. The
  versionCode follows from it (`major * 1_000_000 + minor * 10_000 + patch * 100`, the TV release
  `+ 1`), so minor and patch must stay below 100. Upstream's `Versions.APP_NAME` / `APP_CODE`
  are not used; ignore them.
- **Tags:** releases are `vX.Y.Z` and trigger `.github/workflows/release.yml`, which builds the
  signed APKs and attaches them to the release. Betas are `v<next patch>-beta.<run>` and come
  from `beta.yml` on every push to `main`. Tags below `v2` (`v0.x`, `v1.x`) are Findroid's and
  `v1.2.0-ce.N` are the old Findroid CE releases; never reuse or move them.
- **Semver:** a breaking change (`type!:` or a `BREAKING CHANGE` footer) is major, a `feat` is
  minor, anything else is patch. For users "breaking" means something they have to act on: a
  setting reset, a dropped Android version, a lost download. `scripts/next-version.sh` applies
  these rules; the user has the last word.

## Procedure

### 1. Start from the real main

```bash
git fetch origin
git switch main && git reset --hard origin/main
gh run list --repo midasvo/findroid-ce --branch main --limit 3   # main must be green
```

### 2. Pick the version

```bash
next=$(.claude/skills/release/scripts/next-version.sh)   # prints the reason on stderr
last=$(git tag -l 'v*' --sort=-v:refname | grep -E '^v([2-9]|[1-9][0-9]+)\.[0-9]+\.[0-9]+$' | head -1)
git log --no-merges --format='- %s' "${last:-v1.2.0-ce.6}"..HEAD
```

Show the user the proposed version, the reason and the commits, and ask them to confirm. The
script can't see what a commit means for users: a `fix` that resets a setting is still
breaking, and a `feat` that only touches CI is a patch.

### 3. Release PR: version and changelog

On a branch `release/v$next`:

- Set `NAME = "$next"` in `EphyraVersion.kt` (the first release, 2.0.0, already has it).
- In `CHANGELOG.md`, turn `## [Unreleased]` into `## [v$next] — <YYYY-MM-DD>`, or add that
  section at the top if there is no Unreleased one. Keep what was already written there and
  add the rest from the commits:
  - Open with a short paragraph in plain words: what this release means for someone using the
    app. Then **Added / Changed / Fixed**, each line ending with its issue number, e.g. `(#142)`.
  - Changes merged from Findroid go under **From Findroid** (find them with
    `git log --merges --grep='upstream' "$last"..HEAD` and the sync PRs).
  - Dependency bumps and translation updates go under **Maintenance**, collapsed to a line each.
  - Leave out what users never see (CI, skills, docs), unless it changes how they install or
    update.
- Add `[v$next]: https://github.com/midasvo/findroid-ce/releases/tag/v$next` at the top of the
  link list at the bottom, or the heading renders as plain bracketed text.

```bash
git add buildSrc/src/main/kotlin/EphyraVersion.kt CHANGELOG.md
git commit -m "chore(release): v$next"
git push -u origin HEAD
gh pr create --repo midasvo/findroid-ce --title "chore(release): v$next" --body "<the changelog section>"
```

Merge it once `gh pr checks` is fully green, with `--merge` (the repo uses merge commits; `--auto`
merges right away because `main` has no required checks).

### 4. Tag and publish

Tag the merge commit on `main`, then create the release with the changelog section as its
notes **right away**: `release.yml` appends the APK guide to those notes and attaches the APKs
when its build finishes, a few minutes later.

```bash
git fetch origin && git switch main && git reset --hard origin/main
git tag -a "v$next" -m "Ephyra v$next"
git push origin "v$next"
gh release create "v$next" --repo midasvo/findroid-ce --verify-tag \
  --title "Ephyra v$next" --notes-file <file with the CHANGELOG.md section>
gh run watch --repo midasvo/findroid-ce $(gh run list --repo midasvo/findroid-ce --workflow release.yml --limit 1 --json databaseId --jq '.[0].databaseId')
```

Check that the release has eight APKs (`ephyra-v$next-libre-<abi>.apk` and
`ephyra-v$next-libre-tv-<abi>.apk`). If the workflow failed for a flaky reason, rerun it with
`gh run rerun <run id> --failed`. If the code has to change, fix it on `main` and release the
next patch version; the tag stays where it is.

### 5. Report

Tell the user the version, a link to the release, the APK count, and anything that needs a
follow-up (store listings, a known issue).

## Hard rules

- Never reuse or move a tag that has been pushed. A broken release gets a new patch version.
- Never tag from a branch other than `main`, and never tag a commit whose CI isn't green.
- The version only changes in a release PR, never in a feature PR.
- Never change the application ID `nl.midasvo.findroid.ce`; every release must update over the
  previous one, so the versionCode only goes up.
