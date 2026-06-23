# Upstream Sync & CE Release Playbook

When the user says **"sync with upstream"** (with or without "and do a release"), follow this
procedure. The goal: pull compatible changes from upstream findroid, verify the build, push, update
the changelog, and cut + publish a new `-ce.N` release.

## Background

- **Fork:** `origin` = `midasvo/findroid-ce`. **Upstream:** `jarnedemeulemeester/findroid`.
- This fork carries many CE-only commits ahead of upstream (downloads/SD-card work, device-profile
  transcoding, etc.). Upstream auto-sync is **disabled** — syncing is always manual.
- **Versioning:** App release tags are `v<findroid-version>-ce.<N>`, e.g. `v1.0.2-ce.32`.
  - `<findroid-version>` = upstream's `APP_NAME` in `buildSrc/src/main/kotlin/Versions.kt`.
  - `<N>` = CE iteration counter. It lives **only in the git tag** — we do **not** edit
    `Versions.kt` for a CE release. `APP_CODE`/`APP_NAME` track upstream verbatim.
  - **Increment** `<N>` by 1 for each CE release on the same upstream base.
  - **Reset** `<N>` to `0` when upstream's `APP_NAME` changes — adopt the new base version and
    start the counter over (e.g. upstream bumps to `1.0.3` → first CE release is `v1.0.3-ce.0`).

## Procedure

### 1. Fetch and assess

```bash
git fetch upstream --tags
git rev-list --left-right --count upstream/main...HEAD   # "<behind>  <ahead>"
git log --oneline $(git merge-base main upstream/main)..upstream/main   # what's new upstream
git diff --stat $(git merge-base main upstream/main) upstream/main      # files upstream touches
```

> Use `merge-base..upstream/main`, **not** `main..upstream/main` — the latter compares full trees
> and reports all our ahead-commits as reverse changes, producing a huge misleading diff.

If "behind" is 0, there is nothing to sync — tell the user and stop.

### 2. Judge compatibility

Read the actual upstream diff (from the merge-base). Classify:

- **Clean / compatible** — dependency bumps, additive changes, files we don't fork-modify. Merge
  as-is.
- **Conflicting but easy** — overlaps a CE-modified file but the intent is clear. Merge, resolve
  conflicts preserving CE behavior, explain each resolution.
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

If it fails, fix forward (e.g. KSP/Kotlin version alignment, API changes from bumped deps). If it
can't be fixed easily, roll back to the backup tag and report. **Do not push or tag a broken build.**

### 5. Commit & push

The merge itself is a commit. If conflict resolution or build fixes added changes, commit them too
(clear message describing the resolution). Then:

```bash
git push origin main
```

### 6. Update the changelog

Add a section to `CHANGELOG.md` for the new release — newest-first, keyed by the `v<base>-ce.<N>`
tag. Reconstruct it from the commits in this release range:

```bash
git log --no-merges --format='- %s' v<base>-ce.<N-1>..HEAD
```

Group into **Added / Changed / Fixed**, and collapse routine dependency bumps and translations under
**Maintenance** to keep the signal high. Commit and push to `main` **before tagging**, so the tag
includes the changelog entry:

```bash
git add CHANGELOG.md
git commit -m "docs: changelog for v<base>-ce.<N>"
git push origin main
```

### 7. Tag the CE release

Determine the next tag:
- Compare upstream `APP_NAME` to the base in the latest `v*-ce.*` tag.
- Same base → increment `N`. Changed base → new base, `N = 0`.

```bash
git tag -a v<base>-ce.<N> -m "CE release v<base>-ce.<N>: <one-line summary of what synced>"
git push origin v<base>-ce.<N>
```

### 8. Publish the GitHub release

Reuse the changelog section as the release notes:

```bash
gh release create v<base>-ce.<N> --repo midasvo/findroid-ce \
  --title "v<base>-ce.<N>" --notes "<the CHANGELOG.md section for this release>"
```

### 9. Report

Summarize to the user: what was pulled, how conflicts (if any) were resolved, build result, the new
tag, and the published release.

## Notes

- Never revert the app ID `nl.midasvo.findroid.ce` back to upstream's `dev.jdtech.jellyfin`.
- Tag convention is `v1.0.2-ce.N` notation, not independent semver.
- `.claude/worktrees/` are scratch copies — ignore them when grepping the tree.
