# Amazon Appstore submission kit

What to enter on each page of the Amazon Developer Console for Ephyra's first submission, in the
order the console asks for it. The background and decisions are in `docs/amazon-appstore.md`.
Amazon blocks AI agents on its sign-in, so this is done by hand.

Start at *Apps & Services → My Apps → Add New App → Android*.

## Before you start

- **Company profile** (*Settings → Company Profile*): the developer name shown on the listing and
  the support contact. Suggested website: `https://github.com/midasvo/ephyra/issues`.
- **APKs:** the two `armeabi-v7a` APKs of the latest release:

  ```bash
  gh release download v2.1.0 --repo midasvo/ephyra --pattern "*-armeabi-v7a.apk" --dir amazon-upload
  ```

  | File | For | versionCode |
  |---|---|---|
  | `ephyra-v2.1.0-libre-tv-armeabi-v7a.apk` | Fire TV | 2010001 |
  | `ephyra-v2.1.0-libre-armeabi-v7a.apk` | Fire tablets | 2010000 |

- **Images** in `images/branding/ephyra/` (from `make-ephyra.sh`), and the screenshots in
  `images/amazon/`.

## Step 1: Upload your app file

| Field | Value |
|---|---|
| App files | Drop both APKs, one at a time |
| Apply Amazon DRM? | No |
| App title | Ephyra |
| App SKU | `ephyra` |
| App category | Movies & TV (closest video category) |
| Language support | English (add more once the listing is translated) |
| Public key / IAP | Not needed: no Appstore SDK or in-app purchases |

## Step 2: Target your app

**Supported devices.** Amazon doesn't split devices between APKs on its own, and two APKs can't
both claim a device. Set:

- `ephyra-v2.1.0-libre-tv-armeabi-v7a.apk`: all Fire TV devices, no tablets.
- `ephyra-v2.1.0-libre-armeabi-v7a.apk`: all Fire tablets, no Fire TV.
- Amazon Automotive: off for both.

Some devices may already be greyed out by the manifest (minSdk 29 excludes Fire OS 7); leave them.

| Section | Answer |
|---|---|
| Availability | All countries |
| Target audience | Not directed at children (no COPPA) |
| Content rating | *None* for every content category. Account creation: no (users sign in to their own server). Advertisements: no. Gambling: no. Location: no. User-generated content: no. |
| Collects or transfers user data? | No. Data goes only to the Jellyfin server the user adds. |
| Privacy policy URL (if asked) | https://midasvo.nl/ephyra/privacy.html |

## Step 3: Appstore details

**Pricing:** Free.

**Display title:**

```
Ephyra for Jellyfin
```

**Short description** (Fire TV shows the first 200 characters):

```
Watch your movies and series from your own Jellyfin server. Download for offline viewing, Dolby Vision direct play, subtitle styling and intro skipping, in a native, remote-friendly app.
```

**Long description** (max 4,000 characters):

```
Ephyra is a native Jellyfin client for TVs, tablets and phones, and a community-driven fork of Findroid. Browse and play the movies and series on your Jellyfin server with an interface built for the remote and for touch.

To use this app you need a Jellyfin server. Jellyfin is free, open-source media server software that you run yourself.

What Ephyra offers:
- Resumable downloads with quality selection, bulk downloads and Smart Downloads
- Dolby Vision direct play and wide audio codec support (Atmos, ffmpeg fallback)
- Video quality and version picker during playback
- Subtitle styling (colours, size, fonts) and wide format support (PGS, VobSub, SSA/ASS)
- Audio and subtitle choices remembered per series
- Chapter list and intro and outro skipping
- Background audio playback
- Hide-watched filter and automatic offline mode

Ephyra has no ads, no analytics and no tracking. Your data only goes to your own server.

Based on Findroid by Jarne Demeulemeester. Ephyra is open source: github.com/midasvo/ephyra
```

**Product feature bullets** (one per line):

```
Native app for your own Jellyfin server
Download movies and episodes to watch offline
Dolby Vision direct play and wide audio and subtitle support
Skip intros and outros, with chapters and per-series track memory
No ads, no tracking: data only goes to your own server
```

**Keywords:**

```
jellyfin, media server, home media, movies, tv shows, video player, offline, self-hosted
```

**Images and videos:**

| Asset | File | Status |
|---|---|---|
| Tablet small icon, 114×114 | `images/branding/ephyra/ephyra-icon-114.png` | Ready |
| Tablet large icon, 512×512 | `images/branding/ephyra/ephyra-icon.png` | Ready |
| Tablet promotional image, 1024×500 (optional) | `images/branding/ephyra/ephyra-feature-graphic.png` | Ready |
| Tablet screenshots, 3–10 | `images/amazon/tablet/*.jpg` (4) | Ready |
| Fire TV app icon, 1280×720 | `images/branding/ephyra/ephyra-firetv-icon.png` | Ready |
| Fire TV background, 1920×1080 | `images/branding/ephyra/ephyra-firetv-background.png` | Ready |
| Fire TV screenshots, 3–10 at 1920×1080 | `images/amazon/firetv/*.jpg` (5) | Ready |

## Step 4: Review and submit

| Field | Value |
|---|---|
| When to publish | Leave blank: publish as soon as it passes review |
| Testing instructions | See below |
| Export compliance | Confirm. The app only uses standard HTTPS encryption. |

**Testing instructions:**

```
Ephyra is a client for Jellyfin, a self-hosted media server, so it needs a server to sign in to. Please use the public Jellyfin demo server:

Server address: https://demo.jellyfin.org/stable
Username: demo
Password: (leave empty)

On the first screen, enter the server address, then pick the "demo" user and sign in with an empty password. The demo server only contains public-domain films.
```

Then click **Submit App**.

## Screenshots

`images/amazon/` holds the screenshots, taken with v2.1.0 on the Jellyfin demo server
(`demo.jellyfin.org/stable`, user `demo`, no password), which only has public-domain and
free-culture films. Other artwork risks an IP rejection (`docs/play-store.md`).

- **Fire TV** (`firetv/`, 1920×1080): home, a film's page, the movie library, a season, the player.
  Taken on an Android TV emulator at 1080p.
- **Tablets** (`tablet/`): home, the movie library and a film's page at 1920×1080, the player at
  1920×1200. Taken on a Pixel Tablet emulator (2560×1600, dark theme). The first three are cropped
  to 16:9 to drop the status bar and taskbar, then scaled down.

To retake them, sign in on throwaway emulators, capture with `adb shell screencap`, and convert to
JPG without transparency. On the TV login screen, the D-pad doesn't leave the username field: use
Tab (`adb shell input keyevent TAB`) to reach Login.
