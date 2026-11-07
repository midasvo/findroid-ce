# mpv research: plan

Copy of the plan in [issue #284](https://github.com/midasvo/ephyra/issues/284).

## Goal

Find out how healthy the mpv backend really is. mpv is the backend people switch to for the formats ExoPlayer can't play (Dolby Vision profiles, ASS subtitles, odd audio), yet nearly every player change of the past months was built and verified on ExoPlayer. `MPVPlayer.kt` is an 1849-line implementation of Media3's `Player` interface on top of `libmpv`, and the shared `PlayerViewModel`, the phone `PlayerActivity` and the TV player all assume it behaves like ExoPlayer. We don't know where it doesn't.

This issue is **research only**, in the shape of #121: it ends in a written report under `docs/mpv-research/` and a set of follow-up issues, not in code. Each phase has its own deliverable, so the work can stop after any of them. Quick, obvious fixes found along the way may get their own small PR, linked here; nothing larger is implemented under this issue.

## Where we are now

- **One confirmed symptom.** On a phone with mpv selected, playback starts but the loading indicator stays spinning in the middle of the screen while the video plays. Probable cause from reading the code: `activity_player.xml` uses `show_buffering="always"`, so the spinner follows `playbackState`. `prepareMediaItem` sets `STATE_BUFFERING`, and the first `MPV_EVENT_PLAYBACK_RESTART` (the `!isPlayerReady` branch) never sets `STATE_READY`. Before #267 the initial seek in `applyInitialSeek()` always raised a second restart whose `else` branch did. Since #267 the resume position goes to `loadfile` as `start=`, so when it matches `initialSeekTo` there is no seek, no second restart, and the state stays buffering until the next `paused-for-cache` flip. Needs reproducing, and it is a hint that the state machine was tuned by accident rather than by design.
- **Nobody ran the recent work through mpv.** The media segments polish (#271, PR #283) was verified on ExoPlayer only; its outro-to-next-episode and autoplay-off paths are unit-tested only. The playback-start change (#255, PR #267) rewrote how mpv opens a file. The identity work changed the player chrome. Refresh-rate switching (#139) was written against mpv's properties and should be checked on ExoPlayer too, the other way round.
- **Known thin spots.** `MPVPlayer.kt` still has about ten `TODO("Not yet implemented")` stubs in `Player` methods; nobody has checked whether the app, Media3's `PlayerView`, the media session or `PlaybackService` (#79) can call them. `onPositionDiscontinuity` was only fixed for one arity in #70. mpv reports no queue to `dumpsys media_session` (`size=0`, `position=-1`), which the earlier phone tests noted and worked around rather than fixed.
- **Settings and migrations.** `pref_player_mpv`, `hwdec`, `vo` (`gpu-next`), `ao` (`audiotrack`, with the aaudio migration from #52) and the cache settings exist. Whether the defaults are still the right ones for `libmpv` 1.0.0 and current devices is unverified.
- **Upstream.** `MPVPlayer.kt` comes from upstream Findroid, which moved it towards `PlaybackService`. Every CE change in it makes `upstream-sync` harder, so the report has to say which fixes belong upstream.

## Rules for this work

1. **Both backends, same journey.** Every test runs on mpv and on ExoPlayer with the same item, so a difference is a finding and a shared failure is not an mpv bug.
2. **Real devices for playback.** The phone and the Xiaomi box (the only Dolby Vision device, see #96) for anything about rendering, audio output, hwdec or refresh rate. Emulators are fine for state-machine and UI findings.
3. **Reproduce before explaining.** A finding has the item, the backend, the device, the steps, what happened and what ExoPlayer does. A code reading without a reproduction is a hypothesis and is labelled as one.
4. **Logs over screenshots.** `MPVPlayer` logs its events; add temporary `Timber` lines on a throwaway branch where needed, never in a PR.
5. **Phone is not affected.** Nothing here changes ExoPlayer behaviour without its own issue.
6. **Upstream-friendly.** Note for every proposed fix whether it belongs in a CE file, in upstream's `MPVPlayer.kt`, or in `libmpv`.

## Plan

### Phase 1: How mpv is wired (deliverable: `docs/mpv-research/1-inventory.md`)

- Map `MPVPlayer.kt` against the Media3 `Player` contract: which methods are implemented, which are stubs, which are approximations (`getBufferedPosition`, timeline, tracks, `seekTo` variants, `setMediaItems`, `release`). Table with one row per method group.
- The state machine: every place `playbackState`, `playWhenReady` and `isPlayerReady` change, which mpv event or property drives it, and what listeners see. Draw the transitions for: fresh start, resume at a position, seek while playing, seek while paused, `paused-for-cache`, end of file with and without a next item, background and return, release.
- Who consumes it: `PlayerViewModel` (segments poll, trickplay wait on first ready, still-watching, reporting), `PlaybackService` and the media session, the phone `PlayerView` (`show_buffering`, controls listener), the TV `TvPlayerScreen`, the refresh-rate switcher.
- Settings: what each mpv preference does today, its default, and whether the migrations (`mpv_migrated`, the aaudio one) still fire on a fresh install.

### Phase 2: Test pass (deliverable: `docs/mpv-research/2-test-pass.md`, a results table)

Each row: item, mpv result, ExoPlayer result, device. Run on the phone (debug package, `pref_player_backend` switched through Settings) and on the box; the `ce_tv` AVD for the TV UI rows.

- **Start and stop:** fresh start, resume at a saved position (no frame at 0, #255), the stuck loader, start while the server is slow, back out during loading, release while buffering.
- **Seeking:** gestures, seek bar, double-tap, remote D-pad steps and hold, trickplay while seeking, seek past the end, seek while paused.
- **Media segments (#271):** button window, tap to skip, auto-skip, outro to next episode, auto outro skip with autoplay off, seeking back into a segment.
- **Queue:** next and previous episode, autoplay at the end, still-watching prompt (#70), playlist index handling in `prepareMediaItem`, what the media session shows (`dumpsys media_session`).
- **Tracks:** audio and subtitle dialogs, remembered per-series choice (#115), external subtitles, ASS styling and the override toggle TODO in `SubtitleStyle.kt`, track switch mid-play.
- **Formats on the box:** Dolby Vision profiles 5 and 8 (#96), HDR10, TrueHD and Atmos passthrough, 24p with refresh-rate switching (#139), a file ExoPlayer transcodes but mpv direct-plays.
- **Lifecycle:** picture-in-picture, background audio and return (#79, #80, #90), rotation, headphone unplug, audio focus loss, a phone call.
- **Reporting:** progress, played and stopped reach the server with the right positions.
- **Settings:** each hwdec, vo and ao value on both devices; what breaks and what the user can do about it.

### Phase 3: Findings (deliverable: `docs/mpv-research/3-findings.md`)

One entry per difference from ExoPlayer: impact (broken, wrong, cosmetic), probable cause with file and line, proposed fix, where the fix belongs (CE, upstream, libmpv), size (S/M/L). The stuck loader is entry one.

### Phase 4: Decisions and follow-ups (deliverable: issues, linked below)

- A follow-up issue per finding worth fixing, grouped where the fix is the same (for example "mpv state machine" rather than five symptoms).
- A recommendation on the mpv defaults (hwdec, vo, ao, cache) with the measurements behind it.
- A short mpv checklist in `docs/` that every player PR has to run through, so this does not happen again.
- A statement on how far mpv is from being a first-class backend, and whether it should stay opt-in or be offered automatically for the formats ExoPlayer can't play.

## Follow-up issues

_(filled in during phase 4)_

## Done when

- [ ] `docs/mpv-research/` has the inventory, the test-pass table and the findings.
- [ ] Every finding has a follow-up issue or a stated reason it won't be fixed.
- [ ] The stuck loader is reproduced and has its fix issue (or a small PR).
- [ ] `docs/` has the mpv checklist for player PRs.

