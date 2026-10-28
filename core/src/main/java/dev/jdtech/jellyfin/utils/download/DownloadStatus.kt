package dev.jdtech.jellyfin.utils.download

/**
 * Integer status constants for in-app OkHttp downloads. Values are intentionally identical to
 * [android.app.DownloadManager].STATUS_* so any code that switches on these integers works
 * unchanged after the DownloadManager migration.
 */
object DownloadStatus {
    const val PENDING = 1
    const val RUNNING = 2
    const val PAUSED = 4
    const val SUCCESSFUL = 8
    const val FAILED = 16
}
