package dev.jdtech.jellyfin.player.local.presentation

/** CE (#126): the player's position and duration as state, see [PlayerViewModel.progress]. */
data class PlaybackProgress(val positionMs: Long, val durationMs: Long, val isPlaying: Boolean)

internal const val PROGRESS_TICK_MS = 300L
