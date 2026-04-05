package dev.jdtech.jellyfin.core.presentation.downloader

enum class DownloadStatus {
    NONE,
    QUEUED,
    PENDING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
}

data class DownloadProgress(
    val status: DownloadStatus = DownloadStatus.NONE,
    val progress: Float = 0f,
)
