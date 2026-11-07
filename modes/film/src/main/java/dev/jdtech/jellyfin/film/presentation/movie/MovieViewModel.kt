package dev.jdtech.jellyfin.film.presentation.movie

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.film.domain.VideoMetadataParser
import dev.jdtech.jellyfin.models.FindroidItemPerson
import dev.jdtech.jellyfin.models.FindroidMovie
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
class MovieViewModel
@Inject
constructor(
    private val repository: JellyfinRepository,
    private val appPreferences: AppPreferences,
    private val videoMetadataParser: VideoMetadataParser,
    dataChanges: DataChangeEvents = DataChangeEvents(),
) : ViewModel() {
    private val _state = MutableStateFlow(MovieState())
    val state = _state.asStateFlow()

    private lateinit var movieId: UUID
    private val reloadPolicy = ReloadPolicy(dataChanges)
    private var loadJob: Job? = null

    /** Whether coming back to the screen needs a load (#251), see [ReloadPolicy]. */
    fun shouldReload(movieId: UUID, stopEventFollows: Boolean = false): Boolean =
        !::movieId.isInitialized ||
            this.movieId != movieId ||
            reloadPolicy.shouldReload(stopEventFollows)

    fun loadMovie(movieId: UUID) {
        this.movieId = movieId
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val token = reloadPolicy.begin()
            _state.emit(_state.value.copy(error = null))
            try {
                // The preferences don't depend on the movie, so they are read while it loads.
                val (movie, displayCastAndCrew, displayExtraInfo) = coroutineScope {
                    val movieAsync = async { repository.getMovie(movieId) }
                    val castAsync = async { appPreferences.getValue(appPreferences.displayCastAndCrew) }
                    val extraAsync = async { appPreferences.getValue(appPreferences.displayExtraInfo) }
                    Triple(movieAsync.await(), castAsync.await(), extraAsync.await())
                }
                val videoMetadata = movie.sources.firstOrNull()?.let { videoMetadataParser.parse(it) }
                val actors = if (displayCastAndCrew) getActors(movie) else emptyList()
                val director = if (displayCastAndCrew) getDirector(movie) else null
                val writers = if (displayCastAndCrew) getWriters(movie) else emptyList()
                _state.emit(
                    _state.value.copy(
                        movie = movie,
                        videoMetadata = videoMetadata,
                        actors = actors,
                        director = director,
                        writers = writers,
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

    private suspend fun getActors(item: FindroidMovie): List<FindroidItemPerson> {
        return withContext(Dispatchers.Default) {
            item.people.mergeDuplicateRoles().filter { it.type == PersonKind.ACTOR }
        }
    }

    private suspend fun getDirector(item: FindroidMovie): FindroidItemPerson? {
        return withContext(Dispatchers.Default) {
            item.people.mergeDuplicateRoles().firstOrNull { it.type == PersonKind.DIRECTOR }
        }
    }

    private suspend fun getWriters(item: FindroidMovie): List<FindroidItemPerson> {
        return withContext(Dispatchers.Default) {
            item.people.mergeDuplicateRoles().filter { it.type == PersonKind.WRITER }
        }
    }

    /** Marks the movie played or unplayed and updates the page in place (#252), no full load. */
    private fun setPlayed(played: Boolean) {
        viewModelScope.launch {
            try {
                if (played) repository.markAsPlayed(movieId) else repository.markAsUnplayed(movieId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, if (played) "Failed to mark as played" else "Failed to unmark as played")
                return@launch
            }
            reloadPolicy.changedHere()
            _state.update { state -> state.copy(movie = state.movie?.copy(played = played)) }
        }
    }

    /** Marks the movie favourite or not and updates the page in place (#252), no full load. */
    private fun setFavorite(favorite: Boolean) {
        viewModelScope.launch {
            try {
                if (favorite) repository.markAsFavorite(movieId) else repository.unmarkAsFavorite(movieId)
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
            _state.update { state -> state.copy(movie = state.movie?.copy(favorite = favorite)) }
        }
    }

    fun onAction(action: MovieAction) {
        when (action) {
            is MovieAction.OnRetryClick -> loadMovie(movieId)
            is MovieAction.MarkAsPlayed -> setPlayed(true)
            is MovieAction.UnmarkAsPlayed -> setPlayed(false)
            is MovieAction.MarkAsFavorite -> setFavorite(true)
            is MovieAction.UnmarkAsFavorite -> setFavorite(false)
            else -> Unit
        }
    }
}
