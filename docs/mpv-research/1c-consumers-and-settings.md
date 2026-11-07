# mpv research 1c: consumers of the Player, settings, upstream

Read on `claude/issue-284-sonnet-opus-090a1d` at `7e21c0de`. Code reading only, nothing was built or run, so anything marked **hypothesis** still needs a device test. `MPVPlayer.kt` itself is covered in the other 1-reports; line numbers below point into it only where a consumer depends on that line. `PlayerViewModel.kt` is written `VM`.

## A. Where the backend is chosen

One place: **`PlayerViewModel.init`**, `player/local/.../presentation/PlayerViewModel.kt:361-418`.

| What | Where |
|---|---|
| Preference | `AppPreferences.kt:50` `playerBackend = Preference("pref_player_backend", "exoplayer")`. Values `exoplayer` or `mpv` (`settings/.../res/values/string_arrays.xml:13-16`) |
| Switch | `VM:362` `player = when (playerBackend)`: `"exoplayer"` builds `ExoPlayer` (`:363-396`), `"mpv"` builds `MPVPlayer.Builder` (`:398-416`), anything else throws (`:418`) |
| Settings UI | `SettingsViewModel.kt:415-420`: Player category, first row "Player backend" (`PreferenceSelect`, `R.string.pref_player_backend`) |
| Session | `VM:423-424` builds the `MediaSession` for whichever player came out of the `when` |

- There is no Hilt module, no `@Provides` and no `PlaybackService` involvement. The service only receives a session (`PlaybackService.mediaSession`, set at `VM:439`).
- It is read **once per ViewModel**, so a change in settings applies to the next player session, not the running one. On phone the ViewModel lives in `PlaybackSessionStore.viewModelStore` (`PlayerActivity.kt:64-70`) and survives the activity with background audio. On TV it comes from `hiltViewModel()` (`TvPlayerScreen.kt:135`).
- **The backend also leaks into the data layer.** `JellyfinRepositoryImpl.getPlaybackSources` (`data/.../JellyfinRepositoryImpl.kt:477`) reads `playerBackend == "mpv"` on every call to choose the device profile: mpv gets the permissive direct-play profile (`getDirectPlayProfile()`), or `getDirectPlayTranscodeFallbackProfile()` once a bitrate cap is set; ExoPlayer gets the hardware-probed profile. So mpv sessions are never transcoded unless the user picks a quality cap, and `playerDirectPlayDolbyVision` (`AppPreferences.kt:67`) has no effect on mpv.
- `ExoPlayer`-only things in the VM: `DefaultRenderersFactory`, `AudioDecoderFallback`, `buildMediaSourceFactory`, `setVideoChangeFrameRateStrategy`, tunneling (`trackSelector.setTunnelingEnabled(true)`, `VM:~352`). mpv takes only `trackSelector.parameters` for the preferred languages (`MPVPlayer.kt:218-224`).
- One `is MPVPlayer` check outside the VM: `PlayerGestureHelper.kt:460` (zoom gesture calls `updateZoomMode`, ExoPlayer uses `resizeMode`).

## B. Consumers and what they assume

Two facts about `MPVPlayer` explain most rows below:

1. **The only `STATE_READY` after the first load comes from `paused-for-cache` going false** (`MPVPlayer.kt:438-445`) or a later `PLAYBACK_RESTART` (`:553`). The first `PLAYBACK_RESTART` only sets `isPlayerReady` and unpauses (`:536-551`), it does not set `STATE_READY`. `prepareMediaItem` and `MPV_EVENT_SEEK` set `STATE_BUFFERING` (`:532`, `:1205`). **Hypothesis:** if `paused-for-cache=false` arrives before the first `PLAYBACK_RESTART` (it is ignored while `!isPlayerReady`), the player stays in `STATE_BUFFERING` while playing. That would match the spinner symptom from the plan and would also leave `fileLoaded` false (see 1 and 3).
2. **`time-pos` and `duration` are observed as `MPV_FORMAT_INT64`** (`:245-246`, handled at `:456-466`), so `currentPosition` and `duration` update in whole seconds. `getCurrentPosition()` returns `C.TIME_UNSET` until the first `time-pos` (`:1350`). `bufferedPosition` is `demuxer-cache-time` (`:1358`), or the content position when absent. `isLoading()` is always false (`:1091`).

