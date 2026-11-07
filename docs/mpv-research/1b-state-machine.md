# Phase 1b: the `MPVPlayer` state machine

Read on `main` at `7e21c0de`. Code reading only: nothing was built or run. Line numbers are `MPVPlayer.kt` unless another file is named. Anything about what libmpv does internally (event order, which commands raise which events) is from mpv's documented behaviour and source, not from a device log, and is marked **hypothesis**.

### How events reach the code

- libmpv's JNI layer calls `MPVLib.event(id)` and `MPVLib.eventProperty(name, value)` on its own native event thread (the jar, `libmpv-1.0.0`, loops over its observer list under `synchronized(observers)` and calls each observer on the calling thread). The thread itself lives in the `.so` and isn't visible from Java.
- `MPVPlayer` is that observer (`235`). Every override (`363-559`) just does `handler.post { ... }`, with `handler = Handler(context.mainLooper)` (`73`). All state changes therefore run on the main thread, in mpv's event order, one main-looper message per event.
- Calls from the app (`prepare`, `play`, `seekTo`, ...) also run on the main thread, synchronously. They can interleave with posted mpv events but never run concurrently with them.
- Values read inside a handler (for example `getPropertyBoolean("seekable")` at `524`) are read when the message runs, not when mpv raised the event.
- Observed properties (`239-251`): `track-list`, `paused-for-cache`, `eof-reached`, `seekable`, `time-pos` and `duration` (both `INT64`, so whole seconds, `459`, `461`), `demuxer-cache-time`, `speed`, `playlist-count`, `playlist-current-pos`. **Not observed:** `pause`, `core-idle`, `seeking`, `idle-active`. Events handled: `START_FILE`, `FILE_LOADED`, `SEEK`, `PLAYBACK_RESTART` (`516-555`). **`MPV_EVENT_END_FILE` and `MPV_EVENT_IDLE` are not handled** (`556`, `else -> Unit`).
- Listeners: a Media3 `ListenerSet` on the main looper (`327-331`). `queueEvent` + `flushEvents` call the listener at once; `onEvents` comes afterwards, posted at the front of the looper queue, with the flags collected in that message. `sendEvent` is queue + flush.
- `isPlaying` isn't overridden: it is `BasePlayer.isPlaying()`, so `playbackState == STATE_READY && playWhenReady && playbackSuppressionReason == NONE`. Suppression is always `NONE` (`979`), so in practice `READY && playWhenReady`.

### The notify helper

`setPlayerStateAndNotifyIfChanged(playWhenReady, reason, playbackState)` (`576-610`) is the only path that notifies state changes:

| Condition | Callbacks |
|---|---|
| state changed | `onPlaybackStateChanged(state)` with `EVENT_PLAYBACK_STATE_CHANGED` (`586`) |
| `playWhenReady` changed | `onPlayWhenReadyChanged(pwr, reason)` (`594`). Also registers or unregisters the becoming-noisy receiver (`593`) |
| either of the two changed | **a second `onPlaybackStateChanged(state)`**, queued with `C.INDEX_UNSET` so it adds no flag to `onEvents` (`599-603`). So listeners get `onPlaybackStateChanged` twice on every state change, and once (with the unchanged state) on every pure play/pause |
| `isPlaying` before ≠ after | `onIsPlayingChanged(isPlaying)` (`604-607`). "Before" is read on entry (`583`), so it misses anything `resetInternalState()` changed just before the call |

The default reason is `PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST` (`579`).

### Every assignment

**`playbackState`** (field `337`, initial `STATE_IDLE`)

