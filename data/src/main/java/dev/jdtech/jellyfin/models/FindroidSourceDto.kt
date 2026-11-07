package dev.jdtech.jellyfin.models

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import java.util.UUID

@Entity(tableName = "sources", indices = [Index("itemId")])
data class FindroidSourceDto(
    @PrimaryKey val id: String,
    val itemId: UUID,
    val name: String,
    val type: FindroidSourceType,
    val path: String,
    val downloadId: Long? = null,
    /**
     * The download-quality bitrate cap (bits/sec) this source was requested with,
     * or null for original quality. Persisted so a resume after process death
     * re-resolves the *same* URL kind — resuming a partial transcode with an
     * original-file URL (or vice versa) would append mismatched bytes.
     */
    val downloadMaxBitrate: Int? = null,
    /** Epoch ms at which the download finished; null while in flight and for older rows. */
    val downloadedAt: Long? = null,
    /**
     * True when Smart Downloads queued this download itself, which is the only kind it may
     * delete later. Rows from before the column existed default to false (user-owned).
     */
    @ColumnInfo(defaultValue = "0") val smartQueued: Boolean = false,
)

fun FindroidSource.toFindroidSourceDto(itemId: UUID, path: String): FindroidSourceDto {
    return FindroidSourceDto(
        id = id,
        itemId = itemId,
        name = name,
        type = FindroidSourceType.LOCAL,
        path = path,
    )
}

/** Reads the sources of all [itemIds] with a few `IN` queries instead of one query per item. */
suspend fun ServerDatabaseDao.getSourcesGroupedByItem(
    itemIds: Collection<UUID>
): Map<UUID, List<FindroidSourceDto>> =
    itemIds
        .distinct()
        .chunked(SOURCE_QUERY_CHUNK)
        .flatMap { getSourcesByItemIds(it) }
        .groupBy { it.itemId }

// Well under SQLite's bound-variable limit, which is 999 on older Android versions.
private const val SOURCE_QUERY_CHUNK = 500
