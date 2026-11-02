# Android TV research: plan

Copy of the plan in [issue #121](https://github.com/midasvo/findroid-ce/issues/121).

## Goal

Find out what it takes to turn `app/tv` into a first-class Android TV client: one you pick up with a remote and never miss the phone app. [Wholphin](https://github.com/damontecres/Wholphin) is the bar we measure against, because it is the Jellyfin TV client that feels the most native today.

This issue is **research only**. It ends in a written report and a set of follow-up issues, not in code. Each phase below has its own deliverable, so the work can stop or change direction after any of them.

## Where we are now

We already build a TV APK, but it was never designed as a product. From a quick look at `main` (`d113b8b6`):

- `app/tv` has 57 Kotlin files on `androidx.tv:tv-material` 1.1.0, against a much larger `app/phone`.
- **Screens that exist:** setup (welcome, servers, users, login), Main with a top `TabRow` (Search, Home, libraries, Settings), Library, Movie, Show, Season, Player, Settings.
- **Screens that are missing** compared with phone: Episode, Person, Collection, Favorites, Downloads, About, subtitle style, server addresses.
- **CE features that never reached TV:** per-series audio/subtitle memory (#115) may work through the shared player, but the TV player has no track or quality dialogs of its own to make that visible. Smart Downloads (#116), the Downloads redesign (#81) and offline mode don't exist on TV.
- The TV `PlayerScreen` is its own 346-line implementation. We need to check how it relates to the `PlaybackService` move from #79.
- `app/tv` uses the same `applicationId` (`nl.midasvo.findroid.ce`) as `app/phone`. Today the two APKs replace each other on a device that can run both.
- CI builds `app/tv` in `build.yaml`, but `beta.yml` and `release.yml` don't publish a TV APK.
- The manifest is already correct for the Play Store TV checks: `leanback` feature, `touchscreen` not required, banner set.

## Rules for this work

These hold for the research and for any implementation that follows.

1. **Remote first.** Every screen works with D-pad, OK and Back alone. No action needs a long-press unless there's another way to reach it. Back always goes one level up and never exits the app by surprise.
2. **Focus is never lost.** Something is always visibly focused. After Back, returning from the player or a refresh, focus goes back to the item you came from. No focus traps, no focus jumping to the top of a list.
3. **10-foot UI.** Readable from the couch: minimum text sizes, overscan-safe margins (48 dp sides, 27 dp top and bottom, per the Android TV guidelines), clear focus state (scale, border or glow, not only a colour change).
4. **Shared logic, separate UI.** ViewModels stay in `modes/film`, `player/local`, `setup` and `settings`. TV screens only consume them. If TV needs different behaviour, add it to the ViewModel behind a parameter, never copy the ViewModel into `app/tv`.
5. **Upstream-friendly.** `app/tv` comes from upstream Findroid. Prefer new CE files over large edits in upstream files so `upstream-sync` stays manageable. Record every upstream file we change heavily.
6. **Wholphin is for ideas, not code.** We study its behaviour and structure. Before copying any code we check that its licence is compatible with ours (GPL-3.0), and we credit it in the commit and file header.
7. **Measure on real hardware.** The Xiaomi TV box (32-bit, low RAM, the only Dolby Vision device we have) is the floor. A feature that stutters there isn't done. Record frame times and memory on that box, not just in the emulator.
8. **Translations stay complete.** New TV strings follow the CE rule: English in `strings.xml`, all 36 languages in `strings_ce.xml`, `check-translations.py` passes.
9. **Phone is not affected.** Nothing in this work may change behaviour in `app/phone` without its own issue.

## Plan

### Phase 1: Inventory of `app/tv` (deliverable: table in this issue)

- List every TV screen and component, with what it shows, which ViewModel it uses and how its focus is handled.
- Feature matrix: phone vs TV vs Wholphin, one row per feature (see the list in phase 2).
- Run the current TV debug build on the Xiaomi box and on an emulator (1080p and 4K). Record the worst problems: lost focus, unreachable buttons, slow screens, crashes.
- Find out how the TV player is wired: which `PlayerViewModel`, whether it goes through `PlaybackService`, and which player features (mpv, track choice, trickplay, chapters, skip intro) work.

### Phase 2: Study Wholphin (deliverable: notes document, linked here)

Install Wholphin on the same box and use it next to our TV build. Note what it does, why it feels good, and how it is built. Verify each point; don't assume it from this list.

- **Navigation:** side navigation drawer vs our top tab row; how libraries, search and settings are reached; how deep the screen stack goes.
- **Home:** which rows, whether the user can customise or reorder them, the backdrop and hero behaviour when focus moves.
- **Detail pages:** movie, series, season and episode layout; where play, resume, trailer, version and track choice live.
- **Player:** OSD layout, seek behaviour (D-pad steps, hold to accelerate, trickplay thumbnails), skip intro and credits, next-episode countdown, audio/subtitle switching mid-play, playback speed, info overlay.
- **Profiles:** user switching, PIN or quick connect, multiple servers.
- **Speed:** cold start time, time to first frame of Home, scroll smoothness in big libraries, image sizes it requests.
- **Settings:** what it exposes and how settings screens look on TV.
- **Architecture:** Compose for TV vs Leanback, module layout, how it handles focus (focus requesters, `focusRestorer`, saved focus per screen), which player stack it uses.

### Phase 3: Gap analysis (deliverable: prioritised list)

Merge phases 1 and 2 into one list. Give each gap:

- impact (must-have, nice-to-have, polish),
- rough size (S, M, L),
- where the work lives (TV UI only, shared ViewModel, player, data),
- a note when it touches upstream files.

### Phase 4: Architecture decisions (deliverable: ADRs in `docs/`)

Decide, with the trade-offs written down:

1. **Evolve `app/tv` or rebuild it.** Keep upstream's TV screens and fix them, or build new CE screens next to them and switch over route by route.
2. **Navigation shape.** Side drawer, top tabs, or a hybrid.
3. **Focus strategy.** One shared approach for restoring focus (for example a per-route saved-focus key), so every screen behaves the same.
4. **Player UI.** Keep the TV `PlayerScreen` or bring it onto the same `PlaybackService` and shared controls as phone, with a TV skin.
5. **Application ID.** Keep one ID (one listing, one APK per device type) or give TV its own suffix so both can be installed side by side.
6. **Release.** Publish the TV APK in `beta.yml` and `release.yml`, and how users can tell the two APKs apart.
7. **Offline on TV.** Whether downloads and offline mode belong on TV at all (storage on TV boxes is small).

### Phase 5: Roadmap (deliverable: follow-up issues)

Turn the outcome into separate issues, ordered so each one ships something usable:

1. Foundation: navigation shape, focus restore, theme and spacing tokens, the release of a TV APK.
2. The core loop: Home, detail pages and player feel right for the "open app, continue watching" path.
3. Feature parity with phone CE where it makes sense on TV.
4. Polish: animations, backdrops, theme songs, screensaver, whatever phase 2 shows is worth it.

## Acceptance criteria for this issue

- [ ] Phase 1 inventory and feature matrix are posted here.
- [ ] Phase 2 Wholphin notes exist and are linked, with screenshots or recordings from the Xiaomi box.
- [ ] Phase 3 gap list is prioritised and posted.
- [ ] The phase 4 decisions are written as ADRs and agreed.
- [ ] Phase 5 follow-up issues exist and are linked from here.
- [ ] No production code changed as part of this issue.

## Open questions

- Does TV need the full phone feature set, or a focused "watch" client with fewer screens?
- Do we want TV-only features that phone doesn't have (screensaver with artwork, Android TV home screen channels and "Watch Next" integration, voice search)?
- How much testing on other devices (Shield, Chromecast with Google TV, Fire TV) do we want before calling it done, given we only own the Xiaomi box?

