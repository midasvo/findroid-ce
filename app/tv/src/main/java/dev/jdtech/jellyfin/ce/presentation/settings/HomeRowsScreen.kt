package dev.jdtech.jellyfin.ce.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import dev.jdtech.jellyfin.ce.ui.TvSafeArea
import dev.jdtech.jellyfin.ce.ui.focus.ephyraFocusedBorder
import dev.jdtech.jellyfin.ce.ui.focus.ephyraFocusedGlow
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.film.R as FilmR
import dev.jdtech.jellyfin.film.domain.homerows.HomeRowConfig
import dev.jdtech.jellyfin.film.domain.homerows.HomeRowType
import dev.jdtech.jellyfin.film.presentation.homerows.HomeRowEntry
import dev.jdtech.jellyfin.film.presentation.homerows.HomeRowsAction
import dev.jdtech.jellyfin.film.presentation.homerows.HomeRowsState
import dev.jdtech.jellyfin.film.presentation.homerows.HomeRowsViewModel
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.settings.R as SettingsR

/**
 * CE (#142): Settings → Interface → Home rows on TV. Each row is a switch (OK shows or hides it)
 * with an up and a down button next to it. Moving a row keeps focus on the button that moved it, so
 * pressing OK again moves it further. The buttons stay focusable at the ends of the list, where they
 * do nothing, so focus never has to go somewhere else.
 */
@Composable
fun HomeRowsScreen(viewModel: HomeRowsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    HomeRowsScreenLayout(state = state, onAction = viewModel::onAction)
}

@Composable
private fun HomeRowsScreenLayout(state: HomeRowsState, onAction: (HomeRowsAction) -> Unit) {
    val firstRow = remember { FocusRequester() }
    val retry = remember { FocusRequester() }
    val listState = rememberLazyListState()

    Column(
        modifier =
            Modifier.fillMaxSize()
                .padding(
                    start = TvSafeArea.horizontal,
                    top = TvSafeArea.vertical,
                    end = TvSafeArea.horizontal,
                )
    ) {
        Text(
            text = stringResource(SettingsR.string.home_rows),
            style = MaterialTheme.typography.displayMedium,
        )
        Text(
            text = stringResource(SettingsR.string.home_rows_summary),
            style = MaterialTheme.typography.headlineMedium,
        )
        Box(modifier = Modifier.fillMaxSize()) {
            val error = state.error
            when {
                state.isLoading ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                error != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
                    ) {
                        Text(text = error.localizedMessage.orEmpty())
                        Button(
                            onClick = { onAction(HomeRowsAction.OnRetryClick) },
                            modifier = Modifier.focusRequester(retry),
                        ) {
                            Text(text = stringResource(CoreR.string.retry))
                        }
                    }
                    LaunchedEffect(Unit) { retry.requestFocus() }
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
                        contentPadding = PaddingValues(vertical = MaterialTheme.spacings.large),
                        modifier = Modifier.widthIn(max = 720.dp).testTag("ce_home_rows_settings"),
                    ) {
                        items(state.rows, key = { it.key }) { entry ->
                            HomeRowItem(
                                entry = entry,
                                isFirst = entry == state.rows.first(),
                                isLast = entry == state.rows.last(),
                                onAction = onAction,
                                toggleModifier =
                                    if (entry == state.rows.first()) {
                                        Modifier.focusRequester(firstRow)
                                    } else {
                                        Modifier
                                    },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                    LaunchedEffect(Unit) { firstRow.requestFocus() }
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
    toggleModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
        modifier = modifier.fillMaxWidth().testTag("ce_home_row_${entry.key}"),
    ) {
        Surface(
            onClick = { onAction(HomeRowsAction.OnVisibleChange(entry.key, !entry.row.visible)) },
            shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.small),
            colors =
                ClickableSurfaceDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
            border = ClickableSurfaceDefaults.border(focusedBorder = ephyraFocusedBorder()),
            glow = ClickableSurfaceDefaults.glow(focusedGlow = ephyraFocusedGlow()),
            scale = ClickableSurfaceScale.None,
            modifier = toggleModifier.weight(1f),
        ) {
            Row(
                modifier = Modifier.padding(MaterialTheme.spacings.default),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = entry.title.asString(),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = entry.row.visible, onCheckedChange = null)
            }
        }
        MoveButton(
            icon = CoreR.drawable.ic_chevron_up,
            description = FilmR.string.home_rows_move_up,
            active = !isFirst,
            onClick = { onAction(HomeRowsAction.OnMoveUp(entry.key)) },
        )
        MoveButton(
            icon = CoreR.drawable.ic_chevron_down,
            description = FilmR.string.home_rows_move_down,
            active = !isLast,
            onClick = { onAction(HomeRowsAction.OnMoveDown(entry.key)) },
        )
    }
}

/** Never disabled, so it keeps focus when its row reaches the end. It's dimmed there instead. */
@Composable
private fun MoveButton(icon: Int, description: Int, active: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.alpha(if (active) 1f else 0.4f)) {
        Icon(painter = painterResource(icon), contentDescription = stringResource(description))
    }
}

@Preview(device = "id:tv_1080p")
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
                                HomeRowConfig(HomeRowType.NEXT_UP, visible = false),
                                UiText.StringResource(FilmR.string.next_up),
                            ),
                        ),
                    isLoading = false,
                ),
            onAction = {},
        )
    }
}