MPVPlayer never emits: `onPlayerError` (no `END_FILE` handler in `event()`, `:513-560`; `getPlayerError()` stays null), `onVideoSizeChanged`, `onAvailableCommandsChanged`. It does emit `onTracksChanged`, `onTimelineChanged` (on `playlist-count`), `onMediaItemTransition` (only when the **mediaId** changes, `:480-489`), `onMediaMetadataChanged`, both `onPositionDiscontinuity` overloads (from `seekTo` and the end-of-file advance only), `onPlaybackStateChanged`, `onPlayWhenReadyChanged`, `onIsPlayingChanged`.

`setPlayerStateAndNotifyIfChanged` (`:576-611`) queues a second, `C.INDEX_UNSET` `onPlaybackStateChanged(playbackState)` whenever either state or playWhenReady changed. **Observation:** listeners can therefore see the same state twice (ENDED twice means `NavigateBack` is sent twice; the phone's `finishPlayback()` is idempotent, the effect on TV is untested).

### 1. `PlayerViewModel` (`VM`)

| Feature | Player API used | mpv-relevant assumption |
|---|---|---|
| Start | `setMediaItems(items, 0, startPosition)`, `prepare()`, `play()` (`:516-518`) | `startPosition` may be `C.TIME_UNSET`. MPVPlayer passes a start > 0 into `loadfile ... start=` (`:~733`, CE `592d29bf`). Only the first item's index/position is handled that way |
| Segments poll | `currentPosition`, `duration`, `hasNextMediaItem()`, then `seekTo` or `seekToNextMediaItem()` (`:~660-700`, `:1361-1375`). Polled every 1 s by `PlayerActivity.kt:294-301` and `TvPlayerScreen.kt:207-212`, only if `shouldPollSegments()` | Position resolution is 1 s on mpv (fact 2), so a segment edge can be hit up to 1 s late. `duration` can be `TIME_UNSET` early; `SegmentTracker` receives it as is |
| Outro to next episode | `skipTo(NextItem)`: `refreshPlayingSnapshot()`, `seekToNextMediaItem()` (`:1368-1373`) | Needs the next `MediaItem` to be in the player's list already. It is added by `runDeferredWork` (`:849-880`, `addMediaItem(index + 1, ...)`), which waits for `STATE_READY` (fact 1). MPVPlayer's `addMediaItems` multi-item index fix is CE `6c3a7990` |
| Autoplay-off path | `onPlayWhenReadyChanged`: `!playWhenReady && reason == END_OF_MEDIA_ITEM && playbackState == STATE_READY` (`:886-893`), then `hasNextMediaItem()`, `pause()`, `NavigateBack` | mpv sets `playWhenReady=false` and `STATE_READY` in one call for a non-last item (`:408-417`), so the condition holds when the listener runs (events are flushed after both fields are set). For the last item it sets `STATE_ENDED` instead (`:429-435`), the branch is skipped on purpose and `onPlaybackStateChanged(ENDED)` sends `NavigateBack` (`:1040-1043`) |
| Auto-advance | `pendingAutoAdvanceSeek = true`, `seekToNextMediaItem()`, `play()` (`:945-951`); `onPositionDiscontinuity(oldPos, newPos, reason)` (3-arg, `:1531-1553`) | Needs the 3-arg overload with `DISCONTINUITY_REASON_SEEK`. Fixed for mpv in CE `d62e213a`; comment at `:249` says mpv delivers it synchronously from `seekTo()` |
| Trickplay | Waits for `STATE_READY` via `deferredWork` / `runDeferredWork` (`:836-838`, `:1035-1038`) | If fact 1's hypothesis holds, trickplay and neighbour queueing never run on mpv (no next/previous item, no trickplay). **Test first** |
| Still-watching | `onPlayWhenReadyChanged` as above, `hasNextMediaItem()`, `autoAdvanceToNextItem()`; user activity via `onPositionDiscontinuity(reason == SEEK)` | Works only through the 3-arg discontinuity (see above). No timeline use |
| Server reporting | `postPlaybackStart` in `onMediaItemTransition` (`:766-840`), progress via `currentPosition`, `isPlaying`, `currentMediaItem` (`:693-710`), stop via the `PlayingSnapshot` (`:645-660`, `:715-740`) | Stop for the item being left is sent from `onMediaItemTransition`, so it needs a transition on every item change. mpv emits one only when the mediaId changes; a same-id reload (quality switch) therefore reports nothing, which is intended (`:775-780`). `refreshPlayingSnapshot` skips `position < 0` and unknown duration because mpv resets both when it loads the next file (`:645-657`). `getCurrentPosition()` is `TIME_UNSET` (negative) before the first tick, which that guard also covers; `updatePlaybackProgress` (`:693`) does **not** guard it and would send a negative position |
| Next / previous episode | `hasNextMediaItem()`, `seekToNextMediaItem()`, `seekToPrevious()` (TV `TvPlayerScreen.kt:318-321`), `currentMediaItemIndex` | Index changes arrive through `playlist-current-pos` (`MPVPlayer.kt:473-489`). `seekToPrevious()` is `BasePlayer` logic and depends on `getAvailableCommands()`/`currentPosition` |
| Track selection | `currentTracks.groups` (filter `isSupported`), `trackSelectionParameters` get/set with `setOverrideForType(TrackSelectionOverride(group, 0))`, `setTrackTypeDisabled` (`:1130-1215`) | MPVPlayer maps overrides to `aid`/`sid`/`vid` through `override.key.getFormat(0).id` (`:1277-1295`). Groups come from the `track-list` JSON (`:1775-1830`) with `id`, `title`→`label`, `lang`→`language`, `demux-fps`→`frameRate`. `isSupported` is whatever the JSON helper sets |
| Per-series remembered choice (#115) | `onTracksChanged`: `appliedTrackPreferenceItemId` guard, reset to global, then `matchSeriesTrack` on `Format.language` and `Format.label` of `getFormat(0)` (`:1133-1192`) | Relies on `onTracksChanged` firing once the audio/text groups exist. mpv fires it on every `track-list` change, including its own selection changes, which the `appliedTrackPreferenceItemId` guard absorbs. Language values are mpv's (`eng`, 3-letter), Jellyfin's comparison is in `matchSeriesTrack` (not read here, **hypothesis** that it normalises) |
| Playback speed | `setPlaybackSpeed` (`:1225`) | Maps to mpv `speed` (`MPVPlayer.kt:1233`) |
| Quality switch | `setMediaItems(rebuilt, currentIndex, position)`, `prepare()` (`:1275-1280`) | Same URL list on mpv reloads via `playlist-clear` and `loadfile`; CE `a4d29173` resets the tracked index |
| Refresh rate | `onTracksChanged` and `onVideoSizeChanged` call `handleDisplayRefreshRate(tracks)` and read `Format.frameRate` of the video group (`:1049-1083`), then `DisplayRefreshRateManager` sets `preferredDisplayModeId` (`DisplayRefreshRateManager.kt:~60-95`) | mpv's frame rate is `demux-fps` from `track-list` (`MPVPlayer.kt:1799-1800`), the container value, **not** `container-fps` or `video-params`. Neither string occurs in the code. `onVideoSizeChanged` is never fired by MPVPlayer, so only `onTracksChanged` triggers it. Gated by `playerTvMatchRefreshRate`. MPVPlayer additionally calls `Surface.setFrameRate` itself (`:1648-1670`, CE `cfa96146`), so on mpv both mechanisms can act. **Hypothesis:** they usually agree, untested on the box |
| Background audio / video off | `trackSelectionParameters` with `setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, ...)` (`:598-606`) | mpv maps it to `vid=no`/`auto` (`:1285-1295`); CE `146aaaee` re-fires `onRenderedFirstFrame` when video returns |
| Errors | `onPlayerError` → `AudioDecoderFallback` or `PlayerEvents.PlayerError` (`:1015-1023`) | **Never called on mpv.** A file mpv cannot open produces no error toast, no TV `playbackError`, and presumably stays in `STATE_BUFFERING` (hypothesis) |
| Misc | `release()`, `removeListener`, `isPlaying`, `playWhenReady` (set in `BasePlayerActivity.onResume/onPause`) | `setPlayWhenReady` before `isPlayerReady` only stores the flag (`MPVPlayer.kt:1003-1021`); the first `PLAYBACK_RESTART` applies it |

### 2. `PlaybackService` and the media session

- `PlayerViewModel` builds **the** `MediaSession` for `player` (`VM:423-424`, random UUID id), on phone and TV. It is released in `releasePlayer` (`:626-630`).
- `PlaybackService` (`player/local/.../PlaybackService.kt`) is a plain `MediaSessionService` with a static `mediaSession`. `onGetSession` returns it. It does not own or wrap a player, and is only started when background audio is on (`VM:428-441`, `AndroidManifest.xml:57`). TV never starts it.
- `PlaybackSessionStore` (`PlaybackSessionStore.kt`) owns the ViewModel on phone so it outlives the activity; it never touches the Player directly.
- **Why `dumpsys media_session` shows `size=0, position=-1` on mpv (hypotheses, not verified on a device):**
  - Media3 only publishes the queue to the platform session when the wrapped player reports `COMMAND_GET_TIMELINE`. `MPVPlayer.getAvailableCommands()` (`:903-927`) is `permanentAvailableCommands` (`:1745-1756`: play/pause, speed, get current item, get metadata, change items, set surface, get tracks, set track selection) plus the seek commands. **There is no `COMMAND_GET_TIMELINE`**, no `COMMAND_PREPARE`/`STOP`/`RELEASE`, no volume or audio-attribute getters, no repeat/shuffle. The queue stays empty even though `getCurrentTimeline()` (`:1325`) returns a real timeline.
  - `position=-1` is `PLAYBACK_POSITION_UNKNOWN`. Likely cause: the session reads `getCurrentPosition()`, which is `C.TIME_UNSET` until mpv's first `time-pos` (`:1350`), or the position is gated on the same missing commands. Needs a dump taken while playing, after a few seconds.
  - What the session does get from mpv: `currentMediaItem` and `getMediaMetadata()` (CE `a918b4e3`, `:1308-1310`, plus the `onMediaMetadataChanged` event at `:491`), so the notification title and artwork work. ExoPlayer's `getAvailableCommands()` includes all of the above, which is why it fills `size` and `position`.
- Known stubs a session or controller could hit: `setVolume` (`:1419`), `setAudioAttributes` (`:1648`), `setShuffleModeEnabled` (`:1072`), `removeMediaItems`/`moveMediaItems` (`:863`, `:882`). They are not in the available commands, so a compliant controller will not call them. The Media3 session dispatches only commands the player lists.

### 3. Phone player

- `activity_player.xml:9-15`: `androidx.media3.ui.PlayerView`, `app:show_buffering="always"`. The spinner is `@id/exo_buffering` (`exo_player_view.xml:29`). Media3 shows it when `getPlaybackState() == STATE_BUFFERING` (with `always`, regardless of `playWhenReady`); it re-evaluates on `onPlaybackStateChanged` / `onPlayWhenReadyChanged`. **So the spinner is driven purely by `Player.playbackState`.** On mpv `STATE_BUFFERING` is set at load and on every seek (fact 1) and cleared only by `paused-for-cache=false` or a non-first `PLAYBACK_RESTART`. That is the likely reason for the stuck spinner (hypothesis, plan item).
- `PlayerActivity.kt:126` `binding.playerView.player = viewModel.player`; `:127-134` controller visibility listener (updates the skip button via `isControllerFullyVisible`).
- Controls are the stock `PlayerControlView` (`R.id.exo_controller`, `exo_progress`, `DefaultTimeBar`) inside custom layouts (`exo_player_view.xml`, `exo_main_controls.xml`). They read `currentPosition`, `duration`, `bufferedPosition`, `isPlaying`, `playWhenReady`, `getAvailableCommands()` (prev/next/seek buttons enable by command) and `currentTimeline`. `DefaultTimeBar.setExtraAdGroupMarkers` draws chapters (`:200-217`).
- Buttons are disabled until `uiState.fileLoaded` (`:222-236`), which only `STATE_READY` sets (`VM:1035-1038`). With fact 1's hypothesis the audio/subtitle/speed/quality/PiP/lock buttons would stay greyed out on mpv. **Check this together with the spinner.**
- Dialogs (`presentation/player/*`) go through the VM: `TrackSelectionDialogFragment` reads `viewModel.player.currentTracks`, `SpeedSelectionDialogFragment` `selectSpeed`, `QualitySelectionDialogFragment` `selectMaxBitrate`, `ChapterListDialogFragment` `onAction(JumpToChapter)`.
- Gestures `utils/PlayerGestureHelper.kt`: `currentPosition`, `duration`, `seekTo`, `playWhenReady`, `playbackParameters.speed`, `setPlaybackSpeed` (hold-to-speed, `:142-146`, `:504`), `seekToNextMediaItem` (`:173`), `is MPVPlayer` (`:460`). Seek preview uses trickplay from the VM state, and `PreviewScrubListener.kt:108` reads `player.duration`.
- Progress and polling: `updatePlaybackProgress` every 5 s (`PlayerActivity.kt:279-284`), `refreshPlayingSnapshot` every 1 s (`:286-292`), segment poll every 1 s (`:294-301`).
- PiP: `pipParams` reads `playerView.player.videoSize` (`:450`) and throws `IllegalArgumentException` while it is `UNKNOWN`. MPVPlayer's `getVideoSize()` reads mpv's `width`/`height` (`MPVPlayer.kt:1534-1539`), so it is fine once a file is loaded. `isPlaying` drives `setPictureInPictureParams`/`onUserLeaveHint`. Entering PiP sets `useController=false`.
- Lifecycle (`BasePlayerActivity`): `onResume` sets `player.playWhenReady = viewModel.playWhenReady`, `onPause` stores it and pauses unless background audio is on. With mpv `playWhenReady` setter semantic as above.
- `onStop` keeps the player attached to `PlayerView` (CE `146aaaee`, because detaching drops mpv's surface callbacks).
- Subtitles: `PlayerView.subtitleView.applySubtitleStyle(appPreferences)` (`:160`) styles Media3 cues. `MPVPlayer.getCurrentCues()` returns an empty `CueGroup` (`:1552`), mpv draws subtitles itself, so the CE subtitle style preferences do not apply to mpv (**hypothesis:** check against the settings description).

### 4. TV player

- Used screen: `ce/presentation/player/TvPlayerScreen.kt` (`NavigationRoot.kt:414`). `ui/PlayerScreen.kt` (368 lines) has no caller in the tree (grep: only its own definition) and looks dead.
- It builds a bare `PlayerView` with `useController = false` (`:370-372`) for video and subtitles, and draws its own Compose overlay.
- State read: `viewModel.progress` (position, duration, isPlaying, polled every `PROGRESS_TICK_MS = 300`, `PlaybackProgress.kt:6`), `uiState` (segment, chapters, trickplay, `showStillWatching`), `viewModel.queue`, and directly `player.currentPosition`, `player.duration`, `player.isPlaying`, `player.playWhenReady` (`:275-278`, `:534`), `currentTracks` for the audio/subtitle dialogs (`:553`).
- Calls: `seekTo`, `play`, `pause`, `hasNextMediaItem`, `seekToNextMediaItem`, `seekToPrevious` (`:304-321`, `:347`).
- **No buffering indicator exists**: no `STATE_BUFFERING` handling and no spinner in the file. The `PlayerView` shows none because the controller is off. A stuck `STATE_BUFFERING` would be invisible on TV.
- Errors: `PlayerEvents.PlayerError` sets `playbackError` (`:155`). Never fires on mpv.
- `fileLoaded` is not read on TV; controls are always on. The session comes from the VM (`:196-197`).

### 5. Other consumers

- `PlaybackSessionStore` (above). `MpvCleanupWorker` (`core/.../work/MpvCleanupWorker.kt`) reads the legacy `pref_player_mpv`.
- `PlaylistManager` (`player/local/.../domain/`): builds the `PlayerItem` list and chooses the source (a downloaded copy wins). It never touches the Player; `recreatePlayerItem` is used by the quality switch.
- Downloads/offline playback: same VM and `PlayerItem.mediaSourceUri`. Local files are `file://` URIs; the mpv path hands the URI to `loadfile` unchanged (`MPVPlayer.kt:~733`). The Exo-only `buildMediaSourceFactory` is skipped.
- `modes/film`, `core`: no Player references (grep for `androidx.media3.common.Player` and `PlayerViewModel` finds only `app/phone`, `app/tv`, `player/local`). `ExoThemeMusicPlayer` (TV) is its own ExoPlayer for theme music.
- `SegmentTracker`, `TrickplayLoader` import `Player` only as a type/constant.

## C. mpv settings

All are `AppPreferences` entries (`settings/.../domain/AppPreferences.kt`), shown in **Settings → Player → "mpv options"** (`SettingsViewModel.kt:437-480`; a `PreferenceCategory` with a nested page, no dependency on the backend switch, so it is visible even with ExoPlayer selected). Phone and TV render the same tree (`app/*/.../SettingsGroupCard.kt`).

| Preference | Key | Default | UI | Read | mpv option |
|---|---|---|---|---|---|
| Player backend | `pref_player_backend` | `exoplayer` | Player → "Player backend" (`SettingsViewModel.kt:415-420`) | `VM:361` | selects `MPVPlayer` |
| Hardware decoding | `pref_player_mpv_hwdec` | `mediacodec` | mpv options (`:455-462`), values `no`, `mediacodec`, `mediacodec-copy` | `VM:411` → `hwDec` → `MPVPlayer.kt:198` | `hwdec=<value>`; plus fixed `hwdec-codecs=h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1` (`:199`) |
| Video output | `pref_player_mpv_vo` | `gpu-next` | mpv options (`:463-469`), values `gpu`, `gpu-next` | `VM:409` → `MPVPlayer.kt:192`, again at surface creation `:1700` | `vo=<value>`; `vo=null` while the surface is gone (`:1735`) |
| Audio output | `pref_player_mpv_ao` | `audiotrack` (CE, upstream `aaudio`) | mpv options (`:470-476`), values `audiotrack`, `opensles`, `aaudio` | `VM:410` → `MPVPlayer.kt:193` | `ao=<value>`; also `audiotrack-session-id` / `aaudio-session-id` (`:255-256`) |
| Match refresh rate | `pref_player_tv_match_refresh_rate` | `false` | Player, TV only (`supportedDeviceTypes = TV`, `:427-433`) | `VM:376` (ExoPlayer strategy), `VM:413` → `matchRefreshRate` | `Surface.setFrameRate` (`MPVPlayer.kt:1648-1670`); also `DisplayRefreshRateManager` |
| Config files | none (file edit) | n/a | mpv options → Advanced: "Edit file" for `mpv/mpv.conf` and `mpv/input.conf` (`:480-500`) | `MPVPlayer.kt:185-186` `config=yes`, `config-dir=filesDir/mpv` | whatever the user writes. **Hypothesis:** mpv reads the file at `mpv_initialize()` (`:~232`), after the options above are set, so user lines can override them |
| Legacy switch | `pref_player_mpv` | `false` | none (the old "mpv player" switch; strings remain at `strings.xml:36,49`, a Compose preview still shows it) | `MpvCleanupWorker.kt:32` only | none |
| Legacy migration flag | `mpv_migrated` | `false` | none | `BaseApplication.kt:94-96` (phone) | none |
| aaudio flag | `pref_player_mpv_ao_aaudio_migrated` | `false` | none | `AppPreferences.kt:316-322` | none |

Not preferences, hard-coded in `MPVPlayer.kt:185-231`: `profile=fast`, `gpu-context=android`, `opengl-es=yes`, `tls-verify=no`, `cache=yes`, `cache-pause-initial=yes`, `demuxer-max-bytes=64MiB`, `demuxer-max-back-bytes=32MiB`, `sub-scale-with-window=yes`, `sub-use-margins=no`, `alang`/`slang` from the preferred-language settings, `force-window=no`, `keep-open=always`, `save-position-on-quit=no`, `ytdl=no`, `audio-set-media-role=yes`, `gpu-shader-cache-dir`/`icc-cache-dir` = `cacheDir/mpv`. No `deband`, `fastseek` or other cache preferences exist anywhere in the settings module (grep). Zoom toggles `panscan` and the subtitle margin options (`:1671-1680`). Seek increments (`pref_player_seek_back_inc` / `forward_inc`) are passed to the Builder (`VM:404-407`) but are not mpv settings.

### Migrations

- **`mpv_migrated` + `MpvCleanupWorker`** (`BaseApplication.kt` phone `:94-96`, scheduled once from a background coroutine when the flag is false; worker `MpvCleanupWorker.kt:21-27`): copies the legacy `pref_player_mpv=true` into `pref_player_backend="mpv"` and deletes the old `getExternalFilesDir()/mpv` config directory and `filesDir/mpv/subfont.ttf` + `shader_*` files, then sets `mpv_migrated=true`.
  - **Fresh install:** it runs (flag defaults to `false`), is a no-op (legacy preference false, directories absent), and sets the flag. It runs only from the **phone** `BaseApplication`; the TV `BaseApplication` never schedules it, so on TV the flag stays false (harmless, TV has no legacy data).
  - **Hypothesis:** the worker could delete `filesDir/mpv/shader_*` files the user created under the *new* config dir if a user copied them there before the first run; unlikely.
- **aaudio to audiotrack (#52)**: `AppPreferences.migrateMpvAudioOutput()` (`:316-323`), called synchronously in `onCreate` of both apps (`app/phone/.../BaseApplication.kt:81`, `app/tv/.../BaseApplication.kt:47`). If the stored `pref_player_mpv_ao` equals `aaudio`, it is rewritten to `audiotrack`; then `pref_player_mpv_ao_aaudio_migrated=true`, so a later deliberate choice sticks.
  - **Fresh install:** it fires but does nothing to the value: nothing is stored, `getValue` returns the default `audiotrack` (`:82-83`), so it only sets the flag. Existing users with the old default (key absent) get `audiotrack` from the new default without a rewrite. Only users with `aaudio` written to disk (the picker persists on any tap) are rewritten, which is the point of CE `a4465b9c`.
  - It cannot be folded into `MpvCleanupWorker`: `mpv_migrated` is already true on existing installs, so that worker never runs there (commit message of `a4465b9c`).
- `migrateMediaSegmentActions` (#271) is unrelated to mpv.

### libmpv version

- Dependency `dev.jdtech.mpv:libmpv:1.0.0` (`gradle/libs.versions.toml:38`, `:106`), `implementation` in `player/local/build.gradle.kts:37`, `compileOnly` in `app/phone/build.gradle.kts:196`. The Java wrapper is `dev.jdtech.mpv.MPVLib`.
- The bundled **mpv version** is not in the Gradle files. A CE comment in `MPVPlayer.kt` (`loadfile` section, CE `592d29bf`) says "mpv 0.38+ (the bundled build is 0.41)". Treat 0.41 as the claim of that comment, not as verified from the `.aar` (the `.aar` is in the Gradle cache, nobody has inspected its `libmpv.so` strings here).
- Upstream commit `4d4c85d3` "feat: update mpv and use system fonts" is the last bump in the file history.

## D. Upstream

An `upstream` remote exists (`https://github.com/jarnedemeulemeester/findroid.git`, fetch only). `git log` for the file, newest first. "CE" means not an ancestor of `upstream/main` (checked with `merge-base --is-ancestor`).

| Commit | Author | Origin | Change in `MPVPlayer.kt` |
|---|---|---|---|
| `592d29bf` perf(player): start playback sooner | Midas | CE | start position passed to `loadfile ... start=`, `startPassedToLoadMs`, `applyInitialSeek()`. **Also converted the whole file from LF to CRLF** (numstat 1849/1832; 25/8 ignoring line endings) |
| `cfa96146` TV display refresh rate switching (#139) | Midas | CE | `matchRefreshRate`, `applyFrameRateToSurface`, surface callbacks (+58) |
| `a97f7e22` Merge background-audio branch | Midas | CE | merge |
| `577139ff` speed and stability wins (Moonfin review) | Midas | CE | pause on `ACTION_AUDIO_BECOMING_NOISY` (+35) |
| `146aaaee` lift the shutter when video returns (#90) | Midas | CE | `onRenderedFirstFrame` when a video track is selected again (+8) |
| `a918b4e3` TV session crash, notification metadata, video off in background | Midas | CE | `getMediaMetadata()` follows the current item, `onMediaMetadataChanged` (+6/-1) |
| `d62e213a` emit 3-arg onPositionDiscontinuity | Midas | CE | `notifyPositionDiscontinuity`, `positionInfo`, seek reporting moved to `seekTo`/auto-advance (+72/-14) |
| `48f4f07f`, `11852a1e` merges (sync-63, upstream Navigation 3 / Room 3) | Midas | CE merges | no content of their own |
| `a4d29173` switch stream quality in place | Jorrit Smit | CE (contributor) | reset `currentMediaItemIndex` when the playlist is replaced (+6) |
| `dd9dce84` restore audiotrack as default | Maycon Negrini | CE (contributor) | default `audioOutput` to `Constants.MpvAudioOutput.AUDIOTRACK` (2/2) |
| `a4465b9c` migrate persisted aaudio | Midas | CE | comment only in the file (+5/-2), logic in `AppPreferences` |
| `6c3a7990` correct mpv frame rate key and multi-item insert index | Midas | CE | `demux-w` to `demux-fps`, `index + offset` in `addMediaItems` (3/3) |
| earlier (`a28ac9e7` and back to `8ced2f8b`) | Jarne Demeulemeester | upstream | ktfmt, `period.set`, separate settings/config editor (#1232), system fonts, seek in ms, surface handling |

`git diff upstream/main --stat` for the file shows 1849 insertions/1659 deletions because of the CRLF conversion in `592d29bf`; with `--ignore-space-at-eol` it is **217 insertions, 27 deletions** (244 lines). Upstream's copy is LF; `git ls-files --eol` reports `i/crlf w/crlf` here (8 tracked files are CRLF in the index, including `PlayerViewModel.kt`). **Consequence for `upstream-sync`:** any upstream change to this file will conflict on every line unless the line endings are normalised first. Not a mpv finding, but it belongs in the report.

CE's net additions to the file, by theme, for the "belongs upstream" question: audio default (`dd9dce84`, already upstream-compatible in spirit, upstream still has `aaudio`), position-discontinuity overloads, `demux-fps` key fix, playlist index reset and `addMediaItems` offset fix (generic bug fixes), start position in `loadfile`, becoming-noisy pause, shutter fix, metadata. Refresh-rate switching and TV-only parts are CE specific.
