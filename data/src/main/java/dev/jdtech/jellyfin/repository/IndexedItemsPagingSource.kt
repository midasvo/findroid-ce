package dev.jdtech.jellyfin.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.SortBy
import dev.jdtech.jellyfin.models.SortOrder
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFilter

/**
 * CE: like [JellyfinRepository.getItemsPaging], but with placeholders for the items that aren't
 * loaded yet. The list then has its full length from the first page, so a screen can show a
 * position counter and scroll straight to any index (a letter of the A–Z jump bar). Paging loads
 * the pages around that index instead of every page before it.
 */
fun JellyfinRepository.getItemsPagingIndexed(
    parentId: UUID? = null,
    includeTypes: List<BaseItemKind>? = null,
    recursive: Boolean = false,
    sortBy: SortBy = SortBy.defaultValue,
    sortOrder: SortOrder = SortOrder.ASCENDING,
    filters: List<ItemFilter>? = null,
): Flow<PagingData<FindroidItem>> {
    // A jump invalidates the source; the total doesn't change by that, so count only once.
    var total: Int? = null
    return Pager(
            config =
                PagingConfig(
                    pageSize = PAGE_SIZE,
                    initialLoadSize = PAGE_SIZE * 2,
                    enablePlaceholders = true,
                    jumpThreshold = PAGE_SIZE * 3,
                ),
            pagingSourceFactory = {
                IndexedItemsPagingSource(
                    load = { start, limit ->
                        getItems(
                            parentId = parentId,
                            includeTypes = includeTypes,
                            recursive = recursive,
                            sortBy = sortBy,
                            sortOrder = sortOrder,
                            filters = filters,
                            startIndex = start,
                            limit = limit,
                        )
                    },
                    count = {
                        total
                            ?: getItemCount(
                                    parentId = parentId,
                                    includeTypes = includeTypes,
                                    recursive = recursive,
                                    filters = filters,
                                )
                                .also { total = it }
                    },
                )
            },
        )
        .flow
}

private const val PAGE_SIZE = 30

/**
 * The key is an item index: where a refresh is centred, where an append starts and where a prepend
 * ends.
 */
internal class IndexedItemsPagingSource(
    private val load: suspend (start: Int, limit: Int) -> List<FindroidItem>,
    private val count: suspend () -> Int,
) : PagingSource<Int, FindroidItem>() {
    override val jumpingSupported: Boolean = true

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, FindroidItem> {
        return try {
            val total = count()
            val key = params.key ?: 0
            val (start, end) =
                when (params) {
                    is LoadParams.Refresh -> {
                        val start =
                            (key - params.loadSize / 2).coerceIn(
                                0,
                                (total - params.loadSize).coerceAtLeast(0),
                            )
                        start to (start + params.loadSize).coerceAtMost(total)
                    }
                    is LoadParams.Append -> key to (key + params.loadSize).coerceAtMost(total)
                    is LoadParams.Prepend -> (key - params.loadSize).coerceAtLeast(0) to key
                }
            val items = if (end > start) load(start, end - start) else emptyList()
            val loadedEnd = start + items.size
            LoadResult.Page(
                data = items,
                prevKey = if (start > 0) start else null,
                nextKey = if (loadedEnd < total && items.isNotEmpty()) loadedEnd else null,
                itemsBefore = start,
                itemsAfter = (total - loadedEnd).coerceAtLeast(0),
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, FindroidItem>): Int? = state.anchorPosition
}
