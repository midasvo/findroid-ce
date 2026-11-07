package dev.jdtech.jellyfin.film.presentation.show

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.core.presentation.downloader.DownloadQueue
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItemPerson
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.models.SeasonEpisodeCounts
import dev.jdtech.jellyfin.models.isDownloaded
import dev.jdtech.jellyfin.models.mergeDuplicateRoles
import dev.jdtech.jellyfin.repository.DataChangeEvents
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.repository.ReloadPolicy
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.utils.Downloader
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.PersonKind
import timber.log.Timber

@HiltViewModel
class ShowViewModel
@Inject
constructor(
    private val repository: JellyfinRepository,
    private val appPreferences: AppPreferences,
    private val downloader: Downloader,
    private val downloadQueue: DownloadQueue,
    private val database: ServerDatabaseDao,
    dataChanges: DataChangeEvents = DataChangeEvents(),
) : ViewModel() {
    private val _state = MutableStateFlow(ShowState())
    val state = _state.asStateFlow()

    private val eventsChannel = Channel<ShowEvent>()
    val events = eventsChannel.receiveAsFlow()

    private lateinit var showId: UUID
    private var downloadsOnly: Boolean = false
    private val reloadPolicy = ReloadPolicy(dataChanges)
    private var loadJob: Job? = null

    /** Whether coming back to the page needs a load (#251), see [ReloadPolicy]. */
    fun shouldReload(
        showId: UUID,
        downloadsOnly: Boolean = this.downloadsOnly,
        stopEventFollows: Boolean = false,
    ): Boolean =
        !::showId.isInitialized ||
            this.showId != showId ||
            this.downloadsOnly != downloadsOnly ||
            reloadPolicy.shouldReload(stopEventFollows)

    /**
     * Loads the page in two steps (#252): the show, its seasons, next up and cast first, all
     * requested at once; then the per-season download counts. Those come from Room plus a single
     * request for the series' episodes, not one request per season.
     */
    fun loadShow(showId: UUID, downloadsOnly: Boolean = this.downloadsOnly) {
        this.showId = showId
        this.downloadsOnly = downloadsOnly
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val token = reloadPolicy.begin()
            _state.emit(_state.value.copy(error = null))
            try {
                // coroutineScope { } is required: without it a failure inside an async { }
                // child goes straight to viewModelScope and crashes the app, past this
                // try/catch (see HomeViewModel.loadData).
                coroutineScope {
                    val showAsync = async { repository.getShow(showId) }
                    val nextUpAsync = async { if (downloadsOnly) null else getNextUp(showId) }
                    val seasonsAsync =
                        async { repository.getSeasons(showId, offline = downloadsOnly) }
                    // The download counts are extras: when they fail, the page still shows.
                    val localCountsAsync = async { optional { getLocalEpisodeCounts(showId) } }
                    val serverTotalsAsync =
                        async {
                            if (downloadsOnly) null else optional { getServerEpisodeTotals(showId) }
                        }

                    val show = showAsync.await()
                    val displayCastAndCrew =
                        appPreferences.getValue(appPreferences.displayCastAndCrew)
                    val castAndCrewAsync =
                        async(Dispatchers.Default) {
                            if (displayCastAndCrew) castAndCrew(show) else CastAndCrew()
                        }
                    val nextUp = nextUpAsync.await()
                    val seasons = seasonsAsync.await()
                    val castAndCrew = castAndCrewAsync.await()
                    // Room answers long before the server does, so whether anything is
                    // downloaded is right in the first emit and the buttons don't flip later.
                    val localCounts = localCountsAsync.await()
                    _state.emit(
                        _state.value.copy(
                            show = show,
                            nextUp = nextUp,
                            seasons = seasons,
                            actors = castAndCrew.actors,
                            director = castAndCrew.director,
                            writers = castAndCrew.writers,
                            hasDownloads =
                                localCounts?.let { counts ->
                                    seasons.any { (counts[it.id]?.downloaded ?: 0) > 0 }
                                } ?: _state.value.hasDownloads,
                        )
                    )

                    if (localCounts == null) return@coroutineScope
                    // Offline, the stored episodes are all there is.
                    val totals =
                        if (downloadsOnly) localCounts.mapValues { it.value.total }
                        else serverTotalsAsync.await() ?: return@coroutineScope
                    _state.emit(
                        _state.value.copy(
                            seasonDownloadInfo =
                                seasons.associate { season ->
                                    val total = totals[season.id] ?: 0
                                    val downloaded = localCounts[season.id]?.downloaded ?: 0
                                    season.id to
                                        SeasonDownloadInfo(
                                            downloadedCount = downloaded.coerceAtMost(total),
                                            totalCount = total,
                                        )
                                }
                        )
                    )
                }
                reloadPolicy.complete(token)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.emit(_state.value.copy(error = e))
            }
        }
    }

    /** Stored and downloaded episodes per season, from Room only. */
    private suspend fun getLocalEpisodeCounts(showId: UUID): Map<UUID, SeasonEpisodeCounts> =
        withContext(Dispatchers.IO) {
            database.getSeasonEpisodeCounts(showId).associateBy { it.seasonId }
        }

    /** Episodes per season on the server, from one request for the whole series. */
    private suspend fun getServerEpisodeTotals(showId: UUID): Map<UUID, Int> =
        repository
            .getEpisodes(seriesId = showId, seasonId = null)
            .groupingBy { it.seasonId }
            .eachCount()

    private suspend fun <T> optional(block: suspend () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to load the show's download counts")
            null
        }

    /**
     * CE (#141): look up the series' theme song for the TV show page, only when theme music is
     * turned on. Separate from [loadShow] so the page never waits for it, and the phone never asks.
     */
    fun loadThemeSong(showId: UUID) {
        if (!appPreferences.getValue(appPreferences.tvThemeMusic)) return
        viewModelScope.launch {
            val url = repository.getThemeSongUrl(showId)
            _state.emit(_state.value.copy(themeSongUrl = url))
        }
    }

    fun downloadSeasons(seasonIds: Set<UUID>, maxBitrate: Int? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            // Episodes already in flight count as skipped so the toast count
            // matches what actually got added to the queue.
            val liveQueuedIds =
                downloadQueue.entries.value
                    .filter {
                        it.state is DownloadQueue.EntryState.Pending ||
                            it.state is DownloadQueue.EntryState.Downloading ||
                            it.state is DownloadQueue.EntryState.Paused
                    }
                    .map { it.id }
                    .toSet()
            val toQueue = mutableListOf<FindroidEpisode>()
            var skipped = 0
            for (seasonId in seasonIds) {
                val episodes = repository.getEpisodes(seriesId = showId, seasonId = seasonId)
                for (episode in episodes) {
                    if (episode.isDownloaded() || episode.id in liveQueuedIds) skipped++
                    else toQueue.add(episode)
                }
            }
            downloadQueue.enqueueAll(toQueue, maxBitrate)
            eventsChannel.send(ShowEvent.DownloadResult(toQueue.size, skipped, 0))
            loadShow(showId)
        }
    }

    fun deleteShowDownloads() {
        viewModelScope.launch(Dispatchers.IO) {
            for (season in _state.value.seasons) {
                val episodes =
                    repository.getEpisodes(
                        seriesId = showId,
                        seasonId = season.id,
                        offline = true,
                    )
                for (episode in episodes) {
                    val localSource =
                        episode.sources.firstOrNull { it.type == FindroidSourceType.LOCAL }
                    if (localSource != null) {
                        downloader.deleteItem(item = episode, source = localSource)
                    }
                }
            }
            loadShow(showId)
        }
    }

    private suspend fun getNextUp(showId: UUID): FindroidEpisode? {
        val nextUpItems = repository.getNextUp(showId)
        return nextUpItems.getOrNull(0)
    }

    private class CastAndCrew(
        val actors: List<FindroidItemPerson> = emptyList(),
        val director: FindroidItemPerson? = null,
        val writers: List<FindroidItemPerson> = emptyList(),
    )

    private fun castAndCrew(item: FindroidShow): CastAndCrew {
        val people = item.people.mergeDuplicateRoles()
        return CastAndCrew(
            actors = people.filter { it.type == PersonKind.ACTOR },
            director = people.firstOrNull { it.type == PersonKind.DIRECTOR },
            writers = people.filter { it.type == PersonKind.WRITER },
        )
    }

    /**
     * Marks the show played or unplayed and updates the page in place (#252) instead of loading it
     * again. Next up and the seasons (their played marks and unplayed counts) change with it, so
     * only those two are fetched again, together.
     */
    private fun setPlayed(played: Boolean) {
        viewModelScope.launch {
            try {
                if (played) repository.markAsPlayed(showId) else repository.markAsUnplayed(showId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, if (played) "Failed to mark as played" else "Failed to unmark as played")
                return@launch
            }
            reloadPolicy.changedHere()
            _state.update { state -> state.copy(show = state.show?.copy(played = played)) }
            try {
                coroutineScope {
                    val nextUpAsync = async { if (downloadsOnly) null else getNextUp(showId) }
                    val seasonsAsync =
                        async { repository.getSeasons(showId, offline = downloadsOnly) }
                    val nextUp = nextUpAsync.await()
                    val seasons = seasonsAsync.await()
                    _state.update { state -> state.copy(nextUp = nextUp, seasons = seasons) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to refresh next up and seasons")
            }
        }
    }

    /** Marks the show favourite or not and updates the page in place (#252). */
    private fun setFavorite(favorite: Boolean) {
        viewModelScope.launch {
            try {
                if (favorite) repository.markAsFavorite(showId)
                else repository.unmarkAsFavorite(showId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(
                    e,
                    if (favorite) "Failed to mark as favorite" else "Failed to unmark as favorite",
                )
                return@launch
            }
            reloadPolicy.changedHere()
            _state.update { state -> state.copy(show = state.show?.copy(favorite = favorite)) }
        }
    }

    fun onAction(action: ShowAction) {
        when (action) {
            is ShowAction.OnRetryClick -> loadShow(showId)
            is ShowAction.MarkAsPlayed -> setPlayed(true)
            is ShowAction.UnmarkAsPlayed -> setPlayed(false)
            is ShowAction.MarkAsFavorite -> setFavorite(true)
            is ShowAction.UnmarkAsFavorite -> setFavorite(false)
            else -> Unit
        }
    }

    override fun onCleared() {
        super.onCleared()
        eventsChannel.close()
    }
}
