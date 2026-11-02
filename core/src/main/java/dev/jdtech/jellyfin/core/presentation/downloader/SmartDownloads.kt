package dev.jdtech.jellyfin.core.presentation.downloader

import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidSourceDto
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.models.toFindroidSource
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.repository.PlaybackEvents
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.utils.Downloader
import dev.jdtech.jellyfin.utils.NetworkConnectivity
import dev.jdtech.jellyfin.utils.isOfflineModeActive
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jellyfin.sdk.model.api.ItemFields
import timber.log.Timber

/**
 * Smart Downloads: keeps a few unwatched episodes of a series you are watching downloaded, and
 * removes the ones Smart Downloads queued itself once you have watched them. What to queue and
 * remove is decided by [planSmartDownloads]; this class gathers its input and carries it out.
 *
 * It runs after playback stops and after the sync that pushes offline progress, so episodes
 * watched offline or on another device are covered too.
 */
@Singleton
class SmartDownloads
@Inject
constructor(
    private val downloadQueue: DownloadQueue,
    private val downloader: Downloader,
    private val appPreferences: AppPreferences,
    private val database: ServerDatabaseDao,
    private val repositoryProvider: Provider<JellyfinRepository>,
    private val networkConnectivity: NetworkConnectivity,
    private val playbackEvents: PlaybackEvents,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var started = false

    /** Starts reacting to playback stopping. Safe to call more than once. */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            playbackEvents.stopped.collect { itemId ->
                try {
                    onPlaybackStopped(itemId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Smart Downloads: failed to handle the stop of $itemId")
                }
            }
        }
    }

    private suspend fun onPlaybackStopped(itemId: UUID) {
        if (!isActive()) return
        val episode = repositoryProvider.get().getItem(itemId) as? FindroidEpisode ?: return
        check(episode.seriesId, finishedRecently = episode.played)
    }

    /** Runs [check] for every series that has a completed download. */
    suspend fun checkAll() {
        if (!isActive()) return
        for (seriesId in database.getSeriesIdsWithCompletedDownloads()) {
            check(seriesId, finishedRecently = false)
        }
    }

    /**
     * Tops up [seriesId] and removes what has been watched.
     *
     * @param finishedRecently whether an episode of the series was just finished, which is what
     *   makes a series count as being watched.
     */
    suspend fun check(seriesId: UUID, finishedRecently: Boolean) {
        if (!isActive()) return
        mutex.withLock {
            try {
                run(seriesId, finishedRecently)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Smart Downloads: check failed for series $seriesId")
            }
        }
    }

    /** Smart Downloads needs the server to say what is watched, so it never runs offline. */
    private fun isActive(): Boolean =
        appPreferences.getValue(appPreferences.smartDownloads) &&
            !isOfflineModeActive(appPreferences, networkConnectivity)

    private suspend fun run(seriesId: UUID, finishedRecently: Boolean) {
        // Smart Downloads that was already on before the moment was recorded: only what is watched
        // from now on may be removed.
        var since = appPreferences.getValue(appPreferences.smartDownloadsSince)
        if (since == 0L) {
            since = System.currentTimeMillis()
            appPreferences.setValue(appPreferences.smartDownloadsSince, since)
        }

        val repository = repositoryProvider.get()
        val userId = repository.getUserId()
        val episodes =
            repository.getSeasons(seriesId).flatMap { season ->
                // Without CanDownload every episode reads as not downloadable.
                repository.getEpisodes(seriesId, season.id, fields = listOf(ItemFields.CAN_DOWNLOAD))
            }

        // The server decides what counts as watched, so wait until our own progress has reached
        // it. The sync that pushes it runs a check afterwards.
        if (episodes.any { database.getUserDataToBeSynced(userId, it.id) != null }) {
            Timber.d("Smart Downloads: unsynced progress for series $seriesId, waiting")
            return
        }

        val sourcesByEpisode =
            episodes.associate { episode ->
                episode.id to
                    database.getSources(episode.id).filter { it.type == FindroidSourceType.LOCAL }
            }
        val completed =
            sourcesByEpisode
                .mapValues { (_, sources) -> sources.filter { !it.path.endsWith(".download") } }
                .filterValues { it.isNotEmpty() }
        val downloads =
            completed.mapValues { (_, sources) ->
                SmartDownload(
                    downloadedAt = sources.first().downloadedAt,
                    smartQueued = sources.first().smartQueued,
                )
            }
        val inFlightIds =
            downloadQueue.entries.value
                .filter {
                    it.state !is DownloadQueue.EntryState.Completed &&
                        it.state !is DownloadQueue.EntryState.Failed
                }
                .map { it.id }
                .toSet()

        val plan =
            planSmartDownloads(
                episodes = episodes,
                downloads = downloads,
                inFlightIds = inFlightIds,
                keepReady =
                    appPreferences.getValue(appPreferences.smartDownloadsKeepReady).coerceAtLeast(1),
                since = since,
                finishedRecently = finishedRecently,
                playingItemId = playbackEvents.playingItemId.value,
            )

        Timber.d(
            "Smart Downloads: series $seriesId, queue ${plan.toQueue.map { it.describe() }}, remove ${plan.toDelete.map { it.describe() }}"
        )

        for (episode in plan.toDelete) {
            // The player may have opened it since the plan was made.
            if (playbackEvents.playingItemId.value == episode.id) continue
            val source = completed[episode.id]?.firstOrNull() ?: continue
            Timber.i("Smart Downloads: removing watched ${episode.describe()}")
            downloader.deleteItem(episode, source.toFindroidSource(database))
        }

        if (plan.toQueue.isEmpty()) return
        val maxBitrate = qualityOf(sourcesByEpisode.values.flatten())
        for (episode in plan.toQueue) {
            Timber.i("Smart Downloads: queueing ${episode.describe()}")
            downloadQueue.enqueue(episode, maxBitrate, smartQueued = true)
        }
    }

    /**
     * The quality of the most recent download of the series, or of any source of it. Null, which
     * means original quality, when there is none: there is no default download quality to use.
     */
    private fun qualityOf(sources: List<FindroidSourceDto>): Int? {
        val latest =
            sources.filter { it.downloadedAt != null }.maxByOrNull { it.downloadedAt ?: 0L }
                ?: sources.firstOrNull()
        return latest?.downloadMaxBitrate
    }

    private fun FindroidEpisode.describe() =
        "$seriesName S%02dE%02d".format(parentIndexNumber, indexNumber)
}
