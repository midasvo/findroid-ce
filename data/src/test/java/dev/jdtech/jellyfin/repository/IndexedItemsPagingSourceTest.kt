package dev.jdtech.jellyfin.repository

import androidx.paging.PagingSource.LoadParams
import androidx.paging.PagingSource.LoadResult
import dev.jdtech.jellyfin.models.FindroidItem
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IndexedItemsPagingSourceTest {
    private class Request(val start: Int, val limit: Int, val includeTotal: Boolean)

    private val requests = mutableListOf<Request>()
    private var total: Int? = null

    private fun item(): FindroidItem =
        Proxy.newProxyInstance(
            FindroidItem::class.java.classLoader,
            arrayOf(FindroidItem::class.java),
        ) { _, method, _ ->
            throw NotImplementedError(method.name)
        } as FindroidItem

    /** A library of [size] items; the server counts only when asked. */
    private fun source(size: Int) =
        IndexedItemsPagingSource(
            load = { start, limit, includeTotal ->
                requests += Request(start, limit, includeTotal)
                val end = (start + limit).coerceAtMost(size)
                ItemsPage(
                    items = List((end - start).coerceAtLeast(0)) { item() },
                    totalRecordCount = if (includeTotal) size else null,
                )
            },
            knownTotal = { total },
            onTotal = { total = it },
        )

    private fun refresh(key: Int?, loadSize: Int = 80) =
        LoadParams.Refresh(key, loadSize, placeholdersEnabled = true)

    @Test
    fun `first page takes the total from its own response`() = runBlocking {
        val result = source(500).load(refresh(null)) as LoadResult.Page
        assertEquals(1, requests.size)
        assertTrue(requests[0].includeTotal)
        assertEquals(500, total)
        assertEquals(80, result.data.size)
        assertEquals(420, result.itemsAfter)
        assertEquals(80, result.nextKey)
    }

    @Test
    fun `later pages and sources do not ask for the total`() = runBlocking {
        source(500).load(refresh(null))
        requests.clear()
        val append = source(500).load(LoadParams.Append(80, 40, true)) as LoadResult.Page
        assertEquals(1, requests.size)
        assertEquals(false, requests[0].includeTotal)
        assertEquals(40, append.data.size)
        assertEquals(380, append.itemsAfter)
    }

    @Test
    fun `a jump after the first load clamps to the known total`() = runBlocking {
        source(500).load(refresh(null))
        requests.clear()
        val result = source(500).load(refresh(499)) as LoadResult.Page
        assertEquals(420, result.itemsBefore)
        assertEquals(80, result.data.size)
        assertNull(result.nextKey)
    }

    @Test
    fun `a refresh past a shrunken library reloads inside it`() = runBlocking {
        // Unknown total and a key beyond the end: the first request comes back empty.
        val result = source(100).load(refresh(900)) as LoadResult.Page
        assertEquals(100, total)
        assertEquals(2, requests.size)
        assertEquals(20, result.itemsBefore)
        assertEquals(80, result.data.size)
    }

    @Test
    fun `an empty library has no items and no next key`() = runBlocking {
        val result = source(0).load(refresh(null)) as LoadResult.Page
        assertEquals(0, total)
        assertTrue(result.data.isEmpty())
        assertNull(result.nextKey)
        assertEquals(0, result.itemsAfter)
    }
}
