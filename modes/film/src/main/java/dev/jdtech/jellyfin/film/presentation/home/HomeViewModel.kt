package dev.jdtech.jellyfin.film.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.film.R as FilmR
import dev.jdtech.jellyfin.film.domain.homerows.HomeRowConfig
import dev.jdtech.jellyfin.film.domain.homerows.HomeRowType
import dev.jdtech.jellyfin.film.domain.homerows.HomeRows
import dev.jdtech.jellyfin.film.domain.homerows.HomeRowsProfile
import dev.jdtech.jellyfin.film.domain.homerows.HomeRowsStore
import dev.jdtech.jellyfin.film.domain.homerows.LegacyHomeSwitches
import dev.jdtech.jellyfin.film.domain.homerows.homeLibraries
import dev.jdtech.jellyfin.models.HomeItem
import dev.jdtech.jellyfin.models.HomeSection
import dev.jdtech.jellyfin.models.Server
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.utils.toView
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class HomeViewModel
@Inject
constructor(
    private val repository: JellyfinRepository,
    private val appPreferences: AppPreferences,
    private val database: ServerDatabaseDao,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeState())
    val state = _state.asStateFlow()

    private val uiTextContinueWatching = UiText.StringResource(FilmR.string.continue_watching)
    private val uiTextNextUp = UiText.StringResource(FilmR.string.next_up)

    companion object {
        private val UUID_SUGGESTIONS =
            UUID.fromString("31e47044-9b79-4bb0-99d0-0e477ed65420")
        private val UUID_CONTINUE_WATCHING =
            UUID.fromString("44845958-8326-4e83-beb4-c4f42e9eeb95")
        private val UUID_NEXT_UP =
            UUID.fromString("18bfced5-f237-4d42-aa72-d9d7fed19279")
    }

    private val homeRowsStore = HomeRowsStore(appPreferences, database)

    /**
     * With [silent] the refresh indicator stays hidden, for reloads the user didn't ask for. The
     * returned job ends once every row has loaded, for screens that wait for that (TV Home puts focus
     * back only then, so it lands on the item in its new place).
     */
    fun loadData(silent: Boolean = false): Job {
        Timber.i("Loading data")
        return viewModelScope.launch(Dispatchers.Default) {
            if (!silent) _state.update { it.copy(isLoading = true, error = null) }
            try {
                val server =
                    appPreferences.getValue(appPreferences.currentServer)?.let { serverId ->
                        loadServerName(serverId)
                    }
                if (server != null) {
                    logFailure("user image") { repository.refreshCurrentUserImage() }
                    val user = logFailure("user") { database.getServerCurrentUser(server.id) }
                    _state.update { it.copy(user = user.getOrNull()) }
                }
                // CE (#142): the rows this user chose for this server, in their order.
                val stored = homeRowsStore.read(HomeRowsProfile.of(server))
                val legacy = homeRowsStore.legacy()
                val layout = HomeRows.resolve(stored, null, legacy)
                _state.update { it.copy(rowOrder = layout.map { row -> row.key }) }
                fun show(type: HomeRowType) = layout.any { it.type == type && it.visible }

                // coroutineScope { } is required here: without it, a failure
                // inside any async { } child propagates straight to viewModelScope
                // and crashes the app, even with this try/catch around awaitAll.
                // See BUGREPORT_ANALYSIS.md. Each row catches its own failures, so one
                // row can't cancel the others; this stays for whatever gets past that.
                val rows =
                    coroutineScope {
                        awaitAll(
                            async {
                                logFailure("suggestions") {
                                    loadSuggestions(show(HomeRowType.SUGGESTIONS))
                                }
                            },
                            async {
                                logFailure("resume items") {
                                    loadResumeItems(show(HomeRowType.CONTINUE_WATCHING))
                                }
                            },
                            async {
                                logFailure("next up items") {
                                    loadNextUpItems(show(HomeRowType.NEXT_UP))
                                }
                            },
                            async { logFailure("views") { loadViews(stored, legacy) } },
                        )
                    }
                _state.update { it.copy(error = errorIfAllFailed(rows)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e) }
            }
            _state.update { it.copy(isLoading = false) }
        }
    }

    /** Runs [block], turning a failure into a logged result. A row keeps what it showed. */
    private suspend fun <T> logFailure(name: String, block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to load $name")
            Result.failure(e)
        }

    /**
     * The first error, if no row loaded. Each row reports whether it asked the server at all, so
     * rows turned off in settings don't count as having loaded.
     */
    private fun errorIfAllFailed(rows: List<Result<Boolean>>): Exception? {
        val attempted = rows.filter { it.getOrDefault(true) }
        return if (attempted.all { it.isFailure }) {
            attempted.firstOrNull()?.exceptionOrNull() as Exception?
        } else {
            null
        }
    }

    private suspend fun loadServerName(serverId: String): Server? {
        val server = database.getServer(serverId)
        if (server != null) {
            _state.update { it.copy(server = server) }
        }
        return server
    }

    private suspend fun loadSuggestions(show: Boolean): Boolean {
        Timber.i("Loading suggestions")
        if (!show) {
            _state.update { it.copy(suggestionsSection = null) }
            return false
        }

        val items = repository.getSuggestions()

        val section =
            if (items.isEmpty()) {
                null
            } else {
                HomeItem.Suggestions(id = UUID_SUGGESTIONS, items = items)
            }

        _state.update { it.copy(suggestionsSection = section) }
        return true
    }

    private suspend fun loadResumeItems(show: Boolean): Boolean {
        Timber.i("Loading resume items")
        if (!show) {
            _state.update { it.copy(resumeSection = null) }
            return false
        }

        val resumeItems = repository.getResumeItems()

        val section =
            if (resumeItems.isEmpty()) {
                null
            } else {
                HomeItem.Section(
                    HomeSection(UUID_CONTINUE_WATCHING, uiTextContinueWatching, resumeItems)
                )
            }

        _state.update { it.copy(resumeSection = section) }
        return true
    }

    private suspend fun loadNextUpItems(show: Boolean): Boolean {
        Timber.i("Loading next up items")
        if (!show) {
            _state.update { it.copy(nextUpSection = null) }
            return false
        }

        val nextUpItems = repository.getNextUp()

        val section =
            if (nextUpItems.isEmpty()) {
                null
            } else {
                HomeItem.Section(HomeSection(UUID_NEXT_UP, uiTextNextUp, nextUpItems))
            }

        _state.update { it.copy(nextUpSection = section) }
        return true
    }

    private suspend fun loadViews(
        stored: List<HomeRowConfig>?,
        legacy: LegacyHomeSwitches,
    ): Boolean {
        Timber.i("Loading views")
        if (!HomeRows.needsLibraries(stored, legacy)) {
            _state.update { it.copy(views = emptyList()) }
            return false
        }

        val libraries = repository.homeLibraries()
        // CE (#142): now that the libraries are known, new ones get their place and hidden ones go.
        val layout = HomeRows.resolve(stored, libraries.map { it.id }, legacy)
        _state.update { it.copy(rowOrder = layout.map { row -> row.key }) }
        val shown = layout.filter { it.visible }.mapNotNullTo(mutableSetOf()) { it.libraryId }
        val views = libraries.filter { it.id in shown }

        val results =
            coroutineScope {
                views
                    .map { view ->
                        async {
                            logFailure("library ${view.id}") { repository.getLatestMedia(view.id) }
                        }
                    }
                    .awaitAll()
            }
        // With nothing loaded, this is the row failing as a whole.
        if (results.isNotEmpty() && results.all { it.isFailure }) {
            throw results.first().exceptionOrNull()!!
        }

        val current = _state.value.views
        val items =
            views.zip(results).mapNotNull { (view, result) ->
                result.fold(
                    onSuccess = { latest ->
                        if (latest.isEmpty()) null else HomeItem.ViewItem(view.toView(latest))
                    },
                    onFailure = { current.firstOrNull { it.id == view.id } },
                )
            }

        _state.update { it.copy(views = items) }
        return true
    }

    fun onAction(action: HomeAction) {
        when (action) {
            is HomeAction.OnRetryClick -> {
                loadData()
            }
            else -> Unit
        }
    }
}
