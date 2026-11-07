# Phase 5: roadmap

The work from [4-design.md](4-design.md) in build order. Each row is one issue and one PR. Sizes: S = a day or less, M = a few days, L = a week or more; estimates from reading the code, not commitments. *Upstream* says which upstream files the PR has to touch beyond one `if`.

| # | Work | Design § | Size | Upstream | Depends on |
|---|---|---|---|---|---|
| R0 | ✅ #288 **Stop-gaps**: adaptive `ItemHeader` height, 720 dp cap on item pages and Season | T1–T3 | S | `ItemHeader.kt`, 4 screens, one line each | — |
| R1 | ✅ #288 **Layout tokens and size-class helper**: `Layout.kt`, `currentWidthClass()`, `isCompactHeight()`; replaces `ContentWidth.kt`. The `adaptive-layout` and `adaptive-navigation` deps come with R5 | §2 | S | none | — |
| R2 | ✅ #291 **Status bar over backdrops**: top `scrimOverArt` in the header, light status icons while the header is under the bar | §3, T12 | S | `ItemHeader.kt` | R1 |
| R3 | ✅ #291 **Item pages hero layout**: `ItemHeroHeader`, `MovieWideLayout`, `ShowWideLayout`, `EpisodeWideLayout`; remove the R0 caps; Next Up as a row card | §6, T4, T5 | M | one `when` in `MovieScreen`, `ShowScreen`, `EpisodeScreen` | R1, R2 |
| R4 | ✅ #290 **Rows, grids, search bar, downloads**: card and cell sizes from tokens; search bar cap; Downloads two columns; Favorites/Downloads title alignment | §4, §5, T9–T11 | M | `ItemCard.kt`, `HomeSection.kt`, `HomeView.kt`, `LibraryScreen.kt`, `CollectionGrid.kt`, `SearchBar.kt`, `DownloadsScreen.kt` (one modifier each) | R1 |
| R5 | **Season list-detail** at expanded, including `EpisodeRoute` rendering as the season with a selection | §7 | L | `SeasonScreen.kt`, `EpisodeScreen.kt`, `NavigationRoot.kt` | R3 |
| R6 | ✅ #289 **Player orientation and edge gestures**: runtime orientation by width class, `GESTURE_EDGE_ZONE` at expanded | §9, T6, T7 | S | `AndroidManifest.xml`, `PlayerActivity.kt`, `PlayerGestureHelper.kt`, `Constants.kt` | — |
| R7 | **Player controls at expanded**: `layout-w840dp` variants, trickplay and gesture panel sizes | §9, T8 | M | new resource folders only | R6 |
| R8 | **Player keyboard and mouse**: key map, pointer shows controls; hover and right-click on cards; Ctrl+F | §9, §11, T15 | M | `PlayerActivity.kt`, `ItemCard.kt` | — |
| R9 | **Tabletop posture** in the player (reopens #51) | §10 | M | `PlayerActivity.kt`, `activity_player.xml` | R6 |
| R10 | **Episode side sheet in the player** at expanded (upstream #480) | §9 | M | `activity_player.xml`, `PlayerActivity.kt`; new CE Compose sheet via `ComposeView` | R6, R7 |
| R11 | **Test matrix and store assets**: AVD script for the five sizes, a checklist in `docs/screenshots.md`, Ephyra tablet screenshots for `docs/play-store.md` / #177 | rule 9, §13 | S | docs only | R3, R5 |

Order of value: R1–R3 fix what every tablet user sees first (the item page), R6 fixes the one thing users have reported upstream (gestures), R5 is the feature that makes the app feel built for a tablet. R8–R10 are the tier 2 and tier 1 extras; R10 is the only one that could be dropped without the design losing its shape.

Things deliberately **not** on the list:
- A Compose rewrite of the phone player (D5).
- A navigation drawer at expanded (D2).
- A desktop-specific layout (≥ 1200 dp): the expanded rules hold; revisit when ChromeOS (upstream #729) is a target.
- Changing the TV app: covered by [tv-research](../tv-research/README.md).

## Left over from R2–R6 (2026-11-07)

Small things the PRs above noted but did not finish; each fits in R5 or a one-line follow-up.

- Season still has dark status-bar icons over dark artwork (T12): one `LightStatusBarOverArtwork` call with the lazy-list top-bar alpha. Goes with R5.
- On Downloads at expanded width the storage summary spans the row: `widthIn(max)` can't shrink a full-span grid item, so the cap needs to go on the summary's inner content.
- Next Up as a row card on the show page was only checked in a preview; the demo server had no Next Up.
- Season cards in the show's seasons row now follow `Layout.cardVertical`; the Season page's own 120 dp header poster is unchanged until R5.