| Line | Driver | New value | Callbacks |
|---|---|---|---|
| `933` | `resetInternalState()` (from `prepareMediaItem` `1184`, last-item EOF `434`, `release` `1263`) | `STATE_IDLE` | **none**, silent |
| `1205` | `prepareMediaItem()` (from `prepare` `958`, `seekTo` to another item `1123`, EOF advance `419`) | `STATE_BUFFERING` | `onPlaybackStateChanged(BUFFERING)` ×2. Never `onIsPlayingChanged`, because the reset ran first |
| `532` | `MPV_EVENT_SEEK` | `STATE_BUFFERING` | ×2 `onPlaybackStateChanged`, `onIsPlayingChanged(false)` if it was playing. Nothing if already buffering |
| `553` | `MPV_EVENT_PLAYBACK_RESTART`, `else` branch (only when `isPlayerReady`) | `STATE_READY` | ×2 `onPlaybackStateChanged`, `onIsPlayingChanged(true)` if `playWhenReady` |
| `535-551` | `MPV_EVENT_PLAYBACK_RESTART`, `!isPlayerReady` branch | **unchanged** | `onRenderedFirstFrame` to video listeners (`549-551`) only |
| `441` | `paused-for-cache` = true, only when `isPlayerReady` | `STATE_BUFFERING` | as for `532` |
| `443` | `paused-for-cache` = false, only when `isPlayerReady` | `STATE_READY` | as for `553` |
| `415` | `eof-reached` = true, `isPlayerReady`, a next item exists, `pauseAtEndOfMediaItems` (Ephyra sets it, `PlayerViewModel.kt:408`) | `STATE_READY` (and pwr false) | `onPlayWhenReadyChanged(false, END_OF_MEDIA_ITEM)`, `onPlaybackStateChanged(READY)` ×1 or ×2, `onIsPlayingChanged(false)` |
| `432` | `eof-reached` = true, `isPlayerReady`, last item | `STATE_ENDED` (and pwr false), then `resetInternalState()` makes it `IDLE` silently (`434`) | `onPlayWhenReadyChanged(false, END_OF_MEDIA_ITEM)`, `onPlaybackStateChanged(ENDED)` ×2, `onIsPlayingChanged(false)` |

**`currentPlayWhenReady`** (field `338`, initial `false`)

| Line | Driver | New value | Callbacks |
|---|---|---|---|
| `934` | `resetInternalState()` | `false` | **none**, silent. The noisy receiver isn't unregistered either |
| `1005` | `setPlayWhenReady()` (`play()`/`pause()` from `BasePlayer`) | argument | `onPlayWhenReadyChanged(_, USER_REQUEST)`, dup `onPlaybackStateChanged`, maybe `onIsPlayingChanged`. Sets mpv `pause` **only if `isPlayerReady`** (`1009-1021`) |
| `85` | `ACTION_AUDIO_BECOMING_NOISY` | `false` (+ mpv `pause=yes`) | `onPlayWhenReadyChanged(false, AUDIO_BECOMING_NOISY)`, ... |
| `735` | audio focus `LOSS` or `LOSS_TRANSIENT` | `false` (+ mpv `pause=yes`) | `onPlayWhenReadyChanged(false, AUDIO_FOCUS_LOSS)`, ... On `GAIN` the stored callback sets mpv `pause=no` (`741`) **without** restoring `playWhenReady` |
| `411`, `428` | `eof-reached` (see above) | `false` | `END_OF_MEDIA_ITEM` |

**Other flags**

