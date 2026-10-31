package dev.jdtech.jellyfin.film.presentation.collection

import dev.jdtech.jellyfin.models.CollectionSection

data class CollectionState(
    val sections: List<CollectionSection> = emptyList(),
    val isLoading: Boolean = true,
    val error: Exception? = null,
)
