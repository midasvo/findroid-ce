package dev.jdtech.jellyfin.presentation.film

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import dev.jdtech.jellyfin.core.Constants
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.downloader.DownloaderState
import dev.jdtech.jellyfin.core.presentation.dummy.dummyEpisode
import dev.jdtech.jellyfin.core.presentation.dummy.dummyShow
import dev.jdtech.jellyfin.film.presentation.show.ShowAction
import dev.jdtech.jellyfin.film.presentation.show.ShowState
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.presentation.film.components.ActorsRow
import dev.jdtech.jellyfin.presentation.film.components.Direction
import dev.jdtech.jellyfin.presentation.film.components.DownloadQualityDialog
import dev.jdtech.jellyfin.presentation.film.components.HeroMetadataLine
import dev.jdtech.jellyfin.presentation.film.components.HeroSubtitle
import dev.jdtech.jellyfin.presentation.film.components.HeroTitle
import dev.jdtech.jellyfin.presentation.film.components.InfoText
import dev.jdtech.jellyfin.presentation.film.components.ItemButtonsBar
import dev.jdtech.jellyfin.presentation.film.components.ItemCard
import dev.jdtech.jellyfin.presentation.film.components.ItemHeroHeader
import dev.jdtech.jellyfin.presentation.film.components.ItemPoster
import dev.jdtech.jellyfin.presentation.film.components.ItemTopBar
import dev.jdtech.jellyfin.presentation.film.components.LoadingOrError
import dev.jdtech.jellyfin.presentation.film.components.OverviewText
import dev.jdtech.jellyfin.presentation.film.components.SeasonSelectionDialog
import dev.jdtech.jellyfin.presentation.film.components.rememberTopBarBackgroundAlpha
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.presentation.utils.Layout
import dev.jdtech.jellyfin.presentation.utils.LightStatusBarOverArtwork
import dev.jdtech.jellyfin.presentation.utils.WidthClass
import dev.jdtech.jellyfin.presentation.utils.currentWidthClass
import dev.jdtech.jellyfin.presentation.utils.rememberSafePadding
import dev.jdtech.jellyfin.utils.getShowDateString
import java.util.UUID

/**
 * The show page at Medium and Expanded width: backdrop hero with the poster and the text column,
 * then Next Up, the seasons and the cast as full-width rows (docs/tablet/4-design.md §6). Same
 * state and actions as the compact `ShowScreenLayout`.
 */
