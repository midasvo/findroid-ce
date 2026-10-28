package dev.jdtech.jellyfin.presentation.film.components

import android.text.format.Formatter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.downloader.DownloadProgress
import dev.jdtech.jellyfin.core.presentation.downloader.DownloadStatus
import dev.jdtech.jellyfin.core.presentation.dummy.dummyEpisode
import dev.jdtech.jellyfin.film.presentation.downloads.ActiveDownload
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import kotlin.math.roundToInt

/** A download in progress: artwork, title, one status fact and the progress ring. */
@Composable
fun ActiveDownloadRow(
    activeDownload: ActiveDownload,
    shape: Shape,
    onCancelClick: () -> Unit,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = activeDownload.progress.status

    // A light tap when a download finishes while the user is watching it.
    val haptics = LocalHapticFeedback.current
    var previousStatus by remember { mutableStateOf(status) }
    LaunchedEffect(status) {
        if (status == DownloadStatus.COMPLETED && previousStatus != DownloadStatus.COMPLETED) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        }
        previousStatus = status
    }

    val animatedProgress by
        animateFloatAsState(
            targetValue =
                if (status == DownloadStatus.COMPLETED) 1f else activeDownload.progress.progress,
            animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        )
    val controlState =
        when (status) {
            DownloadStatus.QUEUED -> DownloadControlState.WAITING
            DownloadStatus.PAUSED -> DownloadControlState.PAUSED
            DownloadStatus.FAILED -> DownloadControlState.FAILED
            DownloadStatus.COMPLETED -> DownloadControlState.COMPLETED
            // A transcode of unknown length has no percentage to show yet.
            DownloadStatus.DOWNLOADING ->
                if (activeDownload.totalBytes > 0) DownloadControlState.DOWNLOADING
                else DownloadControlState.PREPARING
            else -> DownloadControlState.PREPARING
        }

    val item = activeDownload.item
    val (title, supporting) = downloadTitles(item)
    DownloadRow(
        item = item,
        title = title,
        supporting = supporting,
        meta = statusLine(activeDownload),
        metaColor =
            if (status == DownloadStatus.FAILED) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = shape,
        modifier = modifier,
        trailing = {
            DownloadProgressButton(
                state = controlState,
                progress = { animatedProgress },
                contentDescription =
                    when (controlState) {
                        DownloadControlState.FAILED -> stringResource(CoreR.string.retry_download)
                        DownloadControlState.COMPLETED ->
                            stringResource(CoreR.string.download_completed)
                        else -> stringResource(CoreR.string.cancel_download)
                    },
                stateDescription =
                    if (controlState == DownloadControlState.DOWNLOADING) {
                        "${(animatedProgress * 100).roundToInt()}%"
                    } else null,
                onClick =
                    when (controlState) {
                        DownloadControlState.FAILED -> onRetryClick
                        DownloadControlState.COMPLETED -> null
                        else -> onCancelClick
                    },
            )
        },
    )
}

/**
 * Title and optional second line for a download row. Episodes put the series name first and
 * the episode on its own line, so a long series name can't truncate away which episode it is.
 */
@Composable
fun downloadTitles(item: FindroidItem): Pair<String, String?> {
    if (item !is FindroidEpisode) return item.name to null
    val episodeLabel =
        stringResource(
            CoreR.string.episode_name_extended,
            item.parentIndexNumber,
            item.indexNumber,
            item.name,
        )
    return if (item.seriesName.isNotEmpty()) item.seriesName to episodeLabel
    else episodeLabel to null
}

/** The single most useful fact about a download right now. */
@Composable
private fun statusLine(download: ActiveDownload): String {
    val context = LocalContext.current
    fun size(bytes: Long) = Formatter.formatShortFileSize(context, bytes)
    return when (download.progress.status) {
        DownloadStatus.QUEUED -> stringResource(CoreR.string.download_queued)
        DownloadStatus.PENDING -> stringResource(CoreR.string.download_pending)
        DownloadStatus.PAUSED -> stringResource(CoreR.string.download_waiting_for_network)
        DownloadStatus.FAILED ->
            download.errorText?.asString() ?: stringResource(CoreR.string.download_failed)
        DownloadStatus.COMPLETED -> stringResource(CoreR.string.download_completed)
        DownloadStatus.DOWNLOADING -> {
            val total = download.totalBytes
            val done = download.bytesDownloaded.coerceAtLeast(0L)
            if (total <= 0L) {
                // A transcode still being produced: at least show that bytes are arriving.
                val preparing = stringResource(CoreR.string.download_preparing_on_server)
                if (done > 0) "$preparing · ${size(done)}" else preparing
            } else {
                // The total of a transcode is estimated from the original file.
                val totalText = (if (download.totalBytesEstimated) "~" else "") + size(total)
                val bytes = stringResource(CoreR.string.download_bytes_of, size(done), totalText)
                val remaining = total - done
                if (download.bytesPerSecond > 0 && remaining > 0) {
                    "$bytes · ${timeLeft(remaining / download.bytesPerSecond)}"
                } else bytes
            }
        }
        else -> ""
    }
}

/** Remaining time in whole minutes, so the estimate doesn't flicker every second. */
@Composable
private fun timeLeft(seconds: Long): String {
    val minutes = ((seconds + 59) / 60).toInt()
    return when {
        seconds < 60 -> stringResource(CoreR.string.download_time_left_under_minute)
        minutes < 60 -> stringResource(CoreR.string.download_time_left_minutes, minutes)
        else -> stringResource(CoreR.string.download_time_left_hours, minutes / 60, minutes % 60)
    }
}

@Preview
@Composable
private fun ActiveDownloadRowPreview() {
    FindroidTheme {
        ActiveDownloadRow(
            activeDownload =
                ActiveDownload(
                    item = dummyEpisode,
                    progress =
                        DownloadProgress(status = DownloadStatus.DOWNLOADING, progress = 0.33f),
                    downloadId = 1L,
                    bytesDownloaded = 164_000_000L,
                    totalBytes = 498_000_000L,
                    bytesPerSecond = 6_400_000L,
                ),
            shape = groupedItemShape(0, 1),
            onCancelClick = {},
            onRetryClick = {},
        )
    }
}
