package dev.jdtech.jellyfin.player.local.domain

import androidx.core.net.toUri
import androidx.media3.common.MimeTypes
import dev.jdtech.jellyfin.models.FindroidChapter
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidSource
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.models.FindroidSources
import dev.jdtech.jellyfin.player.core.domain.models.ExternalSubtitle
import dev.jdtech.jellyfin.player.core.domain.models.PlayerChapter
import dev.jdtech.jellyfin.player.core.domain.models.PlayerItem
import dev.jdtech.jellyfin.player.core.domain.models.TrickplayInfo
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.MediaStreamType
import timber.log.Timber

class PlaylistManager @Inject internal constructor(private val repository: JellyfinRepository) {
    private var startItem: FindroidItem? = null
    private var items: List<FindroidItem> = emptyList()
    private val playerItems: MutableList<PlayerItem> = mutableListOf()
    var currentItemIndex: Int = 0

    private var currentItemId: UUID? = null

    /**
     * Fetches the full episode list for the queue. Set when the first item could start playing
     * without it (an episode already knows its season); run by [loadQueue].
     */
    private var queueLoader: (suspend () -> List<FindroidItem>)? = null
    private val queueMutex = Mutex()

    private suspend fun fetchEpisodes(seriesId: UUID, seasonId: UUID): List<FindroidEpisode> =
        repository
            .getEpisodes(
                seriesId = seriesId,
                seasonId = seasonId,
                fields = listOf(ItemFields.CHAPTERS, ItemFields.TRICKPLAY),
            )
            .filter { !it.missing }

    /**
     * Resolves the item to start playing. For a movie or episode, the item and its playback
     * sources are fetched concurrently, and the episode list for the queue is not fetched at all:
     * call [loadQueue] after the player is prepared (the neighbour lookups also wait for it).
     */
    suspend fun getInitialItem(
        itemId: UUID,
        itemKind: BaseItemKind,
        mediaSourceId: String? = null,
        startFromBeginning: Boolean = false,
        shuffle: Boolean = false,
    ): PlayerItem? {
        Timber.d("Retrieving initial player item")

        queueLoader = null
        // Playback sources fetched while the item itself was still loading.
        var prefetchedSources: List<FindroidSource>? = null

        val initialItem =
            when (itemKind) {
                BaseItemKind.MOVIE -> {
                    val (movie, sources) =
                        coroutineScope {
                            val movie = async { repository.getMovie(itemId) }
                            val sources = async { repository.getPlaybackSources(itemId, mediaSourceId) }
                            movie.await() to sources.await()
                        }
                    prefetchedSources = sources

                    items = listOf(movie)
                    movie
                }
                BaseItemKind.SERIES -> {
                    if (shuffle) {
                        val allSeasons = repository.getSeasons(itemId)
                        val seasons = allSeasons.filter { it.indexNumber != 0 }.ifEmpty { allSeasons }
                        val episodes =
                            seasons
                                .flatMap { season -> fetchEpisodes(itemId, season.id) }
                                .shuffled()

                        if (episodes.isEmpty()) {
                            return null
                        }

                        val episode = episodes.first()
                        items = episodes
                        episode
                    } else {
                        val nextUpEpisode = repository.getNextUp(itemId).firstOrNull()

                        if (nextUpEpisode != null) {
                            // The next-up episode is enough to start; the season's list only fills
                            // the queue.
                            val seasonId = nextUpEpisode.seasonId
                            items = listOf(nextUpEpisode)
                            queueLoader = { fetchEpisodes(itemId, seasonId) }
                            nextUpEpisode
                        } else {
                            val seasons = repository.getSeasons(itemId)
                            if (seasons.isEmpty()) {
                                return null
                            }

                            val episodes = fetchEpisodes(itemId, seasons.first().id)
                            if (episodes.isEmpty()) {
                                return null
                            }

                            items = episodes
                            episodes.first()
                        }
                    }
                }
                BaseItemKind.SEASON -> {
                    val season = repository.getSeason(itemId)
                    val episodes = fetchEpisodes(season.seriesId, season.id)

                    if (episodes.isEmpty()) {
                        return null
                    }

                    val episode = episodes.first()

                    items = episodes
                    episode
                }
                BaseItemKind.EPISODE -> {
                    val (episode, sources) =
                        coroutineScope {
                            val episode = async { repository.getEpisode(itemId) }
                            val sources = async { repository.getPlaybackSources(itemId, mediaSourceId) }
                            episode.await() to sources.await()
                        }
                    prefetchedSources = sources

                    items = listOf(episode)
                    queueLoader = { fetchEpisodes(episode.seriesId, episode.seasonId) }
                    episode
                }
                else -> null
            }

        if (initialItem == null) {
            return null
        }

        startItem = initialItem
        currentItemId = initialItem.id

        currentItemIndex = items.indexOfFirst { it.id == initialItem.id }

        val playbackPosition =
            if (shuffle || startFromBeginning) 0L else initialItem.playbackPositionTicks.div(10000)
        val playerItem = initialItem.toPlayerItem(mediaSourceId, playbackPosition, prefetchedSources)
        playerItems.add(playerItem)

        return playerItem
    }

