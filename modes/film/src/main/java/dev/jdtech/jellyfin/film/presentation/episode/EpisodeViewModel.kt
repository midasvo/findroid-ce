package dev.jdtech.jellyfin.film.presentation.episode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.film.domain.VideoMetadataParser
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItemPerson
import dev.jdtech.jellyfin.models.mergeDuplicateRoles
import dev.jdtech.jellyfin.repository.DataChangeEvents
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.repository.ReloadPolicy
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.PersonKind

@HiltViewModel
class EpisodeViewModel
@Inject
constructor(
    private val repository: JellyfinRepository,
    private val appPreferences: AppPreferences,
    private val videoMetadataParser: VideoMetadataParser,
    dataChanges: DataChangeEvents = DataChangeEvents(),
) : ViewModel() {
    private val _state = MutableStateFlow(EpisodeState())
    val state = _state.asStateFlow()

    private lateinit var episodeId: UUID
    private val reloadPolicy = ReloadPolicy(dataChanges)
    private var loadJob: Job? = null

    /** Whether coming back to the screen needs a load (#251), see [ReloadPolicy]. */
    fun shouldReload(episodeId: UUID, stopEventFollows: Boolean = false): Boolean =
        !::episodeId.isInitialized ||
            this.episodeId != episodeId ||
            reloadPolicy.shouldReload(stopEventFollows)

    fun loadEpisode(episodeId: UUID) {
        this.episodeId = episodeId
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val token = reloadPolicy.begin()
            _state.emit(_state.value.copy(error = null))
            try {
                val episode = repository.getEpisode(episodeId)
                val videoMetadata = episode.sources.firstOrNull()?.let { videoMetadataParser.parse(it) }
                val displayCastAndCrew = appPreferences.getValue(appPreferences.displayCastAndCrew)
                val actors = if (displayCastAndCrew) getActors(episode) else emptyList()
                val displayExtraInfo = appPreferences.getValue(appPreferences.displayExtraInfo)
                _state.emit(
                    _state.value.copy(
                        episode = episode,
                        videoMetadata = videoMetadata,
                        actors = actors,
                        displayExtraInfo = displayExtraInfo,
                    )
                )
                reloadPolicy.complete(token)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.emit(_state.value.copy(error = e))
            }
        }
    }

    private suspend fun getActors(item: FindroidEpisode): List<FindroidItemPerson> {
        return withContext(Dispatchers.Default) {
            item.people.mergeDuplicateRoles().filter { it.type == PersonKind.ACTOR }
        }
    }

    fun onAction(action: EpisodeAction) {
        when (action) {
            is EpisodeAction.OnRetryClick -> loadEpisode(episodeId)
            is EpisodeAction.MarkAsPlayed -> {
                viewModelScope.launch {
                    try {
                        repository.markAsPlayed(episodeId)
                        reloadPolicy.changedHere()
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to mark as played")
                    }
                    loadEpisode(episodeId)
                }
            }
            is EpisodeAction.UnmarkAsPlayed -> {
                viewModelScope.launch {
                    try {
                        repository.markAsUnplayed(episodeId)
                        reloadPolicy.changedHere()
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to unmark as played")
                    }
                    loadEpisode(episodeId)
                }
            }
            is EpisodeAction.MarkAsFavorite -> {
                viewModelScope.launch {
                    try {
                        repository.markAsFavorite(episodeId)
                        reloadPolicy.changedHere()
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to mark as favorite")
                    }
                    loadEpisode(episodeId)
                }
            }
            is EpisodeAction.UnmarkAsFavorite -> {
                viewModelScope.launch {
                    try {
                        repository.unmarkAsFavorite(episodeId)
                        reloadPolicy.changedHere()
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to unmark as favorite")
                    }
                    loadEpisode(episodeId)
                }
            }
            else -> Unit
        }
    }
}
