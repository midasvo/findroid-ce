# Findroid CE (Community Edition)

A community-driven fork of [Findroid](https://github.com/jarnedemeulemeester/findroid) — the native
Jellyfin client for Android. Think of it as a **bleeding-edge channel**: a place to move a little
faster, try out community contributions, and experiment with new features.

## Why Findroid CE?

Findroid is a fantastic app and the foundation for everything here. CE isn't trying to replace it —
it's a faster-moving companion for people who want the latest community work sooner.

- 🚀 **Moves faster** — merges community PRs and ships new features without waiting on the upstream release cadence.
- 🔧 **Adds CE-only enhancements** — a rewritten download engine, Dolby Vision playback, richer
  subtitle support, and more (see below).
- 📦 **Stays current** — dependencies and the Jellyfin SDK are kept up to date via Renovate.
- 🤝 **Two-way street** — we sync improvements *from* upstream regularly, and upstream is welcome to
  take anything from here. It's all GPLv3.
- 📲 **Installs side-by-side** — a separate app ID (`nl.midasvo.findroid.ce`) means you can run it
  next to the original and switch back any time.

If you like living a bit closer to the edge, CE gets community features into your hands sooner. If you
prefer rock-solid stability, stick with upstream — and know that the good stuff here can flow back to it.

## A look at CE

<p align="center">
  <img src="images/screenshots/downloads.webp" width="240" alt="Downloads screen with a storage bar, two active downloads with progress rings, and a downloaded movie">
  <img src="images/screenshots/season.webp" width="240" alt="Season episode list showing downloaded, downloading and not yet downloaded episodes">
  <img src="images/screenshots/download-quality.webp" width="240" alt="Download quality dialog listing original quality and bitrates from 4K down">
  <img src="images/screenshots/downloaded-show.webp" width="240" alt="Downloaded show page listing episodes by season with their sizes">
  <img src="images/screenshots/subtitle-style.webp" width="240" alt="Subtitle style screen with a live preview of yellow subtitles">
  <img src="images/screenshots/player-quality.webp" width="728" alt="Video quality dialog open over the player">
</p>

The Downloads screen with its storage breakdown, download progress in the season list, a quality
choice for every download, a page for each downloaded show, subtitle styling with a live preview,
and video quality during playback.

<sub>All artwork shown belongs to its respective owners.</sub>

## What you get over stock Findroid

### Downloads

- **Resumable download engine** — built on OkHttp instead of Android's `DownloadManager`, with SD card support
- **Download quality picker** — keep the original, or have the server convert it, from 4K 120 Mbps down to 360p
- **Redesigned downloads screen** — one list with progress rings, a per-show page, multi-select, undo, and a storage breakdown
- **Season list built for downloads** — live progress and download, stop and delete buttons on each episode
- **Bulk season & series download** — with a configurable limit on concurrent downloads and per-season status such as "3/10 downloaded"
- **Smart Downloads** — an opt-in setting that keeps the next episodes of a series you are watching downloaded (3 by default) and removes the ones it downloaded once you have watched them

### Playback & player

- **Dolby Vision** — direct play where the device supports it, device-profile-aware transcoding on the rest
- **Better audio decoding** — tries the platform decoder first for Atmos, and falls back to ffmpeg if a decoder fails
- **Version picker & quality selector** — choose a version before playing, and change the video quality during playback
- **Next-season playback** — carries on into the next season, and autoplay can follow your server or be forced on or off
- **Wider subtitle support** — PGS, VobSub, and DVB without transcoding, plus configurable colors, outline, font, and size
- **Media segments** — per-type skip / ask / ignore actions
- **Chapter list** in the player, and an **"Are you still watching?"** inactivity prompt
- **Background audio** (off by default) — keeps playing with the screen off or the app in the background, with artwork and controls on the lock screen. Turn it on under Settings → Player → Background playback; closing the picture-in-picture window then carries on as audio. The video is switched off in the background to save battery

### Library & UI

- **Hide-watched filter** in the library view — persisted and shared across libraries
- **Long-press to copy text** on the movie/show/episode/season/person detail screens
- **Auto offline mode** — automatically enabled when there is no connectivity
- **Error screens with Retry** and proper empty states, instead of endless spinners and blank pages
- **Option to hide cast and crew** on detail screens

### Diagnostics

- **Export device profile** from the About screen — handy for debugging playback/transcoding

See the [changelog](CHANGELOG.md) for the full release-by-release history.

## Install

### Via Obtainium (recommended)

Add this repo URL in [Obtainium](https://github.com/ImranR98/Obtainium):

```
https://github.com/midasvo/findroid-ce
```

Obtainium then checks for new releases automatically and notifies you of updates. Leave
**Include prereleases** off for this entry so it only follows stable releases.

<details>
<summary><strong>Beta channel (Obtainium)</strong></summary>

Beta builds of `main` and of work in progress are published as GitHub prereleases tagged
`v<version>-beta.<n>`. They install as a separate app, **Findroid CE Beta**
(`nl.midasvo.findroid.ce.staging`), with its own data, so they never replace the stable app.

To follow them, add the same repo URL in Obtainium a second time with these settings:

- **Include prereleases**: on
- **Filter release titles by regular expression**: `^Beta`
  (skips stable releases, which can be newer than the latest beta right after a release)
- **Filter APKs by regular expression**: `-beta\.\d+-libre-arm64-v8a\.apk$`
  (swap `arm64-v8a` for your device's ABI if needed)

Betas are untested builds — expect bugs. Every push to `main` publishes one.

</details>

### Manual

Grab the latest APK from the [Releases](https://github.com/midasvo/findroid-ce/releases) page:
`findroid-ce-v<version>-libre-<abi>.apk` for phones and tablets, or `tv-libre-<abi>-release.apk` for
Android TV.

> Findroid CE uses its own application ID (`nl.midasvo.findroid.ce`), so it installs alongside the
> original Findroid rather than replacing it.

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
