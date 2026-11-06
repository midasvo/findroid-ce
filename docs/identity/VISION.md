# Ephyra visual identity: Bioluminescence

Ephyra is the free-swimming young of a jellyfish. The identity takes that literally: the app is
deep water, and the things that are alive in it glow. Everything else is dark, calm and out of
the way of the artwork.

This document is the agreed direction. It is written so that someone with no taste of their own
can apply it consistently. Where it says "must", a code review can quote it.

## 1. Principles

1. **Content is the light source.** Posters, backdrops and video supply the colour. The chrome is
   dark, low-chroma navy that recedes behind them. Never put a saturated brand colour behind, next
   to or across artwork.
2. **Cyan means alive.** One accent, `primary` (`#4FD8F0`), is used only for things that are
   happening or can happen now: playback progress, the seek bar, downloads in flight, the focused
   item on TV, the Play action. If it is static, it is not cyan.
3. **Violet means yours.** `tertiary` (`#B9A3FF` dark / `#6B4FD1` light) marks what the user has
   claimed: favourites, the watched tick, star ratings. Nothing else is violet.
4. **Glow only when alive, and only a little.** A soft halo appears on the TV focus ring, the seek
   thumb and download progress rings. Buttons, badges, text and static icons never glow. The halo
   is subtle enough to survive an OLED or HDR panel without bloom.
5. **Round and buoyant.** Pills for actions, 12–24 dp corners for surfaces, slow decelerating
   motion. Nothing is sharp and nothing snaps.
6. **The brand lives in tokens.** The identity is expressed through Material 3 colour roles,
   shapes, typography and a few CE-owned components. Upstream composables are not restyled one by
   one.

## 2. Dynamic colour

**Off by default.** `FindroidTheme(dynamicColor = ...)` defaults to `false` on phone, and the
`pref_dynamic_colors` preference defaults to `false`. The existing "Dynamic colours" setting stays
as an opt-in: a user who turns it on gets Material You wallpaper colours exactly as today. No new
strings. TV never uses dynamic colour.

Rationale: on Android 12+ the wallpaper scheme replaces the brand scheme entirely, so with the
default on, the palette below would never be seen.

## 3. Colour tokens

Values are final. They map one to one onto `androidx.compose.material3.ColorScheme` (and
`androidx.tv.material3.ColorScheme` for the roles it has). Source of truth:
`core/src/main/java/dev/jdtech/jellyfin/core/presentation/theme/Color.kt`.

### 3.1 Dark scheme (default on phone, only scheme on TV)

| Role | Hex | Role | Hex |
|---|---|---|---|
| primary | `#4FD8F0` | onPrimary | `#00303A` |
| primaryContainer | `#0E4F5C` | onPrimaryContainer | `#BDF4FF` |
| secondary | `#A3B0FF` | onSecondary | `#10207A` |
| secondaryContainer | `#232E66` | onSecondaryContainer | `#DDE1FF` |
| tertiary | `#B9A3FF` | onTertiary | `#2E1F6B` |
| tertiaryContainer | `#4A3A9A` | onTertiaryContainer | `#EADDFF` |
| error | `#FFB4AB` | onError | `#690005` |
| errorContainer | `#93000A` | onErrorContainer | `#FFDAD6` |
| background | `#070B16` | onBackground | `#E4FBFF` |
| surface | `#070B16` | onSurface | `#E4FBFF` |
| surfaceVariant | `#1F2B45` | onSurfaceVariant | `#9FB3C8` |
| outline | `#5A6B85` | outlineVariant | `#2A3650` |
| scrim | `#000000` | | |
| inverseSurface | `#E4FBFF` | inverseOnSurface | `#0D1322` |
| inversePrimary | `#007A8E` | | |
| surfaceDim | `#070B16` | surfaceBright | `#2A3858` |
| surfaceContainerLowest | `#03060D` | surfaceContainerLow | `#0D1322` |
| surfaceContainer | `#121A2C` | surfaceContainerHigh | `#182238` |
| surfaceContainerHighest | `#1F2B45` | | |

### 3.2 Light scheme (phone only)

