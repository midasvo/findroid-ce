# Phase 1a: MPVPlayer against the Media3 `Player` contract

Read at `7e21c0de`, Media3 `1.11.1`. Subject: `player/local/src/main/java/dev/jdtech/jellyfin/player/local/mpv/MPVPlayer.kt` (1849 lines) and `MPVTrackType.kt` (23 lines). Those are the only two files in `mpv/`.

`MPVPlayer` extends `BasePlayer` and implements `MPVLib.EventObserver` and `AudioManager.OnAudioFocusChangeListener` (`MPVPlayer.kt:68`). `BasePlayer`'s `final` convenience methods (`play`, `pause`, `seekTo(long)`, `seekBack`, `setMediaItem`, `addMediaItem`, `removeMediaItem`, `clearMediaItems`, `isCommandAvailable`, ...) all forward to the abstract methods below. That was checked in the 1.11.1 bytecode (`javap` on the cached AAR), so a convenience call reaches whatever the abstract method does, including a `TODO()`.

The app passes `setPauseAtEndOfMediaItems(true)` (`PlayerViewModel.kt:408`), so the end-of-file auto-advance branch in MPVPlayer is never used in practice. The app does its own advance with `seekToNextMediaItem()`.

### Contract table

Status: **impl** = implemented, **approx** = approximation, **stub** = `TODO("Not yet implemented")`, **no-op** = returns a constant or does nothing, **base** = not overridden (BasePlayer default).

