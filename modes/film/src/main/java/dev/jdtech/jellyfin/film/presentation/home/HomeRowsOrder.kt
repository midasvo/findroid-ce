package dev.jdtech.jellyfin.film.presentation.home

import dev.jdtech.jellyfin.film.domain.homerows.HomeRowType
import dev.jdtech.jellyfin.film.domain.homerows.homeRowKey
import dev.jdtech.jellyfin.models.HomeItem

/**
 * CE (#142): the loaded rows in the order the user chose. Hidden rows aren't loaded, so they're not
 * here. A row missing from [HomeState.rowOrder] (a library that's new since the order was set) goes
 * at the end. For phone and TV.
 */
fun HomeState.orderedRows(): List<HomeItem> {
    // Inserted in the default order, which is what's left when rowOrder is empty.
    val byKey = buildMap {
        suggestionsSection?.let { put(homeRowKey(HomeRowType.SUGGESTIONS), it) }
        resumeSection?.let { put(homeRowKey(HomeRowType.CONTINUE_WATCHING), it) }
        nextUpSection?.let { put(homeRowKey(HomeRowType.NEXT_UP), it) }
        views.forEach { put(homeRowKey(HomeRowType.LATEST, it.id), it) }
    }
    val ordered = rowOrder.filter { it in byKey }
    return (ordered + (byKey.keys - ordered.toSet())).map { byKey.getValue(it) }
}
