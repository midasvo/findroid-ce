# Amazon Appstore

What it would take to put Ephyra on the Amazon Appstore. Nothing here is done yet; the two decisions
below come first, the rest is the same work as Google Play (`docs/play-store.md`).

## Where it reaches

Since August 2025 the Appstore only serves Amazon's own devices:

- **Fire TV** on Fire OS: the TV app (`app/tv`). Fire OS 7 is Android 9 (API 28), Fire OS 8 is
  Android 11 (API 30).
- **Fire tablets** on Fire OS 8: the phone app (`app/phone`).

The Fire TV Stick 4K Select (2025) and Fire TV Stick HD (2026) run Vega OS, which is Linux-based and
doesn't run APKs. Reaching those needs a separate Vega app and is out of scope.

## Decision 1: minSdk

`MIN_SDK = 29` (Android 10) in `buildSrc` excludes every Fire OS 7 device, which includes many sticks
still in use (Fire TV Stick 3rd gen, Lite, the first 4K). Fire OS 8 devices are fine.

- **Keep 29:** no code change, Fire OS 8 TVs and tablets only.
- **Lower to 28 for TV:** reaches Fire OS 7. Needs a check of what in Media3, libmpv and our own
  code requires API 29, and a lint pass with `NewApi` at 28.

**Decision needed.** Whatever is chosen also applies to the GitHub and Play TV builds, since they
share the module.

## Decision 2: signing

By default Amazon re-signs the APK with a key it generates per developer account. The Appstore
build then can't update over an install from GitHub, F-Droid or Play, and the other way round. This
is the same trap that Play App Signing avoids by uploading our existing key.

Amazon's docs mention asking developer support for a different certificate ("Questions about
application signatures" under Contact Us). Before the first upload:

1. Ask whether the app can be signed with our existing release key (the `KEYSTORE_BASE64` secret).
2. If it can't, accept that the Amazon build is a separate install line and say so in the listing
   and README. Users switching from a sideloaded build would have to uninstall first and lose their
   downloads and settings.

The Appstore Certificate Hashes on the *Upload Your App File* screen show which key Amazon signs
with. **Decision needed** once support has answered.

## Building and uploading

There's no maintained fastlane action for Amazon, so the first releases are manual uploads in the
Amazon Developer Console of the APKs that `release.yml` already builds:

| Device | APK |
|---|---|
| Fire TV (most sticks are 32-bit) | `ephyra-v<version>-libre-tv-armeabi-v7a.apk` |
| Fire TV 64-bit (4K Max 2nd gen) | `ephyra-v<version>-libre-tv-arm64-v8a.apk` |
| Fire tablets | `ephyra-v<version>-libre-arm64-v8a.apk`, `...-armeabi-v7a.apk` |

Amazon allows several APKs per app and targets each to devices in the console. Every APK needs a
different versionCode; the TV APKs already sit one higher (`EphyraVersion.TV_CODE`). Check in the
console that the ABI splits don't collide, and give them distinct codes if they do.

The App Submission API can automate this later, the way `fastlane android deploy` does for Play.

## Store listing

Reuse `fastlane/metadata/android/en-US/`: title, short and full description, icon. Amazon asks for
its own image sizes (icons, Fire TV background and screenshots at 1920×1080). Check the current
requirements in the console. The screenshots come from the Jellyfin demo server, same as for Play
(`docs/play-store.md`), to avoid IP rejections.

## App content forms

The answers in the Play table carry over:

| Form | Answer |
|---|---|
| Privacy policy | https://midasvo.nl/ephyra/privacy.html |
| Reviewer login | The Jellyfin demo server (`demo.jellyfin.org/stable`), user `demo`, empty password |
| Ads | No ads |
| Content rating | Media player; no user-generated content shared by the app |
| Data collection | None; data goes only to the user's own server |

Amazon has no closed test requirement like Play's 12 testers for 14 days.

## Before submitting

- **Test on a Fire TV.** Amazon's review uses real devices and the remote. We've only tested on the
  Xiaomi box, so test D-pad focus, playback (ExoPlayer and mpv) and downloads on a Fire TV Stick.
- **Watch Next.** Fire TV doesn't show the Android TV Watch Next row that `androidx.tvprovider`
  writes to. It won't break anything, but don't advertise it in the Fire TV listing.
- **No Google Play Services.** The app has no GMS or Firebase dependency, so nothing to replace.
