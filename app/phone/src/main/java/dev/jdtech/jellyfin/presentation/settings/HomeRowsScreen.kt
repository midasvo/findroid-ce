package dev.jdtech.jellyfin.presentation.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.recalculateWindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.film.R as FilmR
import dev.jdtech.jellyfin.film.domain.homerows.HomeRowConfig
import dev.jdtech.jellyfin.film.domain.homerows.HomeRowType
import dev.jdtech.jellyfin.film.presentation.homerows.HomeRowEntry
import dev.jdtech.jellyfin.film.presentation.homerows.HomeRowsAction
import dev.jdtech.jellyfin.film.presentation.homerows.HomeRowsState
import dev.jdtech.jellyfin.film.presentation.homerows.HomeRowsViewModel
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.presentation.film.components.ErrorState
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.settings.R as SettingsR

/** CE (#142): Settings → Interface → Home rows. A switch per row, and buttons to move it. */
@Composable
fun HomeRowsScreen(navigateBack: () -> Unit, viewModel: HomeRowsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    HomeRowsScreenLayout(
        state = state,
        onAction = { action ->
            when (action) {
                is HomeRowsAction.OnBackClick -> navigateBack()
                else -> Unit
            }
            viewModel.onAction(action)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeRowsScreenLayout(state: HomeRowsState, onAction: (HomeRowsAction) -> Unit) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier =
            Modifier.fillMaxSize()
                .recalculateWindowInsets()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(SettingsR.string.home_rows)) },
                navigationIcon = {
                    IconButton(onClick = { onAction(HomeRowsAction.OnBackClick) }) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_arrow_left),
                            contentDescription = null,
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            val error = state.error
            when {
                state.isLoading ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                error != null ->
                    ErrorState(
                        error = error,
                        onRetryClick = { onAction(HomeRowsAction.OnRetryClick) },
                        modifier = Modifier.fillMaxSize(),
                    )
                else ->
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().testTag("ce_home_rows_settings"),
                        contentPadding = innerPadding,
                    ) {
                        items(state.rows, key = { it.key }) { entry ->
                            HomeRowItem(
                                entry = entry,
                                isFirst = entry == state.rows.first(),
                                isLast = entry == state.rows.last(),
                                onAction = onAction,
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
            }
        }
    }
}

@Composable
private fun HomeRowItem(
    entry: HomeRowEntry,
    isFirst: Boolean,
    isLast: Boolean,
    onAction: (HomeRowsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = { Text(entry.title.asString()) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onAction(HomeRowsAction.OnMoveUp(entry.key)) },
                    enabled = !isFirst,
                ) {
                    Icon(
                        painter = painterResource(CoreR.drawable.ic_chevron_up),
                        contentDescription = stringResource(FilmR.string.home_rows_move_up),
                    )
                }
                IconButton(
                    onClick = { onAction(HomeRowsAction.OnMoveDown(entry.key)) },
                    enabled = !isLast,
                ) {
                    Icon(
                        painter = painterResource(CoreR.drawable.ic_chevron_down),
                        contentDescription = stringResource(FilmR.string.home_rows_move_down),
                    )
                }
                Switch(
                    checked = entry.row.visible,
                    onCheckedChange = { onAction(HomeRowsAction.OnVisibleChange(entry.key, it)) },
                )
            }
        },
        modifier = modifier.testTag("ce_home_row_${entry.key}"),
    )
}

@Preview
@Composable
private fun HomeRowsScreenLayoutPreview() {
    FindroidTheme {
        HomeRowsScreenLayout(
            state =
                HomeRowsState(
                    rows =
                        listOf(
                            HomeRowEntry(
                                HomeRowConfig(HomeRowType.CONTINUE_WATCHING),
                                UiText.StringResource(FilmR.string.continue_watching),
                            ),
                            HomeRowEntry(
                                HomeRowConfig(HomeRowType.FAVORITES, visible = false),
                                UiText.StringResource(CoreR.string.title_favorite),
                            ),
                        ),
                    isLoading = false,
                ),
            onAction = {},
        )
    }
}