    /**
     * Fetches the episode list that [getInitialItem] left out, so the queue (previous and next
     * episodes) works. Safe to call any number of times, also concurrently: it loads once. A
     * failure leaves the queue as just the playing item, and the next call tries again.
     */
    suspend fun loadQueue() {
        queueMutex.withLock {
            val loader = queueLoader ?: return
            try {
                val episodes = loader()
                items = episodes
                currentItemIndex = items.indexOfFirst { it.id == currentItemId }
                queueLoader = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e("Failed to retrieve the episode queue: $e")
            }
        }
    }

    suspend fun getPreviousPlayerItem(): PlayerItem? {
        Timber.d("Retrieving previous player item")

        loadQueue()
        val itemIndex = currentItemIndex - 1
        val playerItem =
            when (startItem) {
                is FindroidMovie -> null
                is FindroidEpisode -> {
                    if (currentItemIndex == 0) {
                        null
                    } else {
                        val item = items[itemIndex]
                        if (playerItems.firstOrNull { it.itemId == item.id } == null) {
                            try {
                                item.toPlayerItem(null, 0L)
                            } catch (e: Exception) {
                                Timber.e("Failed to retrieve previous player item: $e")
                                null
                            }
                        } else {
                            null
                        }
                    }
                }
                else -> null
            }

        if (playerItem != null) {
            playerItems.add(playerItem)
        }

        return playerItem
    }

    suspend fun getNextPlayerItem(): PlayerItem? {
        Timber.d("Retrieving next player item")

        loadQueue()
        val itemIndex = currentItemIndex + 1
        val playerItem =
            when (startItem) {
                is FindroidMovie -> null
                is FindroidEpisode -> {
                    if (currentItemIndex == items.lastIndex) {
                        appendNextSeason()
                    }

                    if (currentItemIndex == items.lastIndex) {
                        null
                    } else {
                        val item = items[itemIndex]
                        if (playerItems.firstOrNull { it.itemId == item.id } == null) {
                            try {
                                item.toPlayerItem(null, 0L)
                            } catch (e: Exception) {
                                Timber.e("Failed to retrieve next player item: $e")
                                null
                            }
                        } else {
                            null
                        }
                    }
                }
                else -> null
            }

        if (playerItem != null) {
            playerItems.add(playerItem)
        }

        return playerItem
    }

    /**
     * Queue the episodes of the next season, so playback continues into it. Seasons are fetched
     * only when the current one runs out. Specials (season 0) do not roll over into season 1.
     */
    private suspend fun appendNextSeason() {
        val queuedEpisodes = items.filterIsInstance<FindroidEpisode>()
        val seriesId = queuedEpisodes.lastOrNull()?.seriesId ?: return

        try {
            val seasons = repository.getSeasons(seriesId).sortedBy { it.indexNumber }
            // The highest queued season, not the last episode's: the server can end a season's
            // list with a special that airs after it, and that episode belongs to season 0.
            val queuedSeasonIds = queuedEpisodes.map { it.seasonId }
            val seasonNumber =
                seasons.lastOrNull { it.id in queuedSeasonIds }?.indexNumber ?: return
            if (seasonNumber == 0) {
                return
            }

            val nextEpisodes =
                seasons
                    .filter { it.indexNumber > seasonNumber }
                    .firstNotNullOfOrNull { season ->
                        repository
                            .getEpisodes(
                                seriesId = seriesId,
                                seasonId = season.id,
                                fields = listOf(ItemFields.CHAPTERS, ItemFields.TRICKPLAY),
                            )
                            .filter { !it.missing }
                            .ifEmpty { null }
                    } ?: return

            items = items + nextEpisodes
        } catch (e: Exception) {
            Timber.e("Failed to retrieve next season: $e")
        }
    }

    fun setCurrentMediaItemIndex(itemId: UUID) {
        currentItemId = itemId
        currentItemIndex = items.indexOfFirst { it.id == itemId }
    }

    /** CE (#131): the queued items after the current one, for the TV player's queue row. */
    fun upcomingItems(): List<FindroidItem> =
        if (currentItemIndex < 0) emptyList() else items.drop(currentItemIndex + 1)

    /**
     * CE (#131): start over at [itemId], a queued item, from its beginning. Forgets which items the
     * player already has, as the caller replaces the player's playlist with the returned item.
     */
    suspend fun jumpTo(itemId: UUID): PlayerItem? {
        val index = items.indexOfFirst { it.id == itemId }
        if (index < 0) return null
        val playerItem = items[index].toPlayerItem(null, 0L)
        playerItems.clear()
        playerItems.add(playerItem)
        currentItemIndex = index
        return playerItem
    }