| Method(s) | Status | Notes |
|---|---|---|
| `prepare()` | approx | `loadfile` for every item: index 0 `replace` (with `start=` if the start position is on item 0), the rest `append` (`:944-959`). Then `prepareMediaItem(initialIndex)`. No IDLE check, and it does not clear an error (there are none, see `getPlayerError`) |
| `play()` / `pause()` / `setPlayWhenReady` | impl | base `play/pause` → `setPlayWhenReady` (`:1003`). Requests audio focus, sets mpv `pause`. Before the first `PLAYBACK_RESTART` it only records the flag, and the restart handler unpauses (`:545`) |
| `getPlayWhenReady` | impl | `:1031`. `resetInternalState()` (`:930`) sets it to false **without an event**. That runs on every `prepareMediaItem`, so the `play()` that follows reports a `USER_REQUEST` change ExoPlayer would not send |
| `stop()` | approx | `stop keep-playlist` (`:1246`). No move to `STATE_IDLE`, no events, `isPlayerReady` stays true |
| `release()` | impl | `:1254-1266`: frame rate reset, abandon focus, unregister noisy receiver, `resetInternalState`, `removeObserver`, `destroy`. See "Other differences" |
| `seekTo(index, pos, cmd, repeat)` | approx | `:1106-1134`. Same index: absolute mpv seek (`TIME_UNSET` → pending `initialSeekTo`, usually 0). Other index: `playlist-play-index`, **`positionMs` is ignored**, and it always calls `play()` |
| `seekTo(long)`, `seekTo(int,long)`, `seekToDefaultPosition*`, `seekBack/Forward`, `seekToNext/Previous(MediaItem)` | base | All end in the method above. Next/previous index comes from the default `Timeline.getNextWindowIndex` over the window list |
| `getSeekBack/ForwardIncrement` | impl | From the builder (`:1209-1215`). The app passes its preferences |
| `getMaxSeekToPreviousPosition` | no-op | Constant `C.DEFAULT_MAX_SEEK_TO_PREVIOUS_POSITION_MS` (`:1217`) |
| `setMediaItems(list, resetPosition)` | approx | `:796`. Clears mpv's playlist and stores the list. Does **not** reset `currentMediaItemIndex`, `initialIndex` or `initialSeekTo`, unlike the 3-arg form. Reached by base `setMediaItem(item)` / `setMediaItems(list)` |
| `setMediaItems(list, index, pos)` | impl | `:815-831`. Resets the index (fix in the comment at `:823`), records start index and position for `prepare()` |
| `addMediaItems(index, list)` | approx | `:840`. `internalMediaItems.addAll(index, ...)` + `loadfile ... insert-at`. No index clamping. Base `addMediaItems(list)`/`addMediaItem(item)` pass `Int.MAX_VALUE`, which throws `IndexOutOfBoundsException` here (ExoPlayer clamps). No timeline event of its own, it relies on the `playlist-count` observer (`:467`) |
| `moveMediaItems` / `moveMediaItem` | **stub** | `:864` |
| `replaceMediaItems` / `replaceMediaItem` | **stub** | `:872` |
| `removeMediaItems` / `removeMediaItem` / `clearMediaItems` | **stub** | `:883`. `clearMediaItems()` is `removeMediaItems(0, Int.MAX_VALUE)` |
| `getCurrentTimeline` | approx | `:1325`, one fixed object (`:624-712`). One window and one period per item, uid = index (`Int`). **Every** window gets the current item's `currentDurationMs` and `isSeekable`. `getIndexOfPeriod` casts `uid as Int` (ClassCastException on a foreign uid) |
| `getCurrentMediaItemIndex`, `getCurrentPeriodIndex` | impl | Both `currentMediaItemIndex` (`:1330-1336`), set from mpv's `playlist-current-pos` (`:475-495`) and in `setMediaItems` |
| `getDuration` | impl | Window duration of the current item (`:1342`). `duration` is observed as INT64 (`:245`), so whole seconds, but `FILE_LOADED` reads it as a double first (`:525`) |
| `getCurrentPosition` | approx | `time-pos` observed as **INT64** (`:244`), so whole seconds. `C.TIME_UNSET` before the first update and after each `resetInternalState` (`:1351`). ExoPlayer never returns `TIME_UNSET` here |
| `getContentPosition` | impl | = `getCurrentPosition` (`:1396`) |
| `getBufferedPosition` / `getContentBufferedPosition` | approx | `demuxer-cache-time` in whole seconds, else content position (`:1358`, `:1405`) |
| `getTotalBufferedDuration` | approx | Returns `bufferedPosition` (`:1366`), an absolute position, not "buffered minus current" |
| `getBufferedPercentage`, `getContentDuration`, `isCurrentMediaItemSeekable/Live/Dynamic` | base | Read from the timeline above |
| `getPlaybackState` | approx | `:967`. BUFFERING on load and on `MPV_EVENT_SEEK`, READY on `PLAYBACK_RESTART` and `paused-for-cache=false`, ENDED at the end of the last item. Right after ENDED, `resetInternalState()` (`:434`) sets the field to IDLE silently |
| `getPlaybackSuppressionReason` | no-op | Always `NONE` (`:978`) |
| `isLoading` | no-op | Always false (`:1091`) |
| `getPlayerError` | no-op | Always null (`:992`). `onPlayerError` is never sent: mpv's `END_FILE` (with error) is not handled at all |
| `setRepeatMode` | approx | Sets `loop-file` / `loop-playlist` (`:1040`) |
| `getRepeatMode` | no-op | `repeatMode` is a `val` fixed to OFF (`:340`, `:1063`). No `onRepeatModeChanged` |
| `setShuffleModeEnabled` | **stub** | `:1073` |
| `getShuffleModeEnabled` | no-op | false (`:1081`) |
| `getCurrentTracks` | impl | Parsed from the `track-list` JSON (`:1832`). One `Tracks.Group` per mpv track, `Format.id` = mpv track id. All formats `FORMAT_HANDLED` |
| `getTrackSelectionParameters` | impl | Returns the stored object (`:1272`) |
| `setTrackSelectionParameters` | approx | `:1276-1300`. Only overrides (by `Format.id` → `vid`/`aid`/`sid`) and `disabledTrackTypes` (`no`/`auto`). Preferred languages are used only once, as `alang`/`slang` at init (`:218-224`). No `onTrackSelectionParametersChanged`. `MPVTrackType.fromMedia3TrackType` maps every type other than video and audio to `SUBTITLE` (`MPVTrackType.kt:19`), so disabling e.g. `TRACK_TYPE_IMAGE` would turn subtitles off (hypothesis, the app only disables video, audio and text) |
| `setPlaybackParameters` / `setPlaybackSpeed` | approx | Speed only, pitch ignored (`:1231`). The `speed` observer sends `onPlaybackParametersChanged` (`:503`) |
| `getPlaybackParameters` | impl | `:1242` |
| `setVolume` | **stub** | `:1420` |
| `getVolume` | approx | mpv `volume` / 100 (`:1428`) |
| `getDeviceInfo` | approx | `setMaxVolume(0).setMaxVolume(100)` (`:1557-1561`), so min was likely meant |
| `getDeviceVolume`, `isDeviceMuted` | approx | mpv `volume` and `mute`, i.e. player volume, not the stream volume (`:1575-1582`) |
| `setDeviceVolume(v, flags)`, `setDeviceMuted(m, flags)`, `mute`, `unmute` | approx | Set mpv `volume`/`mute` (`:1596`, `:1636-1646`) |
| `setDeviceVolume(v)`, `increase/decreaseDeviceVolume(...)` (both forms), `setDeviceMuted(m)` | throws | `IllegalArgumentException("You should use global volume controls...")` (`:1590-1634`). Same crash effect as a stub. Not advertised, so the session does not call them |
| `getAudioAttributes` | no-op | Returns `AudioAttributes.DEFAULT` (`:1410`), not the constructor's `audioAttributes` (`:55`), which is used for the focus request |
| `setAudioAttributes` | **stub** | `:1648` |
| `setVideoSurfaceView` / `clearVideoSurfaceView` | impl | Adds/removes a `SurfaceHolder.Callback` (`:1492-1504`, callback `:1683-1739`): attach/detach surface, `vo`, `force-window`, `android-surface-size`, frame rate |
| `setVideoSurface`, `clearVideoSurface()`, `clearVideoSurface(s)`, `setVideoSurfaceHolder`, `clearVideoSurfaceHolder`, `setVideoTextureView`, `clearVideoTextureView` | **stub** | `:1463`, `:1437`, `:1447`, `:1473`, `:1483`, `:1513`, `:1523` |
| `getVideoSize` | approx | mpv `width`/`height` on each call (`:1534`). `onVideoSizeChanged` is never sent |
| `getSurfaceSize` | impl | Parses `android-surface-size` (`:1541`) |
| `getCurrentCues` | no-op | Empty `CueGroup` (`:1552`). No `onCues`: mpv draws subtitles into the video itself, so PlayerView's `SubtitleView` (and `applySubtitleStyle`) does nothing for mpv (hypothesis on the styling, not checked on a device) |
| `addListener` / `removeListener` | impl | `ListenerSet` plus a separate `videoListeners` set for `onRenderedFirstFrame` (`:772-786`) |
| `getApplicationLooper` | impl | Main looper (`:763`) |
| `getAvailableCommands` / `isCommandAvailable` | approx | See below |
| `getMediaMetadata` | impl | Current item's `mediaMetadata` (`:1308`). `onMediaMetadataChanged` sent with each item transition (`:491`) |
| `getPlaylistMetadata` | no-op | `EMPTY` (`:1312`) |
| `setPlaylistMetadata` | **stub** | `:1316` |
| Ads (`isPlayingAd`, `getCurrentAdGroupIndex`, `getCurrentAdIndexInAdGroup`) | no-op | false / `INDEX_UNSET` (`:1371-1389`) |
| Extras outside `Player` | impl | `updateZoomMode` (`:1671`, called from `PlayerGestureHelper.kt:460`), audio focus with ducking via mpv `multiply volume` (`:728-755`), becoming-noisy pause (`:80-90`) |

