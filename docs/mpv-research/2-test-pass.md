# Phase 2: test pass

One row per test, both backends, same item. "mpv" and "ExoPlayer" columns hold what happened; a difference is a finding, a shared failure is not an mpv bug. Devices: `emu` is the `emulator-5558` phone AVD (Android 14, x86_64, emulated 3G), `OPPO` is the CPH2359 phone, `box` is the Xiaomi TV box, `tv` is the `ce_tv` AVD. Blank rows have not run yet.

Procedure used so far: debug build, backend switched through `pref_player_backend` in the app's shared prefs with the app force-stopped, states read from the `PlayerViewModel` "Changed player state" lines in logcat, queue and position from `dumpsys media_session`.

### Start and stop

| Test | Item | mpv | ExoPlayer | Device |
|---|---|---|---|---|
| Fresh start, position 0 | Santiago en de zee S01E06 | OK. `READY` 3.5 s after `file-loaded`, through the app's seek to 0 and a second restart | not run on the same item | emu |
| Resume at a saved position | Santiago S01E06 at 18:00; Lady en de Vagebond II at 30:51 | **Stuck loader.** Plays, state stays `BUFFERING`, spinner in the middle, `fileLoaded` false, no trickplay, media session `BUFFERING` with `position=-1` | OK. `READY` after 17 s, no spinner, media session `PLAYING` with position | emu |
| No frame at 0 before the resume position (#255) | | | | |
| Start while the server is slow | | | ExoPlayer flapped `BUFFERING` ↔ `READY` five times in the first 10 s on the emulated 3G link; mpv did not | emu, incidental |
| Back out during loading | | | | |
| Release while buffering | | | | |

### Seeking

| Test | Item | mpv | ExoPlayer | Device |
|---|---|---|---|---|
| Gestures | | | | |
| Seek bar | | | | |
| Double-tap | | | | |
| Remote D-pad steps and hold | | | | tv |
| Trickplay while seeking | | | | |
| Seek past the end | | | | |
| Seek while paused | | | | |

### Media segments (#271)

| Test | Item | mpv | ExoPlayer | Device |
|---|---|---|---|---|
| Button window | | | | |
| Tap to skip | | | | |
| Auto-skip | | | | |
| Outro to next episode | | | | |
| Auto outro skip with autoplay off | | | | |
| Seeking back into a segment | | | | |

### Queue

| Test | Item | mpv | ExoPlayer | Device |
|---|---|---|---|---|
| Next and previous episode | | | | |
| Autoplay at the end | | | | |
| Still-watching prompt (#70) | | | | |
| Playlist index in `prepareMediaItem` | | | | |
| What the media session shows | Santiago S01E06, playing from 0 | `PLAYING`, `size=0`, `position=-1` (also when `READY`) | `PLAYING`, `size=0`, position and buffered position filled, `active item id=0` | emu |

### Tracks

| Test | Item | mpv | ExoPlayer | Device |
|---|---|---|---|---|
| Audio and subtitle dialogs | | | | |
| Remembered per-series choice (#115) | | | | |
| External subtitles | | | | |
| ASS styling and the override toggle | | | | |
| Track switch mid-play | | | | |

### Formats on the box

| Test | Item | mpv | ExoPlayer | Device |
|---|---|---|---|---|
| Dolby Vision profile 5 (#96) | | | | box |
| Dolby Vision profile 8 (#96) | | | | box |
| HDR10 | | | | box |
| TrueHD and Atmos passthrough | | | | box |
| 24p with refresh-rate switching (#139) | | | | box |
| A file ExoPlayer transcodes but mpv direct-plays | | | | box |

### Lifecycle

| Test | Item | mpv | ExoPlayer | Device |
|---|---|---|---|---|
| Picture-in-picture | | | | |
| Background audio and return (#79, #80, #90) | | | | |
| Rotation | | | | |
| Headphone unplug | | | | |
| Audio focus loss | | | | |
| A phone call | | | | |

### Reporting

| Test | Item | mpv | ExoPlayer | Device |
|---|---|---|---|---|
| Progress reaches the server | Santiago S01E06, from 0 | Reported: after 40 s from the start the item left Continue watching (below Jellyfin's minimum resume percentage), so progress reaches the server once `READY`. Whether it is also reported in the stuck `BUFFERING` state was not checked | | emu, incidental |
| Played and stopped with the right positions | | | | |

### Settings

| Test | Item | mpv | ExoPlayer | Device |
|---|---|---|---|---|
| hwdec: no, mediacodec, mediacodec-copy | | | n/a | |
| vo: gpu, gpu-next | | | n/a | |
| ao: audiotrack, opensles, aaudio | | | n/a | |
