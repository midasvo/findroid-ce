package dev.jdtech.jellyfin.ce.watchnext

import kotlinx.serialization.Serializable

/** Which part of the launcher's Watch Next row an item belongs to. */
enum class WatchNextKind {
    /** Started but not finished: `WATCH_NEXT_TYPE_CONTINUE`. */
    CONTINUE,
    /** The next episode of a series in progress: `WATCH_NEXT_TYPE_NEXT`. */
    NEXT,
}

enum class WatchNextItemType {
    MOVIE,
    EPISODE,
}

/**
 * What one Watch Next program shows. It is also stored in the program itself (as internal provider
 * data), so a sync can tell whether the program needs an update.
 */
@Serializable
data class WatchNextEntry(
    val itemId: String,
    val kind: WatchNextKind,
    val itemType: WatchNextItemType,
    val title: String,
    val episodeTitle: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val description: String = "",
    val posterUri: String? = null,
    val posterIsPortrait: Boolean = false,
    val durationMs: Long = 0,
    val positionMs: Long = 0,
    /** When the user last watched it. Null when the server doesn't say (next up); see [planWatchNext]. */
    val lastEngagementMs: Long? = null,
)

/** A program this app has in the launcher's Watch Next table. */
data class PublishedProgram(
    val programId: Long,
    /** The internal provider id: the Jellyfin item id. */
    val itemId: String?,
    /** False once the user removed it from the row. */
    val browsable: Boolean,
    /** What the program was last published with, or null if it can't be read back. */
    val entry: WatchNextEntry?,
)

data class WatchNextPlan(
    val inserts: List<WatchNextEntry>,
    val updates: List<Pair<Long, WatchNextEntry>>,
    val deletes: List<Long>,
    /** Items the user removed from the row, with their position at that moment. */
    val dismissed: Map<String, Long>,
)

/**
 * Decides how the launcher's Watch Next programs change to match [candidates]: the server's
 * Continue Watching items followed by its Next Up episodes, each in the server's order.
 *
 * - An item gets one program. Its program is updated when what it shows changed, and deleted when
 *   the item is no longer a candidate (finished, or no longer in Continue Watching or Next Up).
 * - An item the user removed from the row ([dismissed], or a program that is no longer browsable)
 *   isn't added back until its position changes, that is until it is watched again. A dismissal is
 *   forgotten once the item stops being a candidate. A dismissal with [UNKNOWN_POSITION] takes the
 *   item's current position.
 * - The server gives no engagement time for Next Up. Such an item keeps the time it was first
 *   published with, or gets [nowMs] minus its rank, so new items keep the server's order.
 * - At most [maxPrograms] programs, the most recently engaged ones.
 */
fun planWatchNext(
    candidates: List<WatchNextEntry>,
    published: List<PublishedProgram>,
    dismissed: Map<String, Long>,
    nowMs: Long,
    maxPrograms: Int = MAX_WATCH_NEXT_PROGRAMS,
): WatchNextPlan {
    val unique = candidates.distinctBy { it.itemId }
    val publishedByItem = published.filter { it.itemId != null }.groupBy { it.itemId!! }

    val allDismissed = dismissed.toMutableMap()
    for (program in published) {
        if (!program.browsable && program.itemId != null) {
            allDismissed.putIfAbsent(program.itemId, program.entry?.positionMs ?: 0)
        }
    }
    val candidatePositions = unique.associate { it.itemId to it.positionMs }
    val stillDismissed =
        allDismissed
            .mapValues { (itemId, position) ->
                if (position == UNKNOWN_POSITION) candidatePositions[itemId] else position
            }
            .filter { (itemId, position) -> position != null && candidatePositions[itemId] == position }
            .mapValues { it.value!! }

    val wanted =
        unique
            .mapIndexed { rank, entry ->
                val engagement =
                    entry.lastEngagementMs
                        ?: publishedByItem[entry.itemId]
                            ?.firstNotNullOfOrNull { it.entry?.lastEngagementMs }
                        ?: (nowMs - rank)
                entry.copy(lastEngagementMs = engagement)
            }
            .filter { it.itemId !in stillDismissed }
            .sortedByDescending { it.lastEngagementMs }
            .take(maxPrograms)

    val inserts = mutableListOf<WatchNextEntry>()
    val updates = mutableListOf<Pair<Long, WatchNextEntry>>()
    val deletes = published.filter { it.itemId == null }.map { it.programId }.toMutableList()
    val wantedIds = wanted.map { it.itemId }.toSet()

    for ((itemId, programs) in publishedByItem) {
        // A program per item: drop extras, and every program of an item that is no longer wanted.
        val keep = programs.firstOrNull { itemId in wantedIds && it.browsable }
        deletes += programs.filter { it !== keep }.map { it.programId }
    }
    for (entry in wanted) {
        val program = publishedByItem[entry.itemId]?.firstOrNull { it.browsable }
        when {
            program == null -> inserts += entry
            program.entry != entry -> updates += program.programId to entry
        }
    }

    return WatchNextPlan(
        inserts = inserts,
        updates = updates,
        deletes = deletes,
        dismissed = stillDismissed,
    )
}

/** A dismissal whose position isn't known: the program was gone before it could be read. */
const val UNKNOWN_POSITION = -1L

/** Launchers show only the first few; more programs only cost work on every sync. */
const val MAX_WATCH_NEXT_PROGRAMS = 20
