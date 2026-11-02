package dev.jdtech.jellyfin.core.presentation.downloader

import dev.jdtech.jellyfin.models.FindroidEpisode
import java.time.ZoneId
import java.util.UUID

/** What is known about a completed download of an episode. */
internal data class SmartDownload(
    /** Epoch ms at which the download finished; null for downloads from before it was stored. */
    val downloadedAt: Long?,
    /** True when Smart Downloads queued it, the only kind it may delete. */
    val smartQueued: Boolean,
)

internal data class SmartDownloadPlan(
    val toQueue: List<FindroidEpisode>,
    val toDelete: List<FindroidEpisode>,
)

/**
 * Decides what Smart Downloads should do for one series. Pure, so the removal rules can be tested.
 *
 * @param episodes the whole series, in any order.
 * @param downloads the completed downloads of the series by episode id.
 * @param inFlightIds episodes that are queued or downloading.
 * @param keepReady how many unwatched episodes to keep downloaded or queued.
 * @param since epoch ms at which Smart Downloads was turned on.
 * @param finishedRecently whether the check was triggered by an episode being finished.
 * @param playingItemId the episode the player has open, if any.
 */
internal fun planSmartDownloads(
    episodes: List<FindroidEpisode>,
    downloads: Map<UUID, SmartDownload>,
    inFlightIds: Set<UUID>,
    keepReady: Int,
    since: Long,
    finishedRecently: Boolean,
    playingItemId: UUID?,
): SmartDownloadPlan {
    // Specials do not take part in the aired order, so neither deleting nor topping up looks at them.
    val ordered =
        episodes
            .filter { it.parentIndexNumber != 0 }
            .sortedWith(compareBy({ it.parentIndexNumber }, { it.indexNumber }))

    // Servers mark an episode as played near its end, while the file is still open.
    fun played(episode: FindroidEpisode) = episode.played && episode.id != playingItemId

    // Watched means the server saw it played after it was downloaded and after Smart Downloads was
    // turned on. Anything with an unknown date stays: an episode kept for a rewatch must survive.
    fun watched(episode: FindroidEpisode): Boolean {
        val download = downloads[episode.id] ?: return false
        val downloadedAt = download.downloadedAt ?: return false
        val lastPlayedAt =
            episode.lastPlayedDate?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli() ?: return false
        return played(episode) && lastPlayedAt > downloadedAt && lastPlayedAt > since
    }

    val watchedEpisodes = ordered.filter { watched(it) }
    val toDelete = watchedEpisodes.filter { downloads.getValue(it.id).smartQueued }

    // Nothing is queued for a series nobody is watching.
    val furthest = ordered.indexOfLast { played(it) }
    if (furthest < 0 || (watchedEpisodes.isEmpty() && !finishedRecently)) {
        return SmartDownloadPlan(toQueue = emptyList(), toDelete = toDelete)
    }

    var held = 0
    val queueable = mutableListOf<FindroidEpisode>()
    for (episode in ordered.drop(furthest + 1)) {
        if (played(episode)) continue
        if (episode.id in downloads || episode.id in inFlightIds) {
            held++
        } else if (!episode.missing && episode.canDownload) {
            queueable.add(episode)
        }
    }
    return SmartDownloadPlan(
        toQueue = queueable.take((keepReady - held).coerceAtLeast(0)),
        toDelete = toDelete,
    )
}
