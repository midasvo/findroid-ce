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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
                // The preferences don't depend on the episode, so they are read while it loads.
                val (episode, displayCastAndCrew, displayExtraInfo) = coroutineScope {
                    val episodeAsync = async { repository.getEpisode(episodeId) }
                    val castAsync = async { appPreferences.getValue(appPreferences.displayCastAndCrew) }
                    val extraAsync = async { appPreferences.getValue(appPreferences.displayExtraInfo) }
                    Triple(episodeAsync.await(), castAsync.await(), extraAsync.await())
                }
                val videoMetadata = episode.sources.firstOrNull()?.let { videoMetadataParser.parse(it) }
                val actors = if (displayCastAndCrew) getActors(episode) else emptyList()
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

    /** Marks the episode played or unplayed and updates the page in place (#252), no full load. */
    private fun setPlayed(played: Boolean) {
        viewModelScope.launch {
            try {
                if (played) repository.markAsPlayed(episodeId) else repository.markAsUnplayed(episodeId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, if (played) "Failed to mark as played" else "Failed to unmark as played")
                return@launch
            }
            reloadPolicy.changedHere()
            _state.update { state -> state.copy(episode = state.episode?.copy(played = played)) }
        }
    }

    /** Marks the episode favourite or not and updates the page in place (#252), no full load. */
    private fun setFavorite(favorite: Boolean) {
        viewModelScope.launch {
            try {
                if (favorite) repository.markAsFavorite(episodeId) else repository.unmarkAsFavorite(episodeId)
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
            _state.update { state -> state.copy(episode = state.episode?.copy(favorite = favorite)) }
        }
    }

    fun onAction(action: EpisodeAction) {
        when (action) {
            is EpisodeAction.OnRetryClick -> loadEpisode(episodeId)
            is EpisodeAction.MarkAsPlayed -> setPlayed(true)
            is EpisodeAction.UnmarkAsPlayed -> setPlayed(false)
            is EpisodeAction.MarkAsFavorite -> setFavorite(true)
            is EpisodeAction.UnmarkAsFavorite -> setFavorite(false)
            else -> Unit
        }
    }
}
