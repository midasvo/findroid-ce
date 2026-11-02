# ADR 0001: Build new CE screens next to the upstream ones, and switch route by route

**Status:** Proposed

## Context

Almost every gap in [3-gaps.md](../3-gaps.md) changes upstream files in `app/tv`. G1 (focus restore) alone touches every screen, and G9 replaces `MainScreen`. Rule 5 of the plan asks to keep `upstream-sync` manageable.

The numbers (measured on 2026-11-02):

- Upstream changed `app/tv` in 14 commits in the last 12 months. Most were chores (formatting, AGP, JDK) plus a few broad refactors, such as the Navigation 3 migration (#1303), which touched every screen.
- CE already changes 23 files in `app/tv` (32 commits, +540/−291).

## Options

1. **Evolve the upstream screens in place.** Least code. But every upstream refactor then conflicts with our rewrites, and after G1 + G9 hardly a line of the upstream screens is left. The merge conflicts are then pure cost.
2. **Build new CE screens next to them, and switch route by route.** Each new screen is a new file in a CE package. `NavigationRoot` points each route at the CE screen once it's ready. The upstream screens stay in the tree, unused, so upstream changes to them still merge cleanly.
3. **Fork `app/tv` into a new module (`app/tv-ce`).** Cleanest break, but CI, signing, translations and `upstream-sync` all need a second app module, and upstream TV fixes no longer reach us at all.

## Decision

Option 2.

- New screens and components go in `app/tv/src/main/java/dev/jdtech/jellyfin/ce/` (`ce/presentation/...`, `ce/ui/...`). The namespace stays `dev.jdtech.jellyfin`.
- `NavigationRoot.kt` and `NavigationRoutes.kt` are the only upstream files we edit structurally. New routes (Search, Collection, Episode, Person, Favorites) are added there.
- An upstream screen stays in the tree until a sync shows it conflicts or gets in the way. Then we delete it in that sync, with a note in the changelog.
- Small fixes to a screen that will be replaced soon (G5, G6, G18) go into the CE replacement, not into the upstream file. Only if the replacement is more than one release away do we fix the upstream file as well.
- Shared helpers (focus, safe area, key handling) are CE files from the start.

## Consequences

- For a while there are two versions of some screens. Reviews must check that the route points at the CE one.
- Upstream improvements to a TV screen don't reach the user automatically once its route has switched. `upstream-sync` has to look at `app/tv` changes and port what is useful. Add a line about this to the upstream-sync skill.
- The ✱ markings in 3-gaps mostly become "new CE file + one line in `NavigationRoot`".
- Upstream's TV previews and dummy data can be reused for the CE screens.
