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
    private val favorites = section()

    private fun section() =
        HomeItem.Section(HomeSection(UUID.randomUUID(), UiText.DynamicString(""), emptyList()))

    private val state =
        HomeState(
            suggestionsSection = suggestions,
            resumeSection = resume,
            nextUpSection = nextUp,
            favoritesSection = favorites,
        )

    @Test
    fun `without an order the rows keep the default order`() {
        assertEquals(listOf(suggestions, resume, nextUp, favorites), state.orderedRows())
    }

    @Test
    fun `rows follow the stored order and hidden rows aren't there`() {
        val rows =
            state
                .copy(
                    suggestionsSection = null,
                    rowOrder = listOf("favorites", "suggestions", "next_up", "continue_watching"),
                )
                .orderedRows()

        assertEquals(listOf(favorites, nextUp, resume), rows)
    }

    @Test
    fun `a row missing from the order goes at the end`() {
        val rows = state.copy(rowOrder = listOf("next_up", "suggestions")).orderedRows()

        assertEquals(listOf(nextUp, suggestions, resume, favorites), rows)
    }
}