| Role | Hex | Role | Hex |
|---|---|---|---|
| primary | `#007A8E` | onPrimary | `#FFFFFF` |
| primaryContainer | `#BDF4FF` | onPrimaryContainer | `#00303A` |
| secondary | `#3E4DB8` | onSecondary | `#FFFFFF` |
| secondaryContainer | `#DDE1FF` | onSecondaryContainer | `#0A1560` |
| tertiary | `#6B4FD1` | onTertiary | `#FFFFFF` |
| tertiaryContainer | `#EADDFF` | onTertiaryContainer | `#22125A` |
| error | `#BA1A1A` | onError | `#FFFFFF` |
| errorContainer | `#FFDAD6` | onErrorContainer | `#410002` |
| background | `#F3FBFF` | onBackground | `#0B1326` |
| surface | `#F3FBFF` | onSurface | `#0B1326` |
| surfaceVariant | `#C8E0E9` | onSurfaceVariant | `#3F4F64` |
| outline | `#6B7A90` | outlineVariant | `#BCC9D8` |
| scrim | `#000000` | | |
| inverseSurface | `#0D1322` | inverseOnSurface | `#E4FBFF` |
| inversePrimary | `#4FD8F0` | | |
| surfaceDim | `#D3DEE4` | surfaceBright | `#F3FBFF` |
| surfaceContainerLowest | `#FFFFFF` | surfaceContainerLow | `#E9F5FA` |
| surfaceContainer | `#DFEFF5` | surfaceContainerHigh | `#D4E8EF` |
| surfaceContainerHighest | `#C8E0E9` | | |

### 3.3 Brand tokens outside the Material scheme

Defined in a CE-owned `EphyraColors` object next to `ColorDark`/`ColorLight` (same file or
`EphyraColor.kt` in `core/.../presentation/theme/`).

| Token | Value | Use |
|---|---|---|
| `glow` | `primary` at 45 % alpha | The halo colour (see §6). |
| `glowTv` | `primary` at 55 % alpha | TV focus halo; a little stronger for 3 m. |
| `scrimOverArt` | `#000000` at 10 % → 60 % | Vertical gradient over backdrops. Replaces the ad-hoc `Color.Black.copy(alpha = …)` brushes where files are CE-owned; upstream files keep theirs. |
| `pale` | `#E4FBFF` | `onSurface` in dark; also the icon shine. Never used as a surface. |
| `starRating` | `tertiary` | Replaces `StarRatingYellow`/`Yellow`. Rating stars are violet, not yellow. |

### 3.4 Semantic colour rules

- **Progress** (watched fraction on cards, seek bar, download ring, storage bar "used"): `primary`.
- **Download states**: in flight `primary`; done `primary` tick on `primaryContainer`; queued
  `onSurfaceVariant`; failed `error`.
- **Watched tick, favourite heart, star rating**: `tertiary`. Unfilled versions use
  `onSurfaceVariant`.
- **Storage summary segments**: Movies `primary`, TV `tertiary`, Other `secondary`, Free
  `surfaceContainerHighest`. These four differ in lightness as well as hue, so they still read for
  deuteranopia and protanopia.
- **Destructive actions** ("Remove all downloads", delete): `error`.
- **Text on artwork** (hero titles, player overlay): white `#FFFFFF` and white at 70 % over a
  `scrimOverArt` gradient. Never `onSurface` (it is pale cyan and tints the image).
- **Navigation bar / rail active indicator**: `secondaryContainer` with `onSecondaryContainer`
  icon. Active icon is not cyan: being on a tab is not "alive".
- **Chips and badges** (resolution, codec, item count): `surfaceContainerHigh` with
  `onSurfaceVariant`. Never coloured.

Contrast: every `on*`/container pair above is ≥ 4.5:1. `primary` on `surface` (dark) is ≈ 11:1;
`onSurfaceVariant` on `surface` (dark) ≈ 8.5:1; light `primary` on `surface` ≈ 4.8:1. Colour is
never the only carrier of a state: a tick, ring or icon always accompanies it.

## 4. Typography

**Display face: Outfit** (SIL OFL, variable, bundled as `core/src/main/res/font/outfit.ttf`, about
90 KB, weights 500–700 used). **Body face: Roboto** (system default, unchanged), so upstream text
keeps its metrics and nothing reflows on sync.

| M3 role | Face | Weight | Phone size | TV size | Where |
|---|---|---|---|---|---|
| displayLarge/Medium | Outfit | 700 | 40 / 32 sp | 56 / 48 sp | TV hero title, welcome screen |
| headlineLarge/Medium/Small | Outfit | 600 | 28 / 24 / 20 sp | 32 / 28 / 24 sp | Screen titles ("Downloads"), item titles |
| titleLarge/Medium/Small | Outfit | 600 | 20 / 16 / 14 sp | 22 / 18 / 16 sp | Section headers ("Continue watching"), card titles, dialog titles |
| bodyLarge/Medium/Small | Roboto | 400 | 16 / 14 / 12 sp | 18 / 16 / 14 sp | Overviews, settings, lists |
| labelLarge/Medium/Small | Roboto | 500 | 14 / 12 / 11 sp | 16 / 14 / 12 sp | Buttons, chips, nav labels, metadata lines |

