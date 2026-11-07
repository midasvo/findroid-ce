package dev.jdtech.jellyfin.ce.presentation.film

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.FilterChipDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.jdtech.jellyfin.ce.ui.TvSafeArea
import dev.jdtech.jellyfin.ce.ui.focus.EphyraFocusScale
import dev.jdtech.jellyfin.ce.ui.focus.FocusPosition
import dev.jdtech.jellyfin.ce.ui.focus.RestoreFocus
import dev.jdtech.jellyfin.ce.ui.focus.ephyraButtonBorder
import dev.jdtech.jellyfin.ce.ui.focus.ephyraFocusedBorder
import dev.jdtech.jellyfin.ce.ui.focus.ephyraFocusedGlow
import dev.jdtech.jellyfin.ce.ui.itemInfoLine
import dev.jdtech.jellyfin.ce.ui.focus.focusPosition
import dev.jdtech.jellyfin.ce.ui.focus.rememberFocusPosition
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.dummy.dummyEpisodes
import dev.jdtech.jellyfin.core.presentation.dummy.dummySeason
import dev.jdtech.jellyfin.core.presentation.dummy.dummyVideoMetadata
import dev.jdtech.jellyfin.film.presentation.episode.EpisodeAction
import dev.jdtech.jellyfin.film.presentation.episode.EpisodeState
import dev.jdtech.jellyfin.film.presentation.episode.EpisodeViewModel
import dev.jdtech.jellyfin.film.presentation.season.SeasonState
import dev.jdtech.jellyfin.film.presentation.season.SeasonViewModel
import dev.jdtech.jellyfin.models.DisplayProfile
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidSeason
import dev.jdtech.jellyfin.models.VideoMetadata
import dev.jdtech.jellyfin.models.getVersions
import dev.jdtech.jellyfin.presentation.film.components.VersionDialog
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.ui.components.Direction
import dev.jdtech.jellyfin.ui.components.ItemPoster
import dev.jdtech.jellyfin.ui.components.ProgressBadge
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import org.jellyfin.sdk.model.api.MediaStreamType

/**
 * CE replacement of the upstream TV season page (ADR 0001, #130).
 *
 * Above the episode row it shows the focused episode: its details, technical chips and buttons
 * (Play/Resume, Restart, Watched, Favourite, versions). The seasons of the series are chips at the
 * top, so you can switch season on the same page.
 *
 * Opened with [episodeId] (an episode on Home or in search), the page finds the season of that
 * episode and focuses it. Back from the player focuses the episode that was played (ADR 0003).
 */
