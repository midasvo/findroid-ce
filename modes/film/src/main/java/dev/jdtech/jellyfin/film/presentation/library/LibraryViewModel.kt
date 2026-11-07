package dev.jdtech.jellyfin.film.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.models.CollectionType
import dev.jdtech.jellyfin.models.SortBy
import dev.jdtech.jellyfin.models.SortOrder
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.repository.getItemsPagingIndexed
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFilter
import timber.log.Timber

@HiltViewModel
class LibraryViewModel
@Inject
constructor(
    private val jellyfinRepository: JellyfinRepository,
    private val appPreferences: AppPreferences,
) : ViewModel() {
    private val _state = MutableStateFlow(LibraryState())
    val state = _state.asStateFlow()

    private lateinit var parentId: UUID
    private lateinit var libraryType: CollectionType

    private lateinit var sortBy: SortBy
    private lateinit var sortOrder: SortOrder
    private var filterWatched: Boolean = false

    private var indexed = false
    private var letterIndexJob: Job? = null

    /** Built indexes, so switching sort or filter back doesn't ask the server again. */
    private data class LetterIndexKey(val unplayedOnly: Boolean, val ascending: Boolean)

    private val letterIndexCache = mutableMapOf<LetterIndexKey, LetterIndex>()

    /**
     * @param indexed CE (#135): load the grid with placeholders, so it knows its length and can
     *   jump, and build a [LetterIndex] when sorting by name. TV uses this, phone doesn't.
     */
    fun setup(parentId: UUID, libraryType: CollectionType, indexed: Boolean = false) {
        this.parentId = parentId
        this.libraryType = libraryType
        this.indexed = indexed
    }

    fun loadItems() {
        val itemType =
            when (libraryType) {
                CollectionType.Movies -> listOf(BaseItemKind.MOVIE)
                CollectionType.TvShows -> listOf(BaseItemKind.SERIES)
                CollectionType.BoxSets -> listOf(BaseItemKind.BOX_SET)
                CollectionType.Mixed,
                CollectionType.Folders ->
                    listOf(BaseItemKind.FOLDER, BaseItemKind.MOVIE, BaseItemKind.SERIES)
                else -> null
            }

        val recursive = itemType == null || !itemType.contains(BaseItemKind.FOLDER)

        viewModelScope.launch {
            _state.emit(_state.value.copy(isLoading = true, error = null))

            initSorting()

            val filters = if (filterWatched) listOf(ItemFilter.IS_UNPLAYED) else null
            if (indexed) resetLetterIndex()
            // Captured now: a late first page of a superseded grid must not index the new sort.
            val indexKey = letterIndexKey()
            val nameSort = sortBy == SortBy.NAME

            try {
                val items =
                    if (indexed) {
                            jellyfinRepository.getItemsPagingIndexed(
                                parentId = parentId,
                                includeTypes = itemType,
                                recursive = recursive,
                                sortBy = serverSortBy(),
                                sortOrder = sortOrder,
                                filters = filters,
                                // The 26 letter counts compete with the grid's own requests,
                                // so they wait for the first page and reuse its total.
                                onFirstPage = { total ->
                                    if (nameSort) {
                                        loadLetterIndex(indexKey, total, itemType, recursive, filters)
                                    }
                                },
                            )
                        } else {
                            jellyfinRepository.getItemsPaging(
                                parentId = parentId,
                                includeTypes = itemType,
                                recursive = recursive,
                                sortBy = serverSortBy(),
                                sortOrder = sortOrder,
                                filters = filters,
                            )
                        }
                        .cachedIn(viewModelScope)
                _state.emit(_state.value.copy(items = items, isLoading = false))
            } catch (e: Exception) {
                _state.emit(_state.value.copy(error = e, isLoading = false))
            }
        }
    }

    // Jellyfin uses a different enum for sorting series by date played
    private fun serverSortBy(): SortBy =
        if (libraryType == CollectionType.TvShows && sortBy == SortBy.DATE_PLAYED)
            SortBy.SERIES_DATE_PLAYED
        else sortBy

    private fun letterIndexKey() =
        LetterIndexKey(unplayedOnly = filterWatched, ascending = sortOrder == SortOrder.ASCENDING)

    /** Clears the bar, or shows the cached index for this sort and filter right away. */
    private fun resetLetterIndex() {
        letterIndexJob?.cancel()
        val cached = if (sortBy == SortBy.NAME) letterIndexCache[letterIndexKey()] else null
        _state.update { it.copy(letterIndex = cached) }
    }

    /**
     * Asks the server where each letter starts. Each is a count without items, so this stays cheap
     * in a large library. The bar is hidden until all counts are in, and for any other sort. Runs
     * after the grid's first page, with that page's [total].
     */
    private fun loadLetterIndex(
        key: LetterIndexKey,
        total: Int,
        itemType: List<BaseItemKind>?,
        recursive: Boolean,
        filters: List<ItemFilter>?,
    ) {
        if (letterIndexCache.containsKey(key)) return
        val ascending = key.ascending
        letterIndexJob?.cancel()
        letterIndexJob =
            viewModelScope.launch {
                val index =
                    try {
                        coroutineScope {
                            val before =
                                LetterIndex.BOUNDARIES.map { boundary ->
                                    async {
                                        jellyfinRepository.getItemCount(
                                            parentId = parentId,
                                            includeTypes = itemType,
                                            recursive = recursive,
                                            filters = filters,
                                            nameLessThan = boundary,
                                        )
                                    }
                                }
                            LetterIndex.build(before.awaitAll(), total, ascending)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.w(e, "Couldn't load the letter index")
                        null
                    }
                if (index != null) letterIndexCache[key] = index
                if (key == letterIndexKey()) _state.update { it.copy(letterIndex = index) }
            }
    }

    private suspend fun initSorting() {
        if (!::sortBy.isInitialized || !::sortOrder.isInitialized) {
            sortBy = SortBy.fromString(appPreferences.getValue(appPreferences.sortBy))
            sortOrder = SortOrder.fromString(appPreferences.getValue(appPreferences.sortOrder))
            filterWatched = appPreferences.getValue(appPreferences.filterWatched)
            _state.emit(_state.value.copy(sortBy = sortBy, sortOrder = sortOrder, filterWatched = filterWatched))
        }
    }

    private fun setSorting(sortBy: SortBy, sortOrder: SortOrder) {
        this.sortBy = sortBy
        this.sortOrder = sortOrder
        viewModelScope.launch {
            _state.emit(_state.value.copy(sortBy = sortBy, sortOrder = sortOrder))
            appPreferences.setValue(appPreferences.sortBy, sortBy.toString())
            appPreferences.setValue(appPreferences.sortOrder, sortOrder.toString())
        }
    }

    private fun toggleWatchedFilter(enabled: Boolean) {
        this.filterWatched = enabled
        viewModelScope.launch {
            _state.emit(_state.value.copy(filterWatched = enabled))
            appPreferences.setValue(appPreferences.filterWatched, enabled)
            loadItems()
        }
    }

    fun onAction(action: LibraryAction) {
        when (action) {
            is LibraryAction.ChangeSorting -> {
                if (action.sortBy != this.sortBy || action.sortOrder != this.sortOrder) {
                    setSorting(sortBy = action.sortBy, sortOrder = action.sortOrder)
                    loadItems()
                }
            }
            is LibraryAction.ToggleWatchedFilter -> toggleWatchedFilter(action.enabled)
            else -> Unit
        }
    }
}
