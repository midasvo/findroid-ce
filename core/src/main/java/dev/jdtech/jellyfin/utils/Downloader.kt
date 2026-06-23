package dev.jdtech.jellyfin.utils

import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidSource
import dev.jdtech.jellyfin.models.UiText

interface Downloader {
    suspend fun downloadItem(
        item: FindroidItem,
        sourceId: String,
        storageIndex: Int = 0,
    ): Pair<Long, UiText?>

    /**
     * Resolves the first available media source for the item and starts the download.
     * Returns (downloadId, null) on success or (-1L, errorText) on failure.
     */
    suspend fun downloadItem(
        item: FindroidItem,
        storageIndex: Int = 0,
    ): Pair<Long, UiText?>

    suspend fun cancelDownload(item: FindroidItem, downloadId: Long)

    suspend fun deleteItem(item: FindroidItem, source: FindroidSource)

    /** Snapshot from the OkHttp download engine for a single download id. */
    data class Progress(
        /** DownloadStatus.* integer constant (PENDING=1, RUNNING=2, PAUSED=4, SUCCESSFUL=8, FAILED=16). */
        val status: Int,
        /** Progress percentage 0..100, or -1 if unknown. */
        val progress: Int,
        /** Bytes downloaded so far, or -1 if unknown. */
        val bytesDownloaded: Long,
        /** Total bytes for the download, or -1 if unknown. */
        val totalBytes: Long,
    )

    suspend fun getProgress(downloadId: Long?): Progress

    /**
     * Batched progress lookup from the OkHttp download engine.
     * Missing ids are returned with a FAILED status (the engine has no record for them).
     */
    suspend fun getProgress(downloadIds: List<Long>): Map<Long, Progress>

    /**
     * Returns every in-flight download known to the DB as (item, downloadId) pairs.
     * Used on app startup to re-attach the queue to partial downloads that survived
     * process death. The engine (OkHttp) does not persist across processes, so these
     * entries are restored as Pending and resumed via Range request on the next pump cycle.
     */
    suspend fun getActiveDownloads(): List<Pair<FindroidItem, Long>>

    /** Persists a pending queue entry so it can be re-queued after process death. */
    suspend fun savePendingDownload(item: FindroidItem)

    /** Removes a persisted pending entry (on start/remove/retry). */
    suspend fun removePendingDownload(itemId: java.util.UUID)

    /**
     * Returns previously queued but not-yet-started items paired with the timestamp
     * they were originally added (ms since epoch).
     *
     * If an item cannot be resolved (server unreachable, offline mode, item deleted),
     * the row is **kept** for retry on the next app start. Rows are only deleted when
     * their [itemKind] is unrecognized, or when the row is older than 30 days and still
     * cannot be resolved (indicating the item was likely permanently deleted server-side).
     */
    suspend fun getPendingDownloads(): List<Pair<FindroidItem, Long>>

    /**
     * Drops DB rows and on-disk files whose state no longer matches reality:
     *   - completed sources whose file has been deleted externally
     *   - active .download sources whose engine task is gone and file is missing
     *   - orphan .download files not referenced by any DB source row
     *
     * Run on app startup before restoring active downloads.
     */
    suspend fun sweepOrphans()

    /**
     * Promotes a finished download from `<path>.download` to `<path>` and updates
     * the DB source path so the Library tab can find it. Idempotent — safe to call
     * from both the pump and the broadcast receiver, in either order. Returns true
     * if the source is in a finalized state after the call (already finalized, or
     * just finalized successfully).
     */
    suspend fun finalizeDownload(downloadId: Long): Boolean
}
