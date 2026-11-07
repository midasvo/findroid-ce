package dev.jdtech.jellyfin.presentation.film

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import dev.jdtech.jellyfin.core.Constants
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.downloader.DownloaderAction
import dev.jdtech.jellyfin.core.presentation.downloader.DownloaderState
import dev.jdtech.jellyfin.core.presentation.dummy.dummyMovie
import dev.jdtech.jellyfin.core.presentation.dummy.dummyVideoMetadata
import dev.jdtech.jellyfin.film.presentation.movie.MovieAction
import dev.jdtech.jellyfin.film.presentation.movie.MovieState
import dev.jdtech.jellyfin.presentation.film.components.ActorsRow
import dev.jdtech.jellyfin.presentation.film.components.DownloadQualityDialog
import dev.jdtech.jellyfin.presentation.film.components.ExtraInfoText
import dev.jdtech.jellyfin.presentation.film.components.HeroChips
import dev.jdtech.jellyfin.presentation.film.components.HeroMetadataLine
import dev.jdtech.jellyfin.presentation.film.components.HeroSubtitle
import dev.jdtech.jellyfin.presentation.film.components.HeroTitle
import dev.jdtech.jellyfin.presentation.film.components.InfoText
import dev.jdtech.jellyfin.presentation.film.components.ItemButtonsBar
import dev.jdtech.jellyfin.presentation.film.components.ItemHeroHeader
import dev.jdtech.jellyfin.presentation.film.components.ItemTopBar
import dev.jdtech.jellyfin.presentation.film.components.LoadingOrError
import dev.jdtech.jellyfin.presentation.film.components.OverviewText
import dev.jdtech.jellyfin.presentation.film.components.VideoMetadataBar
import dev.jdtech.jellyfin.presentation.film.components.rememberTopBarBackgroundAlpha
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.presentation.utils.Layout
import dev.jdtech.jellyfin.presentation.utils.LightStatusBarOverArtwork
import dev.jdtech.jellyfin.presentation.utils.currentWidthClass
import dev.jdtech.jellyfin.presentation.utils.rememberSafePadding

/**
 * The movie page at Medium and Expanded width: backdrop hero with the poster and the text column,
 * cast below at full width (docs/tablet/4-design.md §6). Same state and actions as the compact
 * `MovieScreenLayout`.
 */
@Composable
fun MovieWideLayout(
    state: MovieState,
    downloaderState: () -> DownloaderState,
    onAction: (MovieAction) -> Unit,
    onDownloaderAction: (DownloaderAction) -> Unit,
) {
    val widthClass = currentWidthClass()
    val safePadding = rememberSafePadding()
    val margin = Layout.margin(widthClass)
    val rowPadding =
        PaddingValues(start = safePadding.start + margin, end = safePadding.end + margin)

    val scrollState = rememberScrollState()
    val topBarBackgroundAlpha = rememberTopBarBackgroundAlpha(scrollState)

    var downloadQualityDialogOpen by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        state.movie?.let { movie ->
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(scrollState)) {
                ItemHeroHeader(
                    item = movie,
                    scrollState = scrollState,
                    widthClass = widthClass,
                    title = {
                        HeroTitle(text = movie.name, widthClass = widthClass)
                        movie.originalTitle?.let { originalTitle ->
                            if (originalTitle != movie.name) HeroSubtitle(text = originalTitle)
                        }
                    },
                ) {
                    Spacer(Modifier.height(MaterialTheme.spacings.small))
                    HeroMetadataLine(
                        texts =
                            listOfNotNull(
                                movie.premiereDate?.year?.toString(),
                                stringResource(
                                    CoreR.string.runtime_minutes,
                                    movie.runtimeTicks.div(Constants.TICKS_PER_MINUTE),
                                ),
                                movie.officialRating,
                            ),
                        communityRating = movie.communityRating,
                    )
                    Spacer(Modifier.height(MaterialTheme.spacings.small))
                    state.videoMetadata?.let { videoMetadata ->
                        HeroChips { VideoMetadataBar(videoMetadata) }
                        Spacer(Modifier.height(MaterialTheme.spacings.small))
                    }
                    ItemButtonsBar(
                        item = movie,
                        downloaderStateProvider = downloaderState,
                        onPlayClick = { startFromBeginning ->
                            onAction(MovieAction.Play(startFromBeginning = startFromBeginning))
                        },
                        onPlayVersionClick = { startFromBeginning, mediaSourceId ->
                            onAction(
                                MovieAction.Play(
                                    startFromBeginning = startFromBeginning,
                                    mediaSourceId = mediaSourceId,
                                )
                            )
                        },
                        onMarkAsPlayedClick = {
                            when (movie.played) {
                                true -> onAction(MovieAction.UnmarkAsPlayed)
                                false -> onAction(MovieAction.MarkAsPlayed)
                            }
                        },
                        onMarkAsFavoriteClick = {
                            when (movie.favorite) {
                                true -> onAction(MovieAction.UnmarkAsFavorite)
                                false -> onAction(MovieAction.MarkAsFavorite)
                            }
                        },
                        onTrailerClick = { uri -> onAction(MovieAction.PlayTrailer(uri)) },
                        onDownloadClick = { downloadQualityDialogOpen = true },
                        onDownloadCancelClick = {
                            onDownloaderAction(DownloaderAction.CancelDownload(movie))
                        },
                        onDownloadDeleteClick = {
                            onDownloaderAction(DownloaderAction.DeleteDownload(movie))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(MaterialTheme.spacings.small))
                    if (state.displayExtraInfo && state.videoMetadata != null) {
                        ExtraInfoText(videoMetadata = state.videoMetadata!!)
                        Spacer(Modifier.height(MaterialTheme.spacings.medium))
                    }
                    OverviewText(text = movie.overview, maxCollapsedLines = 4)
                    Spacer(Modifier.height(MaterialTheme.spacings.medium))
                    InfoText(
                        genres = movie.genres,
                        director = state.director,
                        writers = state.writers,
                    )
                }
                Spacer(Modifier.height(MaterialTheme.spacings.medium))
                if (state.actors.isNotEmpty()) {
                    ActorsRow(
                        actors = state.actors,
                        onActorClick = { personId ->
                            onAction(MovieAction.NavigateToPerson(personId))
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
                    onRetryClick = { onAction(MovieAction.OnRetryClick) },
                )
            }

        LightStatusBarOverArtwork(
            hasArtwork = state.movie != null,
            topBarBackgroundAlpha = topBarBackgroundAlpha,
        )
        ItemTopBar(
            hasBackButton = true,
            hasHomeButton = true,
            backgroundAlpha = topBarBackgroundAlpha,
            onBackClick = { onAction(MovieAction.OnBackClick) },
            onHomeClick = { onAction(MovieAction.OnHomeClick) },
        )

        if (downloadQualityDialogOpen) {
            DownloadQualityDialog(
                onSelect = { maxBitrate ->
                    state.movie?.let { movie ->
                        onDownloaderAction(DownloaderAction.Download(movie, maxBitrate))
                    }
                    downloadQualityDialogOpen = false
                },
                onDismiss = { downloadQualityDialogOpen = false },
            )
        }
    }
}

@PreviewScreenSizes
@Preview(widthDp = 1280, heightDp = 800)
@Preview(widthDp = 800, heightDp = 1280)
@Composable
private fun MovieWideLayoutPreview() {
    FindroidTheme {
        MovieWideLayout(
            state = MovieState(movie = dummyMovie, videoMetadata = dummyVideoMetadata),
            downloaderState = { DownloaderState() },
            onAction = {},
            onDownloaderAction = {},
        )
    }
}
