---
name: upstream-sync
description: >-
  Sync the findroid-ce fork with upstream findroid and cut a new -ce.N release.
  Use this whenever the user mentions syncing with upstream, pulling or merging
  upstream/jarnedemeulemeester changes, cutting/tagging/publishing a CE release,
  or simply says "sync" or "do a release" in this repo — even without the word
  "upstream". Also use it when only part of the flow is requested (e.g. just the
  changelog or just the tag), so the conventions stay consistent.
---

# Upstream Sync & CE Release Playbook

Goal: pull compatible changes from upstream findroid, verify the build, push, update
the changelog, and cut + publish a new `-ce.N` release. The user may ask for the full
flow or a subset — follow the relevant steps either way.

## Background

- **Fork:** `origin` = `midasvo/findroid-ce`. **Upstream:** `jarnedemeulemeester/findroid`.
- This fork carries many CE-only commits ahead of upstream (downloads/SD-card work,
  device-profile transcoding, etc.). Upstream auto-sync is **disabled** — syncing is
  always manual.
- **Versioning:** App release tags are `v<findroid-version>-ce.<N>`, e.g. `v1.0.2-ce.32`.
  - `<findroid-version>` = upstream's `APP_NAME` in `buildSrc/src/main/kotlin/Versions.kt`.
  - `<N>` = CE iteration counter. It lives **only in the git tag** — we do **not** edit
    `Versions.kt` for a CE release. `APP_CODE`/`APP_NAME` track upstream verbatim.
  - Use `scripts/next-ce-tag.sh` (run from the repo root, after merging) to compute the
    next tag — it handles both the increment and the base-change reset.

## Procedure

### 1. Fetch and assess

```bash
git fetch origin                                         # FIRST — local main is often stale
git fetch upstream --tags
git checkout main && git reset --hard origin/main        # start from the true fork tip
git rev-list --left-right --count upstream/main...HEAD   # "<behind>  <ahead>"
git log --oneline $(git merge-base main upstream/main)..upstream/main   # what's new upstream
git diff --stat $(git merge-base main upstream/main) upstream/main      # files upstream touches
```

> **Always `git fetch origin` and reset `main` to `origin/main` before merging.** Other work
> (PRs, docs, renovate) lands on `origin/main` between syncs, so the local `main` is routinely
> behind. Merging onto a stale base makes `git push origin main` get rejected as
> non-fast-forward, forcing a redo of the whole merge. Do not force-push to recover — reset to
> `origin/main` and re-merge.

> Use `merge-base..upstream/main`, **not** `main..upstream/main` — the latter compares full
> trees and reports all our ahead-commits as reverse changes, producing a huge misleading diff.

If "behind" is 0, there is nothing to sync — tell the user and stop.

### 2. Judge compatibility

Read the actual upstream diff (from the merge-base). Classify:

- **Clean / compatible** — dependency bumps, additive changes, files we don't fork-modify.
  Merge as-is.
- **Conflicting but easy** — overlaps a CE-modified file but the intent is clear. Merge,
  resolve conflicts preserving CE behavior, explain each resolution.
- **Risky / large** — upstream refactors that collide with CE features (downloads, player,
  navigation, device profiles). **Pause and ask the user** before proceeding; summarize the
  conflict and propose options.

### 3. Safety backup, then merge

```bash
git tag findroid-sync-backup-$(date -u +%Y%m%dT%H%M%SZ)   # rollback point before merging
git merge upstream/main
```

Resolve any conflicts per step 2. CE behavior wins unless the upstream change is a deliberate
fix we want.

### 4. Build (gate)

```bash
./gradlew :app:phone:assembleLibreDebug
```

If it fails, fix forward (e.g. KSP/Kotlin version alignment, API changes from bumped deps). If
it can't be fixed easily, roll back to the backup tag and report. **Do not push or tag a broken
build.**

### 5. Commit & push

The merge itself is a commit. If conflict resolution or build fixes added changes, commit them
too (clear message describing the resolution). Then:

```bash
git push origin main
```

### 6. Update the changelog

Determine the next tag first:

```bash
next_tag=$(.claude/skills/upstream-sync/scripts/next-ce-tag.sh)
```

Add a section to `CHANGELOG.md` for `$next_tag` — newest-first. Reconstruct it from the commits
in this release range (previous tag = most recent existing `v*-ce.*` tag):

```bash
git log --no-merges --format='- %s' <previous-tag>..HEAD
```

Group into **Added / Changed / Fixed**, and collapse routine dependency bumps and translations
under **Maintenance** to keep the signal high. Commit and push to `main` **before tagging**, so
the tag includes the changelog entry:

```bash
git add CHANGELOG.md
git commit -m "docs: changelog for $next_tag"
git push origin main
```

### 7. Tag the CE release

```bash
git tag -a "$next_tag" -m "CE release $next_tag: <one-line summary of what synced>"
git push origin "$next_tag"
```

### 8. Publish the GitHub release

Reuse the changelog section as the release notes:

```bash
gh release create "$next_tag" --repo midasvo/findroid-ce \
  --title "$next_tag" --notes "<the CHANGELOG.md section for this release>"
```

### 9. Report

Summarize to the user: what was pulled, how conflicts (if any) were resolved, build result,
the new tag, and the published release.

## Hard rules

- Never revert the app ID `nl.midasvo.findroid.ce` back to upstream's `dev.jdtech.jellyfin`.
- Tag convention is `v1.0.2-ce.N` notation, not independent semver.
- `.claude/worktrees/` are scratch copies — ignore them when grepping the tree.
- **CE-owned defaults** — keep these on the CE value when a merge touches them; upstream still
  ships the other value, so every sync re-presents it:
  - `AppPreferences.playerMpvAo` = `audiotrack` (upstream: `aaudio`). Reverting it reintroduces
    the mpv seek/track-switch video freeze — issue #52, upstream #1246, mpv-android#1283 (open).
    `MPVPlayer`'s constructor and Builder defaults mirror it.
