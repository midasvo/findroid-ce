package dev.jdtech.jellyfin.film.presentation.show

sealed interface ShowEvent {
    data class DownloadResult(
        val started: Int,
        val skipped: Int,
        val failed: Int,
    ) : ShowEvent
}
