package dev.jdtech.jellyfin.film.presentation.home

import dev.jdtech.jellyfin.models.HomeItem
import dev.jdtech.jellyfin.models.HomeSection
import dev.jdtech.jellyfin.models.UiText
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeRowsOrderTest {
    private val suggestions = HomeItem.Suggestions(UUID.randomUUID(), emptyList())
    private val resume = section()
    private val nextUp = section()

    private fun section() =
        HomeItem.Section(HomeSection(UUID.randomUUID(), UiText.DynamicString(""), emptyList()))

    private val state =
        HomeState(
            suggestionsSection = suggestions,
            resumeSection = resume,
            nextUpSection = nextUp,
        )

    @Test
    fun `without an order the rows keep the default order`() {
        assertEquals(listOf(suggestions, resume, nextUp), state.orderedRows())
    }

    @Test
    fun `rows follow the stored order and hidden rows aren't there`() {
        val rows =
            state
                .copy(
                    suggestionsSection = null,
                    rowOrder = listOf("next_up", "suggestions", "continue_watching"),
                )
                .orderedRows()

        assertEquals(listOf(nextUp, resume), rows)
    }

    @Test
    fun `a row missing from the order goes at the end`() {
        val rows = state.copy(rowOrder = listOf("next_up")).orderedRows()

        assertEquals(listOf(nextUp, suggestions, resume), rows)
    }
}
