# Phase 4: architecture decisions

The decisions from the plan, written as ADRs in [`adr/`](adr). All are **Proposed** until they're agreed in [issue #121](https://github.com/midasvo/findroid-ce/issues/121).

| ADR | Decision | Plan question | Unblocks |
|---|---|---|---|
| [0001](adr/0001-new-ce-screens-next-to-upstream.md) | Build new CE screens in `ce/` next to the upstream ones, and switch route by route in `NavigationRoot` | 1. Evolve or rebuild | every ✱ in 3-gaps |
| [0002](adr/0002-side-navigation-drawer.md) | Side drawer with the libraries in it. Back on Home opens the drawer, a second Back exits | 2. Navigation shape | G9, G4, G17 |
| [0003](adr/0003-focus-restore.md) | One focus helper: a saved position per screen, `focusRestorer` on every row and grid, scroll before focus | 3. Focus strategy | G1 |
| [0004](adr/0004-tv-player-ui.md) | A new Compose player UI on the shared `PlayerViewModel`: key handler, track dialogs, overlay, trickplay | 4. Player UI | G2, G3, G12, G14 |
| [0005](adr/0005-application-id.md) | Keep one application ID. Name the TV APKs `findroid-ce-v…-libre-tv-<abi>.apk` | 5. Application ID | G8 |
| [0006](adr/0006-release-tv-apk.md) | TV APK in betas too, clear names through `outputFileName`, a line in the release notes | 6. Release | G8 |
| [0007](adr/0007-no-offline-on-tv.md) | No downloads or offline mode on TV for now | 7. Offline on TV | G11 |
| [0008](adr/0008-tv-flag-in-shared-modules.md) | Shared modules get an `AppVariant` (phone/TV) from DI for UI choices. `FEATURE_LEANBACK` stays for hardware questions | new | G11, seek settings |

## Correction

The plan and the inventory said that releases don't publish a TV APK. They do: `release.yml` builds everything with `assembleRelease` and uploads `app/**/…/release/*.apk`, so `v1.2.0-ce.4` contains `tv-libre-<abi>-release.apk`. Only the betas lack a TV APK. ADR 0006 is written with that in mind.

## How the decisions change the order in 3-gaps

Hardly at all. G8 becomes small (ADR 0006 is only a Gradle and workflow change) and can go first, so every following step comes out as a TV beta:

1. G8: TV beta + APK names (ADR 0005, 0006)
2. G1: focus helper (ADR 0003), together with the first CE screen
3. G2 + G3: player keys and tracks (ADR 0004)
4. G9 + G10: drawer and safe area (ADR 0002). From here on, every new screen is a CE screen inside the new frame (ADR 0001).
5. G4, G5, G6, G18, then G7, G12, G13, and the rest as in 3-gaps
