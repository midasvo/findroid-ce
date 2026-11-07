# :baselineprofile

Macrobenchmark module for `app/phone` (#249): records the baseline profile and measures startup
and scrolling. It is a `com.android.test` module with the `androidx.baselineprofile` plugin and
targets the `libre` flavour. `app/tv` is not covered: it is a different app with different
screens and would need its own journey and module.

Generation never runs as part of a normal build (`automaticGenerationDuringBuild = false`).

## The journey

`Journey.kt` starts the app and drives it by structure (the scrollable list, its clickable cards),
because the screens have no test tags and text is translated.

- **Logged in:** Home rows, scroll down and up, open the first card, open the first card on that
  screen (a season, when the first card was a show), back to Home.
- **Not logged in:** only the welcome / setup screens are profiled (app start plus a scroll).

A Gradle managed device starts with an empty app, so it only records the second case. **For a
full profile the device must already be logged in:** use your phone.

## Generate the profile

```
# Phone, already logged in to a server (USB or wireless adb, rooting not needed on Android 13+):
./gradlew :app:phone:generateLibreReleaseBaselineProfile -Pbaselineprofile.useConnectedDevices=true

# Managed device (pixel6Api34, AOSP image; downloads the system image, setup screens only):
./gradlew :app:phone:generateLibreReleaseBaselineProfile
```

The profile is written to `app/phone/src/libreRelease/generated/baselineProfiles/`; commit it.
`profileinstaller` then installs it on first run (the phone manifest keeps its initializer).

## Benchmark

`StartupBenchmark` runs the journey cold, 10 iterations, with `CompilationMode.None()` and with
the baseline profile (`Partial`, which needs a committed profile). On a logged-in phone:

```
./gradlew :baselineprofile:connectedLibreBenchmarkReleaseAndroidTest
```

Results are in `baselineprofile/build/outputs/connected_android_test_additional_output/`.
See `docs/performance.md` for what to record.
