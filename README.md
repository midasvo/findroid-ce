<img src="images/branding/ephyra/ephyra-icon-rounded.png" alt="Ephyra icon: a cyan and violet jellyfish whose tentacles fork" width="96" align="right">

# Ephyra

Ephyra is a Jellyfin client for Android phones, tablets and Android TV. It is a fork of
[Findroid](https://github.com/jarnedemeulemeester/findroid) that puts its effort into three things:
downloads that are worth using, playback of demanding files, and a TV app made for a remote.

It was called Findroid CE until version 2.0.0. If you still have Findroid CE installed, the update
installs over it and keeps your login, settings and downloads.

You need a [Jellyfin](https://jellyfin.org/) server and a device running Android 10 or newer.

[What Ephyra adds](#what-ephyra-adds-to-findroid) · [Install](#install) · [Android TV](#android-tv) ·
[Bugs and contributions](#bugs-ideas-and-contributions) · [Privacy](#privacy) · [Thanks](#thanks) ·
[License](#license)

<table>
  <tr>
    <td width="33%" align="center"><img src="images/screenshots/downloads.webp" alt="Downloads screen with a storage bar, two active downloads with progress rings, and a downloaded movie"><br><sub>Downloads, with a storage breakdown</sub></td>
    <td width="33%" align="center"><img src="images/screenshots/season.webp" alt="Season episode list showing downloaded, downloading and not yet downloaded episodes"><br><sub>Download progress per episode</sub></td>
    <td width="33%" align="center"><img src="images/screenshots/download-quality.webp" alt="Download quality dialog listing original quality and bitrates from 4K down"><br><sub>Every download asks for a quality</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="images/screenshots/downloaded-show.webp" alt="Downloaded show page listing episodes by season with their sizes"><br><sub>A page per downloaded show</sub></td>
    <td align="center"><img src="images/screenshots/subtitle-style.webp" alt="Subtitle style screen with a live preview of yellow subtitles"><br><sub>Subtitle styling, with a live preview</sub></td>
    <td align="center"><img src="images/screenshots/home-rows.webp" alt="Home rows setting listing the Home rows with arrows to reorder them and switches to hide them"><br><sub>Choose and order the Home rows</sub></td>
  </tr>
  <tr>
    <td colspan="3" align="center"><img src="images/screenshots/player-quality.webp" alt="Video quality dialog open over the player"><br><sub>Change the video quality during playback</sub></td>
  </tr>
</table>

<sub>All artwork shown belongs to its respective owners.</sub>

## What Ephyra adds to Findroid

**Downloads.** Ephyra has its own download engine, built on OkHttp instead of Android's
DownloadManager. Downloads resume after an interruption and can go to an SD card. Each one asks for
a quality: the original file, or a conversion by the server anywhere from 4K down to 360p. You can
download a whole season or series at once, with a limit on how many run at the same time. The
downloads screen shows progress per episode and per show, how much space each takes, and supports
multi-select and undo. Smart Downloads, off by default, keeps the next few episodes of whatever you
are watching downloaded and removes them once you have watched them.

**Playback.** Dolby Vision plays directly on devices that support it and is transcoded on the rest;
Dolby Vision downloads can be converted to a device-compatible copy too. For audio, the platform
decoder is tried first, which is what Atmos needs, and ffmpeg takes over if a decoder fails. PGS,
VobSub and DVB subtitles play without transcoding, and subtitle colour, outline, font and size are
yours to set. You can choose a version before playing and change the video quality during playback.
Playback carries on into the next season, and the audio and subtitle tracks you pick for a series
stick for its next episodes, per user, on ExoPlayer and mpv and for downloads. There is a chapter
list, a skip, ask or ignore action per type of media segment, and an "Are you still watching?"
prompt after a long stretch of autoplay. Background audio, off by default, keeps playing with the
screen off and puts the controls on the lock screen. It lives under Settings → Player → Background
playback.

**Library.** Favorites has its own tab in the main navigation. You choose which rows Home shows and
in what order, under Settings → Interface → Home rows. The hide-watched filter stays on across
libraries. Offline mode switches on by itself when there is no connection. Errors get a screen with
a Retry button and empty libraries get a real empty state, rather than a spinner. Text on detail
screens can be copied with a long press, and cast and crew can be hidden.

**Android TV.** A separate app for the TV, described [below](#android-tv).

The rest, the native UI, ExoPlayer and mpv playback, offline mode and picture-in-picture, is
Findroid's work, and Findroid's changes are merged in regularly. The [changelog](CHANGELOG.md) has
the release-by-release detail.

## Install

### Obtainium

Add this URL in [Obtainium](https://github.com/ImranR98/Obtainium) and it will pick up every new
release:

```
https://github.com/midasvo/ephyra
```

Leave **Include prereleases** off. Set **Filter APKs by regular expression** so Obtainium offers the
right file for the device:

| Device | Filter |
|---|---|
| Phone or tablet | `-libre-arm64-v8a\.apk$` |
| Android TV, 64-bit | `.*-tv-arm64-v8a\.apk` |
| Android TV, 32-bit (Xiaomi Mi Box and similar) | `.*-tv-armeabi-v7a\.apk` |

An existing entry for `midasvo/findroid-ce` keeps working, because GitHub redirects the old address.

### Releases page

Download the APK from [Releases](https://github.com/midasvo/ephyra/releases). Phone and tablet
builds are named `ephyra-v<version>-libre-<abi>.apk`, TV builds `ephyra-v<version>-libre-tv-<abi>.apk`.
Nearly every current device is `arm64-v8a`; older 32-bit TV boxes such as the Xiaomi Mi Box need
`armeabi-v7a`.

Ephyra has its own application ID, `nl.midasvo.findroid.ce`, so it installs next to Findroid rather
than over it. The phone and TV builds share that ID.

### F-Droid

Ephyra has been [submitted to F-Droid](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/56048)
and is waiting for review. The F-Droid build is set up to be reproducible and verified against the
GitHub release APKs, so once it is listed you will be able to switch between the two sources without
reinstalling. Until then, use Obtainium or the releases page.

### Beta builds

Every push to `main` publishes a prerelease tagged `v<next version>-beta.<n>`. Betas install as a
separate app, **Ephyra Beta**, with their own data, so they never touch the stable install. Nobody
tests them before they go out, so expect rough edges.

To follow them in Obtainium, add the repository URL a second time with:

- **Include prereleases** on
- **Filter release titles by regular expression**: `^Beta`
- **Filter APKs by regular expression**: `-beta\.\d+-libre-arm64-v8a\.apk$`, or
  `-beta\.\d+-libre-tv-armeabi-v7a\.apk$` on a 32-bit TV box

The title filter matters: right after a stable release, the stable version is newer than the latest
beta, and without the filter Obtainium would offer it to the beta entry.

## Android TV

<table>
  <tr>
    <td colspan="2" align="center"><img src="images/screenshots/tv-home.webp" alt="TV Home with a large artwork hero for the focused item, a Suggestions row below it and a collapsed side drawer"><br><sub>Home, with a hero for the focused item</sub></td>
  </tr>
  <tr>
    <td width="50%" align="center"><img src="images/screenshots/tv-library.webp" alt="TV shows library grid with Hide watched and Sort by buttons, an A to Z bar on the right and a 4 of 44 counter"><br><sub>Library grid with an A to Z jump bar</sub></td>
    <td width="50%" align="center"><img src="images/screenshots/tv-season.webp" alt="Bluey season 3 with season tabs, the details of the focused episode and a row of episodes"><br><sub>Episodes open in their season</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="images/screenshots/tv-player.webp" alt="Player overlay with the series name, episode title, ends-at time, previous and next buttons and an Up next header"><br><sub>Player overlay with the next episodes</sub></td>
    <td align="center"><img src="images/screenshots/tv-trickplay.webp" alt="A trickplay thumbnail above the seek time while skipping forward"><br><sub>Trickplay thumbnails while seeking</sub></td>
  </tr>
</table>

The TV app uses the same login and server as the phone app and ships in the same releases, as the
`-tv-` APKs above. It was built around the remote rather than adapted from the phone layout:

- A side drawer with Search, Home, Favorites, your libraries and Settings.
- Home shows a large hero for the focused item, and the rows stay where they are instead of
  reshuffling. You pick which rows show and in what order, as on the phone.
- Library grids have an A to Z jump bar, a counter and the hide-watched filter.
- Episodes open in their season with the details first, instead of playing straight away. Movies and
  shows have a cast row, and every person has a page.
- The player seeks with the D-pad, takes media keys, shows trickplay thumbnails while seeking, and
  has an overlay with the series, the time it ends, previous and next, and the queue.
- The display can switch to the video's frame rate during playback.
- Continue Watching and Next Up appear in the Watch Next row on the Android TV home screen.
- A screensaver with artwork from your library, and optional theme music on show pages.
- Collections, trailers and every relevant setting are reachable with the remote. Settings that only
  make sense on a phone are hidden.

The research behind it, where Findroid's TV app fell short, the decisions, and what is still planned,
is in [docs/tv-research/](docs/tv-research/).

## Bugs, ideas and contributions

Open an [issue](https://github.com/midasvo/ephyra/issues). For playback problems, attach the device
profile from the About screen (Export device profile). It lists what the device can decode, which
answers most transcoding questions on the spot.

Pull requests are welcome. [CLAUDE.md](CLAUDE.md) describes the modules and the architecture,
[docs/identity/VISION.md](docs/identity/VISION.md) the visual identity, and
[docs/upstream-sync.md](docs/upstream-sync.md) how Findroid's changes are merged in. A debug build is
one command:

```bash
./gradlew :app:phone:assembleLibreDebug
```

Strings shared with Findroid are translated on Findroid's Weblate. Ephyra's own strings live in
`strings_ce.xml` files next to them, in the same 36 languages; corrections go in a pull request.

## Privacy

Ephyra collects nothing and talks only to the Jellyfin servers you add. The
[privacy policy](https://midasvo.nl/ephyra/privacy.html) says the same at greater length.

## Thanks

Ephyra would not exist without [Findroid](https://github.com/jarnedemeulemeester/findroid) by
[Jarne Demeulemeester](https://github.com/jarnedemeulemeester) and its
[contributors](https://github.com/jarnedemeulemeester/findroid/graphs/contributors).

An ephyra is the juvenile stage of a jellyfish, the animal in the Jellyfin logo. The icon was made
for this project; its sources and the script that generates them are in
[images/branding/ephyra/](images/branding/ephyra/).

## License

[GPLv3](LICENSE).