Rules:
- Letter-spacing: Outfit roles at 700 use `-0.01em`; all other roles use M3 defaults.
- Line height: display 1.05, headline 1.15, title 1.25, body 1.5.
- Metadata lines ("2016 · 78 mins · Ends at 4:14 PM") are `labelMedium` in `onSurfaceVariant`
  with `tabular-nums`.
- Never use Outfit below 14 sp; small text is Roboto.
- Never set text in all caps.
- Episode codes are written `S03E01`, no spaces.

Phone currently has no `Type.kt`; add `app/phone/.../theme/Type.kt` (CE-owned) and pass
`typography = Typography` in `FindroidTheme` (one line in an upstream file). TV already has
`Type.kt`; update it.

## 5. Shape

| M3 shape role | Value | Used for |
|---|---|---|
| extraSmall | 8 dp | Menu items, small thumbnails, text fields |
| small | 12 dp | Posters, episode cards, list cards, chips that are not pills |
| medium | 16 dp | Home hero, dialogs' inner cards, download cards |
| large | 24 dp | Dialogs, bottom sheets (top corners), TV side panels |
| extraLarge | 28 dp | Full-screen sheets |

- Buttons, filter chips, the nav indicator and the "Select" action are **full pills**
  (`CircleShape` / `RoundedCornerShape(50)`).
- Icon buttons on artwork (back, home on the item page) are circles on `scrim` at 50 %.
- Focus ring on TV follows the card's own shape with +2 dp offset.
- No shape is sharper than 8 dp anywhere in the app.

## 6. Motion

Buoyant: things settle slowly, like something drifting to rest. Nothing overshoots.

| Token | Value |
|---|---|
| `durationShort` | 200 ms (icon state, chip select, glow fade-in) |
| `durationMedium` | 350 ms (screen enter, sheet open, list item appear) |
| `durationLong` | 700 ms (hero/backdrop crossfade, TV hero swap) |
| `easingEnter` | M3 `EmphasizedDecelerate` (`CubicBezier(0.05, 0.7, 0.1, 1.0)`) |
| `easingExit` | M3 `EmphasizedAccelerate` (`CubicBezier(0.3, 0, 0.8, 0.15)`) |
| `easingStandard` | M3 `Standard` for everything in between |

Rules:
- Screen transitions: fade + 8 dp upward drift on enter, fade only on exit. No horizontal slides.
- TV focus: scale 1.00 → 1.06 over `durationShort` with `easingEnter`; the glow fades in over the
  same duration and out over 150 ms. Scale on TV cards is the only scale animation in the app.
- Backdrop changes (TV hero, phone item page) always crossfade over `durationLong`; they never cut.
- Progress rings and bars animate value changes over `durationMedium`.
- The glow is a static halo (no pulse, no breathing), except the TV screensaver, which may drift
  the bioluminescent specks slowly.
- Respect reduced motion: when the system animator scale is 0, durations collapse to 0 and the
  focus scale is replaced by the ring alone.

### Glow spec

A halo drawn behind the element: `Modifier.shadow`-style blur with `glow` colour, blur radius
10 dp on phone and 20 dp on TV, spread 0, no offset. Applied only to:

1. the TV focus ring (always, on every focusable card and button),
2. the seek bar thumb while the player controls are visible,
3. download progress rings while a download is in flight.

Nothing else. In light theme the glow is disabled on phone (it has nothing to glow against).

## 7. Iconography

- Keep the current Lucide-style set in `core/src/main/res/drawable/`: 24 dp grid, 2 dp stroke,
  round caps and joins. New icons must match that spec; no Material filled icons mixed in.
- Default tint `onSurfaceVariant`; `onSurface` for the primary action in a row; `primary` only for
  a state that is alive (downloading, playing); `tertiary` for favourite/watched/rating when set.
- Filled variants are used only for "set" states (`ic_heart_filled`, a filled star). Navigation
  icons stay outlined even when active; the pill indicator carries the state.
