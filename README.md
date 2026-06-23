![Findroid banner](images/findroid-banner.png)

# Findroid CE (Community Edition)

A community-driven fork of [Findroid](https://github.com/jarnedemeulemeester/findroid) — the native
Jellyfin client for Android — that ships the features you've been waiting for.

## Why Findroid CE?

Findroid is a great app, but it's maintained by one person in their spare time, so useful community
pull requests can sit unmerged for a long time and releases are infrequent.

**Findroid CE picks up where that leaves off:**

- 🚀 **Merges good community PRs** that haven't made it into upstream yet.
- 🔧 **Adds CE-only enhancements** — a rewritten download engine, Dolby Vision playback, richer
  subtitle support, and more (see below).
- 📦 **Stays current** — dependencies and the Jellyfin SDK are kept up to date via Renovate.
- 🤝 **Tracks upstream** — improvements from Findroid are merged in regularly, so you don't fall behind.
- 📲 **Installs side-by-side** — a separate app ID (`nl.midasvo.findroid.ce`) means you can run it
  next to the original without conflicts.

If you already use and like Findroid, CE is the same app with the rough edges sanded down and the
backlog of community work actually shipped.

## What you get over stock Findroid

### Downloads

- **OkHttp resumable download engine** — replaces Android's `DownloadManager` for reliable, resumable downloads (including SD card support)
- **Per-episode download progress & inline buttons** — real-time status (pending, downloading, completed, failed) and tap-to-download/delete right in the season episode list, Netflix/Disney+ style
- **Bulk season & series download** — download an entire season or series with one tap, with queued concurrent downloading
- **Configurable max concurrent downloads** — limit how many episodes download at once (default: 2)
- **Season download status on the show screen** — see "3/10 downloaded" per season in the season selection dialog
- **Redesigned downloads screen** — active downloads with progress on top, completed items below, storage usage at the bottom

### Playback & player

- **Dolby Vision transcoding** — DV content plays everywhere via device-profile-aware transcoding
- **Wider subtitle delivery** — PGS, VobSub, and DVB subtitles delivered without transcoding
- **Configurable subtitle styling** — colors, outline, font, and size
- **Media segments** — per-type skip / ask / ignore actions
- **Chapters in the player UI**
- **"Are you still watching?"** inactivity prompt
- **Trickplay** loader behind a developer toggle

### Library & UI

- **Hide-watched filter** in the library view — persisted and shared across libraries
- **Long-press to copy text** on the movie/show/episode/season/person detail screens
- **Auto offline mode** — automatically enabled when there is no connectivity

### Diagnostics

- **Export device profile** from the About screen — handy for debugging playback/transcoding

See the [changelog](CHANGELOG.md) for the full release-by-release history.

## Install

### Via Obtainium (recommended)

Add this repo URL in [Obtainium](https://github.com/ImranR98/Obtainium):

```
https://github.com/midasvo/findroid-ce
```

Obtainium then checks for new releases automatically and notifies you of updates.

### Manual

Grab the latest APK from the [Releases](https://github.com/midasvo/findroid-ce/releases) page.

> Findroid CE uses its own application ID (`nl.midasvo.findroid.ce`), so it installs alongside the
> original Findroid rather than replacing it.

## Screenshots

| Home | Library | Movie | Season | Episode |
|------|---------|-------|--------|---------|
| ![Home](fastlane/metadata/android/en-US/images/phoneScreenshots/1_en-US.png) | ![Library](fastlane/metadata/android/en-US/images/phoneScreenshots/2_en-US.png) | ![Movie](fastlane/metadata/android/en-US/images/phoneScreenshots/3_en-US.png) | ![Season](fastlane/metadata/android/en-US/images/phoneScreenshots/4_en-US.png) | ![Episode](fastlane/metadata/android/en-US/images/phoneScreenshots/5_en-US.png) |

## Built on Findroid

Findroid CE is a fork — all the core functionality (the native UI, ExoPlayer & mpv playback, offline
downloads, picture-in-picture, etc.) comes from upstream Findroid. We sync with it regularly; the
process is documented in [docs/upstream-sync.md](docs/upstream-sync.md).

**All credit** to [Jarne Demeulemeester](https://github.com/jarnedemeulemeester) and the
[Findroid contributors](https://github.com/jarnedemeulemeester/findroid/graphs/contributors) for
building the app this fork stands on.

## License

GPLv3 — same as upstream. See [LICENSE](LICENSE).

The logo is a combination of the Jellyfin logo and the Android robot. The Android robot is reproduced
or modified from work created and shared by Google and used according to terms described in the
Creative Commons 3.0 Attribution License.
