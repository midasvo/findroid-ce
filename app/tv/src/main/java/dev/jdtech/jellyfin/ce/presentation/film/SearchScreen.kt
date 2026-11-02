package dev.jdtech.jellyfin.ce.presentation.film

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon as M3Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text as M3Text
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.jdtech.jellyfin.ce.ui.TvSafeArea
import dev.jdtech.jellyfin.ce.ui.focus.FocusPosition
import dev.jdtech.jellyfin.ce.ui.focus.RestoreFocus
import dev.jdtech.jellyfin.ce.ui.focus.focusPosition
import dev.jdtech.jellyfin.ce.ui.focus.rememberFocusPosition
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.dummy.dummyEpisodes
import dev.jdtech.jellyfin.core.presentation.dummy.dummyMovies
import dev.jdtech.jellyfin.film.presentation.search.SearchAction
import dev.jdtech.jellyfin.film.presentation.search.SearchState
import dev.jdtech.jellyfin.film.presentation.search.SearchViewModel
import dev.jdtech.jellyfin.models.FindroidBoxSet
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.ui.components.Direction
import dev.jdtech.jellyfin.ui.components.ItemCard
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemKind

/**
 * CE search screen (G4), a drawer destination (ADR 0002). Results update while typing and come in
 * one row per kind. Down leaves the text field for the results, and focus comes back to the result
 * that was opened (ADR 0003).
 */
