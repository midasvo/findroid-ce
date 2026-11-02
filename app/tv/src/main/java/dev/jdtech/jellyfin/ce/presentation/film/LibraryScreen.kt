package dev.jdtech.jellyfin.ce.presentation.film

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.jdtech.jellyfin.ce.ui.TvSafeArea
import dev.jdtech.jellyfin.ce.ui.focus.FocusPosition
import dev.jdtech.jellyfin.ce.ui.focus.RestoreFocus
import dev.jdtech.jellyfin.ce.ui.focus.focusPosition
import dev.jdtech.jellyfin.ce.ui.focus.rememberFocusPosition
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.dummy.dummyMovies
import dev.jdtech.jellyfin.film.presentation.library.LetterIndex
import dev.jdtech.jellyfin.film.presentation.library.LibraryAction
import dev.jdtech.jellyfin.film.presentation.library.LibraryState
import dev.jdtech.jellyfin.film.presentation.library.LibraryViewModel
import dev.jdtech.jellyfin.models.CollectionType
import dev.jdtech.jellyfin.models.FindroidBoxSet
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
import kotlinx.coroutines.launch

/**
 * CE replacement of the upstream TV library grid (ADR 0001). Focus comes back to the card that was
 * opened, at the same scroll position (ADR 0003).
 *
 * The grid knows its full length from the first page (#135), so it shows a position counter, and
 * when sorted by name an A–Z bar on the right that jumps to a letter.
 */
