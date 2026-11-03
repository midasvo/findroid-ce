#!/usr/bin/env bash
# Generates the Ephyra concept media as standalone SVG files.
set -euo pipefail
OUT="$1"
mkdir -p "$OUT"

# One arm of the ephyra, pointing up: a stalk that forks into two round lappets.
ARM='M -22 -28 C -20 -45 -14 -55 -14 -62 C -22 -70 -26 -84 -18 -90 C -12 -95 -4 -92 -2 -84 C -1 -80 1 -80 2 -84 C 4 -92 12 -95 18 -90 C 26 -84 22 -70 14 -62 C 14 -55 20 -45 22 -28 Z'

# Shared defs for the glowing mark (ids prefixed so files can embed it once).
mark_defs() {
cat <<EOF
    <radialGradient id="ephBody" cx="0" cy="0" r="96" gradientUnits="userSpaceOnUse">
      <stop offset="0" stop-color="#F0FDFF"/>
      <stop offset="0.28" stop-color="#7DEBFA"/>
      <stop offset="0.66" stop-color="#8B93F8"/>
      <stop offset="1" stop-color="#C792FC"/>
    </radialGradient>
    <filter id="ephGlow" x="-60%" y="-60%" width="220%" height="220%">
      <feGaussianBlur stdDeviation="9"/>
    </filter>
    <path id="ephArm" d="$ARM"/>
    <g id="ephShape">
      <circle r="44"/>
$(for a in 0 45 90 135 180 225 270 315; do echo "      <use href=\"#ephArm\" transform=\"rotate($a)\"/>"; done)
    </g>
    <g id="ephMark">
      <use href="#ephShape" fill="#8B93F8" opacity="0.55" filter="url(#ephGlow)"/>
      <use href="#ephShape" fill="url(#ephBody)"/>
      <g stroke="#FFFFFF" stroke-opacity="0.32" stroke-width="3" stroke-linecap="round" fill="none">
$(for a in 0 45 90 135 180 225 270 315; do echo "        <path d=\"M 0 -20 L 0 -70\" transform=\"rotate($a)\"/>"; done)
      </g>
      <g fill="#FFFFFF">
$(for a in 0 45 90 135 180 225 270 315; do echo "        <circle cx=\"0\" cy=\"-80.5\" r=\"2.4\" transform=\"rotate($a)\"/>"; done)
      </g>
      <g fill="#FFFFFF" opacity="0.55">
$(for a in 0 90 180 270; do echo "        <ellipse cx=\"0\" cy=\"-11\" rx=\"5.5\" ry=\"9\" transform=\"rotate($((a + 45)))\"/>"; done)
      </g>
      <circle r="8" fill="#FFFFFF" opacity="0.95"/>
    </g>
EOF
}

# Monochrome version: the silhouette with the canals cut out.
mono_defs() {
cat <<EOF
    <path id="ephArm" d="$ARM"/>
    <mask id="ephCanals">
      <rect x="-100" y="-100" width="200" height="200" fill="#FFFFFF"/>
      <g stroke="#000000" stroke-width="4" stroke-linecap="round" fill="none">
$(for a in 0 45 90 135 180 225 270 315; do echo "        <path d=\"M 0 -22 L 0 -68\" transform=\"rotate($a)\"/>"; done)
      </g>
      <circle r="10" fill="#000000"/>
    </mask>
EOF
}

# Bioluminescent specks, deterministic so reruns give the same picture.
specks() { # width height count seed
  awk -v w="$1" -v h="$2" -v n="$3" -v s="$4" 'BEGIN {
    srand(s)
    for (i = 0; i < n; i++) {
      x = rand() * w; y = rand() * h; r = 0.8 + rand() * 2.6; o = 0.15 + rand() * 0.55
      c = (rand() < 0.5) ? "#7DEBFA" : "#C792FC"
      printf "    <circle cx=\"%.1f\" cy=\"%.1f\" r=\"%.1f\" fill=\"%s\" opacity=\"%.2f\"/>\n", x, y, r, c, o
    }
  }'
}

bg_defs() { # id cx cy
cat <<EOF
    <linearGradient id="$1" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#13265A"/>
      <stop offset="1" stop-color="#060C20"/>
    </linearGradient>
    <radialGradient id="$1Halo" cx="$2" cy="$3" r="0.5">
      <stop offset="0" stop-color="#5B6CF0" stop-opacity="0.45"/>
      <stop offset="1" stop-color="#5B6CF0" stop-opacity="0"/>
    </radialGradient>
EOF
}

