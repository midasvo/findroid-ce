# Phase 3: what the platform asks, and what peers do

Sources were read on 2026-11-07. Numbers are quoted; where a page gave none, it says so.

## 1. Window size classes

From [developer.android.com: window size classes](https://developer.android.com/develop/ui/compose/layouts/adaptive/window-size-classes).

| Width class | dp | Typically |
|---|---|---|
| Compact | < 600 | 99.96 % of phones in portrait |
| Medium | 600–839 | 93.73 % of tablets in portrait, large unfolded inner displays in portrait, split-screen halves |
| Expanded | 840–1199 | 97.22 % of tablets in landscape, unfolded inner displays in landscape |
| Large | 1200–1599 | Large tablets |
| Extra-large | ≥ 1600 | Desktop |

| Height class | dp | Typically |
|---|---|---|
| Compact | < 480 | 99.78 % of phones in landscape |
| Medium | 480–899 | 96.56 % of tablets in landscape, 97.59 % of phones in portrait |
| Expanded | ≥ 900 | 94.25 % of tablets in portrait |

Rules Google states: classes "are explicitly not determined by the size of the device screen" and "are not intended for isTablet-type logic". Width matters most; height matters for phones and foldables in landscape (medium width, compact height). API: `currentWindowAdaptiveInfo(supportLargeAndXLargeWidth = true)`.

Our reference device (1280×800 dp) is **Expanded × Medium** in landscape and **Medium × Expanded** in portrait. A phone in landscape (915×412) is **Medium × Compact**, so every medium-width rule we write also hits phones in landscape.

## 2. Canonical layouts

From [canonical layouts](https://developer.android.com/develop/ui/compose/layouts/adaptive/canonical-layouts) and [media on large screens](https://developer.android.com/media/implement/surfaces/large_screens).

- **List-detail** (`ListDetailPaneScaffold`): both panes at expanded width; one at a time below, with Back returning to the list. Google names "interactive media browsers" and "media titles displayed side by side with a playing video" as fits.
- **Supporting pane** (`SupportingPaneScaffold`): primary about two thirds. Compact: supporting content below or in a bottom sheet. Medium: 50/50. Expanded: 70/30. Media examples: "a streaming video and a list of related videos", "a scrolling list of similar titles, published reviews, or additional works by the same actors".
- **Feed**: `LazyVerticalGrid` with `GridCells.Adaptive(minSize = 180.dp)`, `maxLineSpan` for full-width items; "a music feed or movie and TV kiosk".
- Pane APIs live in `androidx.compose.material3.adaptive:adaptive-layout` and `adaptive-navigation`; `NavigationSuiteScaffold` in `material3-adaptive-navigation-suite` (already in the app).

## 3. Navigation

From [build adaptive navigation](https://developer.android.com/develop/ui/compose/layouts/adaptive/build-adaptive-navigation): bottom bar on compact; rail "alongside the app" on expanded, "easier to reach while holding the left and right sides of the device". `NavigationSuiteScaffold` picks a bar "if the width or height is compact or if the device is in tabletop posture", a rail otherwise. A drawer is an explicit override for expanded.

## 4. Adaptive app quality tiers

From [adaptive app quality](https://developer.android.com/docs/quality-guidelines/adaptive-app-quality) and its tier pages. These replaced the "large screen" guidelines.

- **Tier 3, adaptive ready:** fills the window, no letterboxing, no compatibility mode; state survives rotate / fold / resize ("media playback resumes where it left off"); fully functional in multi-window; keeps playing when unfocused (multi-resume); basic keyboard, mouse, trackpad and stylus. Test on sw ≥ 600 dp, Android 12+, including multi-window.
- **Tier 2, adaptive optimized:** layouts "determined by window size classes" with rails, grid scaling and multi-pane (`ListDetailPaneScaffold` named); bottom sheets, buttons and text fields "not full width on large screens"; images "not stretched or cropped"; 48 dp touch targets; keyboard navigation (Tab, arrows), **Space plays/pauses, Esc exits full-screen video and cancels Up Next timers**; context menus on right-click; hover states.
- **Tier 1, adaptive differentiated:** foldable postures, desktop, stylus, drag and drop.

Test devices Google names: Foldable 841×701 dp, 8" tablet 1024×640 dp, **10.5" tablet 1280×800 dp**, 13" Chromebook 1600×900 dp, Googlebook 160 ppi.

## 5. Foldables

From [learn about foldables](https://developer.android.com/develop/ui/compose/layouts/adaptive/foldables/learn-about-foldables) and [make your app fold-aware](https://developer.android.com/develop/ui/compose/layouts/adaptive/foldables/make-your-app-fold-aware): "split content into two areas when the device is half opened — top and bottom in tabletop posture"; for video, "placing the playback above the fold and positioning controls and supplementary content just following". Keep dialogs off the fold. API: `collectFoldingFeaturesAsState()` or `currentWindowAdaptiveInfoV2().windowPosture.isTabletop`; tabletop is `state == HALF_OPENED && orientation == HORIZONTAL`. Google's `MediaPlayerActivity` sample (platform-samples) does this with Media3.

This is exactly what Ephyra issue #51 asked for.

## 6. Peers

| App | Tablet behaviour (what could be verified) |
|---|---|
| Jellyfin official Android | A web wrapper ("the client is only a web wrapper", [jellyfin-android](https://github.com/jellyfin/jellyfin-android)); layout is the web client's, which is responsive but not native. |
| Findroid upstream | Same code as ours before this work. Open tablet issues: #1144 (gesture zones), #729 (ChromeOS), #1159 (Fire tablet download progress). |
| [JellyPlay](https://github.com/raulshma/jellyplay) | Claims "adaptive layouts across phones, tablets, foldables, and Android TV" and a tablet navigation-rail toggle; no breakpoints or screenshots published. |
| Streamyfin | React Native; README says nothing about tablets. Its App Store listing says "designed for iPad". |
| Wholphin (TV reference from tv-research) | Hero + rows, side drawer; its detail page (poster left, text right, rows below) is the layout the TV ADRs adopted and the obvious model for the tablet detail page. |
| Google's own media gallery | The [large-screens media gallery](https://developer.android.com/large-screens/gallery/media) is JavaScript-rendered and couldn't be read; its tags name feed, supporting pane, detail, playback and carousel patterns. |

Take-away: nobody in the Jellyfin space has a convincing native tablet client. The bar is not a competitor; it is Google's tier 2 checklist plus the Netflix/Disney-style detail page (hero on the left or top, metadata on the right, rows below) that users already know from TV and tablet streaming apps.
