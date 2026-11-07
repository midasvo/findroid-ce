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
    onFirstPage: (total: Int) -> Unit = {},
): Flow<PagingData<FindroidItem>> {
    // A jump invalidates the source; the total doesn't change by that, so it is taken once, from
    // the first page's response.
    var total: Int? = null
    var firstPageReported = false
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
                    load = { start, limit, includeTotal ->
                        getItemsPage(
                            parentId = parentId,
                            includeTypes = includeTypes,
                            recursive = recursive,
                            sortBy = sortBy,
                            sortOrder = sortOrder,
                            filters = filters,
                            startIndex = start,
                            limit = limit,
                            includeTotal = includeTotal,
                        )
                    },
                    knownTotal = { total },
                    onTotal = { total = it },
                    onPage = { pageTotal ->
                        if (!firstPageReported) {
                            firstPageReported = true
                            onFirstPage(pageTotal)
                        }
                    },
                )
            },
        )
        .flow
}

private const val PAGE_SIZE = 40

/**
 * The key is an item index: where a refresh is centred, where an append starts and where a prepend
 * ends.
 *
 * The total comes from the response of the first page that loads ([knownTotal] is null until then,
 * and [load] is asked to include it); later pages, and later sources after a jump, reuse it.
 */
internal class IndexedItemsPagingSource(
    private val load: suspend (start: Int, limit: Int, includeTotal: Boolean) -> ItemsPage,
    private val knownTotal: () -> Int?,
    private val onTotal: (Int) -> Unit,
    /** Called with the total after each page that loaded. */
    private val onPage: (total: Int) -> Unit = {},
) : PagingSource<Int, FindroidItem>() {
    override val jumpingSupported: Boolean = true

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, FindroidItem> {
        return try {
            val key = params.key ?: 0
            var total = knownTotal()
            var range = rangeOf(params, key, total)
            var page = loadRange(range, includeTotal = total == null)
            if (total == null) {
                total = page.totalRecordCount ?: (range.first + page.items.size)
                onTotal(total)
                // The first request couldn't be clamped to the total; redo it if it fell outside.
                if (range.first >= total && range.first > 0) {
                    range = rangeOf(params, key, total)
                    page = loadRange(range, includeTotal = false)
                }
            }
            val start = range.first
            val items = page.items
            val loadedEnd = start + items.size
            onPage(total)
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

    private suspend fun loadRange(range: Pair<Int, Int>, includeTotal: Boolean): ItemsPage {
        val (start, end) = range
        // A request that needs the total goes out even for an empty range (a limit of 0 returns
        // no items but still counts).
        return if (end > start || includeTotal) load(start, end - start, includeTotal)
        else ItemsPage(emptyList())
    }

    /** The first and one past the last index to load; the end isn't clamped without a [total]. */
    private fun rangeOf(params: LoadParams<Int>, key: Int, total: Int?): Pair<Int, Int> {
        val max = total ?: Int.MAX_VALUE
        return when (params) {
            is LoadParams.Refresh -> {
                val start =
                    (key - params.loadSize / 2).coerceIn(
                        0,
                        if (total != null) (total - params.loadSize).coerceAtLeast(0) else Int.MAX_VALUE,
                    )
                start to (start.toLong() + params.loadSize).coerceAtMost(max.toLong()).toInt()
            }
            is LoadParams.Append ->
                key to (key.toLong() + params.loadSize).coerceAtMost(max.toLong()).toInt()
            is LoadParams.Prepend -> (key - params.loadSize).coerceAtLeast(0) to key
        }
    }

    override fun getRefreshKey(state: PagingState<Int, FindroidItem>): Int? = state.anchorPosition
}
