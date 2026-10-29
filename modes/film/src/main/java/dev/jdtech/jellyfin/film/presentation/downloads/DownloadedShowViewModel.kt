package dev.jdtech.jellyfin.film.presentation.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.core.presentation.downloader.DownloadQueue
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.models.toFindroidEpisode
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.utils.Downloader
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The downloaded episodes of one show, grouped by season, for managing them in one place. */
@HiltViewModel
class DownloadedShowViewModel
@Inject
constructor(
    private val repository: JellyfinRepository,
    private val downloadQueue: DownloadQueue,
    private val downloader: Downloader,
    private val database: ServerDatabaseDao,
) : ViewModel() {
    private val _state = MutableStateFlow(DownloadedShowState())
    val state = _state.asStateFlow()

    fun loadEpisodes(showId: UUID) {
        viewModelScope.launch {
            val (episodes, sizes) =
                withContext(Dispatchers.IO) {
                    val userId = repository.getUserId()
                    // Episodes with only a `.download` source are still in progress.
                    val episodes =
                        database
                            .getEpisodesByShowId(showId)
                            .filter { ep ->
                                database.getSources(ep.id).any { !it.path.endsWith(".download") }
                            }
                            .map { it.toFindroidEpisode(database, userId) }
                            .sortedWith(
                                compareBy({ it.parentIndexNumber }, { it.indexNumber })
                            )
                    val sizes =
                        episodes.associate { ep ->
                            ep.id to
                                database.getSources(ep.id).sumOf { src ->
                                    File(src.path).takeIf { it.exists() }?.length() ?: 0L
                                }
                        }
                    episodes to sizes
                }
            val seasons =
                episodes
                    .groupBy { it.parentIndexNumber }
                    .map { (number, seasonEpisodes) ->
                        DownloadedSeason(
                            number = number,
                            name = seasonEpisodes.first().seasonName,
                            episodes = seasonEpisodes,
                        )
                    }
            _state.emit(DownloadedShowState(seasons = seasons, itemSizes = sizes, isLoading = false))
        }
    }

    fun deleteEpisodes(showId: UUID, episodes: List<FindroidEpisode>) {
        downloadDeletionScope.launch {
            for (episode in episodes) {
                downloadQueue.remove(episode.id)
                val source =
                    episode.sources.firstOrNull { it.type == FindroidSourceType.LOCAL } ?: continue
                downloader.deleteItem(episode, source)
            }
            loadEpisodes(showId)
        }
    }
}
