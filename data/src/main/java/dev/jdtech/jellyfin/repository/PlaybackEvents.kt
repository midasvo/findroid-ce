package dev.jdtech.jellyfin.repository

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Tells screens when the player has finished reporting an item. That happens after the player has
 * closed, so later than the moment the screen underneath it resumes.
 */
class PlaybackEvents {
    private val _stopped =
        MutableSharedFlow<Unit>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val stopped: SharedFlow<Unit> = _stopped.asSharedFlow()

    fun onStopped() {
        _stopped.tryEmit(Unit)
    }
}