| Field | Lines | Notes |
|---|---|---|
| `isPlayerReady` (`345`) | `536` (true, first `PLAYBACK_RESTART`), `931` (false, reset) | Gates `paused-for-cache` (`439`), `eof-reached` (`408`), `applyInitialSeek` (`1142`), `setPlayWhenReady` → mpv `pause` (`1009`), `START_FILE` → `initialCommands` (`517`), the shutter lift in `track-list` (`394`) |
| `initialSeekTo` (`353`) | `830` (`setMediaItems` 3-arg), `1115` (`seekTo` same item), `1146` (0 after a seek is issued), `541` (0 when `start=` already covered it) | Not reset by `resetInternalState`. `seekTo` before the first restart stores the target here; it is applied at the first restart |
| `startPassedToLoadMs` (`359`) | `945` (null), `953` (= `initialSeekTo` when `start=` is used), `538` (null at the first restart) | Added by #267 |
| `initialIndex` (`352`) | `829` | Only set by the 3-arg `setMediaItems` |
| `currentMediaItemIndex` (`347`) | `828` (0 in `setMediaItems`), `479` (`playlist-current-pos`) | Not set by `seekTo`/`prepareMediaItem`, so it lags until mpv reports the new position |
| `initialCommands` | `1186` (sub-add per external subtitle), `940` (cleared on reset) | Run on every `START_FILE` while `!isPlayerReady` (`517-521`); never cleared after running |
| `isSeekable` | `449`, `524`, `932` | |
| `playbackSuppressionReason` | none | Always `NONE` (`979`); `onPlaybackSuppressionReasonChanged` never fires |
| `isLoading` | none | Always `false` (`1092`); `onIsLoadingChanged` never fires |
| `playerError` | none | Always `null` (`993`); `onPlayerError` never fires |

### Transition walkthroughs

Ephyra's start path is `setMediaItems(mediaItems, 0, startPosition)`, `prepare()`, `play()` (`PlayerViewModel.kt:516-518`). `startPosition` is the item's `playbackPosition` in ms, 0 when there is none (`PlayerViewModel.kt:509-514`, `PlaylistManager.kt:165-166`). The phone `PlayerView` has `show_buffering="always"` (`app/phone/src/main/res/layout/activity_player.xml:15`), so the spinner shows whenever the state is `BUFFERING`. `PlayerViewModel.onPlaybackStateChanged(READY)` sets `fileLoaded` and runs the deferred work (trickplay, queueing the neighbouring items) (`PlayerViewModel.kt:1035-1038`, `836`).

**(a) Fresh start, no resume position** (`initialSeekTo = 0`)

1. `setMediaItems` (`815`): `playlist-clear`, `playlist-remove current`; index 0, `initialIndex = 0`, `initialSeekTo = 0`. No callbacks.
2. `prepare` (`944`): `initialSeekTo > 0` is false, so a plain `loadfile <uri> replace` (`955`). `prepareMediaItem(0)` → silent reset to `IDLE`/pwr false → `BUFFERING` → `onPlaybackStateChanged(BUFFERING)` ×2.
3. `play()` → `onPlayWhenReadyChanged(true, USER_REQUEST)` + dup `onPlaybackStateChanged(BUFFERING)`. mpv `pause` isn't touched (`!isPlayerReady`); mpv's default is `pause=no`, so it is loading and will play by itself.
4. mpv `START_FILE` → `sub-add` commands. `playlist-count` → `onTimelineChanged`; `playlist-current-pos` → `onMediaItemTransition(AUTO)` + `onMediaMetadataChanged` if the media id changed.
5. `FILE_LOADED` → `isSeekable`, duration. `track-list` → `onTracksChanged` (no shutter lift yet, `!isPlayerReady`).
6. **Hypothesis:** with `cache-pause-initial=yes` (`206`), mpv waits for the cache *before* it completes the restart, so `paused-for-cache` true→false arrives while `isPlayerReady` is false and is dropped (`439`).
7. `PLAYBACK_RESTART` #1 → `isPlayerReady = true`; `startPassedToLoadMs` is null → `applyInitialSeek()` issues `seek 0.0 absolute` (`1143`); `pause=no`; `onRenderedFirstFrame`. **State stays `BUFFERING`.**
8. `SEEK` → `BUFFERING`, no change, no callbacks.
9. `PLAYBACK_RESTART` #2 → `else` → `READY`: `onPlaybackStateChanged(READY)` ×2, `onIsPlayingChanged(true)`.

`READY` is reached, but only through the redundant seek to 0 in step 7. `isPlaying` turns true at step 9.

**(b) Start with a resume position** (`initialSeekTo = P > 0`, index 0)

