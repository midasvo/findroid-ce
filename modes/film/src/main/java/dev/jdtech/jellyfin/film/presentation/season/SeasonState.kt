package dev.jdtech.jellyfin.film.presentation.season

import dev.jdtech.jellyfin.core.presentation.downloader.DownloadProgress
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidSeason
import java.util.UUID

data class SeasonState(
    val season: FindroidSeason? = null,
    val episodes: List<FindroidEpisode> = emptyList(),
    val episodeDownloadProgress: Map<UUID, DownloadProgress> = emptyMap(),
    val error: Exception? = null,
)
