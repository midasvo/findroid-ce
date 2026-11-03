package dev.jdtech.jellyfin.ce.presentation.film

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.jdtech.jellyfin.ce.ui.TvSafeArea
import dev.jdtech.jellyfin.ce.ui.focus.FocusPosition
import dev.jdtech.jellyfin.ce.ui.focus.RestoreFocus
import dev.jdtech.jellyfin.ce.ui.focus.focusPosition
import dev.jdtech.jellyfin.ce.ui.focus.rememberFocusPosition
import dev.jdtech.jellyfin.core.Constants
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.dummy.dummyEpisodes
import dev.jdtech.jellyfin.core.presentation.dummy.dummyMovies
import dev.jdtech.jellyfin.film.presentation.collection.CollectionState
import dev.jdtech.jellyfin.film.presentation.favorites.FavoritesViewModel
import dev.jdtech.jellyfin.models.CollectionSection
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.ui.components.Direction
import dev.jdtech.jellyfin.ui.components.ItemCard
import java.util.UUID
import kotlinx.coroutines.withTimeoutOrNull

/**
 * CE Favorites screen (#137, ADR 0001, ADR 0003):
 *
 * Displays favorite movies, shows, and episodes in rows by type.
 * When empty, explains how to add favorites using the heart icon on detail pages.
 * Restores focus to the last-viewed card on return.
 */
@Composable
fun FavoritesScreen(
    navigateToMovie: (itemId: UUID) -> Unit,
    navigateToShow: (itemId: UUID) -> Unit,
    navigateToEpisode: (episodeId: UUID) -> Unit,
    viewModel: FavoritesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    var initialLoad by rememberSaveable { mutableStateOf(true) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val job = viewModel.loadItems()
        if (initialLoad) {
            initialLoad = false
            job.join()
        } else {
            withTimeoutOrNull(REFRESH_WAIT_MS) { job.join() }
        }
        loaded = true
    }

    FavoritesScreenLayout(
        state = state,
        loaded = loaded,
        onItemClick = { item ->
            when (item) {
                is FindroidMovie -> navigateToMovie(item.id)
                is FindroidShow -> navigateToShow(item.id)
                is FindroidEpisode -> navigateToEpisode(item.id)
            }
        },
        onRetry = { viewModel.loadItems() },
    )
}

internal const val FAVORITES_SCREEN_TAG = "ce_favorites_screen"
internal const val FAVORITES_EMPTY_TAG = "ce_favorites_empty"

internal fun favoritesCardTag(id: UUID) = "ce_favorites_card_$id"

@Composable
internal fun FavoritesScreenLayout(
    state: CollectionState,
    loaded: Boolean = true,
    onItemClick: (FindroidItem) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    focusPosition: FocusPosition = rememberFocusPosition(row = 0, column = 0),
) {
    val rowStates = remember { mutableMapOf<Int, LazyListState>() }

    Box(modifier = modifier.fillMaxSize().testTag(FAVORITES_SCREEN_TAG)) {
        when {
            (!loaded || state.isLoading) && state.sections.isEmpty() -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            state.error != null && state.sections.isEmpty() -> {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(TvSafeArea.horizontal),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
                ) {
                    Text(
                        text =
                            state.error?.localizedMessage.orEmpty().ifBlank {
                                stringResource(CoreR.string.error_loading_data_body)
                            },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.focusPosition(focusPosition, 0, 0),
                    ) {
                        Text(text = stringResource(CoreR.string.retry))
                    }
                }
            }
            state.sections.isEmpty() -> {
                Box(
                    modifier =
                        Modifier.fillMaxSize()
                            .focusPosition(focusPosition, 0, 0)
                            .focusable()
                            .testTag(FAVORITES_EMPTY_TAG),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
                        modifier = Modifier.padding(horizontal = TvSafeArea.horizontal),
                    ) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_heart),
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                        Text(
                            text = stringResource(CoreR.string.no_favorites),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text(
                            text = stringResource(CoreR.string.favorites_empty_body),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(vertical = TvSafeArea.vertical),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(key = "header") {
                        Text(
                            text = stringResource(CoreR.string.title_favorite),
                            style = MaterialTheme.typography.headlineLarge,
                            modifier = Modifier.padding(horizontal = TvSafeArea.horizontal),
                        )
                    }

                    itemsIndexed(state.sections, key = { _, section -> section.id }) {
                        rowIndex,
                        section ->
                        FavoritesRowContent(
                            section = section,
                            rowIndex = rowIndex,
                            focusPosition = focusPosition,
                            rowStates = rowStates,
                            onItemClick = onItemClick,
                        )
                    }
                }
            }
        }
    }

    val left = remember { Triple(focusPosition.row, focusPosition.column, focusPosition.key) }
    RestoreFocus(
        position = focusPosition,
        ready = loaded && (!state.isLoading || state.sections.isNotEmpty()),
    ) { position ->
        if (state.sections.isEmpty()) return@RestoreFocus

        val (leftRow, leftColumn, key) = left
        val found =
            state.sections.withIndex().firstNotNullOfOrNull { (r, section) ->
                section.items
                    .indexOfFirst { it.id.toString() == key }
                    .takeIf { it >= 0 }
                    ?.let { c -> r to c }
            }
        if (found != null) {
            position.save(found.first, found.second, key)
        } else {
            val r = leftRow.coerceIn(0, state.sections.lastIndex)
            position.save(r, leftColumn.coerceIn(0, state.sections[r].items.lastIndex))
        }
        listState.scrollIntoView(1 + position.row)
        withFrameNanos {}
        rowStates[state.sections[position.row].id]?.scrollIntoView(position.column)
    }
}

