package dev.jdtech.jellyfin.ce.presentation.film

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.jdtech.jellyfin.ce.ui.focus.RestoreFocus
import dev.jdtech.jellyfin.ce.ui.focus.focusPosition
import dev.jdtech.jellyfin.ce.ui.focus.rememberFocusPosition
import dev.jdtech.jellyfin.core.presentation.dummy.dummyEpisodes
import dev.jdtech.jellyfin.core.presentation.dummy.dummySeason
import dev.jdtech.jellyfin.film.presentation.season.SeasonAction
import dev.jdtech.jellyfin.film.presentation.season.SeasonState
import dev.jdtech.jellyfin.film.presentation.season.SeasonViewModel
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.ui.components.EpisodeCard
import java.util.UUID

/**
 * CE replacement of the upstream TV season page (ADR 0001). Back from the player focuses the episode
 * that was played (ADR 0003).
 */
@Composable
fun SeasonScreen(
    seasonId: UUID,
    navigateToPlayer: (itemId: UUID) -> Unit,
    viewModel: SeasonViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(true) { viewModel.loadSeason(seasonId = seasonId) }

    SeasonScreenLayout(
        state = state,
        onAction = { action ->
            when (action) {
                is SeasonAction.NavigateToItem -> navigateToPlayer(action.item.id)
                else -> Unit
            }
            viewModel.onAction(action)
        },
    )
}

@Composable
private fun SeasonScreenLayout(state: SeasonState, onAction: (SeasonAction) -> Unit) {
    // Default focus: the first episode. After Back from the player: the episode that was played.
    val focusPosition = rememberFocusPosition()
    val listState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        state.season?.let { season ->
            Row(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier =
                        Modifier.weight(1f)
                            .padding(
                                start = MaterialTheme.spacings.extraLarge,
                                top = MaterialTheme.spacings.large,
                                end = MaterialTheme.spacings.large,
                            )
                ) {
                    Text(text = season.name, style = MaterialTheme.typography.displayMedium)
                    Text(text = season.seriesName, style = MaterialTheme.typography.headlineMedium)
                }
                LazyColumn(
                    state = listState,
                    contentPadding =
                        PaddingValues(
                            top = MaterialTheme.spacings.large,
                            bottom = MaterialTheme.spacings.large,
                        ),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
                    modifier =
                        Modifier.weight(2f)
                            .padding(end = MaterialTheme.spacings.extraLarge)
                            .focusRestorer(),
                ) {
                    itemsIndexed(state.episodes, key = { _, episode -> episode.id }) {
                        index,
                        episode ->
                        // The Box carries the focus position: EpisodeCard (upstream) takes no modifier.
                        Box(
                            modifier =
                                Modifier.focusPosition(
                                    focusPosition,
                                    row = 0,
                                    column = index,
                                    key = episode.id.toString(),
                                )
                        ) {
                            EpisodeCard(
                                episode = episode,
                                onClick = { onAction(SeasonAction.NavigateToItem(episode)) },
                            )
                        }
                    }
                }
            }

            RestoreFocus(position = focusPosition, ready = state.episodes.isNotEmpty()) { position ->
                position.resolve(0, state.episodes.size) { i -> state.episodes[i].id.toString() }
                listState.scrollIntoView(position.column)
            }
        } ?: run { CircularProgressIndicator(modifier = Modifier.align(Alignment.Center)) }
    }
}

/** Scrolls only when [index] isn't on screen, so a return keeps the old scroll position. */
private suspend fun LazyListState.scrollIntoView(index: Int) {
    if (layoutInfo.visibleItemsInfo.none { it.index == index }) scrollToItem(index)
}

@Preview(device = "id:tv_1080p")
@Composable
private fun SeasonScreenLayoutPreview() {
    FindroidTheme {
        SeasonScreenLayout(
            state = SeasonState(season = dummySeason, episodes = dummyEpisodes),
            onAction = {},
        )
    }
}