1. As (a).
2. `prepare`: `loadfile <uri> replace -1 start=P/1000` (`952`), `startPassedToLoadMs = P` (`953`). `BUFFERING` ×2.
3. As (a).
4. to 6. As (a). **Hypothesis:** mpv may also raise `MPV_EVENT_SEEK` for its internal start seek, which is harmless (already buffering).
7. `PLAYBACK_RESTART` #1 → `isPlayerReady = true`; `passed == initialSeekTo` → `initialSeekTo = 0` (`541`), **no seek**; `pause=no`; `onRenderedFirstFrame`.
8. No further restart. **The state stays `BUFFERING`** while mpv plays. `isPlaying` stays false, `onIsPlayingChanged(true)` never fires, `fileLoaded` and the deferred work don't run.

The state only leaves `BUFFERING` on the next `SEEK` + `PLAYBACK_RESTART` (any user seek), a `paused-for-cache` true→false after a stall, or an `eof-reached` with a next item (`415`). Play/pause doesn't help: it fires `onPlayWhenReadyChanged` but never sets the state.

Variants, by reading `539`:
- `seekTo(Q)` before restart #1, with `Q ≠ P`: `initialSeekTo = Q` (`1115`), `applyInitialSeek` returns early (`1142`). At restart #1 `passed ≠ initialSeekTo` → seek to Q → restart #2 → `READY`.
- `initialIndex ≠ 0` (a reload of a later queue item): no `start=` (`948`), so the path is (a) with a real seek → `READY`.
- Quality switch mid-play (`PlayerViewModel.kt:1279-1280`: `setMediaItems(rebuilt, currentIndex, position)` + `prepare()`, no `play()`): with `currentIndex == 0` and `position > 0` it takes the `start=` path and gets stuck as above. On top of that, the reset silently sets `playWhenReady` to false and nothing sets it back, while mpv keeps its old `pause=no` and plays. Listeners never get `onIsPlayingChanged(false)` or `onPlayWhenReadyChanged(false)`.

**(c) Seek while playing** (`READY`, pwr true)

1. `seekTo(idx, pos)` (`1106`), same item: `initialSeekTo = pos`, `applyInitialSeek()` → `seek pos absolute`, `initialSeekTo = 0`. `onPositionDiscontinuity(SEEK)` (both overloads), synchronously.
2. `SEEK` → `BUFFERING`: ×2 `onPlaybackStateChanged`, `onIsPlayingChanged(false)`.
3. `PLAYBACK_RESTART` → `READY`: ×2 `onPlaybackStateChanged`, `onIsPlayingChanged(true)`.

`READY` is reached. In the stuck state of (b), step 2 has no change and step 3 is the first `READY`: one seek unsticks it.

**(d) Seek while paused** (`READY`, pwr false)

Same as (c). mpv raises `SEEK` and `PLAYBACK_RESTART` while paused too (it decodes the target frame), so `BUFFERING` → `READY`. `isPlaying` stays false throughout, so `onIsPlayingChanged` doesn't fire.

**(e) `paused-for-cache` true → false mid-play**

1. `true` → `BUFFERING` (`441`): ×2 `onPlaybackStateChanged`, `onIsPlayingChanged(false)`.
2. `false` → `READY` (`443`): ×2 `onPlaybackStateChanged`, `onIsPlayingChanged(true)`.

Both are ignored before the first restart (`439`). `playWhenReady` doesn't change.

**(f) End of file**

