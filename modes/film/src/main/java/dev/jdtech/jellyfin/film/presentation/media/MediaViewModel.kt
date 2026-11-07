package dev.jdtech.jellyfin.film.presentation.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.repository.JellyfinRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class MediaViewModel @Inject constructor(private val repository: JellyfinRepository) : ViewModel() {
    private val _state = MutableStateFlow(MediaState())
    val state = _state.asStateFlow()

    /**
     * When the libraries last loaded, from [System.nanoTime], or null before the first successful
     * load. CE (#257): TV Main reloads only when this is old, instead of on every return.
     */
    var librariesLoadedAtNanos: Long? = null
        private set

    /**
     * Loads the libraries. [silent] (CE, #257) leaves the current list and loading state alone: no
     * loading flash, and a failure keeps the list on screen.
     */
    fun loadData(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) _state.emit(_state.value.copy(isLoading = true, error = null))
            try {
                val libraries = repository.getLibraries()
                librariesLoadedAtNanos = System.nanoTime()
                _state.emit(_state.value.copy(libraries = libraries, error = null))
            } catch (e: Exception) {
                if (silent) Timber.e(e, "Could not refresh the libraries")
                else _state.emit(_state.value.copy(error = e))
            }
            if (!silent) _state.emit(_state.value.copy(isLoading = false))
        }
    }

    fun onAction(action: MediaAction) {
        when (action) {
            is MediaAction.OnRetryClick -> {
                loadData()
            }
            else -> Unit
        }
    }
}
