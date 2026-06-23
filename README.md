![Findroid banner](images/findroid-banner.png)

# Findroid CE (Community Edition)

A maintained fork of [Findroid](https://github.com/jarnedemeulemeester/findroid), the native Jellyfin Android client.

## Why this fork?

The original Findroid is an excellent Jellyfin client, but many community PRs with useful features aren't being merged. Findroid CE merges select community contributions and keeps dependencies up to date.

## Installing

### Via Obtainium (recommended)

Add this repo URL in [Obtainium](https://github.com/ImranR98/Obtainium): `https://github.com/midasvo/findroid-ce`

Obtainium will automatically check for new releases and notify you of updates.

### Manual

Download the latest APK from the [Releases](https://github.com/midasvo/findroid-ce/releases) page.

Findroid CE uses a different application ID (`nl.midasvo.findroid.ce`) so it can be installed alongside the original Findroid.

## Screenshots

| Home | Library | Movie | Season | Episode |
|------|---------|-------|--------|---------|
| ![Home](fastlane/metadata/android/en-US/images/phoneScreenshots/1_en-US.png) | ![Library](fastlane/metadata/android/en-US/images/phoneScreenshots/2_en-US.png) | ![Movie](fastlane/metadata/android/en-US/images/phoneScreenshots/3_en-US.png) | ![Season](fastlane/metadata/android/en-US/images/phoneScreenshots/4_en-US.png) | ![Episode](fastlane/metadata/android/en-US/images/phoneScreenshots/5_en-US.png) |

## Features

- Completely native interface
- Supported media: movies, series, seasons, episodes (direct play, no transcoding)
- Offline playback / downloads (including SD card support)
- ExoPlayer
  - Video: H.263, H.264, H.265, VP8, VP9, AV1
  - Audio: Vorbis, Opus, FLAC, ALAC, PCM, MP3, AAC, AC-3, E-AC-3, DTS, DTS-HD, TrueHD
  - Subtitles: SRT, VTT, SSA/ASS, PGSSUB
- mpv
  - Containers: mkv, mov, mp4, avi
  - Video: H.264, H.265, H.266, VP8, VP9, AV1
  - Audio: Opus, FLAC, MP3, AAC, AC-3, E-AC-3, TrueHD, DTS, DTS-HD
  - Subtitles: SRT, VTT, SSA/ASS, DVDSUB
- Picture-in-picture mode
- Media chapters (timeline markers, chapter navigation)
- Trickplay (requires Jellyfin 10.9+)
- Media segments with skip button and auto-skip (requires Jellyfin 10.10+)

## What's different from upstream?

Findroid CE adds a lot on top of upstream Findroid. Highlights, grouped by area:

### Downloads

- **OkHttp resumable download engine** — replaces Android's `DownloadManager` for reliable, resumable downloads (including SD card support)
- **Per-episode download progress & inline buttons** — real-time status (pending, downloading, completed, failed) and tap-to-download/delete right in the season episode list, Netflix/Disney+ style
- **Bulk season & series download** — download an entire season or series with one tap, with queued concurrent downloading
- **Configurable max concurrent downloads** — limit how many episodes download at once (default: 2)
- **Season download status on the show screen** — see "3/10 downloaded" per season in the season selection dialog
- **Redesigned downloads screen** — active downloads with progress on top, completed items below, storage usage at the bottom
- **Download feedback** — toast messages summarizing bulk results (started, skipped, failed)

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

### Diagnostics & maintenance

- **Export device profile** from the About screen — useful for debugging playback/transcoding
- **Stability fixes** across startup/offline crashes, picture-in-picture, and player edge cases
- **Dependencies** kept current via Renovate

For a full release-by-release history, see [CHANGELOG.md](CHANGELOG.md).

## Upstream sync

This fork is synced with upstream manually (automatic syncing is disabled). See
[docs/upstream-sync.md](docs/upstream-sync.md) for the procedure.

## License

GPLv3 — same as upstream. See [LICENSE](LICENSE).

The logo is a combination of the Jellyfin logo and the Android robot. The Android robot is reproduced or modified from work created and shared by Google and used according to terms described in the Creative Commons 3.0 Attribution License.

## Credits

All credit to [Jarne Demeulemeester](https://github.com/jarnedemeulemeester) and the [Findroid contributors](https://github.com/jarnedemeulemeester/findroid/graphs/contributors).
