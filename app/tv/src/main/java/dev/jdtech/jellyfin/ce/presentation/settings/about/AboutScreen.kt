package dev.jdtech.jellyfin.ce.presentation.settings.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import dev.jdtech.jellyfin.about.AboutInfo
import dev.jdtech.jellyfin.about.AboutServer
import dev.jdtech.jellyfin.about.BugReportLink
import dev.jdtech.jellyfin.about.InstallSource
import dev.jdtech.jellyfin.about.ReleaseChannel
import dev.jdtech.jellyfin.about.ScreenSummary
import dev.jdtech.jellyfin.ce.ui.QrCode
import dev.jdtech.jellyfin.ce.ui.TvSafeArea
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.settings.R as SettingsR
import dev.jdtech.jellyfin.viewmodels.AboutAction
import dev.jdtech.jellyfin.viewmodels.AboutState
import dev.jdtech.jellyfin.viewmodels.AboutViewModel

/**
 * CE (#211): Settings → About on TV. One screen, no scrolling: the version and a QR code for a bug
 * report on the left, what you'd put in that report on the right, and buttons for the longer lists
 * (licenses, device profile). There's no link to open: most TVs have no browser, so the QR code is
 * the way to GitHub.
 */
@Composable
fun AboutScreen(
    navigateToLicenses: () -> Unit,
    navigateToDeviceProfile: () -> Unit,
    viewModel: AboutViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.onAction(AboutAction.OnLoadInfo) }

    AboutScreenLayout(
        state = state,
        onLicensesClick = navigateToLicenses,
        onDeviceProfileClick = navigateToDeviceProfile,
    )
}

@Composable
private fun AboutScreenLayout(
    state: AboutState,
    onLicensesClick: () -> Unit,
    onDeviceProfileClick: () -> Unit,
) {
    val firstButton = remember { FocusRequester() }

    Row(
        modifier = Modifier.fillMaxSize().padding(TvSafeArea.paddingValues),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.large),
    ) {
        Column(modifier = Modifier.width(260.dp).fillMaxHeight()) {
            Image(
                painter = painterResource(CoreR.drawable.ic_banner),
                contentDescription = null,
                modifier = Modifier.width(220.dp),
            )
            state.info?.let { info ->
                Text(
                    text = info.versionName,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(top = MaterialTheme.spacings.small),
                )
                Spacer(Modifier.weight(1f))
                QrCode(
                    text =
                        BugReportLink.build(
                            info = info,
                            jellyfinVersion = state.server?.jellyfinVersion,
                            isTv = true,
                        ),
                    size = 184.dp,
                )
                Text(
                    text = stringResource(SettingsR.string.about_report_problem),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = MaterialTheme.spacings.small),
                )
                Text(
                    text = stringResource(SettingsR.string.about_report_problem_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Text(
                text = stringResource(SettingsR.string.about),
                style = MaterialTheme.typography.displaySmall,
            )
            Spacer(Modifier.height(MaterialTheme.spacings.default))
            state.info?.let { info ->
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.large)) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
                    ) {
                        AppSection(info)
                        state.server?.let { ServerSection(it) }
                    }
                    DeviceSection(info = info, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium)) {
                Button(
                    onClick = onLicensesClick,
                    modifier = Modifier.focusRequester(firstButton),
                ) {
                    Text(stringResource(SettingsR.string.about_licenses))
                }
                OutlinedButton(onClick = onDeviceProfileClick) {
                    Text(stringResource(SettingsR.string.about_device_profile))
                }
            }
        }
    }

    LaunchedEffect(Unit) { firstButton.requestFocus() }
}

@Composable
private fun AppSection(info: AboutInfo) {
    InfoSection(title = stringResource(SettingsR.string.about_section_app)) {
        InfoRow(
            label = stringResource(SettingsR.string.about_version),
            value = "${info.versionName} (${info.versionCode})",
        )
        InfoRow(
            label = stringResource(SettingsR.string.about_channel),
            value = channelText(info.channel),
        )
        InfoRow(
            label = stringResource(SettingsR.string.about_installed_from),
            value = installSourceText(info.installSource),
        )
        InfoRow(label = stringResource(SettingsR.string.about_build), value = buildText(info))
    }
}

@Composable
private fun ServerSection(server: AboutServer) {
    val unknown = stringResource(SettingsR.string.about_unknown)
    InfoSection(title = stringResource(SettingsR.string.about_section_server)) {
        InfoRow(label = stringResource(SettingsR.string.about_server_name), value = server.name)
        InfoRow(
            label = stringResource(SettingsR.string.about_server_address),
            value = server.address ?: unknown,
        )
        InfoRow(
            label = stringResource(SettingsR.string.about_signed_in_as),
            value = server.userName ?: unknown,
        )
        InfoRow(
            label = stringResource(SettingsR.string.about_jellyfin_version),
            value = server.jellyfinVersion ?: unknown,
        )
    }
}

@Composable
private fun DeviceSection(info: AboutInfo, modifier: Modifier = Modifier) {
    InfoSection(title = stringResource(SettingsR.string.about_section_device), modifier = modifier) {
        InfoRow(label = stringResource(SettingsR.string.about_device_model), value = info.device)
        InfoRow(
            label = stringResource(SettingsR.string.about_android_version),
            value = info.androidVersion,
        )
        InfoRow(
            label = stringResource(SettingsR.string.about_screen),
            value = resolutionText(info.screen),
        )
        InfoRow(
            label = stringResource(SettingsR.string.about_hdr),
            value = hdrText(info.screen.hdrTypes),
        )
        InfoRow(
            label = stringResource(SettingsR.string.about_video_decoders),
            value = decodersText(info),
        )
    }
}

internal val previewInfo =
    AboutInfo(
        versionName = "2.3.0",
        versionCode = 2030010,
        channel = ReleaseChannel.STABLE,
        installSource = InstallSource.Obtainium,
        abi = "armeabi-v7a",
        is64Bit = false,
        manufacturer = "Xiaomi",
        model = "MIBOX4",
        androidVersion = "9",
        screen =
            ScreenSummary(
                widthPx = 3840,
                heightPx = 2160,
                refreshRateHz = 60f,
                supportedRefreshRatesHz = listOf(23.976f, 24f, 50f, 59.94f, 60f),
                hdrTypes = listOf("DOLBY_VISION", "HDR10", "HLG"),
            ),
        videoDecoders = listOf("av1", "h264", "hevc", "vp9"),
        dolbyVisionProfiles = listOf(5, 8),
    )

@Preview(device = "id:tv_1080p")
@Composable
private fun AboutScreenLayoutPreview() {
    FindroidTheme {
        AboutScreenLayout(
            state =
                AboutState(
                    info = previewInfo,
                    server =
                        AboutServer(
                            name = "Home",
                            address = "https://jellyfin.example.com",
                            userName = "demo",
                            jellyfinVersion = "10.11.2",
                        ),
                ),
            onLicensesClick = {},
            onDeviceProfileClick = {},
        )
    }
}
