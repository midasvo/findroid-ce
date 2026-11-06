# Google Play

How Ephyra gets onto Google Play (issue #177). Phone and TV share one application ID
(`nl.midasvo.findroid.ce`, ADR 0005). The TV bundle has a versionCode one higher than the phone
bundle (`EphyraVersion.TV_CODE`) and goes to the Android TV form factor track.

## Signing

Use Play App Signing with our **existing** release key, the one in the `KEYSTORE_BASE64` secret. If
Google generates the app signing key instead, installs from GitHub and F-Droid can't update to the
Play build or the other way around. This choice can't be undone.

1. In the Play Console, on the first upload, choose **Use existing app signing key** and then
   **Export and upload a key from Java keystore**. Download `pepk.jar` and the encryption key, run
   the command shown with our release `.jks`, and upload the zip it writes.
2. Make a separate upload key for CI and register its certificate in the same screen:

   ```bash
   keytool -genkeypair -keystore ephyra-upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
   keytool -export -rfc -keystore ephyra-upload.jks -alias upload -file upload_certificate.pem
   ```

Keep both keystores out of the repo. If the upload key is lost, Google can reset it; the app signing
key can't be replaced.

## Building and uploading

`fastlane android deploy` builds both bundles and uploads them (see the comments in
`fastlane/Fastfile`). It signs with whatever the `KEYSTORE_*` variables point at, so point them at
the **upload** key, and set `PLAY_API_CREDENTIALS` to a service account JSON key with release access
to the app.

Until the app has its first live release, the Play API only accepts drafts, and the TV track only
exists once Android TV is added under *Test and release → Advanced settings → Form factors*:

```bash
fastlane android deploy tv:false release_status:draft
```

By hand, without fastlane:

```bash
./gradlew :app:phone:bundleLibreRelease :app:tv:bundleLibreRelease
```

The bundles are in `app/{phone,tv}/build/outputs/bundle/libreRelease/`.

## Store listing

The listing comes from `fastlane/metadata/android/en-US/`: title, short and full description,
icon, feature graphic, TV banner and the changelog per versionCode.

The phone and tablet screenshots there are still upstream Findroid's and must be replaced before the
first upload. Take the new ones, and the TV screenshots (`images/tvScreenshots/` in the same folder, 1920×1080), on
the Jellyfin demo server (`demo.jellyfin.org/stable`, user `demo`, no password), which only has
public-domain films. Third-party artwork in listing screenshots risks an IP rejection.

## App content forms

| Form | Answer |
|---|---|
| Privacy policy | https://midasvo.nl/ephyra/privacy.html (source: `site/privacy.html`) |
| App access | Login needed: the Jellyfin demo server, user `demo`, empty password |
| Ads | No ads |
| Content rating | Media player; no user-generated content shared by the app |
| Target audience | 18 and over only, so the Families policy doesn't apply |
| Data safety | No data collected or shared; data goes only to the user's own server |
| Foreground services | `dataSync` for downloads, `mediaPlayback` for playback, each with a short video |

## Closed test

New personal developer accounts need a closed test with at least 12 opted-in testers for 14 days in
a row before they can apply for production. Testers join with their Google account through a Google
Group or an opt-in link.