With a next item (Ephyra: `pauseAtEndOfMediaItems = true`). With `keep-open=always` (`228`), mpv pauses on the last frame and sets `eof-reached`:
1. `eof-reached` true → pwr false (`END_OF_MEDIA_ITEM`) + `READY` (`411-416`): `onPlayWhenReadyChanged(false, END_OF_MEDIA_ITEM)`, `onPlaybackStateChanged(READY)`, `onIsPlayingChanged(false)`.
2. `PlayerViewModel.onPlayWhenReadyChanged` (`PlayerViewModel.kt:886`) checks `READY` and launches `autoAdvanceToNextItem()` → `seekToNextMediaItem()` → `seekTo(i+1, TIME_UNSET)` → `else` branch (`1122`).
3. `prepareMediaItem(i+1)`: silent reset, `playlist-play-index i+1`, `BUFFERING` ×2. `onPositionDiscontinuity(SEEK)` with `positionInfo(i+1, initialSeekTo = 0)`. `play()` → `onPlayWhenReadyChanged(true)`.
4. mpv loads the next file: `playlist-current-pos` → index, `onMediaItemTransition(AUTO)`. Restart #1 → seek to 0 → restart #2 → `READY`, `onIsPlayingChanged(true)`.

The `pauseAtEndOfMediaItems = false` branch (`417-426`, discontinuity `AUTO_TRANSITION`) is dead code in Ephyra.

Without a next item: `ENDED` + pwr false (`428-433`): `onPlayWhenReadyChanged(false, END_OF_MEDIA_ITEM)`, `onPlaybackStateChanged(ENDED)` ×2, `onIsPlayingChanged(false)`. The view model navigates back (`PlayerViewModel.kt:1040-1043`). Then `resetInternalState()` silently sets `IDLE`; `getPlaybackState()` returns `IDLE` from then on.

`eof-reached` before the first restart is dropped (`408`).

**(g) Background and return**

- `PlayerActivity.onStop` (`PlayerActivity.kt:513-520`): with background audio and `isPlaying`, `setVideoEnabled(false)` → `setTrackSelectionParameters` → `vid=no` (`1295`) → `track-list` → `onTracksChanged`. The `SurfaceView` goes, `surfaceDestroyed` (`1730`): `vo=null`, `force-window=no`, `detachSurface()`. **No state or `playWhenReady` change**, no state callbacks.
- Return: `surfaceCreated` (`1693`): attach, `force-window=yes`, `vo` back. `onStart` → `setVideoEnabled(true)` → `vid=auto` → `track-list`: video selected again and `isPlayerReady` → `onRenderedFirstFrame` lifts the shutter (`394-397`).
- **Hypothesis:** turning video back on makes mpv reinit the video chain with a refresh seek, which would raise `SEEK` + `PLAYBACK_RESTART`: a short `BUFFERING` → `READY` blip on return. If so it also unsticks (b).
- Without background audio, neither `MPVPlayer` nor `PlayerActivity` pauses on `onStop` (no `pause()` there in a grep of `PlayerActivity.kt` and `PlayerViewModel.kt`). Whether something else pauses is for the device test.
- A user pause is just `setPlayWhenReady(false)`: `onPlayWhenReadyChanged(false)`, dup `onPlaybackStateChanged`, `onIsPlayingChanged(false)`, mpv `pause=yes`.

**(h) `release()`** (`1254-1266`)

1. Frame rate reset, audio focus abandoned, noisy receiver unregistered.
2. `resetInternalState()`: `IDLE`, pwr false, `isPlayerReady` false, all silent.
3. `removeObserver(this)`, `destroy()`. `MPVLib.destroy()` zeroes `nativeInstance`; every later call throws `IllegalStateException("MPVLib is not initialized")` from `checkCreated()`.

No callbacks fire. `PlayerViewModel` removes itself before releasing (`PlayerViewModel.kt:622-623`); the `MediaSession` is released afterwards and sees no `IDLE`. `release()` doesn't clear `handler` or release `listeners`. **Hypothesis:** an mpv event already posted but not yet run (`FILE_LOADED` calls `getPropertyBoolean` at `524`; a `PLAYBACK_RESTART` takes the `!isPlayerReady` branch after the reset and calls `mpvLib.command`/`setPropertyBoolean`) would throw on the main thread after `destroy()`. This window is most likely when the player is released during loading.

### The stuck-loader hypothesis

Claim from the issue: `prepareMediaItem` sets `BUFFERING`, the first restart's `!isPlayerReady` branch never sets `READY`, before #267 the initial seek always raised a second restart, and since #267 a matching `start=` skips it.

