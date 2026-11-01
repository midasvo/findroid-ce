# README screenshots

The README only shows UI that stock Findroid doesn't have. Home, Library and the movie page look
like upstream, so they stay out.

The files live in `images/screenshots/`, not `fastlane/`: the fastlane images are upstream's, and
replacing them would cause binary conflicts on sync.

## Capturing

Use a debug build of `main`. `adb install -r` keeps the login.

1. **Language:** switch the debug app to English, and note the old value first.

   ```bash
   adb shell cmd locale get-app-locales nl.midasvo.findroid.ce.debug
   adb shell cmd locale set-app-locales nl.midasvo.findroid.ce.debug --locales en-US
   ```

2. **Theme:** set Settings → Interface → Theme to Dark and turn Dynamic colors off. Change the
   theme in the app, because editing the preferences file doesn't apply it on Android 12 and later.
3. **Capture:** save to a file on the device, then pull it. Redirecting `screencap` in PowerShell
   corrupts the PNG.

   ```bash
   adb shell screencap -p /data/local/tmp/cap.png
   adb pull /data/local/tmp/cap.png
   ```

4. **Downloads:** start a large item and a few episodes together to get active rings and finished
   items in one shot. After the capture, remove them with Select on the Downloads screen.
5. **Player:** use a watched episode and exit within 20 seconds, so the server keeps no resume
   position. Two quick taps count as a double-tap seek, so tap once to show the controls.
6. **Clean up:** restore the language, theme and Dynamic colors.

## Processing

Portrait shots lose the status bar and navigation bar, which removes notifications and keeps the
files small. These crop values are for a 1080×2374 screen.

```bash
# Portrait
ffmpeg -i in.png -vf "crop=1080:2122:0:120,scale=540:-1:flags=lanczos" \
  -c:v libwebp -quality 84 -compression_level 6 images/screenshots/name.webp

# Landscape player
ffmpeg -i in.png -vf "scale=1080:-1:flags=lanczos" \
  -c:v libwebp -quality 84 -compression_level 6 images/screenshots/name.webp
```

Keep each file under about 200 KB.
