---
name: upstream-sync
description: >-
  Merge changes from upstream Findroid (jarnedemeulemeester/findroid) into Ephyra (repo
  midasvo/findroid-ce) in a pull request. Use this whenever the user mentions syncing with
  upstream or Findroid, pulling or merging upstream/jarnedemeulemeester changes, taking
  Findroid's translations or a Findroid fix, or simply says "sync" in this repo. A sync never
  releases anything: releases are the `release` skill.
---

# Upstream Sync Playbook

Goal: bring what is worth having from Findroid into Ephyra, through a normal PR. Ephyra has its
own versions and release rhythm (see the `release` skill), so a sync is just another change on
`main`; it reaches users with the next release, under **From Findroid** in the changelog.

## When to sync

There is no fixed rhythm. Sync when Findroid has something worth having: Weblate translations
for the strings Ephyra still shares (most of the app), or a fix in code Ephyra hasn't rewritten.
About once a month is a good default. Dependency bumps don't need a sync; Renovate does those.

**Merge or cherry-pick.** Merge `upstream/main` while that stays reasonable: the shared history
keeps later merges small. When a merge would mostly be conflicts in code Ephyra has rewritten
(the TV app, downloads, the player), say so to the user and propose cherry-picking instead:
the translation commits (`Translated using Weblate`) and the specific fixes, with
`git cherry-pick -x` so the origin stays visible.

## Background

- **Fork:** `origin` = `midasvo/findroid-ce`, the app is **Ephyra** (formerly Findroid CE).
  **Upstream:** `jarnedemeulemeester/findroid`. Upstream auto-sync is **disabled**; syncing is
  always manual.
- Ephyra carries many commits of its own (downloads, the TV app, device-profile transcoding,
  branding). Where they meet upstream changes, Ephyra's behaviour wins unless the upstream
  change is a fix we want.

## Procedure

### 1. Fetch and assess

```bash
git config remote.upstream.tagOpt --no-tags              # once; Findroid's tags must not come in
git fetch origin
git fetch upstream --no-tags
git switch -c sync/findroid-$(date -u +%Y-%m-%d) origin/main
git rev-list --left-right --count upstream/main...HEAD   # "<behind>  <ahead>"
git log --oneline $(git merge-base HEAD upstream/main)..upstream/main   # what's new upstream
git diff --stat $(git merge-base HEAD upstream/main) upstream/main      # files upstream touches
```

> Use `merge-base..upstream/main`, **not** `HEAD..upstream/main`: the latter compares full trees
> and reports all of Ephyra's own commits as reverse changes, producing a huge misleading diff.

If "behind" is 0, there is nothing to sync: tell the user and stop.

### 2. Judge compatibility

Read the actual upstream diff (from the merge-base). Classify:

- **Clean / compatible:** translations, additive changes, files Ephyra doesn't change. Merge.
- **Conflicting but easy:** overlaps a file Ephyra changed but the intent is clear. Merge,
  resolve keeping Ephyra's behaviour, and explain each resolution in the PR.
- **Risky / large:** upstream refactors that collide with Ephyra's features (downloads, player,
  navigation, device profiles, the TV app). **Pause and ask the user** before going on;
  summarise the conflict and propose options, including cherry-picking only part of it.

### 3. Merge

```bash
git merge upstream/main
```

Resolve conflicts per step 2 and the hard rules below.

- **CE translations collision:** if upstream adds a string or plural with the same key as one
  in `strings_ce.xml`, the build fails on duplicate resources. Delete the key from
  `strings_ce.xml` in all languages so upstream's Weblate translation takes over.

### 4. Check

```bash
./gradlew ktfmtCheck testDebugUnitTest :app:phone:assembleLibreDebug :app:tv:assembleLibreDebug
python3 .claude/skills/translate-strings/scripts/check-translations.py
```

If something fails, fix forward in the same branch (KSP/Kotlin alignment, API changes). If it
can't be fixed easily, stop and report; the branch is the rollback, `main` is untouched.

### 5. Pull request

```bash
git push -u origin HEAD
gh pr create --repo midasvo/findroid-ce   --title "chore(upstream): sync with Findroid $(git rev-parse --short upstream/main)"   --body "<what came in, grouped (translations, fixes, features), and how conflicts were resolved>"
```

Merge it with `--merge` once `gh pr checks` is fully green (a merge commit keeps the shared
history; `--auto` merges right away because `main` has no required checks). Don't touch
`CHANGELOG.md` here; the release skill writes the **From Findroid** part.

### 6. Report

Summarise for the user: what came in, how conflicts were resolved, the checks, and the PR.

## Hard rules

- Never revert the app ID `nl.midasvo.findroid.ce` back to upstream's `dev.jdtech.jellyfin`.
- **Branding:** the app is Ephyra. Keep `app_name` (`Ephyra` / `Ephyra Debug` / `Ephyra Beta`), the
  launcher icons and TV banner from `images/branding/ephyra/`, the `ephyra-v…` APK names and the
  "Ephyra Device Profile" names on the CE side. Upstream's `welcome`, `welcome_text` and
  `permission_local_network_rationale` say "Findroid"; CE shows `ce_welcome`, `ce_welcome_text` and
  `ce_permission_local_network_rationale` instead, so take upstream's changes to those keys as they come.
- **Versions are ours.** Upstream's `APP_NAME` / `APP_CODE` in `Versions.kt` are unused: take
  whatever upstream has there, and never touch `EphyraVersion.kt` or tag anything in a sync.
- **No upstream tags.** Fetch upstream with `--no-tags` (the remote has `tagOpt = --no-tags`).
  Findroid's tags would clash with Ephyra's `vX.Y.Z` as soon as Findroid reaches 2.0.0.
- `.claude/worktrees/` are scratch copies — ignore them when grepping the tree.
- **CE-owned defaults** — keep these on the CE value when a merge touches them; upstream still
  ships the other value, so every sync re-presents it:
  - `AppPreferences.playerMpvAo` = `audiotrack` (upstream: `aaudio`). Reverting it reintroduces
    the mpv seek/track-switch video freeze — issue #52, upstream #1246, mpv-android#1283 (open).
    `MPVPlayer`'s constructor and Builder defaults mirror it.
  - `ktfmt` = `0.26.0` in `gradle/libs.versions.toml` (upstream: `0.27.0`). 0.27.0 bundles ktfmt
    0.64, whose formatting differs from 0.62 across the whole tree; taking the bump turns Lint
    red on ~100 files. Upstream's own `main` is red for this reason — their bump PR touched only
    `libs.versions.toml`, and the lint workflow's `**.kt`/`**.kts` path filter meant Lint never
    ran on it (upstream has since renamed `lint.yaml` → `format.yaml`). Unpin only together with
    a tree-wide `ktfmtFormat`, ideally after upstream reformats.
  - **JDK 21** — `Versions.JAVA` = `JavaLanguageVersion.of(21)` in `buildSrc/.../Versions.kt` and
    `java-version: 21` in every `.github/workflows/*` (upstream: 17, since `1d5081d6`). Local dev
    machines only have JDK 21 and toolchain auto-provisioning isn't configured, so taking 17
    breaks the build with "Cannot find a Java installation … languageVersion=17".
