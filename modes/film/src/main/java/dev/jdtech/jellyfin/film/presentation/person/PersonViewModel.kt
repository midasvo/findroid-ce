package dev.jdtech.jellyfin.film.presentation.person

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemKind

@HiltViewModel
class PersonViewModel @Inject internal constructor(private val repository: JellyfinRepository) :
    ViewModel() {
    private val _state = MutableStateFlow(PersonState())
    val state = _state.asStateFlow()

    fun loadPerson(personId: UUID) {
        viewModelScope.launch {
            _state.emit(_state.value.copy(isLoading = true, error = null))
            try {
                // The person and their items don't depend on each other, so both are requested
                // at once (#252). coroutineScope { } is required: without it a failure inside an
                // async { } child crashes the app past this try/catch (see HomeViewModel).
                coroutineScope {
                    val personAsync = async { repository.getPerson(personId) }
                    val itemsAsync = async {
                        repository.getPersonItems(
                            personIds = listOf(personId),
                            includeTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                            recursive = true,
                            limit = PERSON_ITEMS_LIMIT,
                        )
                    }
                    val person = personAsync.await()
                    val items = itemsAsync.await()

                    val movies = items.filterIsInstance<FindroidMovie>()
                    val shows = items.filterIsInstance<FindroidShow>()

                    _state.emit(
                        _state.value.copy(
                            person = person,
                            starredInMovies = movies,
                            starredInShows = shows,
                            isLoading = false,
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.emit(_state.value.copy(isLoading = false, error = e))
            }
        }
    }
}

/**
 * The most titles asked for on a person's page (#252). A person with more is cut off at the limit:
 * the page shows those and has no "load more", as a cast member with over 100 titles is rare.
 */
internal const val PERSON_ITEMS_LIMIT = 100
