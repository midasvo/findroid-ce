# Phase 4: the tablet design

This is the agreed direction for `app/phone` on windows of medium width and up. It is written so that a review can quote it. "Must" is a rule; "should" is the default unless a screen has a reason not to.

It builds on [1-inventory.md](1-inventory.md) (what exists), [2-walkthrough.md](2-walkthrough.md) (findings T1–T15) and [3-guidelines.md](3-guidelines.md) (Google's size classes and quality tiers). The visual identity is unchanged: everything here is layout, and [VISION.md](../identity/VISION.md) still decides colour, type, shape and motion.

## 1. Rules

1. **Size class, never device.** Every branch in the UI keys on `WindowSizeClass` width (and height where it matters), read through one helper (§2). No `isTablet`, no `AppVariant.TABLET`, no `smallestScreenWidthDp`. A phone in landscape, a split-screen half, an unfolded foldable and a desktop window all get the layout their window earns. (Google: size classes "are not intended for isTablet-type logic".)
2. **Compact is the phone, and the phone does not change.** Every rule below starts at 600 dp. A compact window renders exactly what it renders today. Where a phone in landscape (medium width, compact height) would get a worse result from a medium rule, the rule says so.
3. **Content is the light source** (VISION §1). A wider window shows *more artwork at a better crop*, not more chrome. Backdrops grow with the window; menus, bars and buttons do not.
4. **Measure, don't stretch.** Text columns are at most 720 dp wide (about 75 characters of `bodyMedium`). Buttons, text fields, bottom sheets and dialogs never span a medium or expanded window (Google tier 2).
5. **Use the room for the next step.** On expanded windows a page shows the thing the user is most likely to do next beside the thing they are looking at: the episode beside the season list, the queue beside the video. Two panes at expanded, one at medium and compact (Material canonical layouts).
6. **Same ViewModels, same state.** Tablet layouts are new stateless `*Layout` composables or branches inside the existing ones; they consume the same `FooState` and send the same `FooAction`. If a tablet layout needs data the ViewModel does not expose, the ViewModel grows a field, never a second ViewModel.
7. **Upstream-friendly.** The detail screens are upstream files. A wide layout goes in a new CE file (`presentation/film/<Foo>WideLayout.kt`) and the upstream screen gets one `if` to pick it. Shared tokens go in CE files under `presentation/utils/`.
8. **Everything reachable without touch.** Any action on a tablet layout works with Tab/arrows/Enter and with a mouse. Space, arrows and Esc do what Google's tier 2 list says in the player.
9. **Test at the five sizes.** Phone (412×915), phone landscape (915×412), 8" tablet (1024×640), 10.5" tablet (1280×800) both orientations, foldable (841×701 outer / 2208×1840 inner). A change to a screen in the matrix in §12 is reviewed with its `@PreviewScreenSizes` preview or an emulator screenshot.

## 2. Layout tokens

One CE file, `app/phone/.../presentation/utils/Layout.kt`, replaces `ContentWidth.kt` and is the only place that reads `currentWindowAdaptiveInfo()` for layout:

```kotlin
enum class WidthClass { Compact, Medium, Expanded }   // 0–599, 600–839, 840+

@Composable fun currentWidthClass(): WidthClass
@Composable fun isCompactHeight(): Boolean            // height < 480 dp (phone landscape)

object Layout {
    val margin        = 16.dp / 24.dp / 24.dp      // per width class, outside the rail
    val gutter        = 12.dp / 16.dp / 16.dp      // between grid cells and row cards
    val textMaxWidth  = 720.dp                     // any column of running text
    val formMaxWidth  = 480.dp                     // setup, login (exists today)
    val settingsMaxWidth = 640.dp                  // settings lists (exists today)
    val cardHorizontal = 260.dp / 300.dp / 320.dp  // 16:9 cards in rows
    val cardVertical   = 150.dp / 170.dp / 180.dp  // posters in rows
    val gridMinCell    = 160.dp / 170.dp / 180.dp  // poster grids (min 2 columns stays)
    val posterInHeader = 0.dp  / 160.dp / 200.dp   // poster beside the text on detail pages
}
```

Values are starting points, chosen so that on the 1280×800 reference exactly 4 horizontal cards or 7 posters fit beside an 80 dp rail with 24 dp margins. They are tuned once on the emulator, then frozen.

Dependencies to add: `androidx.compose.material3.adaptive:adaptive-layout` and `adaptive-navigation` (for `ListDetailPaneScaffold` and `SupportingPaneScaffold`, §6 and §9). `androidx.window` is already present for `FoldingFeature` (§10).

## 3. Navigation shell

Unchanged in structure; this is the part that already works.

| Width | Navigation | Top area |
|---|---|---|
| Compact | Bottom `NavigationBar` | Server chip, search, avatar as today |
| Medium | `NavigationRail` (library default) | Same |
| Expanded | `NavigationRail` (forced, as today) | Same; the search icon opens the search *field docked in the top area*, not a full-width sheet (§5) |

- The rail stays an icon rail. No drawer on expanded: a drawer hides the content behind the libraries, and the Media tab already lists them. (This differs from the TV ADR 0002 on purpose: TV has no touch and needs the drawer for library names; a tablet has a tap.)
- The rail hides on item pages exactly as the bar does today (`showBottomBar`). Back goes one level up; nothing else changes.
- Status bar: edge-to-edge as now. Over a backdrop the header draws `scrimOverArt` from the **top** as well (10 % → 0 over the first 96 dp), and while the header is under the status bar the screen requests light status-bar icons regardless of theme. This fixes T12 on every width, phone included (small visible change on phone, accepted).

## 4. Home

- Carousel: 1 / 2 / 3 pages (exists). At expanded the page height is `min(width / 3 × 9/16, window height × 0.4)` so three pages never push "Continue watching" below the fold on a 10.5" landscape tablet.
- Rows use `Layout.cardHorizontal` / `cardVertical` instead of the fixed 260 / 150 dp (T10). Row headers keep their 42 dp. Row content padding is the rail-aware safe padding plus `Layout.margin`.
- Nothing else changes. Home is the screen the user sees most, and its rhythm (carousel, rows) is already right.

## 5. Media, Library, Collection, Favorites, Search, Downloads

- **Media**: library cards keep their 160 / 240 / 320 dp breakpoints. The search bar is capped at `textMaxWidth` and aligned to the start at medium and expanded (T9); expanded is the only width that actually hits the cap.
- **Grids** (Library, Collection, Favorites, search results): `gridMinCell` per width class, minimum 2 columns stays. Section headers in `CollectionGrid` use `maxLineSpan`. Nothing centred: a feed uses the width.
- **Downloads**: at expanded the download rows become a two-column `LazyVerticalGrid(GridCells.Adaptive(minSize = 480.dp))`; a row never exceeds 720 dp. Favorites and Downloads put the title in the same place (T11): title first, actions in the top bar.
- **Dialogs and sheets**: M3 caps `AlertDialog` at 560 dp and `ModalBottomSheet` at 640 dp already (T13 is fine as is). The rule is: never pass `fillMaxWidth` to a dialog or sheet container, and `SortByDialog`'s `heightIn(max = 540.dp)` becomes `min(540 dp, window height − 96 dp)` so it fits a compact-height window.

## 6. Item pages (Movie, Show, Episode)

The core of the design. Today's page is one column under a header; it stays that way on compact. At medium and expanded it becomes a **hero with a side-by-side header**, the layout users know from TV and tablet streaming apps and the one the TV app already uses (Wholphin pattern, [2-wholphin.md](../tv-research/2-wholphin.md)).

```
┌──────────────────────────────────────────────────────────────┐
│ ← ⌂                      backdrop (full width)               │  height: see below
│                                                              │
│          ┌────────┐  Title                            (chips)│
│          │ poster │  2016 · 97 mins · NR · ★ 7.2             │  ← bottom third of the hero,
│          │        │  ▶ Play   ⟲  ✓  ♡  ⤓                     │     over the scrim
│          └────────┘  Overview, 4 lines, "more"…               │
│                      Genres · Director · Writers             │
├──────────────────────────────────────────────────────────────┤
│ Next Up (show) / Seasons (show) / Cast & crew / Similar      │  rows, full width
└──────────────────────────────────────────────────────────────┘
```

- **Hero height**: `max(320 dp, min(width × 9/16, window height × 0.55))` at medium and expanded; compact keeps 288 dp. The backdrop is `ContentScale.Crop` with the focal point at the top centre (faces are usually in the upper half), and a `scrimOverArt` gradient from the bottom **and** from the left edge (0 → 60 %) so white text reads over any art (VISION §3.4: text on artwork is white).
- **Header row** sits in the bottom part of the hero, `Layout.margin` from the left edge: poster (`posterInHeader` wide, 2:3; for an episode a 16:9 still instead, 1.6 × that width) then a column capped at `textMaxWidth`: title (`headlineLarge` at expanded, `headlineMedium` at medium), original title, metadata line, chips, `ItemButtonsBar` (already compact at 600 dp+), overview collapsed to 4 lines with "more", then genres / director / writers. The column is the stop-gap 720 dp column, moved into the hero.
- **Rows below** (`NextUp`, seasons, cast, and later "similar"/"more like this" when the ViewModel exposes it) span the width with `Layout.margin`, card sizes from §2. Next Up on the show page becomes a row item at `cardHorizontal` width, not a lone 420 dp card (T5).
- **Phone landscape** (medium width, compact height, 412 dp tall): the hero would eat the screen. Rule 2 applies: compact height uses the compact layout. The helper exposes `isCompactHeight` for exactly this.
- **Episode**: same layout; the "still" replaces the poster; "Season 1 · Episode 2" is the overline; the show's logo is not used (it competes with the title).
- Motion: the hero crossfades over `durationLong` as today; the header column drifts in with the screen (VISION §6).

Implementation: one CE composable `ItemHeroHeader(item, poster, title, meta, buttons, overview, info)` in `presentation/film/components/`, used by three new CE files `MovieWideLayout.kt`, `ShowWideLayout.kt`, `EpisodeWideLayout.kt`; each upstream `FooScreenLayout` gets a single `when (widthClass)` at the top. `ItemHeader` stays for compact. `DetailContentMaxWidth` and the stop-gap caps go away when this lands.

## 7. Season

Compact and medium: today's page (header, then the episode list capped at `textMaxWidth`).

**Expanded: list-detail.** The season is the one place where the next step is always "pick an episode, read about it, play it", and today that costs a navigation each time.

```
┌──────────────┬───────────────────────────────────────────────┐
│ ← ⌂  Show    │                                               │
│ [poster]     │   still (16:9)                                │
│ Season 1     │   Season 1 · Episode 2                        │
│ ▶ Play  ✓ ♡  │   The Man From Mars                           │
│──────────────│   6/25/10 · 38 mins · ★ 4.0  [chips]          │
│ E1 Earthfall │   ▶ Play   ✓  ♡  ⤓                            │
│►E2 The Man…  │   Overview…                                   │
│ E3 Alone in… │   Cast & crew                                 │
│ E4 …         │                                               │
└──────────────┴───────────────────────────────────────────────┘
      list 40 %                      detail 60 %
```

- `ListDetailPaneScaffold` with `rememberListDetailPaneScaffoldNavigator<UUID>()`. The list pane is the current season header plus episode cards (compact variant: still, title, code, duration, download state; no overview). The detail pane is the episode layout from §6 **without its own back/home buttons**, driven by `EpisodeViewModel` for the selected id.
- Selecting an episode updates the detail pane; it does **not** push a route. Opening the season selects the first unwatched episode (the same one `Play` on the season would start), so the right pane is never empty. Back leaves the season.
- Play, watched, favourite and download in the detail pane act on that episode through `EpisodeAction` as today; the list pane refreshes through `SeasonViewModel` as it already does after a download.
- Deep links to an episode (from Home "Next Up", search, a notification) still open `EpisodeRoute`; at expanded width that route renders the season list-detail with that episode selected, so the user lands in the same place either way.
- The split is 40/60 with a 1 dp `outlineVariant` divider; the list pane is at least 360 dp and at most 480 dp.

## 8. Person

Already two-column at 600 dp+ (image left, text right). Only the tokens change: the image is `posterInHeader × 1.6` tall instead of a fixed 320 dp, and the credit rows use `cardVertical`.

## 9. Player

The player is the second biggest gap after the item pages. It is View/XML (`PlayerActivity`); the changes below are deliberately possible without rewriting it in Compose, because a rewrite is its own project.

**Orientation (T7).** The manifest no longer sets `screenOrientation`; `PlayerActivity.onCreate` (and `onConfigurationChanged`) picks it: `SENSOR_LANDSCAPE` when the **shorter side of the maximum window** is under 600 dp (a phone, whichever way it is held, or a folded foldable), `UNSPECIFIED` otherwise. The shorter side rather than the current width, because the player opens in landscape on a phone and would otherwise unlock itself; the maximum window rather than the current one, so split-screen and a return from picture-in-picture don’t force landscape on a tablet. A tablet or unfolded foldable in portrait then plays in portrait, letterboxed, as every video app does. The lock button locks to the current orientation and unlock restores the chosen value. *(Built in R6, #289.)*

**Gestures (T6, upstream #1144).** Vertical swipes for volume and brightness are accepted only in **edge zones** when the player view is at least **840 × 480 dp**; smaller players keep the left and right halves (a phone in landscape is about 900 × 410 dp and stays on halves). Each zone is `clamp(width × 0.2, 96 dp, 240 dp)` from its edge; a vertical swipe that starts between the zones is ignored until the finger lifts, so it can’t toggle the controls or become a seek. Horizontal seek and double-tap seek keep today’s rules. The bounds are `Constants` values, not a preference. *(Built in R6, #289.)*

**Controls at expanded.** Same layout files, size-qualified where it matters: `layout-w840dp/exo_main_controls.xml` with 48 dp spacing between the five transport buttons (32 today), the title in `titleLarge`, time labels in `labelLarge`; `activity_player.xml` trickplay preview 320×180 dp and gesture panels 80 dp wide. Chapter thumbnails 160×90 dp. Nothing moves; it breathes.

**Supporting pane at expanded (upstream #480, "episode list inside the player").** A button in the top-right of the controls opens a side sheet over the right 30 % of the window with the season's episodes (`SeasonViewModel`, same compact episode card as §7's list pane). Tapping one plays it through the existing queue; the video keeps playing while the sheet is open. The sheet is `surfaceContainer` at 92 % opacity, 24 dp corners (VISION §5 "large"), dismissed by tap outside, Back or Esc. Not for movies.

**Picture-in-picture.** Already supported. The tablet test adds: entering PiP from portrait, Esc leaves PiP (tier 2), and the queue continues in PiP.

**Keyboard and mouse (T15, tier 2).** In `PlayerActivity.onKeyDown`: Space = play/pause, Left/Right = seek −10 s / +30 s (the TV defaults from ADR work), Up/Down = volume, M = mute, F = toggle controls lock, Esc = exit PiP or leave the player (and cancel the next-episode countdown), media keys as the system sends them. Mouse movement shows the controls; the pointer hides with them.

## 10. Foldables

- **Tabletop posture** (issue #51, Google's fold-aware guidance): when `windowPosture.isTabletop` (half-opened, horizontal hinge), the player constrains the video surface to the area above the fold (`FoldingFeature.bounds.top`) and moves the control bar below it. Everything else keeps playing as before. Implemented in `PlayerActivity` with `WindowInfoTracker.windowLayoutInfo` (already a dependency); the Compose shell doesn't need it.
- **Book posture**: nothing special; the window is medium or expanded and the rules above apply. Dialogs and sheets are M3 and already avoid the hinge by being narrower than it.
- **Fold/unfold during playback**: `PlayerActivity` already handles size `configChanges`; the gesture helper reads the view, not the display (#55). Test that the surface resizes without a rebuffer.

## 11. Input beyond touch

Tier 2 items that cost little and make the app usable on a Chromebook or with a keyboard case:

- Every clickable card and button is focusable (Compose does this) and shows the M3 focus indication; the rail is Tab-reachable.
- Hover: cards use the M3 `hoverable` indication (a `surfaceContainerHigh` tint), play buttons raise their tonal elevation.
- Right-click on a card opens the same menu as long-press, where a long-press menu exists.
- Ctrl+F focuses search on the Media screen.

## 12. Screen matrix

| Screen | Compact | Medium (600–839) | Expanded (840+) |
|---|---|---|---|
| Home | bar, 1 page | rail, 2 pages, cards 300/170 | rail, 3 pages, cards 320/180 |
| Media | as today | search ≤ 720 dp | search ≤ 720 dp |
| Library / Collection / Favorites / Search | 160 dp cells | 170 dp cells | 180 dp cells |
| Downloads | list | list ≤ 720 dp | 2 columns |
| Movie / Show / Episode | header + column | hero + poster 160 + column | hero + poster 200 + column, rows full width |
| Season | header + list | header + list ≤ 720 dp | list-detail 40/60 |
| Person | column | row (exists) | row (exists) |
| Settings / setup | as today (640 / 480 caps) | same | same |
| Player | sensor landscape, half-screen gestures | free orientation, half-screen gestures | free orientation, edge gestures, roomier controls, episode side sheet |
| Compact height (phone landscape) | | item pages and Season use the compact layout | |

## 13. Acceptance criteria

The tablet work is done when, on the five sizes in rule 9:

1. No page shows a backdrop cropped tighter than 2.2:1, and no running text column is wider than 720 dp.
2. Movie, Show and Episode at expanded use the hero layout with the poster; Season at expanded is list-detail; rotating a tablet between portrait and landscape keeps the selected episode and the scroll position.
3. The player opens in the device's current orientation on medium and expanded windows, and volume/brightness swipes at expanded width only work within 240 dp of an edge.
4. Space, Left/Right, Esc work in the player; Tab reaches every control on Home, Media and an item page.
5. Split-screen at 600 dp and PiP both work without visual breakage, and playback survives a rotate, a fold and a resize (tier 3).
6. Tabletop posture puts the video above the fold.
7. The phone layout at 412×915 is pixel-identical to `main` before this work, except the status-bar scrim (§3).
8. `docs/play-store.md` has Ephyra's own tablet screenshots (closes that part of #177).

## 14. Decisions taken here

Short form; an ADR can be written for any of them if they are contested.

| # | Decision | Why |
|---|---|---|
| D1 | Size classes, no `AppVariant.TABLET` | Google's guidance; split-screen and foldables make "tablet" meaningless; ADR 0008 keeps `AppVariant` for app-level behaviour only. |
| D2 | Rail, no drawer at expanded | Touch makes the drawer unnecessary; the drawer hides the content it is for. TV differs because it has no touch. |
| D3 | Hero + side header for item pages, not a two-column body | Keeps one scroll axis, matches the TV app and what users know, keeps rows full width for more artwork. |
| D4 | List-detail for Season only | It is the only screen where "look, then act on a sibling" dominates. Show → seasons is not worth a pane; Library → item is a feed. |
| D5 | Keep the XML player, size-qualify its layouts | A Compose player is its own project; every player change here is a layout qualifier, a runtime orientation call, a gesture constant, a key handler or a fold listener. |
| D6 | Edge gestures only on a player of at least 840 × 480 dp | Phones in landscape are medium width but compact height, and today’s halves work there; the complaint (#1144) is from a 1280 dp-class tablet. Width alone would have caught phones in landscape, so the height bound was added when R6 was built. |
| D7 | Phone pixel-identical except the status-bar scrim | Rule 2. The scrim fixes a real phone bug (T12 exists on phones with dark backdrops too) and is a one-line, on-brand change. |
