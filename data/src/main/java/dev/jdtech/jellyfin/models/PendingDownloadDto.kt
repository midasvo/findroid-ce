package dev.jdtech.jellyfin.models

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import java.util.UUID

/**
 * Persisted record of a queued download waiting for a free slot. When the app is
 * killed while items are still Pending (not yet picked up by the download pump),
 * this table lets us recover them on next launch. Rows are deleted once the item
 * transitions to Downloading or is removed by the user.
 */
@Entity(tableName = "pending_downloads")
data class PendingDownloadDto(
    @PrimaryKey val itemId: UUID,
    val itemKind: String,
    val addedAt: Long,
    /** Download-quality bitrate cap (bits/sec) chosen for this item; null = original. */
    val maxBitrate: Int? = null,
)