@Composable
fun SeasonScreen(
    seasonId: UUID?,
    episodeId: UUID?,
    navigateToPlayer: (itemId: UUID, mediaSourceId: String?, startFromBeginning: Boolean) -> Unit,
    seriesId: UUID? = null,
    seasonViewModel: SeasonViewModel = hiltViewModel(),
    episodeViewModel: EpisodeViewModel = hiltViewModel(),
) {
    val seasonState = seasonViewModel.state.collectAsStateWithLifecycle()
    // Kept as a State and read only by the details panel and the effects below, so loading an
    // episode doesn't recompose the whole page (#257).
    val episodeState = episodeViewModel.state.collectAsStateWithLifecycle()
    val state = seasonState.value

    // The season on screen, which changes with the season chips. Null until the season of
    // [episodeId] is known.
    var currentSeasonId by rememberSaveable { mutableStateOf(seasonId?.toString()) }
    val selection =
        rememberSaveable(saver = EpisodeSelection.Saver) { EpisodeSelection(episodeId?.toString()) }

    LaunchedEffect(true) {
        if (currentSeasonId == null && episodeId != null) episodeViewModel.loadEpisode(episodeId)
    }
    LaunchedEffect(Unit) {
        snapshotFlow { episodeState.value.episode }
            .collect { episode ->
                if (currentSeasonId == null) currentSeasonId = episode?.seasonId?.toString()
            }
    }
    // Runs again on each return to the screen, so watch progress is up to date.
    LaunchedEffect(currentSeasonId) {
        currentSeasonId?.let {
            if (!seasonViewModel.shouldReload(UUID.fromString(it))) return@let
            // Every season chip is of the same series, so [seriesId] holds for all of them.
            seasonViewModel.loadSeason(
                UUID.fromString(it),
                includeSeriesSeasons = true,
                // Read from the flow, not the composable state: this effect runs outside composition.
                seriesId = seriesId ?: episodeViewModel.state.value.episode?.seriesId,
            )
        }
    }

    // The full episode (media streams, versions) loads when focus rests on a card for a moment.
    // Read in a snapshotFlow, so a focus change doesn't recompose this screen.
    LaunchedEffect(Unit) {
        snapshotFlow { selection.selected(seasonState.value.episodes)?.id }
            .collectLatest { id ->
                if (id == null) return@collectLatest
                if (episodeState.value.episode?.id != id) delay(DETAILS_DELAY_MS)
                episodeViewModel.loadEpisode(id)
            }
    }
    // Watched and Favourite change the full episode. Reload the season so its card follows.
    LaunchedEffect(Unit) {
        snapshotFlow { episodeState.value.episode }
            .collect { full ->
                if (full == null) return@collect
                val card =
                    seasonState.value.episodes.firstOrNull { it.id == full.id } ?: return@collect
                if (card.played != full.played || card.favorite != full.favorite) {
                    seasonViewModel.loadSeason(card.seasonId)
                }
            }
    }

    SeasonScreenLayout(
        state = state,
        // While another season loads, the old one stays on screen, so focus stays on its chip.
        episodes = state.episodes,
        selection = selection,
        episodeState = { episodeState.value },
        onSelectSeason = { season ->
            if (season.id.toString() != currentSeasonId) {
                currentSeasonId = season.id.toString()
                selection.id = null
            }
        },
        onPlay = { episode, mediaSourceId, startFromBeginning ->
            navigateToPlayer(episode.id, mediaSourceId, startFromBeginning)
        },
        onAction = episodeViewModel::onAction,
    )
}

/**
 * The focused episode (#257). Episode cards write it on focus; only the details panel and each
 * card's own focus target read it, so a step along the row doesn't recompose the backdrop, the
 * season chips or the other cards. Saved, so a restored page focuses the same episode.
 */
@Stable
private class EpisodeSelection(initialId: String?) {
    /** The focused episode's ID, or null for [nextUp]. */
    var id by mutableStateOf(initialId)

    fun selected(episodes: List<FindroidEpisode>): FindroidEpisode? =
        episodes.firstOrNull { it.id.toString() == id } ?: nextUp(episodes)

    companion object {
        val Saver: Saver<EpisodeSelection, String> =
            Saver(save = { it.id.orEmpty() }, restore = { EpisodeSelection(it.ifEmpty { null }) })
    }
}

/** The episode to focus first: the one in progress, else the first unwatched one. */
private fun nextUp(episodes: List<FindroidEpisode>): FindroidEpisode? =
    episodes.firstOrNull { it.playbackPositionTicks > 0 && !it.played }
        ?: episodes.firstOrNull { !it.played }
        ?: episodes.firstOrNull()

