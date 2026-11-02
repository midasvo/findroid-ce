# ADR 0008: Tell shared modules which app they run in, not which device

**Status:** Proposed

## Context

Some shared behaviour has to differ in the TV app:

- settings that make no sense on TV: gestures, trickplay until it exists, downloads ([1-walkthrough.md](../1-walkthrough.md), F7; ADR 0007);
- TV-only settings: seek steps (ADR 0004).

`PlayerViewModel` already has an `isTv`, but it reads it from the device (`FEATURE_LEANBACK`). That's right for decoder choice, which depends on the hardware. It's wrong for UI choices: the phone APK on a TV box would then hide phone settings that the phone UI does use.

## Decision

- Add a `@AppVariant` value to the DI graph: an enum `AppVariant { PHONE, TV }`, provided by a small Hilt module in each app (`app/phone` and `app/tv`). It isn't defined in `core`, so each APK states what it is.
- Shared ViewModels that need it inject it (for example `SettingsViewModel` filters its tree with it). The default behaviour is the phone one, so phone doesn't change (rule 9).
- `FEATURE_LEANBACK` stays the check for hardware questions (decoders, tunneling, refresh rate). The two questions are kept separate on purpose.

## Consequences

- The settings tree gets a `variants` (or `tvOnly` / `phoneOnly`) field per item instead of `if`s spread around. Phone shows everything it shows today.
- New shared features have to decide per variant. Add a line about this to `CLAUDE.md` once the first change uses it.
