#!/usr/bin/env bash
# Generates the Ephyra icon set (#171): SVG sources, Android resources and, when a Chromium
# browser is given, the PNGs for the stores and the README.
#
#   images/branding/ephyra/make-ephyra.sh images/branding/ephyra
#   BROWSER="/path/to/chrome" images/branding/ephyra/make-ephyra.sh images/branding/ephyra
#
# The mark is a young jellyfish seen from the side: a dome in two overlapping colours and
# tentacles of which two fork at the end, a nod to being a fork. All geometry lives in one
# coordinate space (about -80..80 wide, -66..80 high) and is shared by the SVGs and the
# Android VectorDrawables, so both always match. No blur anywhere: VectorDrawable can't blur,
# so the glow is a radial gradient.
set -euo pipefail
OUT="$1"
mkdir -p "$OUT/android/drawable" "$OUT/android/mipmap-anydpi" "$OUT/android/mipmap-xhdpi"

CYAN='#4FD8F0'
VIOLET='#9B7CF6'
SHINE='#E4FBFF'

# Geometry. Path data uses only M, L, C, A and Z, which SVG and VectorDrawable both read.
DOME='M -64 -2 C -64 -40 -36 -66 0 -66 C 36 -66 64 -40 64 -2 Z'
DOME_FRONT='M -64 -2 C -64 -40 -36 -66 0 -66 C 12 -66 22 -63 30 -58 C 6 -44 -4 -22 2 -2 Z'
SHINE_ARC='M -46 -20 C -45 -40 -30 -54 -10 -57 C -27 -47 -38 -36 -38 -20 A 4 4 0 0 1 -46 -20 Z'
# Tentacles are stroked, 9 wide with round caps. Left side is cyan, right side violet.
STROKE=9
fork() { # x: straight down, then two branches
  local x="$1"
  echo "M $x 6 L $x 46 C $x 58 $((x - 7)) 62 $((x - 10)) 76 M $x 46 C $x 58 $((x + 7)) 62 $((x + 10)) 76"
}
LEFT_TENTACLES="M -11 6 L -11 80 $(fork -36) M -58 6 C -58 28 -72 34 -77 18"
RIGHT_TENTACLES="M 11 6 L 11 80 $(fork 36) M 58 6 C 58 28 72 34 77 18"
# Moves the mark so its middle sits on the origin.
DY=-8
# Adaptive icon: scale of the mark on the 108dp canvas, so it stays inside the 66dp safe zone.
ADAPTIVE_SCALE=0.355

# Shared defs for the coloured mark (ids prefixed so files can embed it once).
mark_defs() {
cat <<EOF
    <radialGradient id="ephGlow" cx="0" cy="-30" r="110" gradientUnits="userSpaceOnUse">
      <stop offset="0" stop-color="$VIOLET" stop-opacity="0.55"/>
      <stop offset="0.5" stop-color="$CYAN" stop-opacity="0.16"/>
      <stop offset="1" stop-color="$CYAN" stop-opacity="0"/>
    </radialGradient>
    <g id="ephMark">
      <g transform="translate(0 $DY)">
        <circle cy="-30" r="110" fill="url(#ephGlow)"/>
        <path d="$DOME" fill="$VIOLET"/>
        <path d="$DOME_FRONT" fill="$CYAN"/>
        <path d="$SHINE_ARC" fill="$SHINE" opacity="0.85"/>
        <g fill="none" stroke-width="$STROKE" stroke-linecap="round" stroke-linejoin="round">
          <path d="$RIGHT_TENTACLES" stroke="$VIOLET"/>
          <path d="$LEFT_TENTACLES" stroke="$CYAN"/>
        </g>
      </g>
    </g>
EOF
}

# One-colour silhouette: the dome with the shine cut out, and the tentacles.
mono_mark() { # colour
cat <<EOF
  <g transform="translate(0 $DY)">
    <path d="$DOME $SHINE_ARC" fill="$1" fill-rule="evenodd"/>
    <path d="$LEFT_TENTACLES $RIGHT_TENTACLES" fill="none" stroke="$1" stroke-width="$STROKE" stroke-linecap="round" stroke-linejoin="round"/>
  </g>
EOF
}

