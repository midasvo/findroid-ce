package dev.jdtech.jellyfin.models

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
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
