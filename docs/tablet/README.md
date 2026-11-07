# Tablet research and design

Research into making the phone app (`app/phone`) a first-class tablet client, next to phone and Android TV. Written 2026-11-07 from `main` at `ab64c3b9`.

| Document | Contents |
|---|---|
| [1-inventory.md](1-inventory.md) | What the code does with window size today: every breakpoint, cap and fixed size, plus the phone/TV screen matrix |
| [2-walkthrough.md](2-walkthrough.md) | The debug build on a 10.5" tablet emulator, landscape and portrait, with 15 findings and screenshots |
| [3-guidelines.md](3-guidelines.md) | What Android and Material ask of a tablet app (size classes, canonical layouts, adaptive quality tiers), and what peer apps do |
| [4-design.md](4-design.md) | **The design:** rules, the layout per size class, every screen, the player, foldables, input, acceptance criteria |
| [5-roadmap.md](5-roadmap.md) | The work in build order, sized, ready to become issues |

## Test setup

- AVD `ce_tablet_tmp`: device profile `pixel_tablet`, image `system-images;android-34;google_apis;x86_64`, 2560×1600 at 320 dpi, so **1280×800 dp** (Google's "10.5-inch tablet" reference size). Started with `-port 5562`.
- `./gradlew :app:phone:assembleLibreDebug`, the `x86_64` APK, logged in to `demo.jellyfin.org/stable` as `demo`.
- Portrait is `settings put system user_rotation 1` on this AVD (its natural orientation is landscape). Phone sizes were checked on the same AVD with `wm size 1080x2400` and `wm density 420`.
- Split-screen: `am start --windowingMode 6` followed by `am task resize <id> 0 0 1200 1600`.

## Scope

- `app/phone` only. The TV app is covered by [tv-research](../tv-research/README.md).
- "Tablet" here means any window of **medium width (600 dp) or wider**, which includes unfolded foldables, phones in landscape at 600 dp+, split-screen on a tablet, Chromebooks and desktop windows. The design is by window size class, never by device type.