Events never sent anywhere in the file: `onPlayerError`, `onVideoSizeChanged`, `onCues`, `onIsLoadingChanged`, `onRepeatModeChanged`, `onShuffleModeEnabledChanged`, `onVolumeChanged`, `onDeviceVolumeChanged`, `onAvailableCommandsChanged`, `onTrackSelectionParametersChanged`, `onPlaylistMetadataChanged`. Each one appears only in a KDoc comment.

### `TODO()` stubs and who can call them

`TODO()` throws `NotImplementedError` (an `Error`, not an `Exception`). The app's `catch (e: Exception)` blocks do not catch it, so any call crashes the app.

Media3 gates incoming session commands: `MediaSessionLegacyStub.dispatchSessionTaskWithPlayerCommand` and `MediaSessionStub` both go through `ConnectedControllersManager.isPlayerCommandAvailable`, which checks `PlayerWrapper.getAvailableCommands()` (checked in the 1.11.1 bytecode). So a stub can only be reached remotely if its command is advertised. The app has no `MediaController`/`MediaBrowser` of its own, and the session is built with the default callback (`PlayerViewModel.kt:424`).

| Line | Stub | App callers | Media3 callers | Call path |
|---|---|---|---|---|
| `:864` | `moveMediaItems` (+ base `moveMediaItem`) | none | Media3 controllers' `moveMediaItem(s)`, gated by `COMMAND_CHANGE_MEDIA_ITEMS`, **which is advertised** | Plausible: any external Media3 controller (another app). No system UI moves items |
| `:872` | `replaceMediaItems` (+ `replaceMediaItem`) | none | Controllers' `replaceMediaItem(s)`, same command | Plausible, same as above |
| `:883` | `removeMediaItems` (+ `removeMediaItem`, `clearMediaItems`) | none | Legacy `onRemoveQueueItem` → `removeMediaItem`, gated by command 20 = `COMMAND_CHANGE_MEDIA_ITEMS`, advertised. Also Media3 controllers' `removeMediaItem(s)` / `clearMediaItems` | Plausible: a legacy `MediaControllerCompat` client that edits the queue (Android Auto / Wear queue editing, third-party remotes). None found in the app |
| `:1073` | `setShuffleModeEnabled` | none (no `exo_shuffle` in `exo_main_controls.xml`, so `PlayerControlView` has no shuffle button) | Legacy `onSetShuffleMode` (command 14), controllers | None today: `COMMAND_SET_SHUFFLE_MODE` is not advertised |
| `:1317` | `setPlaylistMetadata` | none | Controllers, `COMMAND_SET_PLAYLIST_METADATA` | None: not advertised |
| `:1420` | `setVolume` | none (the `player.volume =` calls in `app/tv/.../ExoThemeMusicPlayer.kt` are on an ExoPlayer) | Controllers, `COMMAND_SET_VOLUME` | None: not advertised |
| `:1437`, `:1447` | `clearVideoSurface()`, `clearVideoSurface(Surface)` | none | `MediaController.clearVideoSurface` through the session, `COMMAND_SET_VIDEO_SURFACE` **advertised** | Plausible only for a Media3 controller that renders video. No such client in the app |
| `:1463` | `setVideoSurface` | none | Same, plus Compose `PlayerSurface` (media3-ui-compose, not used) | Same as above |
| `:1473`, `:1483` | `set/clearVideoSurfaceHolder` | none | Same | Same |
| `:1513`, `:1523` | `set/clearVideoTextureView` | none | `PlayerView` calls them when `surface_type="texture_view"` (confirmed in the `PlayerView` bytecode) | None today: `activity_player.xml` and the two TV `PlayerView(context)` (`TvPlayerScreen.kt:370`, `ui/PlayerScreen.kt:181`) use the default `surface_view`. Setting `texture_view` anywhere would crash on attach |
| `:1648` | `setAudioAttributes` | none (the builder's `setAudioAttributes` at `PlayerViewModel.kt:400` is `Builder`, not the player) | Controllers, `COMMAND_SET_AUDIO_ATTRIBUTES` | None: not advertised |

Not `TODO()`, but the same kind of crash risk:

- **`addMediaItems(Int.MAX_VALUE, …)`**: base `addMediaItem(item)` / `addMediaItems(list)` throw `IndexOutOfBoundsException`. The legacy `onAddQueueItem(desc)` passes index `-1` (seen in the bytecode). Media3 resolves it to an append (not verified), and the default callback rejects items with no URI before that anyway. The app always passes an index (`PlayerViewModel.kt:868`, `:874`).
- **Immutable list (hypothesis, strong)**: `setMediaItems` keeps the caller's list as `internalMediaItems` (`:799`, `:822`). `playQueueItem` passes `listOf(x)` (`PlayerViewModel.kt:581`), which is `Collections.singletonList` on the JVM. The next `addMediaItem` (`runDeferredWork`, `:868`/`:874`) then throws `UnsupportedOperationException`. The `catch (e: Exception)` there logs it, so it does not crash. But after "play from queue" on mpv, the previous and next episodes would not be added to the player playlist. Base `setMediaItem(item)` passes a Guava `ImmutableList` and has the same problem. Not checked on a device.
- `setDeviceVolume(Int)`, `increase/decreaseDeviceVolume`, `setDeviceMuted(Boolean)` throw `IllegalArgumentException` (`:1590-1634`). Unreachable from the session because the volume commands are not advertised.

### Available commands

`getAvailableCommands()` (`:903-928`) is the stock `BasePlayer`/SimpleBasePlayer-style list:

- Always: `PLAY_PAUSE`, `SET_SPEED_AND_PITCH`, `GET_CURRENT_MEDIA_ITEM`, `GET_METADATA`, `CHANGE_MEDIA_ITEMS`, `SET_VIDEO_SURFACE`, `GET_TRACKS`, `SET_TRACK_SELECTION_PARAMETERS` (`:1745-1757`).
- Conditional: `SEEK_TO_DEFAULT_POSITION`, `SEEK_TO_MEDIA_ITEM` (always, no ads), `SEEK_IN_CURRENT_MEDIA_ITEM` / `SEEK_BACK` / `SEEK_FORWARD` (if mpv says `seekable`), `SEEK_TO_PREVIOUS(_MEDIA_ITEM)`, `SEEK_TO_NEXT(_MEDIA_ITEM)` (from the timeline).

Mismatches:

- **Advertised but only partly there**: `CHANGE_MEDIA_ITEMS` (only `addMediaItems` with a valid index works, move/replace/remove/clear are stubs), `SET_VIDEO_SURFACE` (only the `SurfaceView` pair works), `SET_TRACK_SELECTION_PARAMETERS` (overrides and disabled types only), `SET_SPEED_AND_PITCH` (no pitch).
- **Implemented but not advertised**: `PREPARE`, `STOP`, `RELEASE`, `GET_TIMELINE`, `SET_MEDIA_ITEM`, `SET_REPEAT_MODE` (half), `GET_AUDIO_ATTRIBUTES`, `GET_VOLUME`, `GET_DEVICE_VOLUME`, `SET_DEVICE_VOLUME_WITH_FLAGS`, `GET_TEXT` (empty anyway). Effects:
  - Bluetooth/notification Stop is dropped (`onStop` needs command 3).
  - Media3's `handlePlayButtonAction` and the session's play request skip `prepare()` in IDLE.
  - Without `GET_TIMELINE`, `PlayerWrapper.getCurrentTimelineWithCommandCheck` hides the real timeline from controllers. Hypothesis: they see a single-item timeline built from the current item, so external queue UIs show no queue.
- `onAvailableCommandsChanged` is never sent. Incoming commands are checked live, but the commands advertised to controllers (notification next/previous buttons) are a snapshot that is only refreshed when other events make Media3 re-read them (hypothesis: possibly stale next/previous buttons after neighbours are added).

### `onPositionDiscontinuity`

All sends go through `notifyPositionDiscontinuity` (`:1170-1180`), which calls **both** arities on each listener in one `sendEvent`: the deprecated `onPositionDiscontinuity(reason)` (`:1177`) and `onPositionDiscontinuity(oldPosition, newPosition, reason)` (`:1178`). Before #70 (`d62e213a`, merged in #72), only the 1-arg form was sent, from `MPV_EVENT_SEEK`. #70 added the 3-arg form, kept the 1-arg one and moved the send out of `MPV_EVENT_SEEK` (`:528-533`). So both arities fire today.

Call sites:

| Line | Reason | When |
|---|---|---|
| `:420` | `AUTO_TRANSITION` | End-of-file advance, only without `pauseAtEndOfMediaItems` (dead in the app) |
| `:1117` | `SEEK` | `seekTo` within the current item |
| `:1126` | `SEEK` | `seekTo` to another, existing item |

Gaps compared with ExoPlayer:

- Item changes that mpv makes on its own (e.g. `loop-playlist`) are not reported.
- `setMediaItems` + `prepare` is not reported.
- `positionMs` in a cross-item `PositionInfo` is `initialSeekTo` (normally 0), not the requested position, because that position is dropped (`:1128`).
- `onMediaItemTransition` always says `MEDIA_ITEM_TRANSITION_REASON_AUTO` (`:486`), also for seeks and playlist replacement, where ExoPlayer says `SEEK` / `PLAYLIST_CHANGED`. It is also only sent when the `mediaId` changes (`:481`).

### Other differences from ExoPlayer semantics

- **Timeline**: not a single window. It has one window per item, but every window reports the *current* item's duration and seekability (`:654`, `:658`, `:685`). It is the same `Timeline` instance every time, so `onTimelineChanged` receivers that compare old and new by reference see no change (hypothesis about any such receiver). It only fires on `playlist-count` (`:467`), not when the duration becomes known.
- **Second resolution**: `time-pos`, `duration` and `demuxer-cache-time` are observed as INT64 (`:244-246`). Position, buffered position and (after the first update) duration move in whole seconds. `getBufferedPosition` uses `demuxer-cache-time`, which per the mpv docs is the timestamp of the end of the demuxer cache (not checked here). `getTotalBufferedDuration` returns that same absolute value.
- **Doubled state callback**: `setPlayerStateAndNotifyIfChanged` queues an extra `onPlaybackStateChanged(currentState)` (`:599-603`) whenever the state *or* playWhenReady changed. A plain pause/resume therefore calls `onPlaybackStateChanged` with an unchanged state, and a real state change calls it twice.
- **ENDED then IDLE**: after the last item ends, listeners get ENDED, then `resetInternalState()` silently sets IDLE and `isPlayerReady = false` (`:428-434`). Hypothesis: play from a controller after the end calls `seekToDefaultPosition` (advertised) → `applyInitialSeek` returns early (`:1142`) and nothing restarts.
- **Errors**: no `END_FILE` handling and no `onPlayerError`. A stream that fails to load stays in BUFFERING. `PlayerViewModel.onPlayerError` (`:1015`, audio-decoder fallback and the error event) never runs on mpv.
- **`release()` ordering**: it does not clear `handler` messages and does not call `listeners.release()`. Runnables posted by mpv callbacks before `removeObserver` (`:368`, `:405`, `:457`, `:514`) can still run after `destroy()`. Some of them call `mpvLib` (e.g. `MPV_EVENT_START_FILE` → `mpvLib.command`, `:519`). The app calls `removeListener` before `release` (`PlayerViewModel.kt:622-623`), so its own listener is safe. Whether calling a destroyed `MPVLib` crashes is not verified.
- **`resetInternalState` resets more than the item**: it is called on every item change and also clears `playbackParameters` to default (`:939`) without `onPlaybackParametersChanged`. The mpv `speed` property is untouched, so `getPlaybackParameters()` can report 1.0 while mpv keeps the old speed until the next `speed` event (hypothesis about what mpv does with `speed` across `playlist-play-index`).
- **Becoming noisy**: `resetInternalState` clears `currentPlayWhenReady` without `setBecomingNoisyRegistered(false)`. The receiver stays registered across item changes. Harmless, since it is re-armed on the next play.
- **Audio focus**: requested in `init` (`:259-269`), before anything plays, and again on each `setPlayWhenReady(true)` once ready. The lost-focus resume callback (`:739-742`) unpauses mpv directly without updating `playWhenReady`.
- **Frame rate**: `getVideoSize` is live, but `onVideoSizeChanged` never fires. On mpv, `PlayerViewModel.onVideoSizeChanged` → `handleDisplayRefreshRate` (`:1081`) depends on other triggers. MPVPlayer applies `Surface.setFrameRate` itself when `matchRefreshRate` is set (`:374-388`, `:1652`).
- **Debug leftover**: `println` of the subtitle language in `init` (`:222`).
