package dev.jdtech.jellyfin.models

import java.util.UUID

/** CE (#252): stored and downloaded episode counts of one season, from the local database. */
data class SeasonEpisodeCounts(val seasonId: UUID, val total: Int, val downloaded: Int)
