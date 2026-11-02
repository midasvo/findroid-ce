# ADR 0006: Publish the TV APK in betas and releases, with clear names

**Status:** Accepted (2026-11-02, in #121)

## Context

- `release.yml` runs `./gradlew assembleRelease` and uploads `app/**/build/outputs/apk/**/release/*.apk`, so releases already contain the TV APKs, under the unclear name `tv-libre-<abi>-release.apk`. *(The plan and the inventory said that releases don't publish a TV APK. That was wrong.)*
- `beta.yml` only builds `:app:phone:assembleLibreStaging`, so there's no TV beta.
- The TV work is going to land in many small steps (3-gaps, "Order"). Without a TV beta there's no way to try them on a real TV between releases.

## Decision

1. **Beta:** `beta.yml` also builds `:app:tv:assembleLibreStaging` and attaches `app/tv/build/outputs/apk/libre/staging/*.apk`. The staging TV build uses the same `.staging` suffix as phone (ADR 0005).
2. **Names:** `app/tv/build.gradle.kts` sets `outputFileName` to `findroid-ce-v<version>-libre-tv-<abi>.apk`, the same way `app/phone/build.gradle.kts:130-132` does for phone. Then the workflows need no rename step. Copy the beta versionCode logic from phone as well (`app/phone/build.gradle.kts:121-124`), so TV betas always go up.
3. **Release notes:** one line saying which APKs are for Android TV, with the 32-bit note (`armeabi-v7a` for boxes such as the Xiaomi).
4. **CI checks:** nothing to do. `build.yaml` runs `assembleDebug` on every PR, which builds `app/tv` too, so a shared-module change that breaks TV already fails the PR.

## Consequences

- A TV beta appears from the next merge to `main`. The beta release gets 4 more files, and the retention of 3 betas (#102) keeps that in check.
- Users on the box can follow the betas with Obtainium.
- G8 in 3-gaps becomes S: a workflow change, not a decision.
