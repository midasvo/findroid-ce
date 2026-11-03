package dev.jdtech.jellyfin.film.domain.homerows

import java.util.UUID

/** CE (#142): the rows Home can show. [id] is what gets stored, so it never changes. */
enum class HomeRowType(val id: String) {
    SUGGESTIONS("suggestions"),
    CONTINUE_WATCHING("continue_watching"),
    NEXT_UP("next_up"),
    LATEST("latest"),
    FAVORITES("favorites"),
}

/** One row on Home. Latest has a row per library, told apart by [libraryId]. */
data class HomeRowConfig(
    val type: HomeRowType,
    val libraryId: UUID? = null,
    val visible: Boolean = true,
) {
    val key: String
        get() = homeRowKey(type, libraryId)
}

fun homeRowKey(type: HomeRowType, libraryId: UUID? = null): String =
    if (libraryId == null) type.id else "${type.id}:$libraryId"

/** The switches Home had before #142. They set the layout of anyone who hasn't changed it yet. */
data class LegacyHomeSwitches(
    val suggestions: Boolean = true,
    val continueWatching: Boolean = true,
    val nextUp: Boolean = true,
    val latest: Boolean = true,
)

/**
 * CE (#142): the order and visibility of the rows on Home, per user and server.
 *
 * Nothing is stored until the user changes the layout. Until then it comes from the old switches,
 * so nobody loses their choice, and another user on the same device starts from them too.
 */
object HomeRows {
    /** The order Home had before #142, plus Favorites at the end, hidden so Home stays the same. */
    fun defaults(legacy: LegacyHomeSwitches, libraries: List<UUID>): List<HomeRowConfig> =
        buildList {
            add(HomeRowConfig(HomeRowType.SUGGESTIONS, visible = legacy.suggestions))
            add(HomeRowConfig(HomeRowType.CONTINUE_WATCHING, visible = legacy.continueWatching))
            add(HomeRowConfig(HomeRowType.NEXT_UP, visible = legacy.nextUp))
            libraries.forEach { add(HomeRowConfig(HomeRowType.LATEST, it, legacy.latest)) }
            add(HomeRowConfig(HomeRowType.FAVORITES, visible = false))
        }

    /**
     * The layout to use. With [libraries] known, Latest rows of libraries that are gone are left
     * out, and new libraries are added at the end, visible. With [libraries] null (not loaded),
     * the stored Latest rows stay as they are.
     */
    fun resolve(
        stored: List<HomeRowConfig>?,
        libraries: List<UUID>?,
        legacy: LegacyHomeSwitches,
    ): List<HomeRowConfig> {
        if (stored == null) return defaults(legacy, libraries.orEmpty())

        val rows =
            stored
                .distinctBy { it.key }
                .filter { it.type != HomeRowType.LATEST || libraries == null || it.libraryId in libraries }
                .toMutableList()
        val keys = rows.mapTo(mutableSetOf()) { it.key }
        // A row type added in a later version, so a stored layout is never short of one.
        defaults(LegacyHomeSwitches(), emptyList())
            .filter { it.key !in keys }
            .forEach { rows.add(it) }
        libraries
            ?.filter { homeRowKey(HomeRowType.LATEST, it) !in keys }
            ?.forEach { rows.add(HomeRowConfig(HomeRowType.LATEST, it, visible = true)) }
        return rows
    }

    /**
     * Whether Home has to ask for the libraries: only to leave them all out does it not. That's
     * the case for the old Latest switch turned off, as long as nothing is stored.
     */
    fun needsLibraries(stored: List<HomeRowConfig>?, legacy: LegacyHomeSwitches): Boolean =
        stored != null || legacy.latest

    /** Moves the row with [key] [by] places, staying inside the list. */
    fun move(rows: List<HomeRowConfig>, key: String, by: Int): List<HomeRowConfig> {
        val from = rows.indexOfFirst { it.key == key }
        if (from < 0) return rows
        val to = (from + by).coerceIn(0, rows.lastIndex)
        if (to == from) return rows
        return rows.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun setVisible(rows: List<HomeRowConfig>, key: String, visible: Boolean): List<HomeRowConfig> =
        rows.map { if (it.key == key) it.copy(visible = visible) else it }

    /** One row per entry, `type,libraryId,1`, entries split by `;`. Ids hold neither. */
    fun encode(rows: List<HomeRowConfig>): String =
        rows.joinToString(";") {
            "${it.type.id},${it.libraryId ?: ""},${if (it.visible) 1 else 0}"
        }

    /** Null for nothing stored. Entries it can't read are skipped. */
    fun decode(value: String?): List<HomeRowConfig>? {
        if (value == null) return null
        return value.split(";").mapNotNull { entry ->
            val parts = entry.split(",")
            if (parts.size != 3) return@mapNotNull null
            val type = HomeRowType.entries.firstOrNull { it.id == parts[0] } ?: return@mapNotNull null
            val libraryId =
                if (parts[1].isEmpty()) null
                else runCatching { UUID.fromString(parts[1]) }.getOrNull() ?: return@mapNotNull null
            if ((type == HomeRowType.LATEST) != (libraryId != null)) return@mapNotNull null
            HomeRowConfig(type, libraryId, parts[2] == "1")
        }
    }
}
