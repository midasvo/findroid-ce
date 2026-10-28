package dev.jdtech.jellyfin.presentation.film.components

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.jdtech.jellyfin.core.presentation.dummy.dummyMovie
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme

private val GroupOuterCorner = 20.dp
private val GroupInnerCorner = 4.dp

/**
 * Corner shape for row [index] of a grouped list of [count] rows: large outer corners on the
 * first and last row, small inner ones in between, so adjacent rows read as one rounded group.
 */
fun groupedItemShape(index: Int, count: Int): Shape {
    val top = if (index == 0) GroupOuterCorner else GroupInnerCorner
    val bottom = if (index == count - 1) GroupOuterCorner else GroupInnerCorner
    return RoundedCornerShape(
        topStart = top,
        topEnd = top,
        bottomStart = bottom,
        bottomEnd = bottom,
    )
}

/**
 * One row of the Downloads screen: 16:9 artwork, up to three lines of text and an optional
 * trailing control. Shared by in-progress downloads and the downloaded library.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DownloadRow(
    item: FindroidItem,
    title: String,
    meta: String,
    shape: Shape,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    metaColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    showPlaybackProgress: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier,
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .then(
                        if (onClick != null || onLongClick != null) {
                            Modifier.combinedClickable(
                                onClick = onClick ?: {},
                                onLongClick = onLongClick,
                            )
                        } else Modifier
                    )
                    .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DownloadArtwork(
                item = item,
                showPlaybackProgress = showPlaybackProgress,
                modifier = Modifier.width(112.dp),
            )
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = if (supporting == null) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
                supporting?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = meta,
                    // Tabular figures keep sizes and times from jittering as they update.
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = metaColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            trailing?.invoke()
        }
    }
}

/** 16:9 artwork for a download row, preferring wide images over cropped posters. */
@Composable
fun DownloadArtwork(
    item: FindroidItem,
    modifier: Modifier = Modifier,
    showPlaybackProgress: Boolean = false,
) {
    val context = LocalContext.current
    var imageUri =
        when (item) {
            is FindroidEpisode -> item.images.primary ?: item.images.showBackdrop
            else -> item.images.backdrop ?: item.images.primary
        }
    // Downloaded images are stored as paths relative to the files directory.
    if (imageUri != null && imageUri.scheme == null) {
        imageUri =
            Uri.Builder()
                .appendEncodedPath("${context.filesDir}")
                .appendEncodedPath(imageUri.path)
                .build()
    }
    Box(
        modifier =
            modifier
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        AsyncImage(
            model = imageUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        val progress =
            if (showPlaybackProgress && item.runtimeTicks > 0) {
                (item.playbackPositionTicks.toFloat() / item.runtimeTicks).coerceIn(0f, 1f)
            } else 0f
        if (progress > 0f) {
            Box(
                modifier =
                    Modifier.align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.Black.copy(alpha = 0.4f))
            ) {
                Box(
                    modifier =
                        Modifier.fillMaxHeight()
                            .fillMaxWidth(progress)
                            .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

/** Red action revealed behind a row while it is swiped, clipped to the row's [shape]. */
@Composable
fun SwipeActionBackground(icon: Int, shape: Shape, modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier.fillMaxSize().clip(shape).background(MaterialTheme.colorScheme.errorContainer),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.padding(horizontal = 24.dp),
            tint = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DownloadRowPreview() {
    FindroidTheme {
        DownloadRow(
            item = dummyMovie,
            title = dummyMovie.name,
            meta = "2024 · 87 mins · 1.7 GB",
            shape = groupedItemShape(0, 1),
            showPlaybackProgress = true,
        )
    }
}