@Composable
fun LibraryScreen(
    libraryId: UUID,
    libraryName: String,
    libraryType: CollectionType,
    navigateToLibrary: (libraryId: UUID, libraryName: String, libraryType: CollectionType) -> Unit,
    navigateToMovie: (itemId: UUID) -> Unit,
    navigateToShow: (itemId: UUID) -> Unit,
    navigateToCollection: (collectionId: UUID, collectionName: String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(key = libraryId.toString()),
) {
    val state by viewModel.state.collectAsState()

    var initialLoad by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(true) {
        viewModel.setup(parentId = libraryId, libraryType = libraryType, indexed = true)
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
                        is FindroidBoxSet ->
                            navigateToCollection(action.item.id, action.item.name)
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
    val letterIndex = state.letterIndex

    var showSortByDialog by remember { mutableStateOf(false) }

    // The focused card, for the counter and the bar. Only those two read it, so a focus change
    // doesn't recompose the grid.
    val focusedIndex = remember { mutableIntStateOf(NO_CARD) }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                state = gridState,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
                contentPadding =
                    PaddingValues(
                        start = TvSafeArea.horizontal,
                        end =
                            if (letterIndex != null) MaterialTheme.spacings.default
                            else TvSafeArea.horizontal,
                        top = TvSafeArea.vertical,
                        bottom = TvSafeArea.vertical + COUNTER_HEIGHT,
                    ),
                // Instead of focusRestorer (ADR 0003): that also catches the request of a jump and
                // sends it back to the old card. This restores only when focus comes in by a key,
                // from the bar or the drawer, and lets a direct request through.
                modifier =
                    Modifier.weight(1f)
                        .fillMaxHeight()
                        .focusProperties {
                            onEnter = {
                                if (
                                    requestedFocusDirection != FocusDirection.Enter &&
                                        focusPosition.row == ROW_ITEMS
                                ) {
                                    focusPosition.requestFocus()
                                }
                            }
                        }
                        .focusGroup(),
            ) {
                item(span = { GridItemSpan(this.maxLineSpan) }) {
                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(MaterialTheme.spacings.default),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier.onFocusChanged {
                                if (it.hasFocus) focusedIndex.intValue = NO_CARD
                            },
                    ) {
                        Text(
                            text = libraryName,
                            style = MaterialTheme.typography.displayMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            onClick = {
                                onAction(LibraryAction.ToggleWatchedFilter(!state.filterWatched))
                            },
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
                    if (item == null) {
                        // Not loaded yet, for example right after a jump.
                        ItemCardSkeleton()
                    } else {
                        ItemCard(
                            item = item,
                            direction = Direction.VERTICAL,
                            onClick = { onAction(LibraryAction.OnItemClick(item)) },
                            showYear = true,
                            modifier =
                                Modifier.animateItem()
                                    .focusPosition(focusPosition, ROW_ITEMS, i, item.id.toString())
                                    .onFocusChanged { if (it.hasFocus) focusedIndex.intValue = i }
                                    .testTag(libraryCardTag(item.id)),
                        )
                    }
                }
            }
            if (letterIndex != null) {
                LetterJumpBar(
                    index = letterIndex,
                    focusedIndex = { focusedIndex.intValue },
                    onJump = { position ->
                        scope.launch { jumpTo(position, gridState, focusPosition) }
                    },
                    modifier =
                        Modifier.fillMaxHeight()
                            .padding(
                                end = TvSafeArea.horizontal,
                                top = TvSafeArea.vertical,
                                bottom = TvSafeArea.vertical,
                            )
                            .testTag(LETTER_BAR_TAG),
                )
            }
        }
        if (items.itemCount > 0) {
            PositionCounter(
                focusedIndex = { focusedIndex.intValue },
                total = items.itemCount,
                modifier =
                    Modifier.align(Alignment.BottomEnd)
                        .padding(
                            end =
                                TvSafeArea.horizontal +
                                    if (letterIndex != null) LETTER_BAR_WIDTH else 0.dp,
                            bottom = TvSafeArea.vertical / 2,
                        ),
            )
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

internal const val LETTER_BAR_TAG = "ce_library_letter_bar"

private const val NO_CARD = -1
private val COUNTER_HEIGHT = 24.dp
private val LETTER_BAR_WIDTH = 32.dp

/** About two seconds at 60 fps, for the page of a jump to come in on a slow box. */
private const val JUMP_FOCUS_FRAMES = 120

private fun LazyPagingItems<FindroidItem>.peekId(index: Int): String? =
    peek(index)?.id?.toString()

/** Scrolls only when [index] isn't on screen, so a return keeps the old scroll position. */
private suspend fun LazyGridState.scrollIntoView(index: Int) {
    if (layoutInfo.visibleItemsInfo.none { it.index == index }) scrollToItem(index)
}

/**
 * Scrolls to [position] and focuses that card once its page is in. Focus stays on the bar until
 * then. A later jump or focus move wins: this stops as soon as the saved position changes.
 */
private suspend fun jumpTo(position: Int, gridState: LazyGridState, focusPosition: FocusPosition) {
    focusPosition.save(ROW_ITEMS, position)
    gridState.scrollToItem(HEADER_ITEMS + position)
    repeat(JUMP_FOCUS_FRAMES) {
        withFrameNanos {}
        if (focusPosition.row != ROW_ITEMS || focusPosition.column != position) return
        if (focusPosition.requestFocus()) return
    }
}

/** `23 / 44`: the focused card and the length of the list. Hidden while the header has focus. */
@Composable
private fun PositionCounter(focusedIndex: () -> Int, total: Int, modifier: Modifier = Modifier) {
    val index = focusedIndex()
    if (index == NO_CARD) return
    Text(
        text = "${index + 1} / $total",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier =
            modifier
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    MaterialTheme.shapes.small,
                )
                .padding(horizontal = MaterialTheme.spacings.small, vertical = 2.dp),
    )
}

/**
 * The A–Z bar. The letter of the focused card is highlighted, empty letters are dimmed and skipped
 * by focus, and OK jumps. Entering the bar from the grid lands on the highlighted letter, so the
 * next letter is one press away.
 *
 * It reads the focused card through a derived letter, so moving within a letter doesn't recompose
 * it.
 */
@Composable
private fun LetterJumpBar(
    index: LetterIndex,
    focusedIndex: () -> Int,
    onJump: (position: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current by remember(index) { derivedStateOf { index.letterAt(focusedIndex()) } }
    val requesters = remember(index) { index.letters.associate { it.char to FocusRequester() } }
    Column(
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            modifier
                .width(LETTER_BAR_WIDTH)
                .focusProperties {
                    onEnter = {
                        val target =
                            current?.let { requesters[it] }
                                ?: index.letters
                                    .firstOrNull { !it.isEmpty }
                                    ?.let { requesters[it.char] }
                        target?.requestFocus()
                    }
                }
                .focusGroup(),
    ) {
        index.letters.forEach { letter ->
            LetterButton(
                letter = letter,
                highlighted = letter.char == current,
                onClick = { onJump(letter.start) },
                modifier = Modifier.weight(1f).focusRequester(requesters.getValue(letter.char)),
            )
        }
    }
}

@Composable
private fun LetterButton(
    letter: LetterIndex.Letter,
    highlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        enabled = !letter.isEmpty,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor =
                    if (highlighted) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                focusedContainerColor = MaterialTheme.colorScheme.onSurface,
                focusedContentColor = MaterialTheme.colorScheme.surface,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.2f),
        modifier =
            modifier.aspectRatio(1f, matchHeightConstraintsFirst = true).focusProperties {
                canFocus = !letter.isEmpty
            },
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = letter.char.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

/** The shape of a vertical [ItemCard] while its page loads, so rows don't jump when it's in. */
@Composable
private fun ItemCardSkeleton() {
    Column(modifier = Modifier.width(150.dp)) {
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .aspectRatio(0.66f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacings.small))
        Spacer(modifier = Modifier.height(40.dp))
    }
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