@Composable
@OptIn(ExperimentalTvMaterial3Api::class)
private fun SeasonScreenLayout(
    state: SeasonState,
    episodes: List<FindroidEpisode>,
    selection: EpisodeSelection,
    episodeState: () -> EpisodeState,
    onSelectSeason: (FindroidSeason) -> Unit,
    onPlay: (episode: FindroidEpisode, mediaSourceId: String?, startFromBeginning: Boolean) -> Unit,
    onAction: (EpisodeAction) -> Unit,
) {
    // Default focus: the selected episode. After Back from the player: the element that was used.
    val focusPosition = rememberFocusPosition(row = ROW_EPISODES)
    val episodesState = rememberLazyListState()
    val seasonsState = rememberLazyListState()

    // Where focus lands the first time it enters each row: the current season, Play, and the
    // selected episode, instead of the nearest item.
    val seasonRequester = remember { FocusRequester() }
    val playRequester = remember { FocusRequester() }
    val episodeRequester = remember { FocusRequester() }

    // Not read here: only the details panel and the cards' focus targets follow the selection.
    val selected = remember(selection, episodes) { derivedStateOf { selection.selected(episodes) } }

    Box(modifier = Modifier.fillMaxSize()) {
        val season = state.season
        // No episodes is the only case without a selected one.
        if (season == null || episodes.isEmpty()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            return@Box
        }

        SeasonBackdrop(season = season)

        Column(modifier = Modifier.fillMaxSize().padding(vertical = TvSafeArea.vertical)) {
            Column(modifier = Modifier.padding(horizontal = TvSafeArea.horizontal)) {
                Text(
                    text = season.seriesName,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = .7f),
                )
                Spacer(modifier = Modifier.height(MaterialTheme.spacings.small))
            }
            LazyRow(
                state = seasonsState,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
                contentPadding = PaddingValues(horizontal = TvSafeArea.horizontal),
                modifier = Modifier.focusRestorer(seasonRequester),
            ) {
                val seasons = state.seriesSeasons.ifEmpty { listOf(season) }
                itemsIndexed(seasons, key = { _, item -> item.id }) { index, item ->
                    FilterChip(
                        selected = item.id == season.id,
                        onClick = { onSelectSeason(item) },
                        shape = FilterChipDefaults.shape(shape = CircleShape),
                        // FilterChipDefaults.border()'s own colours and widths (tv-material 1.1.0),
                        // with the Ephyra focus ring when focused.
                        border =
                            FilterChipDefaults.border(
                                border = pillBorder(1.dp, MaterialTheme.colorScheme.border),
                                focusedBorder = ephyraFocusedBorder(CircleShape),
                                selectedBorder =
                                    pillBorder(1.dp, MaterialTheme.colorScheme.secondary),
                                disabledBorder =
                                    pillBorder(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                                focusedSelectedBorder = ephyraFocusedBorder(CircleShape),
                            ),
                        modifier =
                            Modifier.focusRequesterIf(item.id == season.id, seasonRequester)
                                .focusPosition(
                                focusPosition,
                                ROW_SEASONS,
                                index,
                                item.id.toString(),
                            ),
                    ) {
                        Text(text = item.name)
                    }
                }
            }

            EpisodeDetails(
                selected = selected,
                episodeState = episodeState,
                focusPosition = focusPosition,
                playRequester = playRequester,
                onPlay = onPlay,
                onAction = onAction,
                modifier =
                    Modifier.weight(1f)
                        .padding(horizontal = TvSafeArea.horizontal)
                        .padding(top = MaterialTheme.spacings.medium),
            )

            Spacer(modifier = Modifier.height(MaterialTheme.spacings.medium))
            LazyRow(
                state = episodesState,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
                contentPadding = PaddingValues(horizontal = TvSafeArea.horizontal),
                modifier = Modifier.focusRestorer(episodeRequester),
            ) {
                itemsIndexed(episodes, key = { _, item -> item.id }) { index, item ->
                    // Only the card that gains or loses the selection recomposes.
                    val isSelected by
                        remember(selected, item.id) {
                            derivedStateOf { selected.value?.id == item.id }
                        }
                    EpisodeRowCard(
                        episode = item,
                        onClick = { onPlay(item, null, false) },
                        onFocus = { selection.id = item.id.toString() },
                        modifier =
                            Modifier.focusRequesterIf(isSelected, episodeRequester)
                                .focusPosition(
                                focusPosition,
                                ROW_EPISODES,
                                index,
                                item.id.toString(),
                            ),
                    )
                }
            }
        }

        RestoreFocus(position = focusPosition, ready = episodes.isNotEmpty()) { position ->
            // First visit: no episode was focused yet, so point the default at the selected one.
            if (position.row == ROW_EPISODES && position.key == null) {
                selected.value?.let { position.save(ROW_EPISODES, 0, it.id.toString()) }
            }
            position.resolve(ROW_EPISODES, episodes.size) { i -> episodes[i].id.toString() }
            position.resolve(ROW_SEASONS, state.seriesSeasons.size) { i ->
                state.seriesSeasons[i].id.toString()
            }
            if (position.row == ROW_EPISODES) episodesState.scrollIntoView(position.column)
            if (position.row == ROW_SEASONS) seasonsState.scrollIntoView(position.column)
        }
        // After a season switch, show the episode that the row will focus.
        LaunchedEffect(season.id) {
            episodes
                .indexOf(selected.value)
                .takeIf { it >= 0 }
                ?.let { episodesState.scrollIntoView(it) }
        }
    }
}

