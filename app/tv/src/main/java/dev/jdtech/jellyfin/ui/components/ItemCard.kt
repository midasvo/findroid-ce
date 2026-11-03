package dev.jdtech.jellyfin.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.jdtech.jellyfin.core.R
import dev.jdtech.jellyfin.core.presentation.dummy.dummyEpisode
import dev.jdtech.jellyfin.core.presentation.dummy.dummyMovie
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidSeason
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings

@Composable
fun ItemCard(
    item: FindroidItem,
    direction: Direction,
    onClick: (FindroidItem) -> Unit,
    modifier: Modifier = Modifier,
    showSeriesName: Boolean = false,
    showYear: Boolean = false,
) {
    val width =
        when (direction) {
            Direction.HORIZONTAL -> 260
            Direction.VERTICAL -> 150
        }
    Column(modifier = modifier.width(width.dp)) {
        Surface(
            onClick = { onClick(item) },
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
            border =
                ClickableSurfaceDefaults.border(
                    focusedBorder =
                        Border(BorderStroke(4.dp, Color.White), shape = RoundedCornerShape(10.dp))
                ),
            scale = ClickableSurfaceScale.None,
        ) {
            Box {
                ItemPoster(item = item, direction = direction)
                ProgressBadge(
                    item = item,
                    modifier =
                        Modifier.align(Alignment.TopEnd).padding(MaterialTheme.spacings.small),
                )
                if (direction == Direction.HORIZONTAL) {
                    Column(
                        modifier =
                            Modifier.align(Alignment.BottomStart)
                                .padding(MaterialTheme.spacings.small)
                    ) {
                        val progress =
                            if (item.runtimeTicks > 0) {
                                (item.playbackPositionTicks.toFloat() / item.runtimeTicks.toFloat()).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                        Box(
                            modifier =
                                Modifier.height(4.dp)
                                    .width(progress.times(width - 16).dp)
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(MaterialTheme.spacings.small))
        val title =
            when (item) {
                is FindroidEpisode -> item.seriesName
                is FindroidSeason -> if (showSeriesName) item.seriesName else item.name
                else -> item.name
            }
        val subtitle =
            when (item) {
                is FindroidEpisode ->
                    stringResource(
                        id = R.string.episode_name_extended,
                        item.parentIndexNumber,
                        item.indexNumber,
                        item.name,
                    )
                is FindroidSeason -> if (showSeriesName) item.name else null
                // CE (#135): the year below the title, a range for a show.
                is FindroidMovie -> if (showYear) item.productionYear?.toString() else null
                is FindroidShow -> if (showYear) showYears(item) else null
                else -> null
            }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = if (direction == Direction.HORIZONTAL) 1 else 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** `2019`, `2019 – 2023`, or `2019 – present` while the show is still running. */
@Composable
private fun showYears(show: FindroidShow): String? {
    val start = show.productionYear ?: return null
    val end = show.endDate?.year
    return when {
        show.status == "Continuing" -> stringResource(R.string.year_range_continuing, start)
        end != null && end != start -> "$start – $end"
        else -> start.toString()
    }
}

@Preview
@Composable
private fun ItemCardPreviewMovie() {
    FindroidTheme { ItemCard(item = dummyMovie, direction = Direction.HORIZONTAL, onClick = {}) }
}

@Preview
@Composable
private fun ItemCardPreviewMovieVertical() {
    FindroidTheme { ItemCard(item = dummyMovie, direction = Direction.VERTICAL, onClick = {}) }
}

@Preview
@Composable
private fun ItemCardPreviewEpisode() {
    FindroidTheme { ItemCard(item = dummyEpisode, direction = Direction.HORIZONTAL, onClick = {}) }
}
