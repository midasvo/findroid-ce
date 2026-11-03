package dev.jdtech.jellyfin.ce.watchnext

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What Watch Next keeps between syncs: whose items the programs are, which program shows which
 * item, and which items the user removed from the row.
 */
@Singleton
class WatchNextStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("watch_next", Context.MODE_PRIVATE)

    /** The user whose items the programs show. */
    var ownerUserId: String?
        get() = prefs.getString(KEY_OWNER, null)
        set(value) = prefs.edit { putString(KEY_OWNER, value) }

    /** Program id per item id. */
    var programs: Map<String, Long>
        get() = readMap(KEY_PROGRAMS)
        set(value) = writeMap(KEY_PROGRAMS, value)

    /** Position (ms) per item id at the moment the user removed it from the row. */
    var dismissed: Map<String, Long>
        get() = readMap(KEY_DISMISSED)
        set(value) = writeMap(KEY_DISMISSED, value)

    @Synchronized
    fun dismiss(programId: Long, positionMs: Long?) {
        val itemId = programs.entries.firstOrNull { it.value == programId }?.key ?: return
        dismissed = dismissed + (itemId to (positionMs ?: UNKNOWN_POSITION))
    }

    /**
     * Stores the dismissals a sync worked out from [before], keeping any the user made while the
     * sync ran.
     */
    @Synchronized
    fun updateDismissed(before: Map<String, Long>, after: Map<String, Long>) {
        dismissed = dismissed.filterKeys { it !in before } + after
    }

    fun clear() {
        prefs.edit { clear() }
    }

    private fun readMap(key: String): Map<String, Long> =
        prefs.getStringSet(key, emptySet()).orEmpty().mapNotNull { line ->
            val itemId = line.substringBefore(SEPARATOR)
            val value = line.substringAfter(SEPARATOR, "").toLongOrNull() ?: return@mapNotNull null
            itemId to value
        }.toMap()

    private fun writeMap(key: String, map: Map<String, Long>) {
        prefs.edit { putStringSet(key, map.map { (itemId, value) -> "$itemId$SEPARATOR$value" }.toSet()) }
    }

    private companion object {
        const val KEY_OWNER = "owner_user_id"
        const val KEY_PROGRAMS = "programs"
        const val KEY_DISMISSED = "dismissed"
        const val SEPARATOR = "="
    }
}
