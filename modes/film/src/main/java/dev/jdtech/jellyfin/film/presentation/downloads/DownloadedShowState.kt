package dev.jdtech.jellyfin.film.presentation.downloads

import dev.jdtech.jellyfin.models.FindroidEpisode
import java.util.UUID

data class DownloadedSeason(
    val number: Int,
    val name: String?,
    val episodes: List<FindroidEpisode>,
)

data class DownloadedShowState(
    val seasons: List<DownloadedSeason> = emptyList(),
    val itemSizes: Map<UUID, Long> = emptyMap(),
    val isLoading: Boolean = true,
)