- Play is the one icon that may sit on a `primary` fill (the hero and item page Play button, the
  TV Play button, the player's main control).
- App icon and TV banner: unchanged (`images/branding/ephyra/`). The monochrome icon stays the
  silhouette.

## 8. Where it lives in code

Mostly theme tokens and CE-owned files, so upstream syncs stay cheap.

| Change | File(s) | Owner |
|---|---|---|
| Colour roles (dark + light) | `core/.../core/presentation/theme/Color.kt` | upstream file, wholesale value swap (merges as one hunk) |
| Brand tokens | `core/.../core/presentation/theme/EphyraColor.kt` (new) | CE |
| Shapes | `app/phone/.../theme/Shape.kt`, `app/tv/.../theme/Shape.kt` | upstream, tiny |
| Phone typography | `app/phone/.../theme/Type.kt` (new) + one line in `Theme.kt` | CE + 1 line |
| TV typography | `app/tv/.../theme/Type.kt` | upstream, small |
| Dynamic colour default | `Theme.kt` default param, `AppPreferences.dynamicColors` default | 2 lines |
| TV background | `app/tv/.../theme/Theme.kt` (replace black→`#001721` gradient with flat `surface`) | 1 line |
| Glow modifier | `core/.../core/presentation/ephyra/Glow.kt` (new) | CE |
| TV focus ring + glow | `app/tv/.../ce/...` screens (already CE-owned: replace `Color.White` 3–4 dp borders with `primary` + glow) | CE |
| Seek thumb / download ring glow | `ProgressBar.kt`, `DownloadProgressButton.kt`, `TvSeekBar.kt` | 2 upstream files, 1 modifier each; TV one is CE |
| Star rating colour | `StarRatingYellow`/`Yellow` → `tertiary` | upstream, 2 lines |
| Font asset | `core/src/main/res/font/outfit.ttf` + `Font.kt` | CE |

Everything not in this table (hard-coded `Color.Black.copy(alpha)` scrims, `Color.White` text on
artwork in upstream files) is left alone: it is already what §3.4 asks for.

## 9. Do and don't

**Do**
- Use `primary` for progress, focus, play and downloads in flight, and for nothing else.
- Use `tertiary` for favourite, watched and rating, and for nothing else.
- Put text on artwork in white over `scrimOverArt`.
- Use pills for actions and 12–24 dp for surfaces.
- Crossfade artwork; drift screens in; let things settle.
- Keep new icons on the 24 dp / 2 dp-stroke grid.
- Put the identity in tokens and CE files; touch an upstream composable only with one modifier
  or one value.

**Don't**
- Don't glow anything that isn't in §6's list of three.
- Don't use cyan for a tab indicator, a chip, a badge background or decorative dividers.
- Don't use yellow for ratings, red for "watched", or any colour the scheme doesn't define
  (`Color.Red`, `Color.Gray`, ad-hoc hex).
- Don't put `onSurface` (pale cyan) text on artwork.
- Don't use gradients as surfaces. The only gradients are scrims over artwork and the icon's own
  glow.
- Don't add sea imagery (waves, bubbles, tentacles) to screens. The specks are allowed only on the
  TV screensaver and empty states.
- Don't set text in all caps or in Outfit below 14 sp.
- Don't re-enable dynamic colour by default, and don't remove the opt-in.
- Don't restyle an upstream composable wholesale to get a brand effect; find the token or skip it.

## 10. Open questions

1. **Status and navigation bars on phone.** Transparent over `surface` (edge-to-edge) is the
   assumption. Confirm on a device with a three-button nav bar that `#070B16` doesn't look like a
   dead strip.
2. **Light theme glow.** §6 disables it. If the seek thumb looks dead in light theme, consider a
   2 dp `primaryContainer` ring instead.
3. **Empty-state specks.** Allowed by §9 but not designed. Decide whether `EmptyState` gets a
   small speck field or stays plain.
4. **README and store screenshots** need retaking once the theme lands; the feature graphic and
   icon are already on-brand.
5. **Welcome screen** (`WelcomeScreen.kt`, CE-owned) is the one place where a larger brand moment
   is acceptable: the mark with its glow on `surface`. Not specced here.
6. **TV `darkSchemeTv`** lacks `outline*` and `surfaceContainer*` roles; the TV screens use the
   phone `MaterialTheme` for those. Check nothing on TV still reads the old `#001721` gradient.
7. **Jellyfin's own purple-blue.** The secondary (`#A3B0FF`) sits close to Jellyfin's brand
   gradient. It is rarely visible (nav indicator, storage "Other"); if it ever reads as "Jellyfin",
   shift it toward the cyan.