FONT='<style>@import url("https://fonts.googleapis.com/css2?family=Outfit:wght@300;400;600&amp;display=swap");
    .word { font-family: Outfit, "Segoe UI", sans-serif; font-weight: 600; fill: #F0FDFF; letter-spacing: 0.01em; }
    .tag { font-family: Outfit, "Segoe UI", sans-serif; font-weight: 400; fill: #B9C3F5; }
    .small { font-family: Outfit, "Segoe UI", sans-serif; font-weight: 300; fill: #7F8BC9; letter-spacing: 0.08em; }</style>'

# Transparent mark.
cat > "$OUT/ephyra-mark.svg" <<EOF
<svg xmlns="http://www.w3.org/2000/svg" viewBox="-100 -100 200 200" width="512" height="512">
  <defs>
$(mark_defs)
  </defs>
  <use href="#ephMark"/>
</svg>
EOF

# Launcher icon, full bleed (Android masks it). The mark sits inside the 66% safe zone.
cat > "$OUT/ephyra-icon.svg" <<EOF
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">
  <defs>
$(bg_defs iconBg 0.5 0.5)
$(mark_defs)
  </defs>
  <rect width="512" height="512" fill="url(#iconBg)"/>
  <rect width="512" height="512" fill="url(#iconBgHalo)"/>
$(specks 512 512 14 7)
  <use href="#ephMark" transform="translate(256 256) scale(1.6)"/>
</svg>
EOF

# Rounded version for the README, GitHub avatar and so on.
cat > "$OUT/ephyra-icon-rounded.svg" <<EOF
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">
  <defs>
$(bg_defs iconBg 0.5 0.5)
$(mark_defs)
    <clipPath id="squircle"><rect width="512" height="512" rx="116"/></clipPath>
  </defs>
  <g clip-path="url(#squircle)">
    <rect width="512" height="512" fill="url(#iconBg)"/>
    <rect width="512" height="512" fill="url(#iconBgHalo)"/>
$(specks 512 512 14 7)
    <use href="#ephMark" transform="translate(256 256) scale(1.75)"/>
  </g>
</svg>
EOF

# Monochrome themed icon (Android 13+), single colour.
cat > "$OUT/ephyra-monochrome.svg" <<EOF
<svg xmlns="http://www.w3.org/2000/svg" viewBox="-100 -100 200 200" width="512" height="512">
  <defs>
$(mono_defs)
  </defs>
  <g fill="#000000" mask="url(#ephCanals)">
    <circle r="44"/>
$(for a in 0 45 90 135 180 225 270 315; do echo "    <use href=\"#ephArm\" transform=\"rotate($a)\"/>"; done)
  </g>
</svg>
EOF

# GitHub social preview, 1280x640.
cat > "$OUT/ephyra-banner.svg" <<EOF
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1280 640" width="1280" height="640">
  $FONT
  <defs>
$(bg_defs bannerBg 0.27 0.5)
$(mark_defs)
  </defs>
  <rect width="1280" height="640" fill="url(#bannerBg)"/>
  <rect width="1280" height="640" fill="url(#bannerBgHalo)"/>
$(specks 1280 640 70 3)
  <use href="#ephMark" transform="translate(320 320) scale(2.1)"/>
  <text class="word" x="580" y="310" font-size="140">Ephyra</text>
  <text class="tag" x="585" y="376" font-size="31">A Jellyfin client for your phone and your TV</text>
  <text class="small" x="587" y="434" font-size="18">DOWNLOADS · ANDROID TV · DOLBY VISION · OPEN SOURCE</text>
</svg>
EOF

# Play Store feature graphic, 1024x500.
cat > "$OUT/ephyra-feature-graphic.svg" <<EOF
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 500" width="1024" height="500">
  $FONT
  <defs>
$(bg_defs featBg 0.27 0.5)
$(mark_defs)
  </defs>
  <rect width="1024" height="500" fill="url(#featBg)"/>
  <rect width="1024" height="500" fill="url(#featBgHalo)"/>
$(specks 1024 500 55 11)
  <use href="#ephMark" transform="translate(255 250) scale(1.7)"/>
  <text class="word" x="470" y="250" font-size="112">Ephyra</text>
  <text class="tag" x="474" y="304" font-size="27">Your Jellyfin library, on every screen</text>
</svg>
EOF

# Android TV launcher banner, 320x180 dp drawn at xhdpi (640x360).
cat > "$OUT/ephyra-tv-banner.svg" <<EOF
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 640 360" width="640" height="360">
  $FONT
  <defs>
$(bg_defs tvBg 0.25 0.5)
$(mark_defs)
  </defs>
  <rect width="640" height="360" fill="url(#tvBg)"/>
  <rect width="640" height="360" fill="url(#tvBgHalo)"/>
$(specks 640 360 26 5)
  <use href="#ephMark" transform="translate(165 180) scale(1.2)"/>
  <text class="word" x="300" y="208" font-size="92">Ephyra</text>
</svg>
EOF

echo "done: $(ls "$OUT")"
