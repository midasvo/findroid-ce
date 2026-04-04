package dev.jdtech.jellyfin.film.presentation.show

import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItemPerson
import dev.jdtech.jellyfin.models.FindroidSeason
import dev.jdtech.jellyfin.models.FindroidShow
import java.util.UUID

data class SeasonDownloadInfo(
    val downloadedCount: Int = 0,
    val totalCount: Int = 0,
)

data class ShowState(
    val show: FindroidShow? = null,
    val nextUp: FindroidEpisode? = null,
    val seasons: List<FindroidSeason> = emptyList(),
    val seasonDownloadInfo: Map<UUID, SeasonDownloadInfo> = emptyMap(),
    val actors: List<FindroidItemPerson> = emptyList(),
    val director: FindroidItemPerson? = null,
    val writers: List<FindroidItemPerson> = emptyList(),
    val hasDownloads: Boolean = false,
    val error: Exception? = null,
)
