package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.Constants
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.downloader.DownloadProgress
import dev.jdtech.jellyfin.core.presentation.downloader.DownloadStatus
import dev.jdtech.jellyfin.core.presentation.dummy.dummyEpisode
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import kotlin.math.roundToInt

/** One episode of a season, in the row style of the Downloads screen. */
@Composable
fun EpisodeCard(
    episode: FindroidEpisode,
    shape: Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    downloadProgress: DownloadProgress? = null,
    onPlayClick: (() -> Unit)? = null,
    onDownloadClick: (() -> Unit)? = null,
    onDownloadCancelClick: (() -> Unit)? = null,
    onDownloadRetryClick: (() -> Unit)? = null,
    onDownloadRemoveClick: (() -> Unit)? = null,
) {
    val meta =
        buildList {
                add(stringResource(CoreR.string.episode_number, episode.indexNumber))
                if (episode.runtimeTicks > 0) {
                    add(
                        stringResource(
                            CoreR.string.runtime_minutes,
                            (episode.runtimeTicks / Constants.TICKS_PER_MINUTE).toInt(),
                        )
                    )
                }
            }
            .joinToString(" · ")

    DownloadRow(
        item = episode,
        title = episode.name,
        meta = meta,
        shape = shape,
        showPlaybackProgress = true,
        showPlayedBadge = true,
        onPlayClick = onPlayClick,
        onClick = onClick,
        footer = episode.overview.takeIf { it.isNotBlank() },
        trailing = {
            EpisodeDownloadControl(
                downloadProgress = downloadProgress,
                onDownloadClick = onDownloadClick,
                onCancelClick = onDownloadCancelClick,
                onRetryClick = onDownloadRetryClick,
                onRemoveClick = onDownloadRemoveClick,
            )
        },
        modifier = modifier,
    )
}

@Composable
private fun EpisodeDownloadControl(
    downloadProgress: DownloadProgress?,
    onDownloadClick: (() -> Unit)?,
    onCancelClick: (() -> Unit)?,
    onRetryClick: (() -> Unit)?,
    onRemoveClick: (() -> Unit)?,
) {
    val status = downloadProgress?.status ?: DownloadStatus.NONE
    if (status == DownloadStatus.NONE) {
        if (onDownloadClick != null) {
            IconButton(onClick = onDownloadClick, modifier = Modifier.size(48.dp)) {
                Icon(
                    painter = painterResource(CoreR.drawable.ic_download),
                    contentDescription = stringResource(CoreR.string.download_button_description),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        return
    }

    val animatedProgress by
        animateFloatAsState(
            targetValue = downloadProgress?.progress ?: 0f,
            animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        )
    val controlState =
        when (status) {
            DownloadStatus.QUEUED -> DownloadControlState.WAITING
            DownloadStatus.DOWNLOADING -> DownloadControlState.DOWNLOADING
            DownloadStatus.PAUSED -> DownloadControlState.PAUSED
            DownloadStatus.FAILED -> DownloadControlState.FAILED
            DownloadStatus.COMPLETED -> DownloadControlState.COMPLETED
            else -> DownloadControlState.PREPARING
        }
    DownloadProgressButton(
        state = controlState,
        progress = { animatedProgress },
        contentDescription =
            when (controlState) {
                DownloadControlState.FAILED -> stringResource(CoreR.string.retry_download)
                DownloadControlState.COMPLETED -> stringResource(CoreR.string.remove_download)
                else -> stringResource(CoreR.string.cancel_download)
            },
        stateDescription =
            if (controlState == DownloadControlState.DOWNLOADING) {
                "${(animatedProgress * 100).roundToInt()}%"
            } else null,
        onClick =
            when (controlState) {
                DownloadControlState.FAILED -> onRetryClick
                DownloadControlState.COMPLETED -> onRemoveClick
                else -> onCancelClick
            },
    )
}

@Preview(showBackground = true)
@Composable
private fun EpisodeCardPreview() {
    FindroidTheme {
        EpisodeCard(
            episode = dummyEpisode,
            shape = groupedItemShape(0, 1),
            onClick = {},
            onDownloadClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EpisodeCardDownloadingPreview() {
    FindroidTheme {
        EpisodeCard(
            episode = dummyEpisode,
            shape = groupedItemShape(0, 1),
            onClick = {},
            downloadProgress =
                DownloadProgress(status = DownloadStatus.DOWNLOADING, progress = 0.4f),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EpisodeCardDownloadedPreview() {
    FindroidTheme {
        EpisodeCard(
            episode = dummyEpisode,
            shape = groupedItemShape(0, 1),
            onClick = {},
            downloadProgress = DownloadProgress(status = DownloadStatus.COMPLETED, progress = 1f),
            onDownloadRemoveClick = {},
        )
    }
}
