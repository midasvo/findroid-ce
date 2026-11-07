# mpv research

Research for [issue #284](https://github.com/midasvo/ephyra/issues/284): how healthy is the mpv backend? Research only. It ends in these documents and in follow-up issues, not in code.

| File | Phase | Status |
|---|---|---|
| [0-plan.md](0-plan.md) | Copy of the plan in the issue | done |
| [1-inventory.md](1-inventory.md) | Phase 1: how mpv is wired, summary and the stuck-loader reproduction | done |
| [1a-player-contract.md](1a-player-contract.md) | Phase 1: `MPVPlayer` against the Media3 `Player` contract | done |
| [1b-state-machine.md](1b-state-machine.md) | Phase 1: the state machine and its transitions | done |
| [1c-consumers-and-settings.md](1c-consumers-and-settings.md) | Phase 1: who consumes the player, settings, migrations, upstream | done |
| [2-test-pass.md](2-test-pass.md) | Phase 2: results table, both backends | started (start and stop rows on the phone emulator) |
| [3-findings.md](3-findings.md) | Phase 3: one entry per difference from ExoPlayer | started (entry 1 confirmed, the rest are hypotheses from phase 1) |
| 4 | Phase 4: follow-up issues, defaults, checklist, verdict | not started |

Code was read on `main` at `7e21c0de`. Device runs so far: the phone debug build on the `emulator-5558` AVD (Android 14, x86_64, emulated 3G network). Nothing has run on the OPPO or the Xiaomi box yet.
