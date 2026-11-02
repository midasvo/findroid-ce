package dev.jdtech.jellyfin.ce.presentation.film

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.jdtech.jellyfin.ce.ui.TvSafeArea
import dev.jdtech.jellyfin.ce.ui.focus.FocusPosition
import dev.jdtech.jellyfin.ce.ui.focus.RestoreFocus
import dev.jdtech.jellyfin.ce.ui.focus.focusPosition
import dev.jdtech.jellyfin.ce.ui.focus.rememberFocusPosition
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.dummy.dummyMovies
import dev.jdtech.jellyfin.film.presentation.library.LibraryAction
import dev.jdtech.jellyfin.film.presentation.library.LibraryState
import dev.jdtech.jellyfin.film.presentation.library.LibraryViewModel
import dev.jdtech.jellyfin.models.CollectionType
import dev.jdtech.jellyfin.models.FindroidFolder
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.presentation.film.components.SortByDialog
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.ui.components.Direction
import dev.jdtech.jellyfin.ui.components.ItemCard
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * CE replacement of the upstream TV library grid (ADR 0001). Focus comes back to the card that was
 * opened, at the same scroll position (ADR 0003).
 */
@Composable
fun LibraryScreen(
    libraryId: UUID,
    libraryName: String,
    libraryType: CollectionType,
    navigateToLibrary: (libraryId: UUID, libraryName: String, libraryType: CollectionType) -> Unit,
    navigateToMovie: (itemId: UUID) -> Unit,
    navigateToShow: (itemId: UUID) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(key = libraryId.toString()),
) {
    val state by viewModel.state.collectAsState()

    var initialLoad by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(true) {
        viewModel.setup(parentId = libraryId, libraryType = libraryType)
        if (initialLoad) {
            viewModel.loadItems()
            initialLoad = false
        }
    }

    LibraryScreenLayout(
        libraryName = libraryName,
        state = state,
        onAction = { action ->
            when (action) {
                is LibraryAction.OnItemClick -> {
                    when (action.item) {
                        is FindroidMovie -> navigateToMovie(action.item.id)
                        is FindroidShow -> navigateToShow(action.item.id)
                        is FindroidFolder ->
                            navigateToLibrary(action.item.id, action.item.name, libraryType)
                    }
                }
                else -> Unit
            }
            viewModel.onAction(action)
        },
    )
}

private const val ROW_HEADER = 0
private const val ROW_ITEMS = 1

/** The header (title and buttons) is grid item 0; the cards follow it. */
private const val HEADER_ITEMS = 1

@Composable
internal fun LibraryScreenLayout(
    libraryName: String,
    state: LibraryState,
    onAction: (LibraryAction) -> Unit,
    gridState: LazyGridState = rememberLazyGridState(),
    focusPosition: FocusPosition = rememberFocusPosition(row = ROW_ITEMS, column = 0),
) {
    val items = state.items.collectAsLazyPagingItems()

    var showSortByDialog by remember { mutableStateOf(false) }

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
            Row(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = libraryName,
                    style = MaterialTheme.typography.displayMedium,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { onAction(LibraryAction.ToggleWatchedFilter(!state.filterWatched)) },
                    modifier = Modifier.focusPosition(focusPosition, ROW_HEADER, 0),
                ) {
                    Icon(
                        painter =
                            painterResource(
                                if (state.filterWatched) CoreR.drawable.ic_eye_off
                                else CoreR.drawable.ic_eye
                            ),
                        contentDescription = stringResource(CoreR.string.toggle_watched),
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(text = stringResource(CoreR.string.toggle_watched))
                }
                Button(
                    onClick = { showSortByDialog = true },
                    modifier = Modifier.focusPosition(focusPosition, ROW_HEADER, 1),
                ) {
                    Icon(
                        painter = painterResource(CoreR.drawable.ic_arrow_down_up),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(text = stringResource(CoreR.string.sort_by))
                }
            }
        }
        items(count = items.itemCount, key = { i -> items.peek(i)?.id ?: i }) { i ->
            val item = items[i]
            item?.let {
                ItemCard(
                    item = item,
                    direction = Direction.VERTICAL,
                    onClick = { onAction(LibraryAction.OnItemClick(item)) },
                    modifier =
                        Modifier.animateItem()
                            .focusPosition(focusPosition, ROW_ITEMS, i, item.id.toString())
                            .testTag(libraryCardTag(item.id)),
                )
            }
        }
    }

    val loaded = items.itemCount > 0 || items.loadState.refresh is LoadState.NotLoading
    RestoreFocus(position = focusPosition, ready = loaded && !state.isLoading) { position ->
        if (items.itemCount == 0) {
            // Nothing to show (for example after a filter): fall back to the first button.
            position.save(ROW_HEADER, 0)
            return@RestoreFocus
        }
        position.resolve(ROW_ITEMS, items.itemCount) { i -> items.peekId(i) }
        if (position.row == ROW_ITEMS) {
            gridState.scrollIntoView(HEADER_ITEMS + position.column)
        }
    }

    if (showSortByDialog) {
        SortByDialog(
            currentSortBy = state.sortBy,
            currentSortOrder = state.sortOrder,
            onUpdate = { sortBy, sortOrder ->
                onAction(LibraryAction.ChangeSorting(sortBy, sortOrder))
            },
            onDismissRequest = { showSortByDialog = false },
        )
    }
}

internal fun libraryCardTag(id: UUID) = "ce_library_card_$id"

private fun LazyPagingItems<FindroidItem>.peekId(index: Int): String? =
    peek(index)?.id?.toString()

/** Scrolls only when [index] isn't on screen, so a return keeps the old scroll position. */
private suspend fun LazyGridState.scrollIntoView(index: Int) {
    if (layoutInfo.visibleItemsInfo.none { it.index == index }) scrollToItem(index)
}

@Preview(device = "id:tv_1080p")
@Composable
private fun LibraryScreenLayoutPreview() {
    val items: Flow<PagingData<FindroidItem>> = flowOf(PagingData.from(dummyMovies))
    FindroidTheme {
        LibraryScreenLayout(
            libraryName = "Movies",
            state = LibraryState(items = items),
            onAction = {},
        )
    }
}
