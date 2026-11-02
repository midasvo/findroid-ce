package dev.jdtech.jellyfin.film.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.repository.JellyfinRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber

@HiltViewModel
class SearchViewModel @Inject constructor(private val repository: JellyfinRepository) :
    ViewModel() {
    private val _state = MutableStateFlow(SearchState())
    val state = _state.asStateFlow()

    private var currentJob: Job? = null

    private var itemKinds: List<BaseItemKind>? = null

    /**
     * CE: searches [itemKinds] instead of the repository default (movies and shows), for screens
     * that group results per kind (TV).
     */
    fun setup(itemKinds: List<BaseItemKind>) {
        this.itemKinds = itemKinds
    }

    private fun search(query: String) {
        currentJob?.cancel()
        currentJob =
            viewModelScope.launch {
                try {
                    if (query.isBlank()) {
                        _state.emit(SearchState(items = emptyList(), loading = false))
                        return@launch
                    }

                    // Inside the job, so the next keystroke's cancel also drops this request.
                    delay(SEARCH_DEBOUNCE_MS)

                    _state.emit(_state.value.copy(loading = true))
                    val items = repository.getSearchItems(query, itemKinds)

                    _state.emit(SearchState(query = query, items = items, loading = false))
                } catch (_: CancellationException) {} catch (e: Exception) {
                    Timber.e(e)
                    _state.emit(_state.value.copy(query = query, loading = false, error = e))
                }
            }
    }

    fun onAction(action: SearchAction) {
        when (action) {
            is SearchAction.Search -> {
                search(query = action.query)
            }
            else -> Unit
        }
    }

    companion object {
        internal const val SEARCH_DEBOUNCE_MS = 300L
    }
}
