package dev.jdtech.jellyfin.film.presentation.search

import dev.jdtech.jellyfin.models.FindroidItem

data class SearchState(
    /** The query [items] and [error] belong to. */
    val query: String = "",
    val items: List<FindroidItem> = emptyList(),
    val loading: Boolean = false,
    val error: Exception? = null,
)
