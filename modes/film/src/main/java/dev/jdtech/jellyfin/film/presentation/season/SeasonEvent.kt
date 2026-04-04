package dev.jdtech.jellyfin.film.presentation.season

sealed interface SeasonEvent {
    data class DownloadResult(
        val started: Int,
        val skipped: Int,
        val failed: Int,
    ) : SeasonEvent
}