/**
 * The season's backdrop under its two scrims. It doesn't read the selected episode, so focus moving
 * along the episodes leaves it alone (#257).
 */
@Composable
private fun SeasonBackdrop(season: FindroidSeason) {
    AsyncImage(
        model = rememberDetailBackdrop(season.images.backdrop ?: season.images.showBackdrop),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
    Box(
        modifier =
            Modifier.fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surface,
                            Color.Black.copy(alpha = .85f),
                            Color.Transparent,
                        )
                    )
                )
    )
    Box(
        modifier =
            Modifier.fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.5f to Color.Transparent,
                        1f to Color.Black.copy(alpha = .9f),
                    )
                )
    )
}

/**
 * The selected episode's details, technical chips and buttons. The only part of the page that
 * recomposes when focus moves along the episodes, or when the full episode loads (#257).
 */
@Composable
private fun EpisodeDetails(
    selected: State<FindroidEpisode?>,
    episodeState: () -> EpisodeState,
    focusPosition: FocusPosition,
    playRequester: FocusRequester,
    onPlay: (episode: FindroidEpisode, mediaSourceId: String?, startFromBeginning: Boolean) -> Unit,
    onAction: (EpisodeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val card = selected.value ?: return
    val loaded = episodeState().takeIf { it.episode?.id == card.id }
    // The full episode, once loaded, has the streams and versions; the card has the rest.
    val episode = loaded?.episode ?: card
    val versions = remember(episode.sources) { episode.getVersions() }
    var versionDialogOpen by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            text = episode.name,
            style = MaterialTheme.typography.headlineMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacings.extraSmall))
        Text(
            text = itemInfoLine(episode),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = .8f),
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacings.small))
        Text(
            text = episode.overview,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(640.dp),
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacings.small))
        // Reserve the chips' height, so the buttons don't jump when they load.
        Box(modifier = Modifier.height(24.dp)) {
            loaded?.let {
                TechnicalChips(chips = technicalChips(it.episode, it.videoMetadata))
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
            verticalAlignment = Alignment.CenterVertically,
            // A plain Row needs focusGroup() for focusRestorer() to work. The first time
            // focus enters the row, it lands on Play instead of the nearest button.
            modifier = Modifier.focusRestorer(playRequester).focusGroup(),
        ) {
            val inProgress = episode.playbackPositionTicks > 0 && !episode.played
            Button(
                border = ephyraButtonBorder(),
                onClick = { onPlay(episode, null, false) },
                modifier =
                    Modifier.focusRequester(playRequester)
                        .focusPosition(focusPosition, ROW_BUTTONS, 0),
            ) {
                Icon(
                    painter = painterResource(id = CoreR.drawable.ic_play),
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text =
                        stringResource(
                            id = if (inProgress) CoreR.string.resume else CoreR.string.play
                        )
                )
            }
            if (inProgress) {
                Button(
                    border = ephyraButtonBorder(),
                    onClick = { onPlay(episode, null, true) },
                    modifier = Modifier.focusPosition(focusPosition, ROW_BUTTONS, 1),
                ) {
                    Icon(
                        painter = painterResource(id = CoreR.drawable.ic_rotate_ccw),
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(id = CoreR.string.restart))
                }
            }
            IconButton(
                border = ephyraButtonBorder(),
                onClick = {
                    onAction(
                        if (episode.played) EpisodeAction.UnmarkAsPlayed
                        else EpisodeAction.MarkAsPlayed
                    )
                },
                // Watched and Favourite act on the full episode: wait until it's there.
                enabled = loaded != null,
                modifier = Modifier.focusPosition(focusPosition, ROW_BUTTONS, 2),
            ) {
                Icon(
                    painter = painterResource(id = CoreR.drawable.ic_check),
                    contentDescription =
                        stringResource(
                            id =
                                if (episode.played) CoreR.string.unmark_as_played
                                else CoreR.string.mark_as_played
                        ),
                    tint = if (episode.played) MaterialTheme.colorScheme.tertiary else LocalContentColor.current,
                )
            }
            IconButton(
                border = ephyraButtonBorder(),
                onClick = {
                    onAction(
                        if (episode.favorite) EpisodeAction.UnmarkAsFavorite
                        else EpisodeAction.MarkAsFavorite
                    )
                },
                enabled = loaded != null,
                modifier = Modifier.focusPosition(focusPosition, ROW_BUTTONS, 3),
            ) {
                Icon(
                    painter =
                        painterResource(
                            id =
                                if (episode.favorite) CoreR.drawable.ic_heart_filled
                                else CoreR.drawable.ic_heart
                        ),
                    contentDescription =
                        stringResource(
                            id =
                                if (episode.favorite) CoreR.string.remove_from_favorites
                                else CoreR.string.add_to_favorites
                        ),
                    tint = if (episode.favorite) MaterialTheme.colorScheme.tertiary else LocalContentColor.current,
                )
            }
            if (versions.size > 1) {
                IconButton(
                    border = ephyraButtonBorder(),
                    onClick = { versionDialogOpen = true },
                    modifier = Modifier.focusPosition(focusPosition, ROW_BUTTONS, 4),
                ) {
                    Icon(
                        painter = painterResource(id = CoreR.drawable.ic_list),
                        contentDescription =
                            stringResource(id = CoreR.string.select_video_version_title),
                    )
                }
            }
        }
    }

    if (versionDialogOpen) {
        VersionDialog(
            versions = versions,
            onSelect = { mediaSourceId ->
                versionDialogOpen = false
                onPlay(episode, mediaSourceId, false)
            },
            onDismiss = { versionDialogOpen = false },
        )
    }
}

