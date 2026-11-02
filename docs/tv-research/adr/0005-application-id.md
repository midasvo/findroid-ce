# ADR 0005: Keep one application ID for phone and TV

**Status:** Accepted (2026-11-02, in #121)

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
- The TV `versionCode` follows the same scheme as phone, so switching from the phone APK to the TV APK on a box is always an upgrade, never a downgrade. Releases already share `Versions.APP_CODE`. For betas, `app/tv` has to copy the `1_000_000 + beta build` versionCode from `app/phone/build.gradle.kts:121-124`.

## Consequences

- People who installed the phone APK on their TV can switch to the TV APK without losing anything. That's the most common situation today, so it outweighs being able to install both side by side.
- Developers can't keep the phone and TV debug builds on one device either. On the emulator that doesn't matter. On the box, switch with `adb install -r` (the login stays).
- If a Play Store or F-Droid listing ever comes, nothing has to change.
