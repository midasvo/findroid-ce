# README screenshots

The README only shows UI that stock Findroid doesn't have. Home, Library and the movie page look
like upstream on phone, so they stay out there. The Android TV app is CE's own, so its screens are
all fair game.

The files live in `images/screenshots/`, not `fastlane/`: the fastlane images are upstream's, and
replacing them would cause binary conflicts on sync. TV shots are named `tv-*.webp`.

Take them on your own server, logged in on the device you capture from. Check every shot for
personal data such as user names or server names and addresses. The TV drawer shows the user and
server name at the top, for example, so it isn't in the README.

## Phone

### Capturing

Use a debug build of `main`. `adb install -r` keeps the login. The phone shot of the Home rows
setting came from the `ce_phone` emulator (1080×2400).

1. **Language:** switch the debug app to English, and note the old value first.

   ```bash
   adb shell cmd locale get-app-locales nl.midasvo.findroid.ce.debug
   adb shell cmd locale set-app-locales nl.midasvo.findroid.ce.debug --locales en-US
   ```

   Restore it afterwards with `--locales ""` if it was empty (`[]`), or with the old value.

2. **Theme:** set Settings → Interface → Theme to Dark and turn Dynamic colors off. Change the
   theme in the app, because editing the preferences file doesn't apply it on Android 12 and later.
3. **Capture:** save to a file on the device, then pull it. Redirecting `screencap` in PowerShell
   corrupts the PNG, and Git Bash rewrites `/data/...` into a Windows path unless
   `MSYS_NO_PATHCONV=1` is set.

   ```bash
   adb shell screencap -p /data/local/tmp/cap.png
   adb pull /data/local/tmp/cap.png
   ```

4. **Downloads:** start a large item and a few episodes together to get active rings and finished
   items in one shot. After the capture, remove them with Select on the Downloads screen.
5. **Player:** use a watched episode and exit within 20 seconds, so the server keeps no resume
   position. Two quick taps count as a double-tap seek, so tap once to show the controls.
6. **Clean up:** restore the language, theme and Dynamic colors.

### Processing

Portrait shots lose the status bar and navigation bar, which removes notifications and keeps the
files small. These crop values are for a 1080×2374 screen; on a 1080×2400 screen, use
`crop=1080:2190:0:130`.

```bash
# Portrait
ffmpeg -i in.png -vf "crop=1080:2122:0:120,scale=540:-1:flags=lanczos" \
  -c:v libwebp -quality 84 -compression_level 6 images/screenshots/name.webp

# Landscape player
ffmpeg -i in.png -vf "scale=1080:-1:flags=lanczos" \
  -c:v libwebp -quality 84 -compression_level 6 images/screenshots/name.webp
```

Keep each file under about 200 KB. The README shows the portrait shots in a table of three columns,
so add or remove them in threes, or the grid gets a gap.

## Android TV

### Capturing

Use the `ce_tv_1080p` emulator (1920×1080, Android 14, x86), logged in to your own server. Install
the x86 debug build of `main` over the existing app, which keeps the login:

```bash
./gradlew :app:tv:assembleLibreDebug
adb -s emulator-5554 install -r app/tv/build/outputs/apk/libre/debug/tv-libre-x86-debug.apk
```

1. **Language:** the same `cmd locale` commands as on phone, with `-s emulator-5554`. The TV app is
   always dark, so there's no theme to change. Titles and descriptions come from the server, so
   they stay in the server's language.
2. **Navigate with the D-pad:** `adb shell input tap` doesn't reach the TV app. Use
   `input keyevent DPAD_UP|DPAD_DOWN|DPAD_LEFT|DPAD_RIGHT|DPAD_CENTER|BACK`, one key at a time, and
   take a screenshot before the next key, because focus restore can put you somewhere unexpected.
   `input text` works in the search field.
3. **Capture:** the same `screencap` and `adb pull` as on phone.
4. **Player:** use Bluey S3:E1 or another watched item, never an unwatched one. Back while the
   controls are hidden leaves the player, so a stray Back mid-episode leaves a resume position.
   To clean up, seek past 90 % (Right with the controls hidden skips ahead) and press Back, which
   marks it played. Then check that playing it again starts at 0:00, and leave within a few
   seconds. Autoplay may start the next episode: stop that one the same way.
5. **Clean up:** restore the language.

### Screens

The README shows these, each one for a CE feature:

| File | Screen | Issue |
|---|---|---|
| `tv-home.webp` | Home with the hero, focus on the first Suggestion | #132 |
| `tv-library.webp` | TV shows grid, focus on a card so the A–Z bar and counter show | #135 |
| `tv-season.webp` | A season with episode details | #130 |
| `tv-player.webp` | Player overlay, shown with Down | #131 |
| `tv-trickplay.webp` | Right a few times with the controls hidden | #134 |

Left out: the open drawer (it shows the user and server name), search (sparse with few results),
and the launcher's Watch Next row (the emulator's launcher is full of store promotions).

### Processing

Scale to 1280 wide; that's sharp enough for the README's two-column table and keeps each file
under about 150 KB.

```bash
ffmpeg -i in.png -vf "scale=1280:-1:flags=lanczos" \
  -c:v libwebp -quality 80 -compression_level 6 images/screenshots/tv-name.webp
```