/** Scrolls only when [index] isn't on screen, so a return keeps the old scroll position. */
private suspend fun LazyListState.scrollIntoView(index: Int) {
    if (layoutInfo.visibleItemsInfo.none { it.index == index }) scrollToItem(index)
}

private const val REFRESH_WAIT_MS = 1_500L

@Composable
private fun FavoritesRowContent(
    section: CollectionSection,
    rowIndex: Int,
    focusPosition: FocusPosition,
    rowStates: MutableMap<Int, LazyListState>,
    onItemClick: (FindroidItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowState = rememberLazyListState()
    DisposableEffect(section.id, rowState) {
        rowStates[section.id] = rowState
        onDispose {
            if (rowStates[section.id] === rowState) {
                rowStates.remove(section.id)
            }
        }
    }

    val direction =
        if (section.id == Constants.FAVORITE_TYPE_EPISODES) Direction.HORIZONTAL
        else Direction.VERTICAL

    Column(modifier = modifier) {
        Text(
            text = section.name.asString(),
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = TvSafeArea.horizontal),
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacings.small))
        LazyRow(
            state = rowState,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
            contentPadding = PaddingValues(horizontal = TvSafeArea.horizontal),
            modifier = Modifier.focusRestorer(),
        ) {
            itemsIndexed(section.items, key = { _, item -> item.id }) { column, item ->
                ItemCard(
                    item = item,
                    direction = direction,
                    showSeriesName = direction == Direction.HORIZONTAL,
                    onClick = { onItemClick(item) },
                    modifier =
                        Modifier.focusPosition(
                                focusPosition,
                                rowIndex,
                                column,
                                item.id.toString(),
                            )
                            .testTag(favoritesCardTag(item.id)),
                )
            }
        }
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun FavoritesScreenLayoutPreview() {
    FindroidTheme {
        FavoritesScreenLayout(
            state =
                CollectionState(
                    sections =
                        listOf(
                            CollectionSection(
                                id = Constants.FAVORITE_TYPE_MOVIES,
                                name = UiText.StringResource(CoreR.string.movies_label),
                                items = dummyMovies,
                            ),
                            CollectionSection(
                                id = Constants.FAVORITE_TYPE_EPISODES,
                                name = UiText.StringResource(CoreR.string.episodes_label),
                                items = dummyEpisodes,
                            ),
                        )
                ),
            onItemClick = {},
            onRetry = {},
        )
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun FavoritesScreenEmptyPreview() {
    FindroidTheme {
        FavoritesScreenLayout(
            state = CollectionState(sections = emptyList()),
            onItemClick = {},
            onRetry = {},
        )
    }
}
