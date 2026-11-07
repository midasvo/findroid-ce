# Phase 1: inventory

What `app/phone` does with window size on `main` (`ab64c3b9`, 2026-11-07), from reading the code. Line numbers are from that commit, except where a line was added by the fix in [2-walkthrough.md](2-walkthrough.md) §Fixed (marked *new*).

## 1. Adaptive infrastructure

**Dependencies** (`gradle/libs.versions.toml`, `app/phone/build.gradle.kts`)

| Artifact | Version | Note |
|---|---|---|
| `material3` | 1.4.0 | |
| `material3-adaptive-navigation-suite` | 1.4.0 | Only adaptive artifact declared. `currentWindowAdaptiveInfo()` comes in transitively. |
| `androidx.window:window` | 1.5.1 | Declared; `FoldingFeature` is not used anywhere. |
| Navigation 3 | 1.2.1 | `NavDisplay` in `NavigationRoot.kt`. |

Not present: `material3-adaptive-layout` (`ListDetailPaneScaffold`, `SupportingPaneScaffold`), `material3-adaptive-navigation`, any `layout-sw600dp` or `values-sw*` resource folder.

**Breakpoints in use.** Only two, read through `currentWindowAdaptiveInfo().windowSizeClass.isWidthAtLeastBreakpoint(...)`:

| File | What changes | At |
|---|---|---|
| `NavigationRoot.kt:269-281` | Forces `NavigationSuiteType.NavigationRail`; below that the library default (bottom bar on compact, rail on medium in practice) | 840 dp |
| `presentation/utils/SafePadding.kt:15-37` | Start inset is 0 because the rail carries it; only when called with `handleStartInsets = false` (`HomeScreen.kt:118`, `MediaScreen.kt:93`) | 600 dp |
| `film/MediaScreen.kt:112-121` | Library-card grid min cell 160 → 240 → 320 dp | 600 / 840 dp |
| `film/PersonScreen.kt:84-141` | Image left, text right in a `Row`; 12 collapsed overview lines instead of 4 | 600 dp |
| `film/components/ItemButtonsBar.kt:69-143` | Play stops being full-width and joins the icon-button row | 600 dp |
| `film/components/HomeCarousel.kt:22-34` | Pager shows 1 / 2 / 3 pages (custom `PageSize`) | 600 / 840 dp of available width |

`BoxWithConstraints` (*new*): `film/components/ItemHeader.kt:143-148` sets the header height to `max(288 dp, min(width × 9/16, window height × 0.5))`.

**Width caps** (`widthIn(max)`)

| Cap | Where |
|---|---|
| 480 dp | Setup: `WelcomeScreen.kt:84`, `ServersScreen.kt:106`, `AddServerScreen.kt:103`, `UsersScreen.kt:106`, `LoginScreen.kt:115`, `PermissionScreen.kt:152` |
| 640 dp | `SettingsScreen.kt:185`, `SubtitleStyleScreen.kt:126-145`, `AboutScreen.kt:186` |
| 720 dp (*new*, `utils/ContentWidth.kt`) | Detail text column: `MovieScreen.kt:198`, `ShowScreen.kt:211`, `EpisodeScreen.kt:206`; episode cards `SeasonScreen.kt:337` |
| 420 dp | Next Up card on the show page, `ShowScreen.kt:315` |
| none | Home, Library, Collection, Favorites, Downloads, Search results, the media screen's search bar, dialogs |

**Grids.** `utils/GridCellsAdaptiveWithMinColumns.kt`: `max(available / minSize, minColumns)` columns. Used with 160 dp / 2 columns by `LibraryScreen.kt:161`, `CollectionGrid.kt:28`, `SearchBar.kt:152`. `UsersScreen.kt:139` uses `GridCells.Adaptive(112.dp)`.

**Previews.** About 20 screens carry `@PreviewScreenSizes`, so Android Studio already renders phone, foldable, tablet and desktop previews. Nobody checks them in CI.

## 2. Fixed sizes that don't scale

| Component | Size | File |
|---|---|---|
| `ItemCard` HORIZONTAL / VERTICAL | 260 dp / 150 dp wide (clamped by grid cells) | `components/ItemCard.kt:41-49` |
| `ItemPoster` aspect | 1.77 / 0.66 | `components/ItemPoster.kt:46` |
| Home row header | 42 dp tall | `HomeSection.kt`, `HomeView.kt` |
| Carousel item | 16:9, fills its page | `HomeCarouselItem.kt:49` |
| Person image | 320 dp tall, 0.66 | `PersonScreen.kt:216-227` |
| Cast item | 110 dp wide | `components/PersonItem.kt:37` |
| Season poster in header | 120 dp wide | `SeasonScreen.kt:224` |
| Download row thumbnail | 112 dp, 16:9 | `components/DownloadRow.kt:128,197` |
| Sort dialog | `heightIn(max = 540.dp)` | `SortByDialog.kt:78` |

