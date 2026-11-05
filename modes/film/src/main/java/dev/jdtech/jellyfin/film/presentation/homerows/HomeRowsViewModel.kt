package dev.jdtech.jellyfin.film.presentation.homerows

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.film.R as FilmR
import dev.jdtech.jellyfin.film.domain.homerows.HomeRowConfig
import dev.jdtech.jellyfin.film.domain.homerows.HomeRowType
import dev.jdtech.jellyfin.film.domain.homerows.HomeRows
import dev.jdtech.jellyfin.film.domain.homerows.HomeRowsProfile
import dev.jdtech.jellyfin.film.domain.homerows.HomeRowsStore
import dev.jdtech.jellyfin.film.domain.homerows.homeLibraries
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * CE (#142): Settings → Interface → Home rows, for phone and TV. Every change is stored straight
 * away for the current user on the current server.
 */
@HiltViewModel
class HomeRowsViewModel
@Inject
constructor(
    private val repository: JellyfinRepository,
    private val store: HomeRowsStore,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeRowsState())
    val state = _state.asStateFlow()

    private var profile: HomeRowsProfile? = null
    private var libraryNames: Map<UUID, String> = emptyMap()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                profile = store.currentProfile()
                val libraries = repository.homeLibraries()
                libraryNames = libraries.associate { it.id to it.name.orEmpty() }
                val rows =
                    HomeRows.resolve(store.read(profile), libraries.map { it.id }, store.legacy())
                _state.update { it.copy(rows = rows.map(::entry), isLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to load the Home rows")
                _state.update { it.copy(isLoading = false, error = e) }
            }
        }
    }

    private fun entry(row: HomeRowConfig): HomeRowEntry {
        val title =
            when (row.type) {
                HomeRowType.SUGGESTIONS -> UiText.StringResource(FilmR.string.home_suggestions_title)
                HomeRowType.CONTINUE_WATCHING -> UiText.StringResource(FilmR.string.continue_watching)
                HomeRowType.NEXT_UP -> UiText.StringResource(FilmR.string.next_up)
                HomeRowType.LATEST ->
                    UiText.StringResource(
                        FilmR.string.latest_library,
                        libraryNames[row.libraryId].orEmpty(),
                    )
            }
        return HomeRowEntry(row, title)
    }

    private fun change(transform: (List<HomeRowConfig>) -> List<HomeRowConfig>) {
        val rows = transform(_state.value.rows.map { it.row })
        _state.update { it.copy(rows = rows.map(::entry)) }
        profile?.let { store.write(it, rows) }
    }

    fun onAction(action: HomeRowsAction) {
        when (action) {
            is HomeRowsAction.OnVisibleChange ->
                change { HomeRows.setVisible(it, action.key, action.visible) }
            is HomeRowsAction.OnMoveUp -> change { HomeRows.move(it, action.key, -1) }
            is HomeRowsAction.OnMoveDown -> change { HomeRows.move(it, action.key, 1) }
            is HomeRowsAction.OnRetryClick -> load()
            is HomeRowsAction.OnBackClick -> Unit
        }
    }
}
