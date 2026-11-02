# ADR 0004: A CE Compose player UI on top of the shared PlayerViewModel

**Status:** Proposed

## Context

TV and phone already share the hard part: `PlayerViewModel` (ExoPlayer and mpv, `PlaybackService`, media session, media segments, per-series tracks from #115, `TrickplayLoader`, `switchToTrack()`).

The UI differs:

- phone uses `PlayerActivity` with Views (`ActivityPlayerBinding`);
- TV uses the Compose `PlayerScreen`.

On TV, seeking doesn't work, and the audio and subtitle buttons do nothing ([1-walkthrough.md](../1-walkthrough.md), F2 and F3).

## Options

1. **Fix the upstream `PlayerScreen` in place.** It's 346 lines with several TODOs and a seek bar that can't be reached. Most of it would be rewritten anyway.
2. **Use the phone `PlayerActivity` on TV with a TV layout.** It's View-based and built around touch gestures. Making it work with D-pad means maintaining focus logic in two UI systems.
3. **A new CE Compose player screen for TV**, on the same `PlayerViewModel`.

## Decision

Option 3 (`ce/presentation/player/`), built in this order:

1. **Key handling (G2).** A `TvPlayerKeyHandler`, based on what Wholphin does, but written by us:
   - controls hidden: Left/Right jump back/forward (default 10 s / 30 s), holding a key speeds up with the video length, OK shows the controls (or pauses, as a setting), Back hides the controls before it leaves the player;
   - media keys: play/pause, FF/RW, next/previous, CC, audio track, stop;
   - controls shown: the seek bar is the first focusable row, Left/Right move it in time steps, and OK confirms.
2. **Track dialogs (G3).** Audio, subtitles and speed in TV dialogs that call `switchToTrack()`, so the choice per series keeps working.
3. **Overlay content (G12).** Series and episode line, remaining time and "ends at", previous/next, a queue row with the next episodes (from `PlaylistManager`), errors shown.
4. **Trickplay (G14).** Thumbnails above the seek bar while seeking, from the existing `currentTrickplay`.

**Changes to shared code (rule 4):**

- `PlayerViewModel` exposes position and duration as state (instead of the 300 ms polling in the screen) and the queue. Both are additive, and phone ignores them.
- Seek step sizes become settings in `settings`, only shown in the TV app (ADR 0008).

## Consequences

- The upstream `PlayerScreen` and `ui/components/player/*` stop being used once the route switches (ADR 0001).
- Player behaviour (tracks, segments, resume, reporting to the server) stays in one place for phone and TV.
- The key handler is plain Kotlin without Compose, so it can be unit tested.
- Test on the Xiaomi box before calling it done: tunneling, Dolby Vision and passthrough behave differently from the emulator.
