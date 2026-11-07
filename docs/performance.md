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

## Measurements

### #249: baseline profile (2026-11-07)

OPPO CPH2359, Android 14 (`CPH2359_14.0.0.3001(EX01)`), logged in (full journey), battery
91-100 %, 28-30 °C, thermal status 0. `benchmarkRelease` build of `main` with the committed
profile, 10 iterations each.

| Metric                               | `journeyNoCompilation` | `journeyBaselineProfile` |
| ------------------------------------ | ---------------------- | ------------------------ |
| `timeToInitialDisplayMs` median      | 350.8                  | 353.5                    |
| `frameDurationCpuMs` P50 / P90 / P99 | 7.9 / 15.0 / 95.1      | 7.4 / 15.7 / 82.6        |
| `frameOverrunMs` P90                 | 3.46                   | 2.90                     |

Cold start with `am start -W`: median `TotalTime` of 5 runs per block, each block after one launch
and `pm compile -r bg-dexopt` (every block ended up `speed-profile`), order V, NP, P, P, NP:

| Build                                | Median TotalTime |
| ------------------------------------ | ---------------- |
| v2.1.0 release (no ProfileInstaller) | 227 ms           |
| main, library profiles only          | 225 / 238 ms     |
| main, library and app profile        | 222 / 234 ms     |

On this phone the profile makes no measurable difference to startup: runs inside one block spread
from 190 to 247 ms, more than any difference between builds, and the warm-up launch lets the
runtime record a profile of its own before `bg-dexopt`. The profile shortens the journey's long
frames (P99 95 → 83 ms). It should count more on slower devices and in the first launches after an
update, before the runtime has recorded anything.
