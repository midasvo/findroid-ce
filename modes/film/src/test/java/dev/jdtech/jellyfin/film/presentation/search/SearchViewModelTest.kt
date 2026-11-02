package dev.jdtech.jellyfin.film.presentation.search

import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val requests = mutableListOf<String>()
    private val viewModel by lazy { SearchViewModel(repository()) }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `typing a word sends one request`() = runTest(dispatcher) {
        "bluey".indices.forEach { end ->
            viewModel.onAction(SearchAction.Search("bluey".take(end + 1)))
            advanceTimeBy(SearchViewModel.SEARCH_DEBOUNCE_MS - 1)
        }
        advanceUntilIdle()

        assertEquals(listOf("bluey"), requests)
        assertEquals("bluey", viewModel.state.value.query)
        assertEquals(1, viewModel.state.value.items.size)
    }

    @Test
    fun `nothing is sent during the debounce`() = runTest(dispatcher) {
        viewModel.onAction(SearchAction.Search("bluey"))
        advanceTimeBy(SearchViewModel.SEARCH_DEBOUNCE_MS - 1)
        runCurrent()

        assertTrue(requests.isEmpty())
        assertFalse(viewModel.state.value.loading)
    }

    @Test
    fun `clearing the query empties the results at once`() = runTest(dispatcher) {
        search("bluey")
        assertEquals(1, viewModel.state.value.items.size)

        viewModel.onAction(SearchAction.Search(""))
        runCurrent()

        assertTrue(viewModel.state.value.items.isEmpty())
    }

    @Test
    fun `clearing the query drops the waiting request`() = runTest(dispatcher) {
        viewModel.onAction(SearchAction.Search("bluey"))
        advanceTimeBy(SearchViewModel.SEARCH_DEBOUNCE_MS - 1)
        viewModel.onAction(SearchAction.Search(""))
        advanceUntilIdle()

        assertTrue(requests.isEmpty())
    }

    private fun TestScope.search(query: String) {
        viewModel.onAction(SearchAction.Search(query))
        advanceUntilIdle()
    }

    private fun repository(): JellyfinRepository =
        Proxy.newProxyInstance(
            JellyfinRepository::class.java.classLoader,
            arrayOf(JellyfinRepository::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getSearchItems" -> {
                    requests += args!![0] as String
                    listOf(item())
                }
                else -> throw NotImplementedError("JellyfinRepository.${method.name}")
            }
        } as JellyfinRepository

    private fun item(): FindroidItem =
        Proxy.newProxyInstance(
            FindroidItem::class.java.classLoader,
            arrayOf(FindroidItem::class.java),
        ) { _, method, _ ->
            throw NotImplementedError("FindroidItem.${method.name}")
        } as FindroidItem
}
