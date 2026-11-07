package dev.jdtech.jellyfin.presentation.film

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import dev.jdtech.jellyfin.core.Constants
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.downloader.DownloaderAction
import dev.jdtech.jellyfin.core.presentation.downloader.DownloaderState
import dev.jdtech.jellyfin.core.presentation.dummy.dummyEpisode
import dev.jdtech.jellyfin.core.presentation.dummy.dummyVideoMetadata
import dev.jdtech.jellyfin.film.presentation.episode.EpisodeAction
import dev.jdtech.jellyfin.film.presentation.episode.EpisodeState
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.presentation.film.components.ActorsRow
import dev.jdtech.jellyfin.presentation.film.components.DownloadQualityDialog
import dev.jdtech.jellyfin.presentation.film.components.ExtraInfoText
import dev.jdtech.jellyfin.presentation.film.components.HeroChips
import dev.jdtech.jellyfin.presentation.film.components.HeroMetadataLine
import dev.jdtech.jellyfin.presentation.film.components.HeroSubtitle
import dev.jdtech.jellyfin.presentation.film.components.HeroTitle
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
import dev.jdtech.jellyfin.utils.format

/**
 * The episode page at Medium and Expanded width: the show's backdrop as the hero, the episode's
 * still beside the text column, cast below at full width (docs/tablet/4-design.md §6). Same state
 * and actions as the compact `EpisodeScreenLayout`.
 */
@Composable
fun EpisodeWideLayout(
    state: EpisodeState,
    downloaderState: () -> DownloaderState,
    onAction: (EpisodeAction) -> Unit,
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
        state.episode?.let { episode ->
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(scrollState)) {
                ItemHeroHeader(
                    item = episode,
                    scrollState = scrollState,
                    widthClass = widthClass,
                    title = {
                        val episodeNumberText =
                            stringResource(id = CoreR.string.episode_number, episode.indexNumber)
                        HeroSubtitle(
                            text = "${seasonName(episode)} · $episodeNumberText",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        HeroTitle(text = episode.name, widthClass = widthClass)
                    },
                ) {
                    Spacer(Modifier.height(MaterialTheme.spacings.small))
                    val locale = LocalConfiguration.current.locales.get(0)
                    HeroMetadataLine(
                        texts =
                            listOfNotNull(
                                episode.premiereDate?.format(locale),
                                stringResource(
                                    CoreR.string.runtime_minutes,
                                    episode.runtimeTicks.div(Constants.TICKS_PER_MINUTE),
                                ),
                            ),
                        communityRating = episode.communityRating,
                    )
                    Spacer(Modifier.height(MaterialTheme.spacings.small))
                    state.videoMetadata?.let { videoMetadata ->
                        HeroChips { VideoMetadataBar(videoMetadata) }
                        Spacer(Modifier.height(MaterialTheme.spacings.small))
                    }
                    ItemButtonsBar(
                        item = episode,
                        downloaderStateProvider = downloaderState,
                        onPlayClick = { startFromBeginning ->
                            onAction(EpisodeAction.Play(startFromBeginning = startFromBeginning))
                        },
                        onPlayVersionClick = { startFromBeginning, mediaSourceId ->
                            onAction(
                                EpisodeAction.Play(
                                    startFromBeginning = startFromBeginning,
                                    mediaSourceId = mediaSourceId,
                                )
                            )
                        },
                        onMarkAsPlayedClick = {
                            when (episode.played) {
                                true -> onAction(EpisodeAction.UnmarkAsPlayed)
                                false -> onAction(EpisodeAction.MarkAsPlayed)
                            }
                        },
                        onMarkAsFavoriteClick = {
                            when (episode.favorite) {
                                true -> onAction(EpisodeAction.UnmarkAsFavorite)
                                false -> onAction(EpisodeAction.MarkAsFavorite)
                            }
                        },
                        onTrailerClick = {},
                        onDownloadClick = { downloadQualityDialogOpen = true },
                        onDownloadCancelClick = {
                            onDownloaderAction(DownloaderAction.CancelDownload(episode))
                        },
                        onDownloadDeleteClick = {
                            onDownloaderAction(DownloaderAction.DeleteDownload(episode))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(MaterialTheme.spacings.small))
                    if (state.displayExtraInfo && state.videoMetadata != null) {
                        ExtraInfoText(videoMetadata = state.videoMetadata!!)
                        Spacer(Modifier.height(MaterialTheme.spacings.medium))
                    }
                    OverviewText(text = episode.overview, maxCollapsedLines = 4)
                }
                Spacer(Modifier.height(MaterialTheme.spacings.medium))
                if (state.actors.isNotEmpty()) {
                    ActorsRow(
                        actors = state.actors,
                        onActorClick = { personId ->
                            onAction(EpisodeAction.NavigateToPerson(personId))
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
                    onRetryClick = { onAction(EpisodeAction.OnRetryClick) },
                )
            }

        LightStatusBarOverArtwork(
            hasArtwork = state.episode != null,
            topBarBackgroundAlpha = topBarBackgroundAlpha,
        )
        ItemTopBar(
            hasBackButton = true,
            hasHomeButton = true,
            backgroundAlpha = topBarBackgroundAlpha,
            onBackClick = { onAction(EpisodeAction.OnBackClick) },
            onHomeClick = { onAction(EpisodeAction.OnHomeClick) },
        ) {
            Spacer(modifier = Modifier.width(MaterialTheme.spacings.extraSmall))
            state.episode?.let { episode ->
                Button(
                    onClick = {
                        onAction(EpisodeAction.NavigateToSeason(episode.seasonId, episode.seriesId))
                    },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = Color.Black.copy(alpha = 0.5f),
                            contentColor = Color.White,
                        ),
                ) {
                    Text(seasonName(episode))
                }
            }
        }

        if (downloadQualityDialogOpen) {
            DownloadQualityDialog(
                onSelect = { maxBitrate ->
                    state.episode?.let { episode ->
                        onDownloaderAction(DownloaderAction.Download(episode, maxBitrate))
                    }
                    downloadQualityDialogOpen = false
                },
                onDismiss = { downloadQualityDialogOpen = false },
            )
        }
    }
}

@Composable
private fun seasonName(episode: FindroidEpisode): String =
    episode.seasonName ?: stringResource(CoreR.string.season_number, episode.parentIndexNumber)

@PreviewScreenSizes
@Preview(widthDp = 1280, heightDp = 800)
@Preview(widthDp = 800, heightDp = 1280)
@Composable
private fun EpisodeWideLayoutPreview() {
    FindroidTheme {
        EpisodeWideLayout(
            state = EpisodeState(episode = dummyEpisode, videoMetadata = dummyVideoMetadata),
            downloaderState = { DownloaderState() },
            onAction = {},
            onDownloaderAction = {},
        )
    }
}
