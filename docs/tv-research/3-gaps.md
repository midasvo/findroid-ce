# Phase 3: gap list

Everything from [1-inventory.md](1-inventory.md), [1-walkthrough.md](1-walkthrough.md) and [2-wholphin.md](2-wholphin.md) in one list, with priorities. Numbers like *F4* point to the findings in the walkthrough.

**Impact:** Must = broken, or blocks the "open the app, continue watching" path. Should = clearly worse than Wholphin, or breaks a rule from the plan. Polish = nice, but nobody gets stuck without it.

**Size:** S = a day or less, M = a few days, L = a week or more. These are rough estimates from reading the code, not commitments.

**Where:** *TV UI* = only `app/tv`. *Shared* = a shared module (`modes/film`, `player/local`, `settings`, `data`), so rule 4 and rule 9 apply (behaviour behind a parameter, phone unchanged).

**Upstream:** a ✱ means the work changes upstream files in `app/tv` a lot (rule 5). Most of `app/tv` comes from upstream, so that applies to nearly everything. Where it does, the column says whether a new CE file can replace the upstream one instead.

The shared ViewModels that TV needs already exist: `SearchViewModel`, `CollectionViewModel`, `PersonViewModel`, `FavoritesViewModel`, `EpisodeViewModel`, and `PlayerViewModel.switchToTrack()`. Many gaps are therefore TV UI only.

## Must

