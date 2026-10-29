package dev.jdtech.jellyfin.presentation.film

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.recalculateWindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jdtech.jellyfin.core.Constants
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.film.presentation.downloads.DownloadedShowViewModel
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.presentation.film.components.DownloadRow
import dev.jdtech.jellyfin.presentation.film.components.SwipeActionBackground
import dev.jdtech.jellyfin.presentation.film.components.groupedItemShape
import dev.jdtech.jellyfin.presentation.film.components.playDownloadedItem
import dev.jdtech.jellyfin.presentation.film.components.rememberUndoController
import java.util.UUID

/** The downloaded episodes of one show, by season, with per-episode and remove-all actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadedShowScreen(
    showId: UUID,
    showName: String,
    navigateBack: () -> Unit,
    onEpisodeClick: (FindroidItem) -> Unit,
    viewModel: DownloadedShowViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Reload on resume so deletes made on the episode screen show up here.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.loadEpisodes(showId)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Nothing left to manage once every episode has been removed.
    LaunchedEffect(state.isLoading, state.seasons.isEmpty()) {
        if (!state.isLoading && state.seasons.isEmpty()) navigateBack()
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    val undo = rememberUndoController(snackbarHostState)
    LaunchedEffect(state.seasons) {
        undo.retainHidden(state.seasons.flatMap { season -> season.episodes.map { it.id } }.toSet())
    }
    val seasons =
        state.seasons
            .map { season -> season.copy(episodes = season.episodes.filterNot { it.id in undo.hiddenIds }) }
            .filter { it.episodes.isNotEmpty() }
    val episodes = seasons.flatMap { it.episodes }

    val removeEpisodes: (List<FindroidEpisode>) -> Unit = { removed ->
        val message =
            removed.singleOrNull()?.let { context.getString(CoreR.string.download_removed, it.name) }
                ?: context.resources.getQuantityString(
                    CoreR.plurals.downloads_removed,
                    removed.size,
                    removed.size,
                )
        undo.run(ids = removed.map { it.id }.toSet(), message = message) {
            viewModel.deleteEpisodes(showId, removed)
        }
    }

    Scaffold(
        modifier =
            Modifier.fillMaxSize()
                .recalculateWindowInsets()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(text = showName, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_arrow_left),
                            contentDescription = stringResource(CoreR.string.navigate_back),
                        )
                    }
                },
                windowInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout),
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (episodes.isNotEmpty()) {
                item(key = "summary") {
                    val totalBytes = episodes.sumOf { state.itemSizes[it.id] ?: 0L }
                    Text(
                        text =
                            pluralStringResource(
                                CoreR.plurals.downloaded_episode_count,
                                episodes.size,
                                episodes.size,
                            ) + " · " + Formatter.formatShortFileSize(context, totalBytes),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp).animateItem(),
                    )
                }
            }
            for (season in seasons) {
                item(key = "season:${season.number}") {
                    Text(
                        text = season.name ?: stringResource(CoreR.string.season_number, season.number),
                        style = MaterialTheme.typography.titleMedium,
                        modifier =
                            Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp).animateItem(),
                    )
                }
                itemsIndexed(items = season.episodes, key = { _, ep -> ep.id }) { index, episode ->
                    EpisodeRow(
                        episode = episode,
                        sizeBytes = state.itemSizes[episode.id],
                        shape = groupedItemShape(index, season.episodes.size),
                        onClick = { onEpisodeClick(episode) },
                        onPlayClick = { context.playDownloadedItem(episode) },
                        onRemove = { removeEpisodes(listOf(episode)) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            if (episodes.isNotEmpty()) {
                item(key = "remove-all") {
                    TextButton(
                        onClick = { removeEpisodes(episodes) },
                        colors =
                            ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp).animateItem(),
                    ) {
                        Text(stringResource(CoreR.string.downloads_remove_all))
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: FindroidEpisode,
    sizeBytes: Long?,
    shape: Shape,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dismissState = rememberSwipeToDismissBoxState()
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) onRemove()
    }
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
                sizeBytes?.let { add(Formatter.formatShortFileSize(context, it)) }
            }
            .joinToString(" · ")
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = { SwipeActionBackground(icon = CoreR.drawable.ic_trash, shape = shape) },
        modifier = modifier,
    ) {
        DownloadRow(
            item = episode,
            title = episode.name,
            meta = meta,
            shape = shape,
            showPlaybackProgress = true,
            onPlayClick = onPlayClick,
            onClick = onClick,
        )
    }
}
