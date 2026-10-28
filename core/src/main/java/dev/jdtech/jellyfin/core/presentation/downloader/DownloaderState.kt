package dev.jdtech.jellyfin.core.presentation.downloader

import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.utils.download.DownloadStatus

data class DownloaderState(
    val status: Int = 0,
    val progress: Float = 0f,
    val errorText: UiText? = null,
) {
    val isDownloading: Boolean
        get() =
            status in
                arrayOf(
                    DownloadStatus.PENDING,
                    DownloadStatus.RUNNING,
                    DownloadStatus.PAUSED,
                    DownloadStatus.FAILED,
                )
}
