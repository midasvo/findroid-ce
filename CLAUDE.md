# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Debug APK (most common during development)
./gradlew :app:phone:assembleLibreDebug

# Release APK
./gradlew :app:phone:assembleLibreRelease

# Compile only (faster feedback, no APK)
./gradlew :app:phone:compileLibreDebugKotlin
./gradlew :modes:film:compileDebugSources

# Full build (all modules)
./gradlew build

# Clean
./gradlew clean
```

```bash
# Formatting check (runs in CI)
./gradlew ktfmtCheck

# Format the tree
./gradlew ktfmtFormat

# Unit tests (coverage is limited to a few modules)
./gradlew testDebugUnitTest
```

## Module Structure

```
app/phone        — Phone UI: screens, navigation, Compose layouts
app/tv           — TV UI: separate screen implementations for Android TV
core             — Shared DI modules, DownloaderViewModel, MainViewModel, common Composables
data             — JellyfinRepository interface + Room database + online/offline implementations
modes/film       — Feature ViewModels and State/Action classes for home, library, search, episode, movie, show, season
player/local     — ExoPlayer + mpv PlayerViewModel and playback logic
setup            — Server discovery and login onboarding flow
settings         — AppPreferences (DataStore) and settings screen
```

The `app/phone` module depends on all other modules. ViewModels live in `modes/film`, `setup`, `settings`, and `player/local` — NOT in `app/phone`. Screens in `app/phone` consume state from those ViewModels.

## Architecture

**MVI-style MVVM with Unidirectional Data Flow:**

Each screen follows this pattern:
- `FooState` — immutable data class holding all UI state
- `FooAction` — sealed interface of user intents
- `FooViewModel` — `@HiltViewModel`, exposes `StateFlow<FooState>`, handles `onAction()`
- `FooScreen` — stateful Composable that wires ViewModel to layout
- `FooScreenLayout` — stateless Composable that takes state + callbacks (used for previews)

Events that fire once (navigation, toasts) use a `Channel<FooEvent>` exposed as `Flow`, observed via `ObserveAsEvents()`.

**Dependency Injection:** Hilt throughout. DI modules are in `core/src/main/java/dev/jdtech/jellyfin/di/`.

**Navigation:** Navigation 3 with `@Serializable` route objects. `NavigationRoot.kt` in `app/phone` is the single source of truth for the nav graph.

**Offline mode:** A `LocalOfflineMode` CompositionLocal toggles the app between online (`JellyfinRepositoryImpl`) and offline (`OfflineJellyfinRepositoryImpl`) data sources. The repository is switched at the DI level.

## Key Patterns

**Downloading:** `Downloader` interface (in `core`) is injected into ViewModels that need it. `DownloaderViewModel` (also in `core`) handles progress polling via `Handler` and exposes `DownloaderState`. Storage index is read from `AppPreferences.downloadStorageIndex`, so downloads don't ask for a storage location. Each download does ask for a quality, through `DownloadQualityDialog`.

**ItemButtonsBar:** The shared download/play/favorite buttons component. Accepts `canDownload` and `isDownloaded` overrides so aggregate items (seasons, shows) can pass computed values instead of deriving from the item directly.

**Build variants:** Only one flavor (`libre`). Build types are `debug`, `release`, `staging`. Always use `Libre` variants (e.g. `assembleLibreDebug`, `compileLibreDebugKotlin`).

**App ID:** `nl.midasvo.findroid.ce` (this is a fork — do not revert to the upstream `dev.jdtech.jellyfin` ID).

**AppVariant:** An `AppVariant` enum (`PHONE`, `TV`) is provided by Hilt modules in `app/phone` and `app/tv`, allowing shared ViewModels (e.g. `SettingsViewModel`) to tailor behavior per variant instead of inspecting device hardware.

## Translations & Localization

All user-visible strings and plurals that CE adds must be translated into all 36 supported languages.
- Upstream translations are managed on Weblate in `values-<lang>/strings.xml`.
- CE-only translations live in separate `<module>/src/main/res/values-<lang>/strings_ce.xml` files per module to prevent merge conflicts on upstream syncs.
- When adding or changing a user-visible string:
  1. Add or edit the English string in default `<module>/src/main/res/values/strings.xml`.
  2. Add or update translations across all 36 languages in `<module>/src/main/res/values-<lang>/strings_ce.xml`.
  3. Run `python3 .claude/skills/translate-strings/scripts/check-translations.py` to verify all keys, format placeholders, and plural quantities.
  CI will fail on any PR that adds or changes a CE-only string without complete translations.