The code today:

```kotlin
// MPVPlayer.kt:534-555
MpvEvent.MPV_EVENT_PLAYBACK_RESTART -> {
    if (!isPlayerReady) {
        isPlayerReady = true
        val passed = startPassedToLoadMs
        startPassedToLoadMs = null
        if (passed != null && passed == initialSeekTo) {
            // mpv opened at the start position already.
            initialSeekTo = 0L
        } else {
            applyInitialSeek()
        }
        if (playWhenReady) { ...; mpvLib.setPropertyBoolean("pause", false) }
        for (videoListener in videoListeners) { videoListener.onRenderedFirstFrame() }
    } else {
        setPlayerStateAndNotifyIfChanged(playbackState = STATE_READY)
    }
}
```

`prepareMediaItem` ends with `setPlayerStateAndNotifyIfChanged(playbackState = STATE_BUFFERING)` (`1205`). The `if` branch has no state change.

Before #267 (`git diff --ignore-cr-at-eol 592d29bf^ 592d29bf`; the PR is merged as `02e5d54f`) the branch was only:

```kotlin
if (!isPlayerReady) {
    isPlayerReady = true
    applyInitialSeek()
    if (playWhenReady) { ... }
    ...
} else {
    setPlayerStateAndNotifyIfChanged(playbackState = STATE_READY)
}
```

and `prepare()` always used a plain `loadfile <uri> replace|append`. `applyInitialSeek()` (`1141-1147`) doesn't check the value: it issues `seek <initialSeekTo> absolute` even for 0. So every start used to raise `SEEK` → `PLAYBACK_RESTART` #2 → `else` → `READY`. Nobody wrote "set READY on the first restart". It came from a seek that was redundant in the fresh-start case.

**Verdict: the reading holds** (hypothesis until the device test):

