package dev.jdtech.jellyfin.ce.presentation.film

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.jdtech.jellyfin.ce.ui.TvSafeArea
import dev.jdtech.jellyfin.ce.ui.focus.FocusPosition
import dev.jdtech.jellyfin.ce.ui.focus.RestoreFocus
import dev.jdtech.jellyfin.ce.ui.focus.focusPosition
import dev.jdtech.jellyfin.ce.ui.focus.rememberFocusPosition
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.dummy.dummyMovies
import dev.jdtech.jellyfin.film.presentation.collection.CollectionState
import dev.jdtech.jellyfin.film.presentation.collection.CollectionViewModel
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

/**
 * CE collection grid on Android TV (ADR 0001). Back from an item restores focus to that card (ADR 0003).
 */
@Composable
fun CollectionScreen(
    collectionId: UUID,
    collectionName: String,
    navigateToMovie: (itemId: UUID) -> Unit,
    navigateToShow: (itemId: UUID) -> Unit,
    navigateToEpisode: (episodeId: UUID) -> Unit,
    viewModel: CollectionViewModel = hiltViewModel(key = collectionId.toString()),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(collectionId) {
        viewModel.loadItems(parentId = collectionId)
    }

    CollectionScreenLayout(
        collectionName = collectionName,
        state = state,
        onItemClick = { item ->
            when (item) {
                is FindroidMovie -> navigateToMovie(item.id)
                is FindroidShow -> navigateToShow(item.id)
                is FindroidEpisode -> navigateToEpisode(item.id)
                else -> Unit
            }
        },
    )
}

@Composable
internal fun CollectionScreenLayout(
    collectionName: String,
    state: CollectionState,
    onItemClick: (FindroidItem) -> Unit,
    gridState: LazyGridState = rememberLazyGridState(),
    focusPosition: FocusPosition = rememberFocusPosition(row = 0, column = 0),
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.isLoading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            state.sections.isEmpty() -> {
                Text(
                    text = stringResource(CoreR.string.collection_no_media),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    state = gridState,
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
                    contentPadding =
                        PaddingValues(
                            horizontal = TvSafeArea.horizontal,
                            vertical = TvSafeArea.vertical,
                        ),
                    modifier = Modifier.fillMaxSize().focusRestorer(),
                ) {
                    item(span = { GridItemSpan(this.maxLineSpan) }) {
                        Text(
                            text = collectionName,
                            style = MaterialTheme.typography.displayMedium,
                        )
                    }
                    state.sections.forEachIndexed { sectionIndex, section ->
                        if (state.sections.size > 1) {
                            item(span = { GridItemSpan(this.maxLineSpan) }) {
                                Text(
                                    text = section.name.asString(),
                                    style = MaterialTheme.typography.headlineSmall,
                                    modifier =
                                        Modifier.padding(top = MaterialTheme.spacings.medium),
                                )
                            }
                        }
                        items(
                            count = section.items.size,
                            key = { i -> section.items[i].id },
                        ) { i ->
                            val item = section.items[i]
                            val direction =
                                if (item is FindroidEpisode) Direction.HORIZONTAL
                                else Direction.VERTICAL
                            ItemCard(
                                item = item,
                                direction = direction,
                                onClick = { onItemClick(item) },
                                modifier =
                                    Modifier.animateItem()
                                        .focusPosition(
                                            focusPosition,
                                            row = sectionIndex,
                                            column = i,
                                            key = item.id.toString(),
                                        )
                                        .testTag(collectionCardTag(item.id)),
                            )
                        }
                    }
                }

                RestoreFocus(
                    position = focusPosition,
                    ready = !state.isLoading && state.sections.isNotEmpty(),
                ) { position ->
                    val sectionIndex = position.row.coerceIn(0, state.sections.lastIndex)
                    val section = state.sections[sectionIndex]
                    position.resolve(sectionIndex, section.items.size) { i ->
                        section.items.getOrNull(i)?.id?.toString()
                    }
                    val flatIndex =
                        calculateFlatGridIndex(position.row, position.column, state.sections)
                    gridState.scrollIntoView(flatIndex)
                }
            }
        }
    }
}

internal fun collectionCardTag(id: UUID) = "ce_collection_card_$id"

private fun calculateFlatGridIndex(row: Int, column: Int, sections: List<CollectionSection>): Int {
    var index = 1 // 0 is collectionName title
    val targetRow = row.coerceIn(0, sections.lastIndex)
    for (s in 0 until targetRow) {
        if (sections.size > 1) index++
        index += sections[s].items.size
    }
    if (sections.size > 1) index++
    index += column.coerceIn(0, (sections[targetRow].items.size - 1).coerceAtLeast(0))
    return index
}

private suspend fun LazyGridState.scrollIntoView(index: Int) {
    if (layoutInfo.visibleItemsInfo.none { it.index == index }) scrollToItem(index)
}

@Preview(device = "id:tv_1080p")
@Composable
private fun CollectionScreenLayoutPreview() {
    FindroidTheme {
        CollectionScreenLayout(
            collectionName = "Marvel",
            state =
                CollectionState(
                    sections =
                        listOf(
                            CollectionSection(
                                id = 0,
                                name = UiText.StringResource(CoreR.string.movies_label),
                                items = dummyMovies,
                            )
                        ),
                    isLoading = false,
                ),
            onItemClick = {},
        )
    }
}
