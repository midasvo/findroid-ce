package dev.jdtech.jellyfin.core.presentation.downloader

import dev.jdtech.jellyfin.models.UiText

sealed interface DownloaderEvent {
    data object Successful : DownloaderEvent

    data object Deleted : DownloaderEvent

    data class Failed(val errorText: UiText?) : DownloaderEvent
}
