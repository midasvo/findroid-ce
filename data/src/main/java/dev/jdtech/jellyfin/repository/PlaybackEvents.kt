package dev.jdtech.jellyfin.repository

import java.util.UUID
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tells screens when the player has finished reporting an item. That happens after the player has
 * closed, so later than the moment the screen underneath it resumes.
 */
class PlaybackEvents(private val dataChanges: DataChangeEvents = DataChangeEvents()) {
    private val _stopped =
        MutableSharedFlow<UUID>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    /** Emits the id of the item whose playback stop has been reported. */
    val stopped: SharedFlow<UUID> = _stopped.asSharedFlow()

    private val _playingItemId = MutableStateFlow<UUID?>(null)
    /** The item the local player has open right now, or null when no player is active. */
    val playingItemId: StateFlow<UUID?> = _playingItemId.asStateFlow()

    fun onStopped(itemId: UUID) {
        dataChanges.playbackPending = false
        dataChanges.onChanged()
        _stopped.tryEmit(itemId)
    }

    fun onPlaying(itemId: UUID?) {
        if (itemId != null) dataChanges.playbackPending = true
        _playingItemId.value = itemId
    }
}
