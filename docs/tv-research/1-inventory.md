# Phase 1: inventory of `app/tv`

Read on `main` at `d113b8b6`. The walkthrough on a device is in [1-walkthrough.md](1-walkthrough.md).

### Screens

| Route | Screen | ViewModel (shared) | What it does | Focus handling |
|---|---|---|---|---|
| Welcome, Permission, Servers, AddServer, Users, Login | `presentation/setup/*` | `setup` module | Full setup flow, works with D-pad | Focuses the first field or button on entry |
| `MainRoute` | `ui/MainScreen.kt` | `MainViewModel` | Top `TabRow`: Search, Home, Libraries + profile button (opens Settings) | Tabs switch only on OK, not on focus. Start tab is Home |
| (tab) Search | none | none | **The tab is empty**: `when (activeTabIndex)` has no branch for index 0 | n/a |
| (tab) Home | `presentation/film/HomeScreen.kt` | `HomeViewModel` | Suggestions carousel, Continue watching, Next up, Latest per library | None. `loadData()` runs again every time Home enters composition |
| (tab) Libraries | `presentation/film/MediaScreen.kt` | `MediaViewModel` | Grid of libraries | Focuses the first library each time the list loads |
| `LibraryRoute` | `presentation/film/LibraryScreen.kt` | `LibraryViewModel` | Paged grid, watched filter, sort dialog, opens folders | Focuses the grid after load |
| `MovieRoute` | `ui/MovieScreen.kt` | `MovieViewModel` | Backdrop, info, Play, Trailer, Played, Favorite, genres, director, writers, version dialog | Focuses Play on every entry |
| `ShowRoute` | `presentation/film/ShowScreen.kt` | `ShowViewModel` | Backdrop, info, Play, Trailer, Played, Favorite, row of seasons | Focuses Play on every entry |
| `SeasonRoute` | `presentation/film/SeasonScreen.kt` | `SeasonViewModel` | Episode list. Clicking an episode starts playback right away | No explicit focus |
| `PlayerRoute` | `ui/PlayerScreen.kt` | `PlayerViewModel` | See "Player" below | Focuses the skip button or play/pause |
| `SettingsRoute`, `SettingsSubRoute` | `presentation/settings/*` | `SettingsViewModel` | Same settings tree as phone | Focuses the first card on entry |

### Rule check: focus (rule 2)

- There is no `focusRestorer`, saved focus key or `BringIntoViewSpec` anywhere in `app/tv`.
- Every detail screen calls `requestFocus()` in a `LaunchedEffect(true)`. Navigation 3 removes screens below the top from composition, so after Back this runs again. Focus then jumps to the Play button or the first item, not to the card you came from.
- Home and Main have no focus handling at all. Coming back from a movie or the player puts focus wherever Compose's default search lands.
- Home calls `loadData()` on every return, so the rows can reshuffle under the user.

This is the biggest structural gap, and it touches every screen. It confirms that phase 4 needs a single focus strategy.

### Player

`PlayerScreen` uses the same `PlayerViewModel` as phone, so ExoPlayer/mpv, `PlaybackService`, media session, media segments and per-series track memory (#115) all run underneath. The TV UI on top of it is thin:

| Feature | TV | Notes |
|---|---|---|
| Play/pause, seek bar, chapters dialog | Yes | Chapter markers on the seek bar |
| Skip intro/credits button | Yes | Polls `updateCurrentSegment()` every second |
| Audio track choice | **No** | The button exists, but `onClick = {}` (`PlayerScreen.kt:287`) |
| Subtitle choice | **No** | Same, `onClick = {}` (`PlayerScreen.kt:293`). There's a `// TODO: implement the track selection dialogs` |
| D-pad left/right to seek with controls hidden | **No** | `dPadEvents` has empty `onLeft`/`onRight`. OK only pauses and shows controls |
| Trickplay thumbnails | No | |
| Playback speed, quality, still-watching | No | Phone has all three |
| Next/previous episode buttons | No | Autoplay to the next episode comes from the ViewModel |
| Error shown to the user | No | `PlayerError` is ignored |
| Position updates | Polled | Every 300 ms in the screen, not from player events |

### Bugs found while reading

1. The Search tab renders nothing (`MainScreen.kt`, `when (activeTabIndex)` covers only 1 and 2).
2. The Trailer button on the movie page does nothing: `MovieScreen` only handles `MovieAction.Play`, and `MovieViewModel` doesn't handle `PlayTrailer`. `ShowScreen` does open the trailer through `uriHandler`.
3. The audio and subtitle buttons in the player do nothing (see above).
4. Settings show phone-only options on TV, such as player gestures (`SettingsViewModel.kt:478`), because the shared settings tree has no TV filter.
5. Same `applicationId` as phone (`nl.midasvo.findroid.ce`), so the two APKs replace each other. The box currently has the phone debug build (`versionCode 34`) as `.debug`. Installing the TV debug build there replaces it.

### Feature matrix: phone CE vs TV

The Wholphin column is in [2-wholphin.md](2-wholphin.md).

| Area | Feature | Phone | TV | Wholphin |
|---|---|---|---|---|
| Navigation | Home / Libraries / Settings | Yes | Yes (top tabs) | |
| | Search | Yes | **Empty tab** | |
| | Favorites | Yes | No | |
| | Downloads | Yes | No | |
| Home | Suggestions, Continue watching, Next up, Latest | Yes | Yes | |
| | Backdrop of the focused item | n/a | No | |
| Browse | Library grid, sort, watched filter | Yes | Yes | |
| | Collections (`CollectionRoute`) | Yes | No | |
| Details | Movie | Yes | Yes (no cast, trailer broken) | |
| | Show, season | Yes | Yes | |
| | Episode page (`EpisodeRoute`) | Yes | No, episode starts playing | |
| | Person page, cast and crew | Yes | No | |
| | Play from beginning | Yes | No (TV always passes `startFromBeginning = false`) | |
| Player | Audio and subtitle choice | Yes | **No (dead buttons)** | |
| | Per-series audio/subtitle memory (#115) | Yes | Works underneath, can't be changed | |
| | Skip intro/credits | Yes | Yes | |
| | Chapters | Yes | Yes | |
| | Trickplay | Yes | No | |
| | Speed, quality | Yes | No | |
| | Still watching | Yes | No | |
| | D-pad seek | n/a | No | |
| Offline | Downloads, offline mode, Smart Downloads (#116) | Yes | No | |
| Settings | Settings tree | Yes | Yes (incl. phone-only options) | |
| | Subtitle style screen | Yes | Yes (in the settings tree) | |
| | About, server addresses | Yes | No | |
| Release | Published APK | Yes | In releases only (`tv-libre-*`), not in betas | |

### Size

`app/tv` has 57 Kotlin files and 7,034 lines. The largest are `ShowScreen` (406), `PlayerScreen` (346), `MovieScreen` (340) and `NavigationRoot` (313).

### Next

The device walkthrough is in [1-walkthrough.md](1-walkthrough.md), the Wholphin comparison and the full matrix in [2-wholphin.md](2-wholphin.md).
