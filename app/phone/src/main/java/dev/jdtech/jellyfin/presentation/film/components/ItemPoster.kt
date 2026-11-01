package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidSeason

enum class Direction {
    HORIZONTAL,
    VERTICAL,
}

@Composable
fun ItemPoster(item: FindroidItem, direction: Direction, modifier: Modifier = Modifier) {
    val sources =
        when (direction) {
            Direction.HORIZONTAL ->
                when (item) {
                    is FindroidMovie -> listOf(item.images.backdrop, item.images.primary)
                    is FindroidEpisode -> listOf(item.images.primary, item.images.showBackdrop)
                    else -> listOf(item.images.primary)
                }
            Direction.VERTICAL ->
                when (item) {
                    is FindroidEpisode -> listOf(item.images.showPrimary)
                    is FindroidSeason -> listOf(item.images.primary, item.images.showPrimary)
                    else -> listOf(item.images.primary)
                }
        }

    ArtworkImage(
        sources = sources,
        contentDescription = stringResource(CoreR.string.image_description_poster, item.name),
        modifier =
            modifier
                .aspectRatio(if (direction == Direction.HORIZONTAL) 1.77f else 0.66f)
                .background(MaterialTheme.colorScheme.surfaceContainer),
    )
}
