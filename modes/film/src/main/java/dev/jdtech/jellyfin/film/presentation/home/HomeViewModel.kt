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
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.HomeItem
import dev.jdtech.jellyfin.models.HomeSection
import dev.jdtech.jellyfin.models.Server
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.repository.DataChangeEvents
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.repository.JellyfinRepositoryOfflineImpl
import dev.jdtech.jellyfin.repository.ReloadPolicy
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
import org.jellyfin.sdk.model.api.BaseItemDto
import timber.log.Timber

@HiltViewModel
class HomeViewModel
@Inject
constructor(
    private val repository: JellyfinRepository,
    private val appPreferences: AppPreferences,
    private val database: ServerDatabaseDao,
    dataChanges: DataChangeEvents = DataChangeEvents(),
    private val homeCache: HomeCache? = null,
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

    private val reloadPolicy = ReloadPolicy(dataChanges)
    private var loadJob: Job? = null
    private var cachedLibraries: List<BaseItemDto>? = null
    private var cachedLibrariesKey: Pair<String?, UUID?>? = null
    private var cacheChecked = false

    /**
     * With [silent] the refresh indicator stays hidden, for reloads the user didn't ask for. The
     * returned job ends once every row has loaded, for screens that wait for that (TV Home puts focus
     * back only then, so it lands on the item in its new place). A load that starts while another
     * is running replaces it (#251).
     */
    fun loadData(silent: Boolean = false): Job {
        Timber.i("Loading data")
        loadJob?.cancel()
        val job = viewModelScope.launch(Dispatchers.Default) { runLoad(silent) }
        loadJob = job
        return job
    }

    /** Whether coming back to Home needs a load (#251), see [ReloadPolicy]. */
    fun shouldReload(stopEventFollows: Boolean = false): Boolean =
        reloadPolicy.shouldReload(stopEventFollows)

    /** [loadData] when [shouldReload] says so, else a job that is already done. */
    fun loadDataIfNeeded(silent: Boolean = false): Job =
        if (shouldReload(stopEventFollows = false)) loadData(silent) else Job().apply { complete() }

    private suspend fun runLoad(silent: Boolean) {
        val token = reloadPolicy.begin()
        if (!silent) _state.update { it.copy(isLoading = true, error = null) }
        var server: Server? = null
        var loaded = false
        try {
            server =
                appPreferences.getValue(appPreferences.currentServer)?.let { serverId ->
                    loadServerName(serverId)
                }
            if (server != null) {
                val user = logFailure("user") { database.getServerCurrentUser(server.id) }
                _state.update { it.copy(user = user.getOrNull()) }
                showCachedRows(server.id, user.getOrNull()?.id)
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
                    // The user image only feeds the avatar: it must not hold the rows back.
                    val avatar = server?.let { s -> async { refreshUserImage(s.id) } }
                    val results =
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
                    avatar?.await()
                    results
                }
            val error = errorIfAllFailed(rows)
            _state.update { it.copy(error = error) }
            loaded = error == null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(error = e) }
        }
        _state.update { it.copy(isLoading = false) }
        if (loaded) {
            reloadPolicy.complete(token)
            saveRows(server)
        }
    }

    /** The avatar's image tag changes on the server; Room holds it, so read the user again. */
    private suspend fun refreshUserImage(serverId: String) {
        logFailure("user image") {
            repository.refreshCurrentUserImage()
            database.getServerCurrentUser(serverId)?.let { user ->
                _state.update { it.copy(user = user) }
            }
        }
    }

    /**
     * Cold start (#251): the rows of the last full load, on screen before the network answers. Only
     * when nothing is on screen yet; the load that follows replaces them.
     */
    private suspend fun showCachedRows(serverId: String, userId: UUID?) {
        val cache = homeCache ?: return
        if (userId == null || cacheChecked || !_state.value.isEmpty) return
        cacheChecked = true
        if (!appPreferences.getValue(appPreferences.homeCache)) {
            // Turned off: whatever an earlier start wrote goes, so nothing stale is kept around.
            cache.clear()
            return
        }
        val rows = cache.read(serverId, userId) ?: return
        _state.update {
            if (!it.isEmpty) {
                it
            } else {
                it.copy(
                    suggestionsSection = rows.suggestions,
                    resumeSection = rows.resume,
                    nextUpSection = rows.nextUp,
                    views = rows.views,
                    rowOrder = rows.rowOrder,
                    isLoading = false,
                )
            }
        }
    }

    /** Offline, the rows are the downloads: not worth showing on the next online start. */
    private suspend fun saveRows(server: Server?) {
        val cache = homeCache ?: return
        val user = _state.value.user ?: return
        if (server == null || repository is JellyfinRepositoryOfflineImpl) return
        if (!appPreferences.getValue(appPreferences.homeCache)) return
        cache.write(server.id, user.id, _state.value)
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

        // The library list rarely changes: later loads start the Latest rows from the last list
        // right away and check the list alongside (#251).
        val key = _state.value.server?.id to _state.value.user?.id
        val known = cachedLibraries?.takeIf { cachedLibrariesKey == key }
        coroutineScope {
            val fresh = async { logFailure("libraries") { repository.homeLibraries() } }
            val libraries = known ?: fresh.await().getOrThrow()
            cachedLibraries = libraries
            cachedLibrariesKey = key
            loadLatest(libraries, stored, legacy)
            if (known != null) {
                val now = fresh.await().getOrNull()
                if (now != null && now.map { it.id } != known.map { it.id }) {
                    cachedLibraries = now
                    loadLatest(now, stored, legacy)
                }
            }
        }
        return true
    }

    /**
     * The Latest row of each library. Every row shows as soon as it arrives, in the order of the
     * libraries, so the slowest library holds back none of the others (#251).
     */
    private suspend fun loadLatest(
        libraries: List<BaseItemDto>,
        stored: List<HomeRowConfig>?,
        legacy: LegacyHomeSwitches,
    ) {
        // CE (#142): now that the libraries are known, new ones get their place and hidden ones go.
        val layout = HomeRows.resolve(stored, libraries.map { it.id }, legacy)
        _state.update { it.copy(rowOrder = layout.map { row -> row.key }) }
        val shown = layout.filter { it.visible }.mapNotNullTo(mutableSetOf()) { it.libraryId }
        val views = libraries.filter { it.id in shown }

        val current = _state.value.views
        val results = arrayOfNulls<Result<List<FindroidItem>>>(views.size)
        // Rows that haven't arrived keep what they showed; the order is that of [views].
        fun items() =
            views.indices.mapNotNull { i ->
                val view = views[i]
                val result = results[i]
                when {
                    result == null -> current.firstOrNull { it.id == view.id }
                    result.isSuccess ->
                        result.getOrThrow().let { latest ->
                            if (latest.isEmpty()) null else HomeItem.ViewItem(view.toView(latest))
                        }
                    else -> current.firstOrNull { it.id == view.id }
                }
            }

        coroutineScope {
            views
                .mapIndexed { i, view ->
                    async {
                        val result =
                            logFailure("library ${view.id}") { repository.getLatestMedia(view.id) }
                        synchronized(results) {
                            results[i] = result
                            _state.update { it.copy(views = items()) }
                        }
                    }
                }
                .awaitAll()
        }
        // With nothing loaded, this is the row failing as a whole.
        if (results.isNotEmpty() && results.all { it?.isFailure == true }) {
            throw results.first()!!.exceptionOrNull()!!
        }
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
