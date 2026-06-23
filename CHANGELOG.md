# Changelog

All notable changes to **Findroid CE** (`nl.midasvo.findroid.ce`) are documented here.

This is a community fork of [findroid](https://github.com/jarnedemeulemeester/findroid). Releases are
tagged `v<upstream-version>-ce.<N>`, where `<upstream-version>` tracks the upstream base
(currently `1.0.2`) and `<N>` is the CE iteration counter. Entries below describe what each CE
release adds on top of upstream; routine dependency bumps and translation updates are grouped under
_Maintenance_.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

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
