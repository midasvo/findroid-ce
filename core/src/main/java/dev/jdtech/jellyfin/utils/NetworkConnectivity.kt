package dev.jdtech.jellyfin.utils

import dev.jdtech.jellyfin.settings.domain.AppPreferences

/** Reports whether the device currently has network connectivity. */
interface NetworkConnectivity {
    /** True when there is an active network capable of reaching the internet. */
    fun isOnline(): Boolean

    /**
     * True when the active network is metered (i.e. lacks
     * [android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED]).
     * Returns false when network capabilities cannot be determined.
     */
    fun isMetered(): Boolean

    /**
     * True when the active network is roaming (i.e. lacks
     * [android.net.NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING]).
     * Requires API 28+ which matches minSdk.
     * Returns false when network capabilities cannot be determined.
     */
    fun isRoaming(): Boolean
}

/**
 * The effective offline mode: the user's manual offlineMode preference OR the absence of network
 * connectivity.
 *
 * Used by both the repository selection (RepositoryModule) and the UI (MainViewModel) so the data
 * source that gets used and what the UI shows always agree. The manual preference is never
 * written by connectivity detection -- it stays the user's explicit choice, so the app returns to
 * online automatically once the network is back.
 */
fun isOfflineModeActive(
    appPreferences: AppPreferences,
    networkConnectivity: NetworkConnectivity,
): Boolean {
    return appPreferences.getValue(appPreferences.offlineMode) || !networkConnectivity.isOnline()
}