## 3. The player

The phone player is View/XML, not Compose: `PlayerActivity.kt`, `BasePlayerActivity.kt`, `res/layout/activity_player.xml`, `exo_player_control_view.xml`, `exo_main_controls.xml`, `exo_locked_controls.xml`, `item_chapter.xml`. `player/local` has no UI; it holds `PlayerViewModel`, `PlaybackService` and the domain classes, and the TV app reuses exactly those with its own Compose controls (`app/tv/.../ce/presentation/player/TvPlayerScreen.kt`).

| Fact | Where |
|---|---|
| `screenOrientation="sensorLandscape"`, own `configChanges`, `supportsPictureInPicture="true"` | `AndroidManifest.xml:31-35` |
| Locks to `SCREEN_ORIENTATION_LOCKED` while controls are locked | `PlayerActivity.kt:347,354` |
| Volume / brightness swipe: left or right **half** of the view (`viewCenterX = measuredWidth / 2`) | `utils/PlayerGestureHelper.kt:328-335` |
| Gesture exclusion from the view's system-gesture insets (correct in split-screen since #55) | `PlayerGestureHelper.kt:533-560` |
| Trickplay preview 272×153 dp; image preview 160×90 dp; volume/brightness panels 64 dp wide, 125 dp bars; chapter thumbs 120×68 dp | `activity_player.xml`, `exo_main_controls.xml`, `item_chapter.xml` |

No size-qualified variants of any player layout exist.

## 4. Screens: phone vs TV

Phone and TV share **ViewModels and state only** (`modes/film`, `setup`, `settings`, `player/local`, `core/.../viewmodels/AboutViewModel`). No screen composable is shared; the only shared composables are the brand pieces in `core/.../core/presentation/ephyra/` (avatar, glow, specks, motion) and the theme.

| Phone screen | TV equivalent | Notes |
|---|---|---|
| Home | `ce/.../HomeScreen` | TV has a hero + rows inside a side drawer (ADR 0002) |
| Media (libraries + search bar) | `ce/.../SearchScreen`; libraries live in the drawer | |
| Library, Collection, Favorites | `ce/.../LibraryScreen`, `CollectionScreen`, `FavoritesScreen` | TV grids are `GridCells.Fixed(5)` |
| Movie, Show, Season | `ce/...` versions | |
| Episode | none | TV opens the season with focus on the episode |
| Person | `ce/.../PersonScreen` | |
| Downloads, DownloadedShow | none | ADR 0007: no offline on TV |
| Settings (+ HomeRows, SubtitleStyle, About, Licenses, FileEdit) | TV settings screens | Same `SettingsViewModel`, filtered by `AppVariant` |
| Setup (Welcome … ServerAddresses) | TV setup, no ServerAddresses | |
| Player (XML) | `TvPlayerScreen` (Compose) | Same `PlayerViewModel` |

**`AppVariant`** (`settings/.../enums/AppVariant.kt`: `PHONE`, `TV`; providers in `app/phone/.../di/AppVariantModule.kt` and `app/tv/...`). Consumers: `SettingsViewModel` (which preferences are shown), `PlayerViewModel:1297` (TV trickplay). ADR 0008 says hardware questions stay on `FEATURE_LEANBACK`. There is no `TABLET` value and, per Google's guidance, there shouldn't be: window size classes "are not intended for isTablet-type logic".

## 5. What the repo already says about tablets

- `README.md:19` promises "phones, tablets and Android TV"; `release.yml:56` labels the phone APK "for phones and tablets".
- `docs/play-store.md:53`: the tablet store screenshots are still upstream Findroid's. Issue #177 (Play publishing) lists tablet screenshots as a to-do.
- Issue #51 asked for tabletop posture support in the player. PR #56 implemented it but was closed unmerged, and the issue was closed on 2026-10-15 after the reporter lost interest. Nothing in the code reads `FoldingFeature` or `WindowInfoTracker`. Treat it as open; PR #56 is a starting point.
- Upstream Findroid issue jarnedemeulemeester/findroid#1144: on a Galaxy Tab S10+ the volume/brightness swipe zones "occupy practically all the screen". That is the half-width rule above.
- Upstream #729 asks for ChromeOS support; same window-size concerns, plus keyboard and mouse.
