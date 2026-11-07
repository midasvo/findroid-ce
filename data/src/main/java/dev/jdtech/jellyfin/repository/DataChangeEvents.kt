package dev.jdtech.jellyfin.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Counts changes to what the screens show that happen outside the screen itself: an item marked
 * played or favourite on a child screen, a finished or deleted download, a reported playback stop.
 * A screen that was loaded at an older [version] is out of date (#251), see [ReloadPolicy].
 */
class DataChangeEvents {
    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version.asStateFlow()

    /** True from the moment the player opens an item until its stop has been reported. */
    @Volatile
    var playbackPending: Boolean = false
        internal set

    fun onChanged() {
        _version.update { it + 1 }
    }
}
