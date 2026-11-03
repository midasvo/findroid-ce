package dev.jdtech.jellyfin.film.presentation.homerows

import dev.jdtech.jellyfin.film.domain.homerows.HomeRowConfig
import dev.jdtech.jellyfin.models.UiText

/** CE (#142): a row on the Home rows settings screen. */
data class HomeRowEntry(val row: HomeRowConfig, val title: UiText) {
    val key: String
        get() = row.key
}

data class HomeRowsState(
    val rows: List<HomeRowEntry> = emptyList(),
    val isLoading: Boolean = true,
    val error: Exception? = null,
)