# Bioluminescent specks, deterministic so reruns give the same picture.
specks() { # width height count seed
  awk -v w="$1" -v h="$2" -v n="$3" -v s="$4" -v a="$CYAN" -v b="$VIOLET" 'BEGIN {
    srand(s)
    for (i = 0; i < n; i++) {
      x = rand() * w; y = rand() * h; r = 0.8 + rand() * 2.6; o = 0.15 + rand() * 0.55
      c = (rand() < 0.5) ? a : b
      printf "    <circle cx=\"%.1f\" cy=\"%.1f\" r=\"%.1f\" fill=\"%s\" opacity=\"%.2f\"/>\n", x, y, r, c, o
    }
  }'
}

bg_defs() { # id cx cy
cat <<EOF
    <linearGradient id="$1" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#1A2F6B"/>
      <stop offset="1" stop-color="#070D22"/>
    </linearGradient>
    <radialGradient id="$1Halo" cx="$2" cy="$3" r="0.5">
      <stop offset="0" stop-color="#5B6CF0" stop-opacity="0.35"/>
      <stop offset="1" stop-color="#5B6CF0" stop-opacity="0"/>
    </radialGradient>
EOF
}

FONT='<style>@import url("https://fonts.googleapis.com/css2?family=Outfit:wght@300;400;600&amp;display=swap");
    .word { font-family: Outfit, "Segoe UI", sans-serif; font-weight: 600; fill: #F0FDFF; letter-spacing: 0.01em; }
    .tag { font-family: Outfit, "Segoe UI", sans-serif; font-weight: 400; fill: #B9C3F5; }
    .small { font-family: Outfit, "Segoe UI", sans-serif; font-weight: 300; fill: #7F8BC9; letter-spacing: 0.08em; }</style>'

# Background with wordmark, used by the banners and the feature graphic.
wide() { # file width height markX markY markScale textX textY textSize seed [tag]
cat > "$OUT/$1" <<EOF
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $2 $3" width="$2" height="$3">
  $FONT
  <defs>
$(bg_defs wideBg 0.25 0.5)
$(mark_defs)
  </defs>
  <rect width="$2" height="$3" fill="url(#wideBg)"/>
  <rect width="$2" height="$3" fill="url(#wideBgHalo)"/>
$(specks "$2" "$3" $(($2 / 18)) "${10}")
  <use href="#ephMark" transform="translate($4 $5) scale($6)"/>
  <text class="word" x="$7" y="$8" font-size="$9">Ephyra</text>
${11:-}
</svg>
EOF
}

# Transparent mark.
cat > "$OUT/ephyra-mark.svg" <<EOF
<svg xmlns="http://www.w3.org/2000/svg" viewBox="-100 -100 200 200" width="512" height="512">
  <defs>
$(mark_defs)
  </defs>
  <use href="#ephMark"/>
</svg>
EOF

# Store icon, full bleed: Play, F-Droid and Amazon round the corners themselves. Amazon also
# wants a 114x114 copy, so the same drawing is written at both sizes.
icon() { # file size
cat > "$OUT/$1" <<EOF
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="$2" height="$2">
  <defs>
$(bg_defs iconBg 0.5 0.45)
$(mark_defs)
  </defs>
  <rect width="512" height="512" fill="url(#iconBg)"/>
  <rect width="512" height="512" fill="url(#iconBgHalo)"/>
$(specks 512 512 14 7)
  <use href="#ephMark" transform="translate(256 256) scale(2.0)"/>
</svg>
EOF
}
icon ephyra-icon.svg 512
icon ephyra-icon-114.svg 114

# Rounded version for the README, GitHub avatar and so on.
cat > "$OUT/ephyra-icon-rounded.svg" <<EOF
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">
  <defs>
$(bg_defs iconBg 0.5 0.45)
$(mark_defs)
    <clipPath id="squircle"><rect width="512" height="512" rx="116"/></clipPath>
  </defs>
  <g clip-path="url(#squircle)">
    <rect width="512" height="512" fill="url(#iconBg)"/>
    <rect width="512" height="512" fill="url(#iconBgHalo)"/>
$(specks 512 512 14 7)
    <use href="#ephMark" transform="translate(256 256) scale(2.1)"/>
  </g>
</svg>
EOF

# Monochrome silhouette (themed icon source, favicons, one-colour print).
cat > "$OUT/ephyra-monochrome.svg" <<EOF
<svg xmlns="http://www.w3.org/2000/svg" viewBox="-100 -100 200 200" width="512" height="512">
$(mono_mark '#000000')
</svg>
EOF

# GitHub social preview, 1280x640.
wide ephyra-banner.svg 1280 640 330 320 2.4 580 310 140 3 \
  '  <text class="tag" x="585" y="376" font-size="31">A Jellyfin client for your phone and your TV</text>
  <text class="small" x="587" y="434" font-size="18">DOWNLOADS · ANDROID TV · DOLBY VISION · OPEN SOURCE</text>'

# Play Store feature graphic, 1024x500.
wide ephyra-feature-graphic.svg 1024 500 255 250 1.9 470 250 112 11 \
  '  <text class="tag" x="474" y="304" font-size="27">Your Jellyfin library, on every screen</text>'

# Android TV launcher banner, 320x180 dp drawn at xhdpi (640x360). It goes in mipmap, as
# drawable/ic_banner is the in-app logo.
wide ephyra-tv-banner.svg 640 360 150 180 1.25 270 212 96 5

# Play Store Android TV banner, 1280x720.
wide ephyra-tv-banner-play.svg 1280 720 300 360 2.5 540 424 192 13

# Amazon Fire TV app icon, 1280x720. Mark and wordmark stay inside Amazon's 882x448 safe area.
wide ephyra-firetv-icon.svg 1280 720 370 360 1.8 545 410 148 17

# Amazon Fire TV background, 1920x1080. Fire TV draws the title and description over the left
# side, so no wordmark, and the mark stays inside the 1214x830 safe area.
cat > "$OUT/ephyra-firetv-background.svg" <<EOF
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1920 1080" width="1920" height="1080">
  <defs>
$(bg_defs wideBg 0.68 0.5)
$(mark_defs)
  </defs>
  <rect width="1920" height="1080" fill="url(#wideBg)"/>
  <rect width="1920" height="1080" fill="url(#wideBgHalo)"/>
$(specks 1920 1080 106 19)
  <use href="#ephMark" transform="translate(1300 540) scale(3.2)"/>
</svg>
EOF

# Android resources. Copy android/* into core/src/main/res.
group_open() {
cat <<EOF
    <group
        android:translateX="54"
        android:translateY="54"
        android:scaleX="$ADAPTIVE_SCALE"
        android:scaleY="$ADAPTIVE_SCALE">
        <group android:translateY="$DY">
EOF
}
group_close() {
cat <<EOF
        </group>
    </group>
EOF
}
argb() { # #RRGGBB alpha-hex
  echo "#$2${1#\#}"
}
vector_open() {
cat <<EOF
<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by images/branding/ephyra/make-ephyra.sh. Edit the script, not this file. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
EOF
}
tentacles_xml() { # leftColour rightColour
cat <<EOF
            <path
                android:pathData="$RIGHT_TENTACLES"
                android:strokeColor="$2"
                android:strokeWidth="$STROKE"
                android:strokeLineCap="round"
                android:strokeLineJoin="round" />
            <path
                android:pathData="$LEFT_TENTACLES"
                android:strokeColor="$1"
                android:strokeWidth="$STROKE"
                android:strokeLineCap="round"
                android:strokeLineJoin="round" />
EOF
}

{
vector_open
group_open
cat <<EOF
            <path android:pathData="M -110 -30 A 110 110 0 1 0 110 -30 A 110 110 0 1 0 -110 -30 Z">
                <aapt:attr name="android:fillColor">
                    <gradient
                        android:type="radial"
                        android:centerX="0"
                        android:centerY="-30"
                        android:gradientRadius="110">
                        <item android:offset="0" android:color="$(argb $VIOLET 8C)" />
                        <item android:offset="0.5" android:color="$(argb $CYAN 29)" />
                        <item android:offset="1" android:color="$(argb $CYAN 00)" />
                    </gradient>
                </aapt:attr>
            </path>
            <path android:pathData="$DOME" android:fillColor="$VIOLET" />
            <path android:pathData="$DOME_FRONT" android:fillColor="$CYAN" />
            <path android:pathData="$SHINE_ARC" android:fillColor="$(argb $SHINE D9)" />
EOF
tentacles_xml "$CYAN" "$VIOLET"
group_close
echo '</vector>'
} > "$OUT/android/drawable/ic_launcher_foreground.xml"

{
vector_open
group_open
cat <<EOF
            <path
                android:pathData="$DOME $SHINE_ARC"
                android:fillColor="#FFFFFFFF"
                android:fillType="evenOdd" />
EOF
tentacles_xml '#FFFFFFFF' '#FFFFFFFF'
group_close
echo '</vector>'
} > "$OUT/android/drawable/ic_launcher_monochrome.xml"

{
vector_open
cat <<EOF
    <path android:pathData="M 0 0 L 108 0 L 108 108 L 0 108 Z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="linear"
                android:startX="0"
                android:startY="0"
                android:endX="0"
                android:endY="108"
                android:startColor="#FF1A2F6B"
                android:endColor="#FF070D22" />
        </aapt:attr>
    </path>
    <path android:pathData="M 0 0 L 108 0 L 108 108 L 0 108 Z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="radial"
                android:centerX="54"
                android:centerY="48"
                android:gradientRadius="54"
                android:startColor="#595B6CF0"
                android:endColor="#005B6CF0" />
        </aapt:attr>
    </path>
</vector>
EOF
} > "$OUT/android/drawable/ic_launcher_background.xml"

# In-app logos: the coloured mark without the glow, which the canvas edges would cut off.
# ic_banner is the big one on the setup and About screens, on a 3:1 canvas that fits where the
# old wordmark was. ic_logo is the small one next to the server name on Home and in the TV top bar.
in_app_logo() { # file width height scale
cat > "$OUT/android/drawable/$1" <<EOF
<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by images/branding/ephyra/make-ephyra.sh. Edit the script, not this file. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="$2dp"
    android:height="$3dp"
    android:viewportWidth="$2"
    android:viewportHeight="$3">
    <group
        android:translateX="$(($2 / 2))"
        android:translateY="$(($3 / 2))"
        android:scaleX="$4"
        android:scaleY="$4">
        <group android:translateY="$DY">
            <path android:pathData="$DOME" android:fillColor="$VIOLET" />
            <path android:pathData="$DOME_FRONT" android:fillColor="$CYAN" />
            <path android:pathData="$SHINE_ARC" android:fillColor="$(argb $SHINE D9)" />
$(tentacles_xml "$CYAN" "$VIOLET")
        </group>
    </group>
</vector>
EOF
}
in_app_logo ic_banner.xml 384 128 0.8
in_app_logo ic_logo.xml 160 160 0.95

cat > "$OUT/android/mipmap-anydpi/ic_launcher.xml" <<EOF
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
EOF

# PNGs, rendered with a headless Chromium browser (Chrome or Edge) when BROWSER is set.
render() { # svg png width height
  local src="$OUT/$1" url
  if command -v cygpath >/dev/null; then url="file:///$(cygpath -m "$(realpath "$src")")"; else url="file://$(realpath "$src")"; fi
  local dst="$(realpath "$OUT")/$2"
  if command -v cygpath >/dev/null; then dst="$(cygpath -w "$dst")"; fi
  "$BROWSER" --headless=new --disable-gpu --hide-scrollbars --default-background-color=00000000 \
    --window-size="$3,$4" --virtual-time-budget=4000 --screenshot="$dst" "$url" >/dev/null 2>&1
}
if [ -n "${BROWSER:-}" ]; then
  render ephyra-mark.svg ephyra-mark.png 512 512
  render ephyra-icon.svg ephyra-icon.png 512 512
  render ephyra-icon-rounded.svg ephyra-icon-rounded.png 512 512
  render ephyra-monochrome.svg ephyra-monochrome.png 512 512
  render ephyra-banner.svg ephyra-banner.png 1280 640
  render ephyra-feature-graphic.svg ephyra-feature-graphic.png 1024 500
  render ephyra-tv-banner.svg ephyra-tv-banner.png 640 360
  render ephyra-tv-banner-play.svg ephyra-tv-banner-play.png 1280 720
  render ephyra-icon-114.svg ephyra-icon-114.png 114 114
  render ephyra-firetv-icon.svg ephyra-firetv-icon.png 1280 720
  render ephyra-firetv-background.svg ephyra-firetv-background.png 1920 1080
  cp "$OUT/ephyra-tv-banner.png" "$OUT/android/mipmap-xhdpi/ic_banner.png"
else
  echo "BROWSER not set: skipped the PNGs"
fi

echo "done: $(cd "$OUT" && find . -type f ! -name '*.sh' | sort | tr '\n' ' ')"
