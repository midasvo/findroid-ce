# ADR 0005: Keep one application ID for phone and TV

**Status:** Accepted (2026-11-02, in #121). Updated 2026-11-03 in #173: the TV release gets a higher versionCode.

## Context

`app/phone` and `app/tv` both use `nl.midasvo.findroid.ce` (and the `.debug` / `.staging` suffixes). Upstream does the same with `dev.jdtech.jellyfin`, so it can ship one Play Store listing with a separate TV APK.

CE ships through GitHub releases, not the Play Store. Since `v1.2.0-ce.4` the release has four phone APKs (`findroid-ce-v1.2.0-libre-<abi>.apk`) and four TV APKs (`tv-libre-<abi>-release.apk`).

Today the two APKs replace each other on a device. The Xiaomi box runs the *phone* debug build at the moment.

## Options

1. **Keep one ID.**
   - Installing the TV APK on a box that has the phone build upgrades it in place, and the login, server list and settings stay.
   - One app on the home screen.
   - The same as upstream.
   - But both can't be installed on one device, and an update tool (Obtainium) has to pick the right APK by file name.
2. **A separate ID for TV** (`nl.midasvo.findroid.ce.tv`).
   - Both can be installed side by side.
   - But users who now run the phone build on a TV have to log in again.
   - There are two apps in the launcher.
   - Phone and TV can't share a future Play/F-Droid listing.

## Decision

Option 1, keep one ID, and make the APKs easy to tell apart:

- Rename the TV release APKs to `findroid-ce-v<version>-libre-tv-<abi>.apk` (now `tv-libre-<abi>-release.apk`), the same pattern as phone.
- Mention in the README and the release notes which APK is for a TV, and give a ready-made Obtainium filter (`.*-tv-armeabi-v7a\.apk` for 32-bit boxes such as the Xiaomi).
- The TV `versionCode` follows the same scheme as phone, so switching from the phone APK to the TV APK on a box is always an upgrade, never a downgrade. Releases already share `Versions.APP_CODE` *(since #173 the TV release is `APP_CODE + 10_000`, see the update below)*. For betas, `app/tv` has to copy the `1_000_000 + beta build` versionCode from `app/phone/build.gradle.kts:121-124`.

## Consequences

- People who installed the phone APK on their TV can switch to the TV APK without losing anything. That's the most common situation today, so it outweighs being able to install both side by side.
- Developers can't keep the phone and TV debug builds on one device either. On the emulator that doesn't matter. On the box, switch with `adb install -r` (the login stays).
- A Play Store or F-Droid listing needs one change, see the update below.

## Update (2026-11-03, #173)

The last consequence was wrong: one application ID works for the stores, but not with the same versionCode for phone and TV.

- **Google Play** needs a unique versionCode for every upload in one app, so the phone and the TV bundle can't both be `APP_CODE`.
- **F-Droid** has one listing per application ID and offers the highest compatible versionCode. The TV APK requires `android.software.leanback`, so phones never see it. On a TV both APKs are compatible, so the TV APK has to be the higher one.

So the TV **release** now gets `Versions.APP_CODE + 10_000` (`tvReleaseCodeOffset` in `app/tv/build.gradle.kts`). The rest stays the same:

- Installing the TV release over the phone release on a box is still an upgrade and keeps the login. Going back from TV to phone is a downgrade now, but that's not a path anyone should take.
- Betas keep `1_000_000 + run` for both, and debug builds keep `APP_CODE` for both, so `adb install -r` still switches between phone and TV during development.
- The offset leaves room for 10,000 releases before the codes could meet.
