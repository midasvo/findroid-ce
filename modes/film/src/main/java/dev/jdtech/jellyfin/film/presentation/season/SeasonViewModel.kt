package dev.jdtech.jellyfin.film.presentation.season

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.core.presentation.downloader.DownloadProgress
import dev.jdtech.jellyfin.core.presentation.downloader.DownloadQueue
import dev.jdtech.jellyfin.core.presentation.downloader.DownloadStatus
import dev.jdtech.jellyfin.film.presentation.downloads.downloadDeletionScope
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.models.isDownloaded
import dev.jdtech.jellyfin.repository.DataChangeEvents
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.repository.ReloadPolicy
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.ItemFields
import timber.log.Timber

internal val seasonEpisodeFields = listOf(ItemFields.OVERVIEW, ItemFields.CAN_DOWNLOAD)

@HiltViewModel
class SeasonViewModel
@Inject
constructor(
    private val repository: JellyfinRepository,
    private val downloader: Downloader,
    private val downloadQueue: DownloadQueue,
    dataChanges: DataChangeEvents = DataChangeEvents(),
) : ViewModel() {
    private val _state = MutableStateFlow(SeasonState())
    val state = _state.asStateFlow()

    private val eventsChannel = Channel<SeasonEvent>()
    val events = eventsChannel.receiveAsFlow()

    private lateinit var seasonId: UUID
    private var downloadsOnly: Boolean = false
    private var includeSeriesSeasons: Boolean = false

    private var wasBusy = false
    private val reloadPolicy = ReloadPolicy(dataChanges)
    private var loadJob: Job? = null

    /** Whether coming back to the page needs a load (#251), see [ReloadPolicy]. */
    fun shouldReload(
        seasonId: UUID,
        downloadsOnly: Boolean = this.downloadsOnly,
        stopEventFollows: Boolean = false,
    ): Boolean =
        !::seasonId.isInitialized ||
            this.seasonId != seasonId ||
            this.downloadsOnly != downloadsOnly ||
            reloadPolicy.shouldReload(stopEventFollows)

    init {
        // Observe queue entries and merge into per-episode progress map.
        viewModelScope.launch {
            downloadQueue.entries
                // Only this season's entries matter, and only their id, state and progress:
                // the queue also re-emits for other downloads and for speed changes.
                .map { entries ->
                    val ids = _state.value.episodes.mapTo(HashSet()) { it.id }
                    entries.filter { it.id in ids }
                }
                .distinctUntilChanged { old, new ->
                    old.size == new.size &&
                        old.indices.all {
                            old[it].id == new[it].id &&
                                old[it].state == new[it].state &&
                                old[it].progress == new[it].progress
                        }
                }
                .collect { entries ->
                val episodes = _state.value.episodes
                if (episodes.isEmpty()) return@collect
                val progressMap = buildDownloadProgressMap(episodes, entries)
                if (progressMap != _state.value.episodeDownloadProgress) {
                    _state.update { it.copy(episodeDownloadProgress = progressMap) }
                }
                // Refresh season data once all of this season's downloads have settled,
                // so episode.sources gets the new LOCAL entry. Firing per-episode would
                // cause N network roundtrips.
                val episodeIds = episodes.map { it.id }.toSet()
                val isBusy =
                    entries.any {
                        it.id in episodeIds &&
                            (it.state is DownloadQueue.EntryState.Downloading ||
                                it.state is DownloadQueue.EntryState.Pending)
                    }
                if (isBusy) {
                    wasBusy = true
                } else if (wasBusy && ::seasonId.isInitialized) {
                    wasBusy = false
                    loadSeason(seasonId)
                }
            }
        }
    }

    /**
     * @param includeSeriesSeasons also load the other seasons of the series into
     *   [SeasonState.seriesSeasons], so TV can switch season on the same page.
     * @param seriesId the season's series, when the caller knows it. Then the season, its episodes
     *   and the series' seasons are all requested at once (#252). Without it, the series is taken
     *   from the season already on screen, or else from the season once it has loaded.
     */
    fun loadSeason(
        seasonId: UUID,
        downloadsOnly: Boolean = this.downloadsOnly,
        includeSeriesSeasons: Boolean = this.includeSeriesSeasons,
        seriesId: UUID? = null,
    ) {
        this.seasonId = seasonId
        this.downloadsOnly = downloadsOnly
        this.includeSeriesSeasons = includeSeriesSeasons
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val token = reloadPolicy.begin()
            val previous = _state.value
            _state.emit(previous.copy(error = null))
            // Another season of the same series (TV season chips) shares the series id.
            val knownSeriesId =
                seriesId
                    ?: previous.season?.takeIf { it.id == seasonId }?.seriesId
                    ?: previous.seriesSeasons.firstOrNull { it.id == seasonId }?.seriesId
            // While another season loads, the old one stays on screen with its episodes, so the
            // new season only shows early when nothing or the same season is on screen.
            val showSeasonEarly = previous.season == null || previous.season.id == seasonId
            try {
                // coroutineScope { } is required: without it a failure inside an async { }
                // child goes straight to viewModelScope and crashes the app, past this
                // try/catch (see HomeViewModel.loadData).
                coroutineScope {
                    val seasonAsync = async {
                        repository.getSeason(seasonId).also { season ->
                            if (showSeasonEarly) _state.update { it.copy(season = season) }
                        }
                    }
                    val resolvedSeriesId = knownSeriesId ?: seasonAsync.await().seriesId
                    val episodesAsync = async {
                        repository.getEpisodes(
                            seriesId = resolvedSeriesId,
                            seasonId = seasonId,
                            fields = seasonEpisodeFields,
                            offline = downloadsOnly,
                        )
                    }
                    val seriesSeasonsAsync = async {
                        if (includeSeriesSeasons) {
                            repository.getSeasons(resolvedSeriesId, offline = downloadsOnly)
                        } else {
                            emptyList()
                        }
                    }
                    val season = seasonAsync.await()
                    val episodes = episodesAsync.await()
                    val seriesSeasons = seriesSeasonsAsync.await()
                    _state.emit(
                        _state.value.copy(
                            season = season,
                            episodes = episodes,
                            seriesSeasons = seriesSeasons,
                            episodeDownloadProgress =
                                buildDownloadProgressMap(episodes, downloadQueue.entries.value),
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

    /**
     * Marks the season played or unplayed and updates the season in place (#252) instead of
     * loading the page again. Only the episodes are fetched again: the server marks them too, the
     * offline repository doesn't, so their state has to come from the repository.
     */
    private fun setPlayed(played: Boolean) {
        viewModelScope.launch {
            try {
                if (played) repository.markAsPlayed(seasonId)
                else repository.markAsUnplayed(seasonId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, if (played) "Failed to mark as played" else "Failed to unmark as played")
                return@launch
            }
            reloadPolicy.changedHere()
            _state.update { state -> state.copy(season = state.season?.copy(played = played)) }
            val seriesId = _state.value.season?.takeIf { it.id == seasonId }?.seriesId ?: return@launch
            try {
                val episodes =
                    repository.getEpisodes(
                        seriesId = seriesId,
                        seasonId = seasonId,
                        fields = seasonEpisodeFields,
                        offline = downloadsOnly,
                    )
                _state.update { state ->
                    state.copy(
                        episodes = episodes,
                        episodeDownloadProgress =
                            buildDownloadProgressMap(episodes, downloadQueue.entries.value),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to refresh the season's episodes")
            }
        }
    }

    /** Marks the season favourite or not and updates the page in place (#252). */
    private fun setFavorite(favorite: Boolean) {
        viewModelScope.launch {
            try {
                if (favorite) repository.markAsFavorite(seasonId)
                else repository.unmarkAsFavorite(seasonId)
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
            _state.update { state -> state.copy(season = state.season?.copy(favorite = favorite)) }
        }
    }

    fun downloadSeason(maxBitrate: Int? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            // An episode already enqueued (pending/downloading/paused) is "in
            // progress" from the user's perspective, so count it as skipped rather
            // than re-enqueuing. enqueueAll dedupes too, but without this the
            // "Started N downloads" toast would overstate the count.
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
            for (episode in _state.value.episodes) {
                if (episode.isDownloaded() || episode.id in liveQueuedIds) {
                    skipped++
                } else {
                    toQueue.add(episode)
                }
            }

            if (toQueue.isEmpty()) {
                eventsChannel.send(SeasonEvent.DownloadResult(0, skipped, 0))
                return@launch
            }

            downloadQueue.enqueueAll(toQueue, maxBitrate)
            eventsChannel.send(
                SeasonEvent.DownloadResult(started = toQueue.size, skipped = skipped, failed = 0)
            )
        }
    }

    fun deleteSeasonDownloads() {
        viewModelScope.launch(Dispatchers.IO) {
            for (episode in _state.value.episodes) {
                downloadQueue.remove(episode.id)
                val localSource =
                    episode.sources.firstOrNull { it.type == FindroidSourceType.LOCAL }
                if (localSource != null) {
                    downloader.deleteItem(item = episode, source = localSource)
                }
            }
            loadSeason(seasonId)
        }
    }

    private fun buildDownloadProgressMap(
        episodes: List<FindroidEpisode>,
        entries: List<DownloadQueue.Entry>,
    ): Map<UUID, DownloadProgress> {
        val byId = entries.associateBy { it.id }
        return episodes.associate { episode ->
            val entry = byId[episode.id]
            val progress =
                when (val s = entry?.state) {
                    is DownloadQueue.EntryState.Downloading ->
                        DownloadProgress(
                            status = DownloadStatus.DOWNLOADING,
                            progress = entry.progress / 100f,
                        )
                    is DownloadQueue.EntryState.Pending ->
                        DownloadProgress(status = DownloadStatus.QUEUED)
                    is DownloadQueue.EntryState.Paused ->
                        DownloadProgress(
                            status = DownloadStatus.PAUSED,
                            progress = entry.progress / 100f,
                        )
                    is DownloadQueue.EntryState.Failed -> DownloadProgress(status = DownloadStatus.FAILED)
                    is DownloadQueue.EntryState.Completed ->
                        DownloadProgress(status = DownloadStatus.COMPLETED, progress = 1f)
                    null ->
                        if (episode.isDownloaded())
                            DownloadProgress(status = DownloadStatus.COMPLETED, progress = 1f)
                        else DownloadProgress()
                }
            episode.id to progress
        }
    }

    fun onAction(action: SeasonAction) {
        when (action) {
            is SeasonAction.OnRetryClick -> loadSeason(seasonId)
            is SeasonAction.MarkAsPlayed -> setPlayed(true)
            is SeasonAction.UnmarkAsPlayed -> setPlayed(false)
            is SeasonAction.MarkAsFavorite -> setFavorite(true)
            is SeasonAction.UnmarkAsFavorite -> setFavorite(false)
            is SeasonAction.DownloadEpisode -> {
                viewModelScope.launch(Dispatchers.IO) {
                    val episode = action.episode
                    if (episode is FindroidEpisode) {
                        downloadQueue.enqueue(episode, action.maxBitrate)
                    }
                }
            }
            is SeasonAction.DeleteEpisodeDownload -> {
                downloadDeletionScope.launch {
                    val item = action.episode
                    downloadQueue.remove(item.id)
                    val localSource =
                        item.sources.firstOrNull { it.type == FindroidSourceType.LOCAL }
                    if (localSource != null) {
                        downloader.deleteItem(item = item, source = localSource)
                    }
                    loadSeason(seasonId)
                }
            }
            is SeasonAction.RetryEpisodeDownload -> downloadQueue.retry(action.episode.id)
            else -> Unit
        }
    }

    override fun onCleared() {
        super.onCleared()
        eventsChannel.close()
    }
}
