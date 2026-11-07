package dev.jdtech.jellyfin.presentation.settings

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.jdtech.jellyfin.ce.ui.TvSafeArea
import dev.jdtech.jellyfin.ce.ui.focus.RestoreFocus
import dev.jdtech.jellyfin.ce.ui.focus.focusPosition
import dev.jdtech.jellyfin.ce.ui.focus.rememberFocusPosition
import dev.jdtech.jellyfin.presentation.settings.components.SettingsGroupCard
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.settings.R as SettingsR
import dev.jdtech.jellyfin.settings.presentation.models.PreferenceCategory
import dev.jdtech.jellyfin.settings.presentation.models.PreferenceGroup
import dev.jdtech.jellyfin.settings.presentation.settings.SettingsAction
import dev.jdtech.jellyfin.settings.presentation.settings.SettingsEvent
import dev.jdtech.jellyfin.settings.presentation.settings.SettingsState
import dev.jdtech.jellyfin.settings.presentation.settings.SettingsViewModel
import dev.jdtech.jellyfin.utils.ObserveAsEvents
import dev.jdtech.jellyfin.utils.restart
import timber.log.Timber

@Composable
fun SettingsScreen(
    navigateToSubSettings: (indexes: List<Int>) -> Unit,
    navigateToServers: () -> Unit,
    navigateToUsers: () -> Unit,
    navigateToHomeRows: () -> Unit,
    navigateToAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current

    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(true) { viewModel.loadPreferences(emptyList()) }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is SettingsEvent.NavigateToSettings -> navigateToSubSettings(event.indexes)
            is SettingsEvent.NavigateToSettingsFileEdit -> {}
            is SettingsEvent.NavigateToUsers -> navigateToUsers()
            is SettingsEvent.NavigateToServers -> navigateToServers()
            is SettingsEvent.NavigateToAbout -> navigateToAbout()
            is SettingsEvent.NavigateToHomeRows -> navigateToHomeRows()
            // TV has no custom subtitle-style screen; route through the standard sub-settings
            // list, which renders the subtitle_style category's nestedPreferenceGroups. The
            // subtitle_style category is nested under the player category, so the full path
            // is needed for SettingsViewModel.loadPreferences to walk down to it.
            is SettingsEvent.NavigateToSubtitleStyle ->
                navigateToSubSettings(
                    listOf(
                        SettingsR.string.settings_category_player,
                        SettingsR.string.subtitle_style,
                    )
                )
            is SettingsEvent.UpdateTheme -> Unit
            is SettingsEvent.CacheCleared ->
                Toast.makeText(context, SettingsR.string.settings_cache_cleared, Toast.LENGTH_SHORT)
                    .show()
            is SettingsEvent.LaunchIntent -> {
                try {
                    context.startActivity(event.intent)
                } catch (e: Exception) {
                    Timber.e(e)
                }
            }
            is SettingsEvent.RestartActivity -> {
                try {
                    (context as Activity).restart()
                } catch (e: Exception) {
                    Timber.e(e)
                }
            }
        }
    }

    SettingsScreenLayout(
        state = state,
        onAction = { action ->
            when (action) {
                is SettingsAction.OnUpdate -> {
                    viewModel.onAction(action)
                    viewModel.loadPreferences(emptyList())
                }
                else -> Unit
            }
        },
    )
}

@Composable
private fun SettingsScreenLayout(state: SettingsState, onAction: (SettingsAction) -> Unit) {
    // CE (#211): Back from a category (About, Interface, ...) returns focus to that category.
    val focusPosition = rememberFocusPosition()

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
        contentPadding =
            PaddingValues(
                horizontal = TvSafeArea.horizontal,
                vertical = TvSafeArea.vertical,
            ),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(this.maxLineSpan) }) {
            Text(
                text = stringResource(id = SettingsR.string.title_settings),
                style = MaterialTheme.typography.displayMedium,
            )
        }
        itemsIndexed(state.preferenceGroups) { index, group ->
            SettingsGroupCard(
                group = group,
                onAction = onAction,
                modifier = Modifier.focusPosition(focusPosition, row = 0, column = index),
            )
        }
    }
    RestoreFocus(position = focusPosition, ready = state.preferenceGroups.isNotEmpty())
}

@Preview(device = "id:tv_1080p")
@Composable
private fun SettingsScreenLayoutPreview() {
    FindroidTheme {
        SettingsScreenLayout(
            state =
                SettingsState(
                    preferenceGroups =
                        listOf(
                            PreferenceGroup(
                                nameStringResource = null,
                                preferences =
                                    listOf(
                                        PreferenceCategory(
                                            nameStringResource =
                                                SettingsR.string.settings_category_language,
                                            iconDrawableId = SettingsR.drawable.ic_languages,
                                        )
                                    ),
                            ),
                            PreferenceGroup(
                                nameStringResource = null,
                                preferences =
                                    listOf(
                                        PreferenceCategory(
                                            nameStringResource =
                                                SettingsR.string.settings_category_appearance,
                                            iconDrawableId = SettingsR.drawable.ic_palette,
                                        )
                                    ),
                            ),
                        )
                ),
            onAction = {},
        )
    }
}