| Case | `start=` used | Seek at restart #1 | `READY` reached |
|---|---|---|---|
| No resume (`initialSeekTo = 0`) | no (`948`) | yes, to 0 | yes, at restart #2 |
| Resume P, index 0, no `seekTo` before restart #1 | yes | **no** (`539`) | **no**, until a seek, a cache stall or EOF |
| Resume P, `seekTo(Q ≠ P)` before restart #1 | yes | yes, to Q | yes |
| Resume P, `seekTo(P)` before restart #1 | yes | no | **no** |
| Reload with `initialIndex ≠ 0` | no | yes | yes |
| Quality switch at index 0 with position > 0 | yes | no | **no**, and pwr is wrongly false (see (b)) |
| Auto-advance / `seekTo` another item | no (`seekTo` doesn't call `prepare`) | yes, to 0 | yes |

Two conditions for the symptom besides `start=`:
- No `paused-for-cache` true→false after the first restart. **Hypothesis:** with `cache-pause-initial=yes` the initial cache wait ends before mpv completes the restart, so that flip is dropped by `439` and nothing rescues the state. A slow server or a transcode that stalls later would unstick it, which fits "the spinner goes away eventually".
- The video track isn't re-enabled (see (g)).

Side effects in the stuck state, from `PlayerViewModel`: `fileLoaded` stays false and `runDeferredWork()` (trickplay, neighbour queueing) waits for `READY` (`PlayerViewModel.kt:836`, `1035-1038`). `onIsPlayingChanged(true)` never fires, so `PlayerEvents.IsPlayingChanged`, `sessionStore.onPlayingChanged` and background progress reports don't start (`PlayerViewModel.kt:1507-1511`). The media session reports buffering.

**Minimal fix:** report `READY` in the branch that skips the seek.

```kotlin
if (passed != null && passed == initialSeekTo) {
    initialSeekTo = 0L
    setPlayerStateAndNotifyIfChanged(playbackState = STATE_READY)  // after the pause=false below
}
```

Order matters for `isPlaying`: call it after `pause=no` (`547`), so put it at the end of the `if (!isPlayerReady)` block, guarded by "no seek was issued". The more robust version is to make `applyInitialSeek()` return whether it seeked, skip the seek when the target is 0 or equal to the current position, and set `READY` on restart #1 whenever no seek is pending. That also removes the redundant seek to 0 on every fresh start. Where it belongs: upstream has no `start=` path (that is CE's #267), so the bug and the minimal fix are CE-only. The redundant seek to 0 and the `READY`-only-on-the-second-restart design are upstream code, so the robust version is worth offering upstream.

### ExoPlayer does it differently

For the same scenario, ExoPlayer would differ from the sequence above here:

1. **Prepare:** ExoPlayer goes `IDLE → BUFFERING → READY` with one `onPlaybackStateChanged` each, as soon as the renderers have data, regardless of seeks. mpv only reaches `READY` through a second restart or a cache flip, and with `start=` not at all (b).
2. **Duplicate callbacks:** mpv calls `onPlaybackStateChanged` twice per state change, and once with an unchanged state on every play/pause (`599-603`). ExoPlayer calls it once, and only on a change.
3. **`playWhenReady` survives prepare in ExoPlayer.** mpv silently sets it to false on every `prepareMediaItem` (`934`), then `play()` reports `onPlayWhenReadyChanged(true)` although listeners never saw false. A reload without `play()` (quality switch) leaves it false while mpv plays.
4. **Missing `onIsPlayingChanged(false)`:** `wasPlaying` is read after the silent reset, so a jump to another item while playing, or a reload, never reports `isPlaying` going false. ExoPlayer reports it.
5. **`playWhenReady = false` before the first restart doesn't pause mpv** (`1009`): mpv starts with `pause=no` and plays while the player says paused. ExoPlayer honours `playWhenReady` from the start.
6. **Audio focus:** ExoPlayer keeps `playWhenReady` true on transient loss and uses `PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS`; on permanent loss it pauses for good. mpv sets `playWhenReady` false for both and on `GAIN` unpauses mpv without restoring it (`739-742`): audio plays while `isPlaying` is false. After a permanent `LOSS` it still resumes on a later `GAIN`.
7. **Seek:** ExoPlayer masks the state to `BUFFERING` synchronously inside `seekTo`; mpv reports it later, from `MPV_EVENT_SEEK`.
8. **Item change by seek:** ExoPlayer updates `currentMediaItemIndex` synchronously and fires `onMediaItemTransition(…, SEEK)` from `seekTo`. mpv updates the index only when `playlist-current-pos` arrives (`479`), always with reason `AUTO` (`486`), and not at all when the media id is the same (`481`).
9. **Last item ends:** ExoPlayer stays in `ENDED`. mpv reports `ENDED`, then silently becomes `IDLE` (`434`).
10. **Errors:** ExoPlayer fires `onPlayerError` and goes `IDLE`. mpv ignores `END_FILE` (`556`) and `getPlayerError()` is always null (`993`), so a file that fails to open stays `BUFFERING` with the spinner. The audio-decoder fallback in `PlayerViewModel.onPlayerError` (`PlayerViewModel.kt:1015-1022`) can't trigger on mpv.
11. **`isLoading`** is always false (`1092`); ExoPlayer toggles it and fires `onIsLoadingChanged`.
12. **`stop()`:** ExoPlayer goes `IDLE` with callbacks. mpv sends `stop keep-playlist` (`1247`) and leaves the state unchanged.
13. **`release()`:** ExoPlayer releases its listener set and stops all callbacks. mpv leaves posted handler messages in place (see (h)).
14. **`onRenderedFirstFrame`** fires on the first restart (`549`), before `READY`, and where a seek follows, for the pre-seek frame. ExoPlayer fires it when the first frame is actually rendered.
15. **Position granularity:** `time-pos` is observed as `INT64` seconds (`244`, `459`), so `currentPosition` moves in 1-second steps. ExoPlayer reports milliseconds.
