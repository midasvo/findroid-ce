package dev.jdtech.jellyfin.settings.presentation.settings

import android.content.Intent

sealed interface SettingsEvent {
    data object NavigateToUsers : SettingsEvent

    data object NavigateToServers : SettingsEvent

    data object NavigateToAbout : SettingsEvent

    data class NavigateToSettings(val indexes: List<Int>) : SettingsEvent

    data class NavigateToSettingsFileEdit(val filePath: String) : SettingsEvent

    data class UpdateTheme(val theme: String) : SettingsEvent

    data class LaunchIntent(val intent: Intent) : SettingsEvent

    data object NavigateToSubtitleStyle : SettingsEvent

    // CE (#142)
    data object NavigateToHomeRows : SettingsEvent

    data object RestartActivity : SettingsEvent

    /** The Clear cache row did its work. */
    data object CacheCleared : SettingsEvent
}
