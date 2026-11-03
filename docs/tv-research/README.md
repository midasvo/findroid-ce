# Android TV research

Research into turning `app/tv` into a first-class Android TV client, with [Wholphin](https://github.com/damontecres/Wholphin) as the reference. Tracked in [issue #121](https://github.com/midasvo/ephyra/issues/121).

| Document | Contents |
|---|---|
| [0-plan.md](0-plan.md) | The plan: goal, ground rules, phases, acceptance criteria |
| [1-inventory.md](1-inventory.md) | Phase 1: every TV screen, the player, bugs found in the code, phone vs TV feature matrix |
| [1-walkthrough.md](1-walkthrough.md) | Phase 1: the TV build on an Android TV emulator with the remote, 14 findings with screenshots |
| [2-wholphin.md](2-wholphin.md) | Phase 2: Wholphin on the same emulator plus its source, the full feature matrix, screenshots |
| [3-gaps.md](3-gaps.md) | Phase 3: every gap with impact, size and location, and the order to build them in |

| [4-decisions.md](4-decisions.md) | Phase 4: eight architecture decisions as ADRs in [`adr/`](adr) (accepted) |

| [5-roadmap.md](5-roadmap.md) | Phase 5: 19 follow-up issues (#124–#142) in build order |

## Test setup

- Android TV emulator, image `system-images;android-34;android-tv;x86` (Android 14, 32-bit like the Xiaomi box), 1080p, 2 GB RAM.
- The TV debug build (`./gradlew :app:tv:assembleLibreDebug`, the `x86` APK) and Wholphin v1.0.9.
- Driven only with D-pad, OK and Back key events (`adb shell input keyevent`).
- The final check on the Xiaomi box (speed, memory, Dolby Vision) is still open.
