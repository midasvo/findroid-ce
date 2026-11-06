package dev.jdtech.jellyfin.ce.presentation.settings.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.jdtech.jellyfin.about.AboutInfo
import dev.jdtech.jellyfin.about.codecName
import dev.jdtech.jellyfin.about.hdrText
import dev.jdtech.jellyfin.about.hzText
import dev.jdtech.jellyfin.ce.ui.TvSafeArea
import dev.jdtech.jellyfin.player.DolbyVisionLimit
import dev.jdtech.jellyfin.player.ProbedCodecs
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.settings.R as SettingsR
import dev.jdtech.jellyfin.viewmodels.AboutAction
import dev.jdtech.jellyfin.viewmodels.AboutViewModel

/**
 * CE (#211): what this device can show and decode, which is what the device profile tells the
 * server. It's the first thing to look at when something transcodes that shouldn't. Each row is
 * focusable, so the remote scrolls the list.
 */
@Composable
fun DeviceProfileScreen(viewModel: AboutViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.onAction(AboutAction.OnLoadInfo) }

    DeviceProfileScreenLayout(info = state.info, codecs = state.codecs)
}

@Composable
private fun DeviceProfileScreenLayout(info: AboutInfo?, codecs: ProbedCodecs?) {
    val firstItem = remember { FocusRequester() }

    Column(
        modifier =
            Modifier.fillMaxSize()
                .padding(start = TvSafeArea.horizontal, top = TvSafeArea.vertical, end = TvSafeArea.horizontal)
    ) {
        Text(
            text = stringResource(SettingsR.string.about_device_profile),
            style = MaterialTheme.typography.displaySmall,
        )
        Text(
            text = stringResource(SettingsR.string.about_device_profile_summary),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (info == null || codecs == null) {
            Box(modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            return@Column
        }

        val none = stringResource(SettingsR.string.about_none)
        val screenRows =
            listOf(
                stringResource(SettingsR.string.about_resolution) to
                    "${info.screen.widthPx}×${info.screen.heightPx}",
                stringResource(SettingsR.string.about_refresh_rates) to
                    info.screen.supportedRefreshRatesHz.joinToString(", ", transform = ::hzText),
                stringResource(SettingsR.string.about_hdr) to hdrText(info.screen.hdrTypes),
            )
        val videoRows =
            codecs.videoCodecProfiles.toSortedMap().map { (codec, profiles) ->
                codecName(codec) to profiles.sorted().joinToString(", ").ifEmpty { none }
            }
        val dolbyVisionRows =
            codecs.dolbyVisionProfiles.toSortedMap().map { (profile, limit) ->
                stringResource(SettingsR.string.about_dolby_vision_profile, profile) to
                    limitText(limit)
            }
        val audioRows =
            listOf(
                // The section title says what it is.
                "" to
                    codecs.audioCodecs.sorted().joinToString(", ", transform = ::codecName)
                        .ifEmpty { none }
            )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
            contentPadding =
                PaddingValues(top = MaterialTheme.spacings.default, bottom = TvSafeArea.vertical),
        ) {
            section(
                title = SettingsR.string.about_screen,
                rows = screenRows,
                firstItem = firstItem,
            )
            section(title = SettingsR.string.about_video_decoders, rows = videoRows)
            if (dolbyVisionRows.isNotEmpty()) {
                section(title = SettingsR.string.about_dolby_vision, rows = dolbyVisionRows)
            }
            section(title = SettingsR.string.about_audio_decoders, rows = audioRows)
        }
        LaunchedEffect(Unit) { firstItem.requestFocus() }
    }
}

private fun LazyListScope.section(
    title: Int,
    rows: List<Pair<String, String>>,
    firstItem: FocusRequester? = null,
) {
    item(key = "title-$title") {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = MaterialTheme.spacings.medium),
        )
    }
    items(rows, key = { "$title-${it.first}" }) { (label, value) ->
        FocusableCard(
            modifier =
                Modifier.fillMaxWidth()
                    .then(
                        if (firstItem != null && label == rows.first().first) {
                            Modifier.focusRequester(firstItem)
                        } else Modifier
                    )
        ) {
            InfoRow(label = label, value = value, labelWidth = MaterialTheme.spacings.large * 7)
        }
    }
}

@Composable
private fun limitText(limit: DolbyVisionLimit): String =
    stringResource(
        SettingsR.string.about_dolby_vision_limit,
        limit.maxWidth,
        limit.maxHeight,
        limit.maxFramerate,
    )

@Preview(device = "id:tv_1080p")
@Composable
private fun DeviceProfileScreenLayoutPreview() {
    FindroidTheme {
        DeviceProfileScreenLayout(
            info = previewInfo,
            codecs =
                ProbedCodecs(
                    videoCodecProfiles =
                        mapOf(
                            "h264" to setOf("high", "main", "baseline"),
                            "hevc" to setOf("main", "main 10"),
                        ),
                    audioCodecs = setOf("aac", "ac3", "eac3", "opus"),
                    dolbyVisionProfiles = mapOf(5 to DolbyVisionLimit(3840, 2160, 60)),
                ),
        )
    }
}