/** Resolution, video codec, HDR format, and the language and format of the first audio track. */
private fun technicalChips(episode: FindroidEpisode?, metadata: VideoMetadata?): List<String> {
    val source = episode?.sources?.firstOrNull() ?: return emptyList()
    val video = source.mediaStreams.firstOrNull { it.type == MediaStreamType.VIDEO }
    val audio = source.mediaStreams.firstOrNull { it.type == MediaStreamType.AUDIO }
    return buildList {
        video?.height?.let { add(resolutionName(it)) }
        metadata?.videoCodecs?.firstOrNull()?.let { add(it.raw) }
        metadata?.displayProfiles?.firstOrNull { it != DisplayProfile.SDR }?.let { add(it.raw) }
        audio?.let { stream ->
            val language =
                stream.language
                    .takeIf { it.isNotBlank() }
                    ?.let { code -> Locale.forLanguageTag(code).displayLanguage.ifBlank { code } }
            val format =
                listOfNotNull(audioCodecName(stream.codec), metadata?.audioChannels?.firstOrNull()?.raw)
                    .joinToString(" ")
            add(listOfNotNull(language, format.ifBlank { null }).joinToString(" "))
        }
    }
}

private fun resolutionName(height: Int): String =
    when {
        height >= 2000 -> "4K"
        height >= 1000 -> "1080p"
        height >= 700 -> "720p"
        else -> "${height}p"
    }