| # | Gap | Size | Where | Upstream | Notes |
|---|---|---|---|---|---|
| G1 | **Focus restore on every screen** (F4, F9, rule 2) | M | TV UI | ✱ every screen; the helper is a new CE file | One shared helper: a saved row/column position per screen (`rememberSaveable`), `focusRestorer()` on every row and grid, focus put back on return. Remove the `LaunchedEffect(true) { requestFocus() }` on every screen. Wholphin's model, see 2-wholphin §1. Do this first, because every later screen depends on it. |
| G2 | **Seek with the remote** (F2) | M | TV UI + a setting | ✱ `PlayerScreen` | A key handler: Left/Right without controls = jump (default 10 s back / 30 s forward), hold to accelerate, media keys (FF/RW, next/previous, CC, audio). Make the seek bar reachable, with time-based steps instead of 5%. The step sizes are a setting in `settings` (TV only). |
| G3 | **Audio and subtitle dialogs in the player** (F3) | S–M | TV UI | ✱ `PlayerScreen` | `PlayerViewModel.switchToTrack()` already exists and saves the per-series choice (#115), so this is mostly a TV dialog. Add speed in the same dialog style. |
| G4 | **Search screen** (F1) | M | TV UI | new CE file | `SearchViewModel` exists (debounce and limit from #114). Results grouped per type, like Wholphin. Make sure focus can leave the text field with the D-pad (Wholphin gets this wrong). |
| G5 | **Open collections** (F5) | S | TV UI | ✱ `LibraryScreen`, `NavigationRoot` | Handle `FindroidBoxSet` → a `CollectionRoute` with a grid on `CollectionViewModel`. |
| G6 | **Settings below "Next episode" can't be reached** (F6) | S | TV UI | ✱ `SettingsSubScreen` | Find out why focus stops there (probably a non-focusable header or a list that doesn't scroll). |
| G7 | **Episode handling** (F12) | M | TV UI | ✱ `SeasonScreen`, `HomeScreen`; new CE file | OK on an episode opens its season with focus on that episode, an overview, technical info and buttons: Play/Resume, Restart, Watched, Favourite. Also wire up "play from beginning" (TV now always passes `startFromBeginning = false`). Based on `EpisodeViewModel`/`SeasonViewModel`. |
| G8 | **Publish the TV APK** | S | CI | no | Add the TV APK to `beta.yml` and `release.yml`. First decide the `applicationId` question (phase 4, decision 5). Without this, nobody can use the work. |

## Should

| # | Gap | Size | Where | Upstream | Notes |
|---|---|---|---|---|---|
| G9 | **Navigation: side drawer, Back on Home opens it** (F10, F11) | M–L | TV UI | ✱ replaces `MainScreen`; new CE file | The libraries sit straight in the drawer. Focus doesn't jump into the content when a tab loads. Decide in phase 4 (decision 2) before building. |
| G10 | **Safe area and visible focus** (F8, F9, F13, rule 3) | S–M | TV UI | ✱ theme + screens | One set of tokens for the 48/27 dp margins. Rows and pages scroll so the focused item stays inside the safe area. Clear focus on the carousel. Settings use the full width. |
| G11 | **Settings that don't apply on TV** (F7, rule 9) | S | Shared (`settings`) | no | A TV flag in `SettingsViewModel` hides gestures, trickplay (until G14) and any other phone-only option. Merge the two sets of segment settings, or explain the difference. |
| G12 | **Player overlay content** | M | TV UI | ✱ `PlayerScreen` | Series name + episode line, ends-at time, remaining time, next/previous buttons, queue row with the next episodes, errors shown to the user. Position from player events instead of polling every 300 ms. |
| G13 | **Home: hero for the focused item, no reshuffle** (F9) | M | TV UI | ✱ `HomeScreen` | Logo, title, metadata and "ends at" of the focused card above the rows, with the backdrop changing with focus. Load again on return without rows jumping under the user, so G1 keeps working. |
| G14 | **Trickplay in the player** | M–L | TV UI | ✱ `PlayerScreen` | Phone has trickplay. TV gets thumbnails above the seek bar while seeking. Depends on G2. |
| G15 | **Library grid** (F14) | M | TV UI | ✱ `LibraryScreen` | A–Z jump bar, position counter, title on a placeholder card, year below the title. Optional: genres tab. |
| G16 | **Cast and person page** | M | TV UI | new CE file | Cast row on the movie and show pages, and a person page on `PersonViewModel`. |
| G17 | **Favorites** | S–M | TV UI | new CE file | A page on `FavoritesViewModel`, reachable from the drawer (G9). |
| G18 | **Show the trailer on the movie page** (inventory bug 2) | S | TV UI | ✱ `MovieScreen` | Handle `MovieAction.PlayTrailer` the same way `ShowScreen` does. |

## Polish

| # | Gap | Size | Where | Upstream | Notes |
|---|---|---|---|---|---|
| G19 | **Android TV Watch Next** | M–L | new (service/worker) | no | Continue watching on the Android TV home screen (`androidx.tvprovider`). The most valuable TV extra. |
| G20 | **Refresh-rate switching** | M | Shared (`player/local`) | no | Switch the display to the frame rate of the video. Only mpv has something for this today. Test on the box with 24p material. |
| G21 | **Screensaver with artwork** | M | new (`DreamService`) | no | |
| G22 | **Theme music for series** | S–M | TV UI + `player` | no | Optional, off by default. |
| G23 | **Customisable Home rows** | L | Shared | no | Order and visibility of rows per user. Phone could use this as well, so it needs its own issue. |
| G24 | **Small things** (F14) | S | TV UI | ✱ | Date format follows the app language, "Findroid CE" branding on the welcome screen, a shuffle button on shows, the tabs-only-on-OK behaviour (falls away with G9). |

## Not now

| Gap | Why not |
|---|---|
| Downloads, offline mode and Smart Downloads on TV | Storage on TV boxes is small, and Wholphin doesn't have it either. Decide in phase 4 (decision 7). If it ever comes, it's L. |
| Live TV, music, photos, Jellyseerr | Outside the "watch films and series" core. That's the open question in the plan ("full client or focused watch client?"). |
| PIN per user | Nice for a household box, but it changes the setup flow for phone as well. Its own issue if wanted. |

## Order

Each step ships something usable on its own:

1. **G1 focus** — the base for everything that follows.
2. **G2 + G3 player keys and tracks** — fixes the worst everyday problem with the least code, because the logic already exists.
3. **G8 publish the APK** — so people can test from here on. Needs decision 5 first.
4. **G4 search, G5 collections, G6 settings, G18 trailer** — the remaining "broken" things, all small.
5. **G9 + G10 navigation and safe area** — after decision 2. Changes the frame of the whole app, so do it before more screens are added.
6. **G7 episodes, G12 player overlay, G13 hero** — the core loop feels right.
7. **G11, G14–G17** — parity with phone where it fits.
8. **Polish** — G19 Watch Next first.

## What phase 4 has to decide first

- **Decision 5 (application ID)** blocks G8.
- **Decision 2 (navigation shape)** blocks G9, and affects G4 and G17 (where Search and Favorites live).
- **Decision 1 (evolve or rebuild)** affects every ✱. Given how many upstream screens change (G1 alone touches all of them), building new CE screens next to the upstream ones and switching route by route is probably cleaner than editing the upstream files. Phase 4 has to confirm this.
- **Decision 3 (focus strategy)** is in effect already answered by G1: Wholphin's model.