@Composable
fun SearchScreen(
    navigateToMovie: (itemId: UUID) -> Unit,
    navigateToShow: (itemId: UUID) -> Unit,
    navigateToSeason: (seasonId: UUID) -> Unit,
    navigateToCollection: (collectionId: UUID, name: String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(true) { viewModel.setup(itemKinds = SearchGroup.entries.map { it.kind }) }

    SearchScreenLayout(
        state = state,
        onAction = { action ->
            when (action) {
                is SearchAction.OnItemClick -> {
                    when (val item = action.item) {
                        is FindroidMovie -> navigateToMovie(item.id)
                        is FindroidShow -> navigateToShow(item.id)
                        // Until #130, an episode opens its season instead of playing right away.
                        is FindroidEpisode -> navigateToSeason(item.seasonId)
                        is FindroidBoxSet -> navigateToCollection(item.id, item.name)
                    }
                }
                else -> Unit
            }
            viewModel.onAction(action)
        },
    )
}

/** The result rows, in screen order. */
private enum class SearchGroup(val kind: BaseItemKind, @StringRes val title: Int) {
    MOVIES(BaseItemKind.MOVIE, CoreR.string.movies_label),
    SHOWS(BaseItemKind.SERIES, CoreR.string.shows_label),
    EPISODES(BaseItemKind.EPISODE, CoreR.string.episodes_label),
    COLLECTIONS(BaseItemKind.BOX_SET, CoreR.string.collections_label);

    fun matches(item: FindroidItem): Boolean =
        when (this) {
            MOVIES -> item is FindroidMovie
            SHOWS -> item is FindroidShow
            EPISODES -> item is FindroidEpisode
            COLLECTIONS -> item is FindroidBoxSet
        }
}

private const val ROW_FIELD = 0

/** Focus row of a result group: one row per [SearchGroup], after the text field. */
private fun SearchGroup.row() = ROW_FIELD + 1 + ordinal

@Composable
internal fun SearchScreenLayout(
    state: SearchState,
    onAction: (SearchAction) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    focusPosition: FocusPosition = rememberFocusPosition(row = ROW_FIELD, column = 0),
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var query by rememberSaveable { mutableStateOf("") }
    // Not saveable: on the first visit the keyboard opens right away, on a return it doesn't.
    var editing by remember { mutableStateOf(query.isEmpty()) }
    var fieldFocused by remember { mutableStateOf(false) }

    LaunchedEffect(editing) { if (editing) keyboardController?.show() }

    val groups =
        remember(state.items) {
            SearchGroup.entries
                .map { group -> group to state.items.filter { group.matches(it) } }
                .filter { (_, items) -> items.isNotEmpty() }
        }

    LazyColumn(
        state = listState,
        contentPadding =
            PaddingValues(horizontal = TvSafeArea.horizontal, vertical = TvSafeArea.vertical),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "field") {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    onAction(SearchAction.Search(it))
                },
                placeholder = { M3Text(stringResource(CoreR.string.search_hint)) },
                leadingIcon = {
                    M3Icon(
                        painter = painterResource(CoreR.drawable.ic_search),
                        contentDescription = null,
                    )
                },
                singleLine = true,
                // The IME shows itself when an input session starts, so moving up from the
                // results would open it. A read-only field starts no session: OK makes it
                // editable and opens the keyboard.
                readOnly = !editing,
                keyboardOptions =
                    KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
                keyboardActions =
                    KeyboardActions(
                        onSearch = {
                            keyboardController?.hide()
                            if (groups.isNotEmpty()) focusManager.moveFocus(FocusDirection.Down)
                        }
                    ),
                modifier =
                    Modifier.fillMaxWidth()
                        .focusPosition(focusPosition, ROW_FIELD, 0)
                        .onFocusChanged {
                            // It also reports "not focused" when attached: only act on a loss.
                            if (fieldFocused && !it.isFocused) editing = false
                            fieldFocused = it.isFocused
                        }
                        .onPreviewKeyEvent { event ->
                            when {
                                !editing && event.key.isOk() -> {
                                    if (event.type == KeyEventType.KeyUp) editing = true
                                    true
                                }
                                // Down always leaves the field for the results, also with text.
                                event.type == KeyEventType.KeyDown &&
                                    event.key == Key.DirectionDown &&
                                    groups.isNotEmpty() -> {
                                    keyboardController?.hide()
                                    focusManager.moveFocus(FocusDirection.Down)
                                }
                                else -> false
                            }
                        }
                        .testTag(SEARCH_FIELD_TAG),
            )
        }

        if (groups.isEmpty()) {
            item(key = "status") {
                val message =
                    when {
                        query.isBlank() || state.loading || state.query != query -> null
                        state.error != null -> state.error?.localizedMessage
                        else -> stringResource(CoreR.string.no_search_results)
                    }
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    message?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        groups.forEach { (group, items) ->
            item(key = group.name) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small)
                ) {
                    Text(
                        text = stringResource(group.title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
                        // Room for the focused card to scale up.
                        contentPadding = PaddingValues(vertical = MaterialTheme.spacings.small),
                        modifier = Modifier.focusRestorer().testTag(searchRowTag(group.kind)),
                    ) {
                        itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                            ItemCard(
                                item = item,
                                direction =
                                    if (group == SearchGroup.EPISODES) Direction.HORIZONTAL
                                    else Direction.VERTICAL,
                                onClick = { onAction(SearchAction.OnItemClick(item)) },
                                modifier =
                                    Modifier.focusPosition(
                                            focusPosition,
                                            group.row(),
                                            index,
                                            item.id.toString(),
                                        )
                                        .testTag(searchCardTag(item.id)),
                            )
                        }
                    }
                }
            }
        }
    }

    // The results live in the ViewModel, so they are there right away when the screen comes back.
    RestoreFocus(position = focusPosition, ready = true) { position ->
        val index = groups.indexOfFirst { (group, _) -> group.row() == position.row }
        if (index < 0) {
            // The row is gone (no results of that kind any more): back to the text field.
            position.save(ROW_FIELD, 0)
            return@RestoreFocus
        }
        val items = groups[index].second
        position.resolve(position.row, items.size) { i -> items[i].id.toString() }
        // The text field is list item 0, the groups follow it.
        listState.scrollToItem(1 + index)
    }
}

private fun Key.isOk() = this == Key.DirectionCenter || this == Key.Enter || this == Key.NumPadEnter

internal const val SEARCH_FIELD_TAG = "ce_search_field"

internal fun searchRowTag(kind: BaseItemKind) = "ce_search_row_${kind.serialName}"

internal fun searchCardTag(id: UUID) = "ce_search_card_$id"

@Preview(device = "id:tv_1080p")
@Composable
private fun SearchScreenLayoutPreview() {
    FindroidTheme {
        SearchScreenLayout(
            state = SearchState(query = "", items = dummyMovies + dummyEpisodes),
            onAction = {},
        )
    }
}