private fun audioCodecName(codec: String): String? =
    when (codec.lowercase()) {
        "" -> null
        "ac3" -> "Dolby Digital"
        "eac3" -> "Dolby Digital+"
        "truehd" -> "Dolby TrueHD"
        "dts" -> "DTS"
        else -> codec.uppercase()
    }

/** A chip border on the pill, where the library's default borders keep their own 8 dp shape. */
private fun pillBorder(width: Dp, color: Color) =
    Border(BorderStroke(width, color), shape = CircleShape)

@Composable
private fun TechnicalChips(chips: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small)) {
        chips.forEach { chip ->
            Text(
                text = chip,
                style = MaterialTheme.typography.labelMedium,
                modifier =
                    Modifier.clip(CircleShape)
                        .background(Color.White.copy(alpha = .15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun EpisodeRowCard(
    episode: FindroidEpisode,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.width(EPISODE_CARD_WIDTH)) {
        Surface(
            onClick = onClick,
            shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.small),
            border = ClickableSurfaceDefaults.border(focusedBorder = ephyraFocusedBorder()),
            glow = ClickableSurfaceDefaults.glow(focusedGlow = ephyraFocusedGlow()),
            scale = ClickableSurfaceDefaults.scale(focusedScale = EphyraFocusScale),
            modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.hasFocus) onFocus() },
        ) {
            Box {
                ItemPoster(item = episode, direction = Direction.HORIZONTAL)
                ProgressBadge(
                    item = episode,
                    modifier =
                        Modifier.align(Alignment.TopEnd).padding(MaterialTheme.spacings.small),
                )
                if (episode.playbackPositionTicks > 0 && episode.runtimeTicks > 0 && !episode.played) {
                    val progress =
                        (episode.playbackPositionTicks.toFloat() / episode.runtimeTicks)
                            .coerceIn(0f, 1f)
                    Box(
                        modifier =
                            Modifier.align(Alignment.BottomStart)
                                .padding(MaterialTheme.spacings.small)
                                .height(4.dp)
                                .width((EPISODE_CARD_WIDTH - 16.dp) * progress)
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(MaterialTheme.spacings.small))
        Text(
            text = stringResource(id = CoreR.string.episode_name, episode.indexNumber, episode.name),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun Modifier.focusRequesterIf(condition: Boolean, requester: FocusRequester): Modifier =
    if (condition) focusRequester(requester) else this

/** Scrolls only when [index] isn't on screen, so a return keeps the old scroll position. */
private suspend fun LazyListState.scrollIntoView(index: Int) {
    if (layoutInfo.visibleItemsInfo.none { it.index == index }) scrollToItem(index)
}

private const val ROW_SEASONS = 0
private const val ROW_BUTTONS = 1
private const val ROW_EPISODES = 2

private const val DETAILS_DELAY_MS = 300L
private val EPISODE_CARD_WIDTH = 208.dp

@Preview(device = "id:tv_1080p")
@Composable
private fun SeasonScreenLayoutPreview() {
    FindroidTheme {
        SeasonScreenLayout(
            state = SeasonState(season = dummySeason, episodes = dummyEpisodes),
            episodes = dummyEpisodes,
            selection = remember { EpisodeSelection(dummyEpisodes.first().id.toString()) },
            episodeState = {
                EpisodeState(episode = dummyEpisodes.first(), videoMetadata = dummyVideoMetadata)
            },
            onSelectSeason = {},
            onPlay = { _, _, _ -> },
            onAction = {},
        )
    }
}
