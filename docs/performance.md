# Performance

Startup and scrolling work (epic #259) is measured with the `:baselineprofile` module. Details
and commands: `baselineprofile/README.md`.

## Baseline profile

The profile lives in `app/phone/src/libreRelease/generated/baselineProfiles/`. Regenerate it
on a logged-in phone when screens or the startup path change a lot, and commit the result:

```
./gradlew :app:phone:generateLibreReleaseBaselineProfile -Pbaselineprofile.useConnectedDevices=true
```

Check it was installed: `adb shell dumpsys package dexopt | grep -A3 findroid.ce` should show a
profile-based compile state after installing a release build.

## Benchmark

```
./gradlew :baselineprofile:connectedLibreBenchmarkReleaseAndroidTest
```

Use the same phone, same server, same battery and thermal state for before and after. Close other
apps, and discard a run where the device was busy.

## What to record in a PR

For a performance change, paste a before/after table from the same device:

| Metric                              | Before | After |
| ----------------------------------- | ------ | ----- |
| `timeToInitialDisplayMs` median     |        |       |
| `frameDurationCpuMs` P50 / P90 / P99 |        |       |
| `frameOverrunMs` P90                |        |       |

Take it for both `journeyNoCompilation` and `journeyBaselineProfile`, plus the device model and
Android version. State whether the device was logged in (full journey) or not (setup screens only).

## R8

App classes are renamed by R8 except the navigation routes (`-keepnames` on `NavKey`
implementations in `app/phone/proguard-rules.pro`). When a crash trace shows short names, retrace
with the build's `mapping.txt`. A new class that is looked up by name needs its own rule.
