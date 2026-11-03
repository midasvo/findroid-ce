# Changelog

All notable changes to **Findroid CE** (`nl.midasvo.findroid.ce`) are documented here.

This is a community fork of [findroid](https://github.com/jarnedemeulemeester/findroid). Releases are
tagged `v<upstream-version>-ce.<N>`, where `<upstream-version>` tracks the upstream base
(currently `1.2.0`) and `<N>` is the CE iteration counter. Entries below describe what each CE
release adds on top of upstream; routine dependency bumps and translation updates are grouped under
_Maintenance_.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [v1.2.0-ce.6] — 2026-11-03

Android TV is stable. One release after the TV app first shipped, it has what a living-room
Jellyfin client needs: Continue Watching on the TV's own home screen, trickplay while seeking,
refresh rate switching, cast and person pages, theme music and a screensaver, all built for the
remote and tested on a 32-bit Xiaomi box. And on phone and TV alike, you choose which Home rows
show, and in what order.

### Added
- Home rows you arrange yourself: Settings → Interface → Home rows lists Suggestions, Continue
  Watching, Next Up, Latest per library and a new Favorites row. Hide any of them or move them up
  and down, on phone and on TV. The layout is kept per server and user, and starts from your old
  Display settings, so nothing changes until you change it (#142).
- TV: Continue Watching and Next Up appear in the Android TV home screen's Watch Next row. Picking
  one resumes it straight away, with its page underneath. Items you remove from the row stay away
  until you watch them again. A switch under Settings → Interface turns it off (#138).
- TV: trickplay thumbnails while seeking, both with the controls shown and when seeking with
  Left/Right. Thumbnails are decoded one at a time, so they fit in a 32-bit box's memory (#134).
- TV: Match display refresh rate switches the display to the video's frame rate during playback
  (#139).
- TV: a cast row on movies and shows, and a page per person with their work (#136).
- TV: a series' theme song plays softly on its show page, off by default. It fades out when you
  leave, play something or the screensaver starts, and stays quiet when another app is playing
  audio (#141).
- TV: a screensaver with random backdrops from your whole library (#140).

### Changed
- TV: Home feels quicker. The backdrops of the cards next to the focused one load ahead of time,
  so moving along a row no longer waits on the network, and the image cache on TV is 100 MB unless
  you picked a size (#155).
- TV: settings that only make sense on a phone, such as gestures, are hidden (#133).

### Fixed
- TV: the next-episode threshold setting can be reached again (#133).
- TV: the trickplay thumbnail no longer covers the "Ends at" time (#134).

### Maintenance
- Synced with upstream findroid: Korean and Portuguese (Brazil) translations.
- README with an Android TV section and screenshots (#167).

## [v1.2.0-ce.5] — 2026-11-02

The first CE release with an Android TV app: its own Home, library, search, season and player
screens built for the remote. Plus CE branding throughout.

### Added
- Android TV APKs ship with every release, as `findroid-ce-v<version>-libre-tv-<abi>.apk`. 32-bit TV
  boxes such as the Xiaomi Mi Box need `armeabi-v7a`. The TV app has CE's own screens (#121):
  - A side navigation drawer, and layouts that keep clear of the TV's safe area (#127).
  - Home with a hero area for the focused item: logo or title, info line, overview and backdrop.
    Rows refresh in the background without reshuffling, and focus comes back to the same item. A
    Suggestions row replaces the carousel (#132).
  - A library grid with an A–Z jump bar when sorting by name, a position counter, the year under
    each title, and title cards for items without artwork (#135).
  - A favorites page (#137) and a search screen (#128).
  - Episodes open in their season instead of playing right away, with the episode's details,
    media chips, Play/Resume, Restart, Watched, Favourite, a version picker and season chips (#130).
  - A player built for the remote: seeking, media keys, audio and subtitle dialogs, the series and
    episode name, elapsed and remaining time, "Ends at", a clock, previous/next, and an "Up next"
    queue. Playback errors show a message with a way out instead of a black screen (#124, #125,
    #131).
  - Focus is restored when you come back to the library, a show or a season (#124).
  - Dates follow the app language, and shows get a shuffle button (#141).

### Changed
- The remaining Findroid names and upstream links are replaced with CE branding (#122).

### Fixed
- TV: collections open, trailers play, and every player setting can be reached (#129). Episodes in
  a collection open in their season (#129).
- TV: focus stays put when you switch drawer destinations (#127).

### Maintenance
- Synced with upstream findroid: Portuguese (Brazil) translations.
- Android TV research and roadmap in `docs/tv-research`.

## [v1.2.0-ce.4] — 2026-11-02

Background audio, audio and subtitle choices that carry over to the next episode, and Smart
Downloads that follow what you watch.

### Added
- Background audio, off by default. Turn it on under Settings → Player → Background playback, and
  playback carries on with the screen off or the app in the background, with artwork and controls
  on the lock screen. Closing the picture-in-picture window or swiping the app away keeps the audio
  going, and the media notification brings the player back. The video is switched off in the
  background to save battery (#36, #79, #80, #118).
- The audio and subtitle track you pick, or subtitles off, now carry over to the next episodes of
  that series. This works on auto-advance and when you start an episode yourself, on ExoPlayer and
  mpv, and for downloads. Each user on the device keeps their own choice (#115, #120).
- Two optional settings from a review of the Moonfin client: smaller artwork requested from the
  server at display size, and a "Max days in Next Up" limit for Home. Both are off by default
  (#114, #117).

### Changed
- Smart Downloads now follows what you watch, not when a download finishes. After playback it
  keeps a set number of unwatched episodes after the furthest one you watched downloaded, across
  seasons. It removes the episodes it downloaded itself once you have watched them. "Episodes to
  keep ready" sets how many (3 by default) (#116, #119).
- Search waits 300 ms after typing and returns at most 50 results. Media requests get longer
  timeouts, and playback pauses when headphones are unplugged, on ExoPlayer and mpv (#114, #117).
- Home keeps the rows and libraries that loaded when others fail (#114, #117).

### Fixed
- Download status lines in some languages no longer run past the edge of the row (#113).

### Maintenance
- `scripts/make-track-test-series.sh` generates a short test series with several audio and subtitle
  tracks, explained in `docs/track-test-series.md`.

## [v1.2.0-ce.3] — 2026-11-01

CE's own text in every language, a lighter Continue Watching query, and download rows that stay
readable.

### Added
- Everything CE adds is now translated into all 36 languages, so these screens no longer fall back
  to English. The translations are machine-assisted, so corrections are welcome (#106, #110).
- "Display cast and crew" setting under Settings → Interface, on by default. Turning it off hides
  actors, directors and writers on movie, show and episode pages, on phone and TV (#104, #105).

### Changed
- Continue Watching now asks the server for video only, which a Jellyfin report says stops it
  scanning the whole library for this row. In a test against one server, a Home refresh got about
  70 ms faster and a cold start didn't change; large libraries may gain more. Thanks @DrHurt (#109,
  #112).

### Fixed
- Active download rows no longer cut off the time left on narrow phones: the size and time left now
  have a line each. In multi-select, a movie's size no longer disappears behind "...". "Less than a
  minute left" is now "Under 1 min left" in English, and some translations may still be cut off
  (#111).

### Maintenance
- The README has screenshots of CE's own screens again and an updated feature list (#107, #108).
- Only the 3 newest beta prereleases are kept (#103).

## [v1.2.0-ce.2] — 2026-11-01

Picking between versions, playback that carries on into the next season, Dolby Vision direct play
and Atmos on phones, and a round of phone UI work.

### Added
- Version picker. Movies and episodes with more than one version now ask which one to play, for
  both Play and play from beginning. On TV this works on the movie screen. Items with a single
  version start straight away, as before (#28, #93).
- Finishing the last episode of a season continues into the next one, and the Next button works
  there too. Seasons with only missing episodes are skipped, and specials don't roll over into
  season 1 (#29, #92).
- "Direct play Dolby Vision" player setting, on by default (#96, #100).
- A partly downloaded season or show now offers both download and delete, so you can finish the
  download instead of only removing it (#94).
- Error screens with Retry. Movie, show, season, episode and person pages used to spin forever on a
  failed load, and Favorites and Collection stayed blank. Search, an empty Home and Downloads show
  the same error, and a failed background reload keeps what's on screen (#95).

### Changed
- Dolby Vision is direct-played when the device can show it: the decoder, profile, level, container
  and display must all support it. Anything else is transcoded as before. Profiles 5 and 10.0 have
  no fallback layer, so their transcode shows green and purple; on capable devices that's now
  avoided (#96, #100).
- Phones now try the platform audio decoder first, so those with a working Atmos decoder get spatial
  audio and hardware decoding again. If an audio decoder fails during playback, on phone or TV, the
  player switches to ffmpeg at the same position and remembers that decoder for next time. Phones
  with no Atmos decoder stay on ffmpeg for E-AC-3 (#97, #99).
- Season lists use the Downloads row style: titles on up to two lines, a play button on the artwork,
  watch progress and a download button that can stop a download. A failed download retries at its
  chosen quality instead of asking again (#94).
- Home, movie, show, season and episode pages refresh after you leave the player (#95).
- Favorites, Collection, search, Library and Home have proper empty states, and "No search results"
  no longer flashes while typing. Library has a first-load spinner, and a "Show watched" button when
  the watched filter hides everything (#95).
- The downloaded badge has its own download icon, so it no longer looks like the watched check. The
  watched check no longer uses the error colour, and Favorites uses the same heart as the item
  buttons. My media has at least two columns, like Library (#95).

### Fixed
- The check mark on an episode no longer deletes the download on one tap. Removing or cancelling
  now shows Undo, and the delete still finishes if you leave the screen (#94).
- Artwork moves on to the next image when one fails to load, and seasons without a poster use the
  show's (#94).
- Content no longer runs through the status bar and floating headers on Home, My media and item
  pages (#94).
- Home no longer opens partly scrolled when the carousel loads last (#95).
- Player controls stay usable in a narrow window such as split-screen: the Next button no longer
  gets cut off, the back button is always visible, and the other buttons scroll when they don't fit
  (#101).
- Swipe gestures no longer fire inside the system gesture area in split-screen and freeform windows
  (#55, #91).
- Stable and beta builds no longer log each other out. Beta builds now have their own device ID; log
  in once more on the beta to separate them fully (#98).

### Maintenance
- From upstream: Android Gradle Plugin 9.5.0, Jellyfin SDK 1.9.4, fastlane update.

## [v1.2.0-ce.1] — 2026-10-29

A redesigned Downloads screen, and a beta channel you can follow in Obtainium.

### Added
- Downloaded show page. Tapping a show on the Downloads screen lists its downloaded episodes by
  season, with sizes, swipe to remove and "Remove all downloads" (#84, #81).
- Multi-select on the Downloads screen. Tap "Select" or long-press a movie or show, then remove
  everything selected at once.
- Play from the Downloads screen. The ▶ on movie and episode artwork resumes playback straight
  away, from the downloaded copy.
- Undo instead of confirmation dialogs. Cancelling or removing a download shows an Undo snackbar.
  A cancelled download keeps running until the snackbar goes away, so undoing loses no progress
  (#82, #84).
- Beta channel. Builds of `main` and of work in progress are published as GitHub prereleases and
  install as a separate app, "Findroid CE Beta", with its own data. The README explains how to
  follow them in Obtainium (#83, #85, #86, #87).

### Changed
- The Downloads screen is now one scrolling list instead of tabs: a storage summary, then
  Downloading, TV Shows and Movies. Sort by recently downloaded, name or size from the ⇅ menu
  (#84, #81).
- Active downloads show a progress ring you can tap to stop, and one status line such as
  "164 MB of 498 MB · 3 min left", "Waiting for network" or the failure reason. The section header
  shows the count and combined speed, and the time-left estimate is smoothed so it settles instead
  of jumping around.
- Finished downloads get a check mark and then move into the library on their own; "Clear
  completed" is gone. Completing, cancelling and removing a download give a short haptic tick.
- Movies and shows on the Downloads screen have 16:9 artwork with watch progress, plus year,
  runtime and size (episode count and size for shows).
- The storage card shows a bar split into movies, TV shows, other files and free space, turns red
  under 10 % free, and opens the download settings. An empty Downloads screen explains what goes
  there and links to the library.

### Fixed
- In the download queue, the percentage no longer wraps onto several lines, red no longer shows at
  the corners of the cards, a long series name no longer hides which episode is downloading, and
  failed downloads show a retry icon instead of a download arrow (#82).

## [v1.2.0-ce.0] — 2026-10-28

An upstream sync to findroid 1.2.0, a download quality picker, and a batch of player fixes.

### Added
- Download quality picker. Every download (movie, episode, season or whole show) now asks for a
  quality: "Original quality" or a bitrate from 4K 120 Mbps down to 360p 720 kbps. Files above the
  chosen bitrate are converted by the server; files already under it download as the original.
  Retries and Smart Downloads keep the chosen quality, and subtitles are saved as separate files,
  image-based ones (PGS, VobSub) included. Thanks @jorritsmit (#75, #74).
- Permission requests, from upstream. On Android 17 the app asks for local network access, which it
  needs to reach a server on your home network. New installs ask during setup, existing users get a
  one-time prompt, and Settings has a new Permissions section. Phone and TV.
- "Play next episode automatically" setting under Settings → Player, on phone and TV. By default
  it follows the Jellyfin account's "Play next episode automatically" playback setting, which
  Findroid used to ignore; "Always" and "Never" override it on this device. With autoplay off,
  playback stops at the end of an episode and the episode is marked as watched; on the phone the
  player closes. An outro set to skip automatically no longer starts the next episode either. The
  next button, the outro "Next episode" button and remote controls still go to the next episode.
  Offline, "Use server setting" keeps autoplaying (#66, upstream #1050).

### Changed
- **Android 10 is now the minimum; Android 9 is no longer supported.** The app now targets
  Android 17. From upstream.
- The home screen asks the server for less. The Continue watching, Next up and Latest rows request
  one poster and one backdrop per item instead of every image, and skip a count nobody uses, which
  helps most on large libraries and slow servers. Thanks @DrHurt for the pointer (#77, #73).
- Release APKs for phone are now named `findroid-ce-v<version>-libre-<abi>.apk`.

### Fixed
- Opening a movie, show or episode no longer crashes on Jellyfin 12 servers when an actor has more
  than one role. Jellyfin 12 sends one entry per role; these are now merged into one cast card with
  the roles joined by " / ", and writers and directors are no longer listed twice (#64).
- With the mpv player, seeking now counts as activity for the "Are you still watching?" prompt, so
  it no longer appears while you are skipping around. mpv was reporting seeks in a way the player
  screen didn't listen for (#70).
- The server now learns that an episode stopped whenever playback moves to another one, not only
  on the automatic advance at the end. This covers the next and previous buttons, the chapter-skip
  gesture, the outro "Next episode" button, and media-session or remote controls, on phone and TV
  and with both ExoPlayer and mpv. Jellyfin no longer shows the old episode as still playing, and
  plugins such as Trakt and Playback Reporting see the stop. Skipping the outro past 90 % of the
  runtime now marks the episode as watched (#65, #31).
- On TV, the player now closes when playback ends (after the last episode, or at the end of an
  episode with autoplay off) instead of sitting paused on the last frame.
- On TV, media-segment skip buttons and automatic skipping (intros, outros, recaps…) now work; the
  TV player never checked the playback position against the segments.
- The progress bar for a lower-quality download now estimates the converted file's size instead of
  the original's. A 20 GB film capped at 3 Mbps used to show "2.7 GB / ~20 GB" and stall near 13 %
  before jumping to done (#76).
- The keyboard no longer covers text fields; the screen resizes around it instead. From upstream.

### Maintenance
- From upstream: Jellyfin SDK 1.9.3, kotlinx-serialization 1.12.0, OkHttp 5.5.1, Gradle 9.8.2.

## [v1.1.0-ce.5] — 2026-10-15

Community contributions plus an upstream sync that moves to Navigation 3 and Room 3.

### Added
- Video quality selector in the phone player (HD button). Picking a bitrate asks the server to
  transcode to it, "Original quality" goes back to direct play, and the choice is remembered. Works
  on both ExoPlayer and mpv; mpv can now transcode when a cap is set. Thanks @jorritsmit (#61).

### Fixed
- The season screen's download button now shows up. Episodes were fetched without the field that
  says they can be downloaded. Thanks @kaixuantan (#62).
- Dolby Digital Plus Atmos (E-AC-3 JOC) files no longer crash ExoPlayer on phones whose hardware
  decoder rejects Atmos frames (seen on Pixel 8). Phones now decode audio with the bundled ffmpeg
  first; Android TV keeps the hardware decoder so passthrough to soundbars and receivers still
  works. Thanks @jorritsmit (#59).
- Transcoded downloads (for example Dolby Vision) now also save the video's built-in subtitle
  tracks as separate files, and subtitle downloads that fail are retried. Thanks @jorritsmit (#60).
- Season cards on the home screen show season details, from upstream.

### Changed
- Navigation moved from Navigation 2 to Navigation 3, from upstream. CE's downloads-only show and
  season screens and the subtitle style screen were carried over.
- Database moved to Room 3, from upstream. The schema stays at version 11, so existing installs and
  downloads upgrade in place.

### Maintenance
- Synced with upstream findroid: Jellyfin SDK `1.9.2`, Kotlin `2.4.21`, Gradle `9.8.1`,
  androidx.paging `3.5.3`, work-runtime `2.12.0`, androidx.core `1.19.1`, aboutlibraries `15.3.0`,
  slf4j `2.0.20`, fastlane `2.241.0`.
- CE stays on JDK 21 rather than upstream's move to JDK 17, and ktfmt stays pinned to `0.26.0`.

## [v1.1.0-ce.4] — 2026-09-13

Upstream sync only — no CE behaviour changes.

### Changed
- Back-gesture animations now follow through on the predictive back preview instead of snapping at
  the end of the gesture, from upstream.

### Maintenance
- Synced with upstream findroid: Kotlin `2.4.20`, KSP `2.3.12`, Android Gradle Plugin `9.4.0`,
  androidx.compose `1.12.1`, media3 `1.11.1`, navigation-compose `2.10.1`, Room `2.8.5`,
  appcompat `1.8.0`, coil `3.6.2`, okhttp `5.5.0`, aboutlibraries `15.2.0`, slf4j `2.0.19`.
- Portuguese translation updates.
- ktfmt stays pinned to `0.26.0` rather than taking upstream's bump to `0.27.0`. The newer plugin
  bundles ktfmt `0.64`, whose formatting differs from `0.62` across the whole tree — upstream's own
  `main` is currently red on lint for this, because their bump touched only the version catalogue
  and the lint workflow only runs on `.kt`/`.kts` changes. Recorded as CE-owned in the
  upstream-sync playbook.

## [v1.1.0-ce.3] — 2026-09-01

Fixes the mpv video freeze after seeking, and an upstream sync.

### Fixed
- **mpv: video no longer freezes after seeking, switching audio track, advancing to the next episode, or turning the screen off and on.** The audio output default returns to `audiotrack`; the `aaudio` default introduced upstream freezes the video pipeline after any operation that flushes it while audio keeps playing. Reported and fixed by [@Emeseis](https://github.com/midasvo/findroid-ce/pull/53) (#52); tracked upstream as [findroid#1246](https://github.com/jarnedemeulemeester/findroid/issues/1246) and [mpv-android#1283](https://github.com/mpv-android/mpv-android/issues/1283), both still open.
- **If you already had the audio output set to `aaudio`, this release moves you to `audiotrack` once.** A stored preference wins over a changed default, and simply opening the Audio output picker used to store a value — so the fix above would not have reached most affected installs on its own. This runs a single time: if you deliberately select `aaudio` again afterwards, it stays. Settings → Player → mpv → Audio output.
- Select settings no longer save a value when you tap the option that is already selected. Doing so used to pin that preference to whatever the default was that day, silently blocking any later change to it — for every dropdown setting in the app, phone and TV.

### Maintenance
- Synced with upstream findroid: Gradle `9.7.1`, Android Gradle Plugin `9.3.2`, KSP `2.3.11`, androidx.paging `3.5.1`, aboutlibraries `15.1.1`.
- Portuguese translation updates.
- The mpv audio output default is recorded as CE-owned in the upstream-sync playbook, so a future sync does not silently restore upstream's `aaudio`.

## [v1.1.0-ce.2] — 2026-08-07

Upstream sync only — no CE behaviour changes.

### Maintenance
- Synced with upstream findroid: Jellyfin SDK `1.8.12`, Android Gradle Plugin `9.3.1`, slf4j-api `2.0.18`, Ruby `4.0.6`.

## [v1.1.0-ce.1] — 2026-07-23

Results of a full codebase audit (scan → triage → fix): 30 targeted commits across all modules.

### Fixed
- Downloaded episodes now show their download state in search, favorites, resume and latest lists while online.
- Home screen sections no longer intermittently disappear due to a state race between the parallel section loaders.
- The "Are you still watching?" prompt can actually trigger during episode auto-advance — its counter was being reset by the auto-advance seek itself.
- Player dialogs (track selection, speed, chapters, still-watching) no longer crash the app when restored after process death; added the `fragment-ktx` dependency this requires.
- External WebVTT subtitles are parsed as WebVTT instead of being fed to the SubRip parser.
- Premiere dates no longer display one day early in timezones west of UTC.
- Resolution badge: sub-720p sources are labeled SD instead of HD, and above-4K content is no longer labeled SD.
- Playback-stop reporting no longer sends a bogus played percentage when the player is released before the duration is known.
- mpv: video frame rate no longer reports the pixel width, and multi-item playlist inserts keep their order.
- TV: DPAD presses on the show screen no longer register twice, and the multi-select settings panel no longer shows the previous preference's checkboxes when moving focus between two multi-selects.
- Progress bars guard against items with unknown runtime (no more infinite-width layout).
- Missing posters/backdrops fall back to the placeholder instead of loading a broken "/null" URI.
- The home pull-to-refresh spinner now stays visible until the reload actually finishes.
- Setup: a server reporting no id shows a friendly error instead of crashing; Quick Connect failures are surfaced instead of silently swallowed; wrong-password detection uses the typed 401 exception instead of message matching; manually added server addresses are normalized, deduplicated, and failures are shown in the UI.
- Settings: subtitle font scale is clamped on the generic (TV) settings path too.

### Changed
- Home screen fetches "latest media" per library in parallel, and the show screen fetches season episodes in parallel — both were sequential network calls that scaled with library/season count.
- Room: added indices on `sources.itemId` and `mediastreams.sourceId` (schema v11, auto-migration) to avoid full-table scans on every list render.
- Downloads screen no longer queries each show's episodes twice per load, the images download worker reuses the shared OkHttp client, and the settings file editor does its I/O off the main thread.

### Maintenance
- Removed dead code: the unused TV track-selector dialog and helper, two unused DAO queries, and unused `onClick` fields on number-input preferences; the download poll loop now uses its named constant.

## [v1.1.0-ce.0] — 2026-07-23

### Changed
- Synced with upstream findroid `1.1.0` (up from `1.0.2`) — the CE tag base bumps accordingly and the iteration counter resets to 0.

### Added
- mpv preference now migrates automatically to the new player-backend preference; mpv settings are grouped together, and the config editor auto-focuses on open (ported from upstream).

### Maintenance
- Upstream's `DownloadManager` cursor-leak fix (#1227) doesn't apply to CE — we already replaced that backend with our own OkHttp-based download engine.
- Updated fastlane, `actions/checkout` (v7), aboutlibraries (v15), androidx.hilt (1.4.0), androidx.lifecycle (2.11.0), androidx.core (1.19.0), androidx.compose (1.11.4), Hilt (2.60.1), Kotlin, and the Android Gradle plugin (9.3.0); compile SDK/build tools bumped to 37.
- Spanish and Portuguese (Brazil) translation updates.

## [v1.0.2-ce.36] — 2026-07-12

### Maintenance
- Synced with upstream findroid (German translation updates).

## [v1.0.2-ce.35] — 2026-07-08

### Maintenance
- Synced with upstream findroid up to `1088a203` (Russian translation updates).
- Formalized the upstream-sync/release playbook as a Claude Code skill.

## [v1.0.2-ce.34] — 2026-07-01

### Added
- mpv `input.conf` editor and a dedicated mpv config editor, with mpv options split out into their own settings section (ported from upstream #1232).
- Configurable long-press player gesture — choose Chapter, 2× speed, or Disabled (#50).

### Fixed
- Kept the CE device-profile direct-play logic working after upstream removed the `playerMpv` boolean in favor of a `playerBackend` preference.

### Maintenance
- Synced with upstream findroid up to `07ac8919`.
- Updated Gradle (9.6.1) and Jetpack Compose (1.11.3); routine dependency bumps (#45) and translation updates.

## [v1.0.2-ce.33] — 2026-06-23

### Added
- Hide-watched filter in the library view, persisted and shared across libraries (#40, ported from upstream #1231).
- Long-press to copy text on the movie/show/episode/season/person detail screens (#42, ported from upstream #1229).

### Changed
- Replaced the Android `DownloadManager` backend with an OkHttp-based resumable download engine (#26, #27, #38).

## [v1.0.2-ce.32] — 2026-06-22

### Fixed
- Reworked the download orphan sweep: correct subtitle deletion, unmounted-SD purge, and non-recursive scanning (P0).
- Kept pending-download rows on a transient repository-resolve failure at startup (P1).
- Cancel paused downloads correctly and fixed an `ensurePump` race.
- Handled the data-sync foreground-service timeout and requested `POST_NOTIFICATIONS` at runtime.

### Maintenance
- Updated Coil, OkHttp, and Kotlin.

## [v1.0.2-ce.31] — 2026-06-09

### Added
- Deliver PGS, VobSub, and DVB subtitles without transcoding (#19).
- Configurable subtitle styling — colors, outline, font, and size (#24).
- Per-type skip / ask / ignore actions for media segments (#20).
- Surface chapters in the player UI (#25).
- "Are you still watching?" inactivity prompt (#22).
- Lazy trickplay loader behind a developer toggle (#21).
- Export device profile action on the About screen (#23).

### Maintenance
- Updated Jellyfin SDK (1.8.11), Gradle (9.5.1), KSP, and other dependencies.
- New and updated translations: Spanish (Latin America), Azerbaijani, Turkish.

## [v1.0.2-ce.30] — 2026-05-19

### Added
- Transcode Dolby Vision so it plays everywhere.

### Fixed
- Stop the download queue speed/ETA from flashing.

## [v1.0.2-ce.29] — 2026-05-19

### Fixed
- Re-resolve the download repository per use so downloads survive an online/offline mode switch.

## [v1.0.2-ce.28] — 2026-05-19

### Added
- Auto-enable offline mode when there is no connectivity.

## [v1.0.2-ce.27] — 2026-05-19

### Fixed
- Prevent a crash on startup when offline.

### Maintenance
- Updated dependencies and the Jellyfin SDK.

## [v1.0.2-ce.26] — 2026-05-09

### Fixed
- Accessibility labels on the back/sort buttons; stable `LazyColumn` keys.
- Clear the library loading state on both success and error.
- Trim whitespace from server address and username on submit.
- Avoid a `NoSuchElementException` on an empty title stack in settings.
- Buffer player events and guard the post-playback-stop math.
- Guard picture-in-picture params against an unknown video size.
- Handle a missing `currentServer` in `getDownloads`.
- Forward all season actions to the ViewModel (TV).

### Changed
- Enabled Gradle parallel builds + caching and explicit non-transitive R class.
- Scoped the release tag trigger to `v*-ce.*`.
- Internal cleanups: tighter ViewModel visibility, `val` section items, Timber over `println`, dead-file removal.

## [v1.0.2-ce.25] — 2026-05-09

### Maintenance
- Dependency updates: Compose, Kotlin, KSP, Android Gradle Plugin (9.2.0), Gradle (9.5.0), navigation-compose.
- Bulgarian translation.

## [v1.0.2-ce.24] — 2026-04-23

### Changed
- Updated mpv and switched to system fonts.
- Added the upstream-sync workflow.

### Maintenance
- French translations and dependency updates.

## [v1.0.2-ce.1 – v1.0.2-ce.23] — 2026-03-28 … 2026-04-10

Initial CE fork off findroid `1.0.2`. These rapid early iterations established the fork: the
`nl.midasvo.findroid.ce` application ID, downloads and SD-card storage improvements, and ongoing
tracking of upstream. See the git history between the corresponding tags for commit-level detail.

[v1.2.0-ce.6]: https://github.com/midasvo/findroid-ce/releases/tag/v1.2.0-ce.6
[v1.2.0-ce.5]: https://github.com/midasvo/findroid-ce/releases/tag/v1.2.0-ce.5
[v1.2.0-ce.4]: https://github.com/midasvo/findroid-ce/releases/tag/v1.2.0-ce.4
[v1.2.0-ce.3]: https://github.com/midasvo/findroid-ce/releases/tag/v1.2.0-ce.3
[v1.2.0-ce.2]: https://github.com/midasvo/findroid-ce/releases/tag/v1.2.0-ce.2
[v1.2.0-ce.1]: https://github.com/midasvo/findroid-ce/releases/tag/v1.2.0-ce.1
[v1.2.0-ce.0]: https://github.com/midasvo/findroid-ce/releases/tag/v1.2.0-ce.0
[v1.1.0-ce.5]: https://github.com/midasvo/findroid-ce/releases/tag/v1.1.0-ce.5
[v1.1.0-ce.4]: https://github.com/midasvo/findroid-ce/releases/tag/v1.1.0-ce.4
[v1.1.0-ce.3]: https://github.com/midasvo/findroid-ce/releases/tag/v1.1.0-ce.3
[v1.1.0-ce.2]: https://github.com/midasvo/findroid-ce/releases/tag/v1.1.0-ce.2
[v1.1.0-ce.1]: https://github.com/midasvo/findroid-ce/releases/tag/v1.1.0-ce.1
[v1.1.0-ce.0]: https://github.com/midasvo/findroid-ce/releases/tag/v1.1.0-ce.0
[v1.0.2-ce.36]: https://github.com/midasvo/findroid-ce/releases/tag/v1.0.2-ce.36
[v1.0.2-ce.35]: https://github.com/midasvo/findroid-ce/releases/tag/v1.0.2-ce.35
[v1.0.2-ce.34]: https://github.com/midasvo/findroid-ce/releases/tag/v1.0.2-ce.34
[v1.0.2-ce.33]: https://github.com/midasvo/findroid-ce/releases/tag/v1.0.2-ce.33
[v1.0.2-ce.32]: https://github.com/midasvo/findroid-ce/releases/tag/v1.0.2-ce.32
[v1.0.2-ce.31]: https://github.com/midasvo/findroid-ce/releases/tag/v1.0.2-ce.31
[v1.0.2-ce.30]: https://github.com/midasvo/findroid-ce/releases/tag/v1.0.2-ce.30
[v1.0.2-ce.29]: https://github.com/midasvo/findroid-ce/releases/tag/v1.0.2-ce.29
[v1.0.2-ce.28]: https://github.com/midasvo/findroid-ce/releases/tag/v1.0.2-ce.28
[v1.0.2-ce.27]: https://github.com/midasvo/findroid-ce/releases/tag/v1.0.2-ce.27
[v1.0.2-ce.26]: https://github.com/midasvo/findroid-ce/releases/tag/v1.0.2-ce.26
[v1.0.2-ce.25]: https://github.com/midasvo/findroid-ce/releases/tag/v1.0.2-ce.25
[v1.0.2-ce.24]: https://github.com/midasvo/findroid-ce/releases/tag/v1.0.2-ce.24