    /**
     * Re-resolve an already-queued item's media sources — used when the user
     * changes the max streaming bitrate mid-playback, which invalidates every
     * queued stream URL. The cached entry is replaced (not just added) so the
     * duplicate check in [getPreviousPlayerItem]/[getNextPlayerItem] keeps
     * treating the item as already queued.
     */
    suspend fun recreatePlayerItem(playerItem: PlayerItem): PlayerItem {
        val item = items.firstOrNull { it.id == playerItem.itemId } ?: return playerItem
        val recreated = item.toPlayerItem(playerItem.mediaSourceId, playerItem.playbackPosition)
        playerItems.removeAll { it.itemId == playerItem.itemId }
        playerItems.add(recreated)
        return recreated
    }

    private suspend fun FindroidItem.toPlayerItem(
        mediaSourceId: String?,
        playbackPosition: Long,
        prefetchedSources: List<FindroidSource>? = null,
    ): PlayerItem {
        Timber.d("Converting FindroidItem ${this.id} to PlayerItem")

        // A downloaded copy shares its id with the server version, so it wins for that version too.
        val mediaSources =
            (prefetchedSources ?: repository.getPlaybackSources(id, mediaSourceId)).let { sources ->
                sources.filter { it.id == mediaSourceId }.ifEmpty { sources }
            }
        val mediaSource =
            mediaSources.firstOrNull { it.type == FindroidSourceType.LOCAL } ?: mediaSources[0]
        val externalSubtitles =
            mediaSource.mediaStreams
                .filter { mediaStream ->
                    // isExternal reflects the *server's* original file layout, which only
                    // matters for a REMOTE source riding along in the same stream ExoPlayer
                    // is already playing. A LOCAL (downloaded) source only ever has a
                    // mediastreams row for a subtitle that was saved as its own sidecar file
                    // — downloadExternalMediaStreams never writes a row for a track that
                    // stays muxed inside the downloaded video — so every row on a LOCAL
                    // source is a real external file on disk, regardless of isExternal.
                    (mediaSource.type == FindroidSourceType.LOCAL || mediaStream.isExternal) &&
                        mediaStream.type == MediaStreamType.SUBTITLE &&
                        !mediaStream.path.isNullOrBlank()
                }
                .map { mediaStream ->
                    ExternalSubtitle(
                        mediaStream.title,
                        mediaStream.language,
                        mediaStream.path!!.toUri(),
                        when (mediaStream.codec) {
                            "subrip" -> MimeTypes.APPLICATION_SUBRIP
                            "webvtt" -> MimeTypes.TEXT_VTT
                            "ass" -> MimeTypes.TEXT_SSA
                            // Image-based subtitle sidecars. media3 ships native parsers
                            // for these, so advertising the correct MIME lets the renderer
                            // actually draw the bitmaps instead of dropping the track.
                            "pgssub" -> MimeTypes.APPLICATION_PGS
                            "dvdsub", "idx" -> MimeTypes.APPLICATION_VOBSUB
                            "dvbsub" -> MimeTypes.APPLICATION_DVBSUBS
                            else -> MimeTypes.TEXT_UNKNOWN
                        },
                    )
                }
        val trickplayInfo =
            when (this) {
                is FindroidSources -> {
                    this.trickplayInfo?.get(mediaSource.id)?.let {
                        TrickplayInfo(
                            width = it.width,
                            height = it.height,
                            tileWidth = it.tileWidth,
                            tileHeight = it.tileHeight,
                            thumbnailCount = it.thumbnailCount,
                            interval = it.interval,
                            bandwidth = it.bandwidth,
                        )
                    }
                }
                else -> null
            }
        return PlayerItem(
            name = name,
            itemId = id,
            mediaSourceId = mediaSource.id,
            mediaSourceUri = mediaSource.path,
            isTranscoded = mediaSource.transcoded,
            playbackPosition = playbackPosition,
            parentIndexNumber = if (this is FindroidEpisode) parentIndexNumber else null,
            indexNumber = if (this is FindroidEpisode) indexNumber else null,
            indexNumberEnd = if (this is FindroidEpisode) indexNumberEnd else null,
            externalSubtitles = externalSubtitles,
            chapters = chapters.toPlayerChapters(),
            trickplayInfo = trickplayInfo,
            seriesName = if (this is FindroidEpisode) seriesName else null,
            seriesId = if (this is FindroidEpisode) seriesId else null,
            artworkUri = images.primary ?: images.showPrimary,
        )
    }

    private fun List<FindroidChapter>.toPlayerChapters(): List<PlayerChapter> {
        return this.map { chapter ->
            PlayerChapter(
                startPosition = chapter.startPosition,
                name = chapter.name,
                imageTag = chapter.imageTag,
            )
        }
    }
}
