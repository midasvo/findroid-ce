package dev.jdtech.jellyfin.core.presentation.downloader

import dev.jdtech.jellyfin.models.FindroidItem

sealed interface DownloaderAction {
    /** [maxBitrate] = download quality cap in bits/sec, null for original quality. */
    data class Download(val item: FindroidItem, val maxBitrate: Int? = null) : DownloaderAction

    data class DeleteDownload(val item: FindroidItem) : DownloaderAction

    data class CancelDownload(val item: FindroidItem) : DownloaderAction
}
