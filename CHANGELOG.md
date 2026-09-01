# Changelog

All notable changes to **Findroid CE** (`nl.midasvo.findroid.ce`) are documented here.

This is a community fork of [findroid](https://github.com/jarnedemeulemeester/findroid). Releases are
tagged `v<upstream-version>-ce.<N>`, where `<upstream-version>` tracks the upstream base
(currently `1.1.0`) and `<N>` is the CE iteration counter. Entries below describe what each CE
release adds on top of upstream; routine dependency bumps and translation updates are grouped under
_Maintenance_.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

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