@Composable
fun ShowWideLayout(
    state: ShowState,
    onAction: (ShowAction) -> Unit,
    onDownloadSeasons: (seasonIds: Set<UUID>, maxBitrate: Int?) -> Unit,
    onDeleteShowDownloads: () -> Unit,
) {
    val widthClass = currentWidthClass()
    val safePadding = rememberSafePadding()
    val margin = Layout.margin(widthClass)
    val rowPadding =
        PaddingValues(start = safePadding.start + margin, end = safePadding.end + margin)

    val scrollState = rememberScrollState()
    val topBarBackgroundAlpha = rememberTopBarBackgroundAlpha(scrollState)

    var seasonSelectionDialogOpen by remember { mutableStateOf(false) }
    // Seasons picked in the selection dialog, awaiting a quality choice.
    var pendingDownloadSeasonIds by remember { mutableStateOf<Set<UUID>?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        state.show?.let { show ->
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(scrollState)) {
                ItemHeroHeader(
                    item = show,
                    scrollState = scrollState,
                    widthClass = widthClass,
                    title = {
                        HeroTitle(text = show.name, widthClass = widthClass)
                        show.originalTitle?.let { originalTitle ->
                            if (originalTitle != show.name) HeroSubtitle(text = originalTitle)
                        }
                    },
                ) {
                    Spacer(Modifier.height(MaterialTheme.spacings.small))
                    HeroMetadataLine(
                        texts =
                            listOfNotNull(
                                getShowDateString(show),
                                stringResource(
                                    CoreR.string.runtime_minutes,
                                    show.runtimeTicks.div(Constants.TICKS_PER_MINUTE),
                                ),
                                show.officialRating,
                            ),
                        communityRating = show.communityRating,
                    )
                    Spacer(Modifier.height(MaterialTheme.spacings.small))
                    ItemButtonsBar(
                        item = show,
                        downloaderState =
                            if (state.seasons.isNotEmpty()) DownloaderState() else null,
                        onPlayClick = { startFromBeginning ->
                            onAction(ShowAction.Play(startFromBeginning = startFromBeginning))
                        },
                        onMarkAsPlayedClick = {
                            when (show.played) {
                                true -> onAction(ShowAction.UnmarkAsPlayed)
                                false -> onAction(ShowAction.MarkAsPlayed)
                            }
                        },
                        onMarkAsFavoriteClick = {
                            when (show.favorite) {
                                true -> onAction(ShowAction.UnmarkAsFavorite)
                                false -> onAction(ShowAction.MarkAsFavorite)
                            }
                        },
                        onTrailerClick = { uri -> onAction(ShowAction.PlayTrailer(uri)) },
                        onDownloadClick = {
                            if (state.seasons.isNotEmpty()) {
                                seasonSelectionDialogOpen = true
                            }
                        },
                        onDownloadCancelClick = {},
                        onDownloadDeleteClick = onDeleteShowDownloads,
                        modifier = Modifier.fillMaxWidth(),
                        canPlay = state.seasons.isNotEmpty(),
                        canDownload = state.seasons.isNotEmpty(),
                        isDownloaded = state.hasDownloads,
                        hasMoreToDownload =
                            state.seasonDownloadInfo.values.any {
                                it.downloadedCount < it.totalCount
                            },
                    )
                    Spacer(Modifier.height(MaterialTheme.spacings.small))
                    OverviewText(text = show.overview, maxCollapsedLines = 4)
                    Spacer(Modifier.height(MaterialTheme.spacings.medium))
                    InfoText(
                        genres = show.genres,
                        director = state.director,
                        writers = state.writers,
                    )
                }
                Spacer(Modifier.height(MaterialTheme.spacings.medium))

                state.nextUp?.let { nextUp ->
                    RowTitle(
                        text = stringResource(CoreR.string.next_up),
                        contentPadding = rowPadding,
                    )
                    LazyRow(
                        contentPadding = rowPadding,
                        horizontalArrangement = Arrangement.spacedBy(Layout.gutter(widthClass)),
                    ) {
                        item(key = nextUp.id) {
                            NextUpCard(
                                episode = nextUp,
                                widthClass = widthClass,
                                onClick = { onAction(ShowAction.NavigateToItem(nextUp)) },
                            )
                        }
                    }
                    Spacer(Modifier.height(MaterialTheme.spacings.medium))
                }

                if (state.seasons.isNotEmpty()) {
                    RowTitle(
                        text = stringResource(CoreR.string.seasons),
                        contentPadding = rowPadding,
                    )
                    LazyRow(
                        contentPadding = rowPadding,
                        horizontalArrangement = Arrangement.spacedBy(Layout.gutter(widthClass)),
                    ) {
                        items(items = state.seasons, key = { item -> item.id }) { season ->
                            ItemCard(
                                item = season,
                                direction = Direction.VERTICAL,
                                onClick = { onAction(ShowAction.NavigateToItem(season)) },
                            )
                        }
                    }
                    Spacer(Modifier.height(MaterialTheme.spacings.medium))
                }

                if (state.actors.isNotEmpty()) {
                    ActorsRow(
                        actors = state.actors,
                        onActorClick = { personId ->
                            onAction(ShowAction.NavigateToPerson(personId))
                        },
                        contentPadding = rowPadding,
                    )
                }
                Spacer(Modifier.height(safePadding.bottom + margin))
            }
        }
            ?: run {
                LoadingOrError(
                    error = state.error,
                    onRetryClick = { onAction(ShowAction.OnRetryClick) },
                )
            }

        LightStatusBarOverArtwork(
            hasArtwork = state.show != null,
            topBarBackgroundAlpha = topBarBackgroundAlpha,
        )
        ItemTopBar(
            hasBackButton = true,
            hasHomeButton = true,
            backgroundAlpha = topBarBackgroundAlpha,
            onBackClick = { onAction(ShowAction.OnBackClick) },
            onHomeClick = { onAction(ShowAction.OnHomeClick) },
        )

        if (seasonSelectionDialogOpen && state.seasons.isNotEmpty()) {
            SeasonSelectionDialog(
                seasons = state.seasons,
                onConfirm = { selectedSeasonIds ->
                    pendingDownloadSeasonIds = selectedSeasonIds
                    seasonSelectionDialogOpen = false
                },
                onDismiss = { seasonSelectionDialogOpen = false },
                seasonDownloadInfo = state.seasonDownloadInfo,
            )
        }
        pendingDownloadSeasonIds?.let { seasonIds ->
            DownloadQualityDialog(
                onSelect = { maxBitrate ->
                    onDownloadSeasons(seasonIds, maxBitrate)
                    pendingDownloadSeasonIds = null
                },
                onDismiss = { pendingDownloadSeasonIds = null },
            )
        }
    }
}

@Composable
private fun RowTitle(text: String, contentPadding: PaddingValues) {
    Column(modifier = Modifier.padding(contentPadding)) {
        Text(text = text, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(MaterialTheme.spacings.small))
    }
}

/** Next Up as a row card of `cardHorizontal` width instead of the compact page's lone card. */
@Composable
private fun NextUpCard(episode: FindroidEpisode, widthClass: WidthClass, onClick: () -> Unit) {
    Column(
        modifier =
            Modifier.width(Layout.cardHorizontal(widthClass))
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = onClick)
    ) {
        ItemPoster(
            item = episode,
            direction = Direction.HORIZONTAL,
            modifier = Modifier.clip(MaterialTheme.shapes.small),
        )
        Spacer(Modifier.height(MaterialTheme.spacings.extraSmall))
        Text(
            text =
                stringResource(
                    id = CoreR.string.episode_name_extended,
                    episode.parentIndexNumber,
                    episode.indexNumber,
                    episode.name,
                ),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@PreviewScreenSizes
@Preview(widthDp = 1280, heightDp = 800)
@Preview(widthDp = 800, heightDp = 1280)
@Composable
private fun ShowWideLayoutPreview() {
    FindroidTheme {
        ShowWideLayout(
            state = ShowState(show = dummyShow, nextUp = dummyEpisode),
            onAction = {},
            onDownloadSeasons = { _, _ -> },
            onDeleteShowDownloads = {},
        )
    }
}
