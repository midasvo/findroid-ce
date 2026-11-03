# Phase 5: roadmap

The follow-up issues, in the order from [4-decisions.md](4-decisions.md). All have the `tv` label (except #142, which is for phone and TV) and are sub-issues of [#121](https://github.com/midasvo/findroid-ce/issues/121).

## Step 1: foundation

| Issue | What | Gaps | ADR | Depends on |
|---|---|---|---|---|
| [#124](https://github.com/midasvo/findroid-ce/issues/124) | ✅ TV APK in betas, clear file names (done in #143) | G8 | 0005, 0006 | — |
| [#125](https://github.com/midasvo/findroid-ce/issues/125) | ✅ Focus restore helper and the first CE screens (library, show, season) (done in #144) | G1 | 0001, 0003 | — |
| [#126](https://github.com/midasvo/findroid-ce/issues/126) | ✅ Player: seek with the remote, media keys, track dialogs (done in #146) | G2, G3 | 0004 | #133 for the seek settings (or a temporary setting) |
| [#127](https://github.com/midasvo/findroid-ce/issues/127) | Side navigation drawer and safe-area layout | G9, G10 | 0001, 0002, 0003 | #125 |

## Step 2: fix what's broken

| Issue | What | Gaps | Depends on |
|---|---|---|---|
| [#128](https://github.com/midasvo/findroid-ce/issues/128) | ✅ Search screen | G4 | #127 (for its place in the drawer) |
| [#129](https://github.com/midasvo/findroid-ce/issues/129) | ✅ Open collections, movie trailers, reach all settings (done in #151) | G5, G6, G18 | — |
| [#133](https://github.com/midasvo/findroid-ce/issues/133) | ✅ `AppVariant` flag, hide phone-only settings on TV (done in #157) | G11 | — |

## Step 3: the core loop

| Issue | What | Gaps | Depends on |
|---|---|---|---|
| [#130](https://github.com/midasvo/findroid-ce/issues/130) | ✅ Open episodes in their season instead of playing right away | G7 | #125 |
| [#131](https://github.com/midasvo/findroid-ce/issues/131) | ✅ Player overlay: series, ends-at time, next/previous, queue | G12 | #126 |
| [#132](https://github.com/midasvo/findroid-ce/issues/132) | ✅ Home with a hero area, no reshuffling | G13 | #125, #127 |

## Step 4: parity with phone

| Issue | What | Gaps | Depends on |
|---|---|---|---|
| [#134](https://github.com/midasvo/findroid-ce/issues/134) | Trickplay while seeking | G14 | #126 |
| [#135](https://github.com/midasvo/findroid-ce/issues/135) | ✅ Library grid: A–Z jump, counter, placeholders (done in #154; genres tab not yet) | G15 | #125 |
| [#136](https://github.com/midasvo/findroid-ce/issues/136) | Cast row and person page | G16 | #125 |
| [#137](https://github.com/midasvo/findroid-ce/issues/137) | Favorites page | G17 | #127 |

## Step 5: polish and TV integration

| Issue | What | Gaps |
|---|---|---|
| [#138](https://github.com/midasvo/findroid-ce/issues/138) | ✅ Continue watching on the Android TV home screen (Watch Next) | G19 |
| [#139](https://github.com/midasvo/findroid-ce/issues/139) | ✅ Switch the display refresh rate to the video frame rate (done in #158) | G20 |
| [#140](https://github.com/midasvo/findroid-ce/issues/140) | Screensaver with artwork | G21 |
| [#141](https://github.com/midasvo/findroid-ce/issues/141) | Small polish: date format and shuffle done (in #153), theme music still open (branding: #122) | G22, G24 |
| [#142](https://github.com/midasvo/findroid-ce/issues/142) | Customisable Home rows, for phone and TV (needs a discussion first) | G23 |

## Still open in #121

- The final check on the Xiaomi box: frame times and memory while scrolling a large library, and Dolby Vision playback. It waits until the box is free. The results can change priorities, for example if the box turns out to stutter on the current Home.
