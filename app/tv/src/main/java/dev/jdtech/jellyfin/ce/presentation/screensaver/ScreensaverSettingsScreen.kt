package dev.jdtech.jellyfin.ce.presentation.screensaver

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import dev.jdtech.jellyfin.ce.presentation.player.TvSelectionDialog
import dev.jdtech.jellyfin.ce.ui.tvSafeAreaPadding
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.settings.R as SettingsR
import dev.jdtech.jellyfin.settings.domain.AppPreferences

@Composable
fun ScreensaverSettingsScreen(
    appPreferences: AppPreferences,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { onBack() }

    val source by
        appPreferences
            .observe(appPreferences.tvScreensaverSource)
            .collectAsStateWithLifecycle(
                initialValue = appPreferences.getValue(appPreferences.tvScreensaverSource)
            )
    val intervalStr by
        appPreferences
            .observe(appPreferences.tvScreensaverInterval)
            .collectAsStateWithLifecycle(
                initialValue = appPreferences.getValue(appPreferences.tvScreensaverInterval)
            )
    val showClock by
        appPreferences
            .observe(appPreferences.tvScreensaverShowClock)
            .collectAsStateWithLifecycle(
                initialValue = appPreferences.getValue(appPreferences.tvScreensaverShowClock)
            )

    val sourceOptions =
        listOf(
            "random" to stringResource(SettingsR.string.pref_tv_screensaver_source_random),
            "recently_added" to
                stringResource(SettingsR.string.pref_tv_screensaver_source_recently_added),
        )

    val intervalOptions =
        listOf(
            "10" to stringResource(SettingsR.string.player_tv_seek_step_10),
            "15" to stringResource(SettingsR.string.player_tv_seek_step_15),
            "20" to stringResource(SettingsR.string.pref_tv_screensaver_interval_20s),
            "30" to stringResource(SettingsR.string.player_tv_seek_step_30),
            "60" to stringResource(SettingsR.string.player_tv_seek_step_60),
        )

    var showSourceDialog by remember { mutableStateOf(false) }
    var showIntervalDialog by remember { mutableStateOf(false) }

    val sourceCardFocus = remember { FocusRequester() }
    val intervalCardFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { sourceCardFocus.requestFocus() }

    val currentSourceName =
        sourceOptions.firstOrNull { it.first == source }?.second
            ?: stringResource(SettingsR.string.pref_tv_screensaver_source_random)

    val currentIntervalName =
        intervalOptions.firstOrNull { it.first == intervalStr }?.second
            ?: stringResource(SettingsR.string.pref_tv_screensaver_interval_20s)

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .tvSafeAreaPadding()
    ) {
        Text(
            text = stringResource(SettingsR.string.pref_tv_screensaver_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(MaterialTheme.spacings.large))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
            modifier = Modifier.fillMaxWidth(0.7f),
        ) {
            item {
                ScreensaverActionCard(
                    title = stringResource(SettingsR.string.pref_tv_screensaver_source),
                    value = currentSourceName,
                    modifier = Modifier.focusRequester(sourceCardFocus),
                    onClick = { showSourceDialog = true },
                )
            }

            item {
                ScreensaverActionCard(
                    title = stringResource(SettingsR.string.pref_tv_screensaver_interval),
                    value = currentIntervalName,
                    modifier = Modifier.focusRequester(intervalCardFocus),
                    onClick = { showIntervalDialog = true },
                )
            }

            item {
                ScreensaverSwitchCard(
                    title = stringResource(SettingsR.string.pref_tv_screensaver_show_clock),
                    summary = stringResource(SettingsR.string.pref_tv_screensaver_show_clock_summary),
                    checked = showClock,
                    onClick = {
                        appPreferences.setValue(appPreferences.tvScreensaverShowClock, !showClock)
                    },
                )
            }
        }
    }

    if (showSourceDialog) {
        val selectedIndex = sourceOptions.indexOfFirst { it.first == source }.coerceAtLeast(0)
        TvSelectionDialog(
            title = stringResource(SettingsR.string.pref_tv_screensaver_source),
            options = sourceOptions.map { it.second },
            selectedIndex = selectedIndex,
            onSelect = { index ->
                appPreferences.setValue(
                    appPreferences.tvScreensaverSource,
                    sourceOptions[index].first,
                )
                showSourceDialog = false
                sourceCardFocus.requestFocus()
            },
            onDismiss = {
                showSourceDialog = false
                sourceCardFocus.requestFocus()
            },
        )
    }

    if (showIntervalDialog) {
        val selectedIndex = intervalOptions.indexOfFirst { it.first == intervalStr }.coerceAtLeast(0)
        TvSelectionDialog(
            title = stringResource(SettingsR.string.pref_tv_screensaver_interval),
            options = intervalOptions.map { it.second },
            selectedIndex = selectedIndex,
            onSelect = { index ->
                appPreferences.setValue(
                    appPreferences.tvScreensaverInterval,
                    intervalOptions[index].first,
                )
                showIntervalDialog = false
                intervalCardFocus.requestFocus()
            },
            onDismiss = {
                showIntervalDialog = false
                intervalCardFocus.requestFocus()
            },
        )
    }
}

@Composable
private fun ScreensaverActionCard(
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
            ),
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder =
                    Border(BorderStroke(4.dp, Color.White), shape = RoundedCornerShape(10.dp))
            ),
        scale = ClickableSurfaceScale.None,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(MaterialTheme.spacings.default),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ScreensaverSwitchCard(
    title: String,
    summary: String,
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
            ),
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder =
                    Border(BorderStroke(4.dp, Color.White), shape = RoundedCornerShape(10.dp))
            ),
        scale = ClickableSurfaceScale.None,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(MaterialTheme.spacings.default),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = null,
            )
        }
    }
}
