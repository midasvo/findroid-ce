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

    suspend fun getProgress(downloadId: Long?): Pair<Int, Int>

    /**
     * Returns every in-flight download known to the DB as (item, downloadId) pairs.
     * Used on app startup to re-attach the queue to Android DownloadManager
     * jobs that survived process death.
     */
    suspend fun getActiveDownloads(): List<Pair<FindroidItem, Long>>
}
