package dev.jdtech.jellyfin.presentation.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.recalculateWindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jdtech.jellyfin.BuildConfig
import dev.jdtech.jellyfin.about.AboutInfo
import dev.jdtech.jellyfin.about.AboutServer
import dev.jdtech.jellyfin.about.BugReportLink
import dev.jdtech.jellyfin.about.InstallSource
import dev.jdtech.jellyfin.about.ReleaseChannel
import dev.jdtech.jellyfin.about.ScreenSummary
import dev.jdtech.jellyfin.about.buildText
import dev.jdtech.jellyfin.about.channelText
import dev.jdtech.jellyfin.about.decodersText
import dev.jdtech.jellyfin.about.hdrText
import dev.jdtech.jellyfin.about.installSourceText
import dev.jdtech.jellyfin.about.resolutionText
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.settings.R as SettingsR
import dev.jdtech.jellyfin.utils.ObserveAsEvents
import dev.jdtech.jellyfin.viewmodels.AboutAction
import dev.jdtech.jellyfin.viewmodels.AboutEvent
import dev.jdtech.jellyfin.viewmodels.AboutState
import dev.jdtech.jellyfin.viewmodels.AboutViewModel
import dev.jdtech.jellyfin.viewmodels.ExportTarget
import timber.log.Timber

private const val REPOSITORY_URL = "https://github.com/midasvo/ephyra"

@Composable
fun AboutScreen(
    navigateBack: () -> Unit,
    navigateToLicenses: () -> Unit,
    viewModel: AboutViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.onAction(AboutAction.OnLoadInfo) }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is AboutEvent.ShareDeviceProfile -> shareJson(context, event.json)
            is AboutEvent.CopyDeviceProfile -> {
                copyToClipboard(context, event.json)
                Toast.makeText(
                    context,
                    context.getString(SettingsR.string.export_device_profile_copied),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            is AboutEvent.ExportFailed -> {
                Timber.e(event.cause, "Failed to build device capability report")
                Toast.makeText(
                    context,
                    context.getString(SettingsR.string.export_device_profile_failed),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    AboutScreenLayout(
        state = state,
        navigateBack = navigateBack,
        onLicensesClick = navigateToLicenses,
        onShareDeviceProfile = {
            viewModel.onAction(
                AboutAction.OnExportDeviceProfile(
                    findroidVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    findroidBuildType = BuildConfig.BUILD_TYPE,
                    target = ExportTarget.Share,
                ),
            )
        },
        onCopyDeviceProfile = {
            viewModel.onAction(
                AboutAction.OnExportDeviceProfile(
                    findroidVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    findroidBuildType = BuildConfig.BUILD_TYPE,
                    target = ExportTarget.Clipboard,
                ),
            )
        },
    )
}

/**
 * CE (#211): the version up top, then what you'd put in a bug report (app, server, device) in the
 * same cards as the settings, then the links. The license list has its own screen.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AboutScreenLayout(
    state: AboutState,
    navigateBack: () -> Unit,
    onLicensesClick: () -> Unit,
    onShareDeviceProfile: () -> Unit,
    onCopyDeviceProfile: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    fun openUri(uri: String) {
        try {
            uriHandler.openUri(uri)
        } catch (e: IllegalArgumentException) {
            Toast.makeText(context, e.localizedMessage, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        modifier =
            Modifier.fillMaxSize()
                .recalculateWindowInsets()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(SettingsR.string.about)) },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
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
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 640.dp).fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        start = MaterialTheme.spacings.default,
                        top = innerPadding.calculateTopPadding(),
                        end = MaterialTheme.spacings.default,
                        bottom =
                            innerPadding.calculateBottomPadding() + MaterialTheme.spacings.default,
                    ),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
            ) {
                item { Header(info = state.info) }
                state.info?.let { info ->
                    item { AppGroup(info) }
                    state.server?.let { server -> item { ServerGroup(server) } }
                    item { DeviceGroup(info) }
                }
                item {
                    AboutCard {
                        state.info?.let { info ->
                            LinkItem(
                                icon = CoreR.drawable.ic_alert_circle,
                                title = stringResource(SettingsR.string.about_report_problem),
                                summary =
                                    stringResource(
                                        SettingsR.string.about_report_problem_summary_phone
                                    ),
                                onClick = {
                                    openUri(
                                        BugReportLink.build(
                                            info = info,
                                            jellyfinVersion = state.server?.jellyfinVersion,
                                            isTv = false,
                                        )
                                    )
                                },
                            )
                        }
                        LinkItem(
                            icon = CoreR.drawable.ic_github,
                            title = stringResource(SettingsR.string.about_source_code),
                            summary = REPOSITORY_URL.removePrefix("https://"),
                            onClick = { openUri(REPOSITORY_URL) },
                        )
                        LinkItem(
                            icon = CoreR.drawable.ic_list,
                            title = stringResource(SettingsR.string.about_licenses),
                            onClick = onLicensesClick,
                        )
                    }
                }
                item {
                    DeviceProfileCard(
                        isExporting = state.isExporting,
                        onShare = onShareDeviceProfile,
                        onCopy = onCopyDeviceProfile,
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(info: AboutInfo?) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = MaterialTheme.spacings.small),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Tap it. Then tap it six more times. Or hold it.
        SwimmingJellyfish()
        Text(
            text = stringResource(CoreR.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
        )
        info?.let {
            Text(
                text = it.versionName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(MaterialTheme.spacings.small))
        Text(
            text = stringResource(CoreR.string.app_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AppGroup(info: AboutInfo) {
    InfoGroup(title = stringResource(SettingsR.string.about_section_app)) {
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
        InfoRow(
            label = stringResource(SettingsR.string.about_build),
            value = buildText(info, isTv = false),
        )
    }
}

@Composable
private fun ServerGroup(server: AboutServer) {
    val unknown = stringResource(SettingsR.string.about_unknown)
    InfoGroup(title = stringResource(SettingsR.string.about_section_server)) {
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
private fun DeviceGroup(info: AboutInfo) {
    InfoGroup(title = stringResource(SettingsR.string.about_section_device)) {
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

/** A titled card, like the groups on the settings screen. */
@Composable
private fun InfoGroup(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title,
            modifier = Modifier.padding(start = MaterialTheme.spacings.medium),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(MaterialTheme.spacings.small))
        AboutCard {
            Column(modifier = Modifier.padding(vertical = MaterialTheme.spacings.small)) {
                content()
            }
        }
    }
}

@Composable
private fun AboutCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        content()
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(
                    horizontal = MaterialTheme.spacings.medium,
                    vertical = MaterialTheme.spacings.small,
                ),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.45f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.55f),
        )
    }
}

@Composable
private fun LinkItem(icon: Int, title: String, summary: String? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        leadingContent = { Icon(painter = painterResource(icon), contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}

@Composable
private fun DeviceProfileCard(isExporting: Boolean, onShare: () -> Unit, onCopy: () -> Unit) {
    AboutCard {
        Column(modifier = Modifier.padding(MaterialTheme.spacings.medium)) {
            Text(
                text = stringResource(SettingsR.string.export_device_profile),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(MaterialTheme.spacings.extraSmall))
            Text(
                text = stringResource(SettingsR.string.export_device_profile_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(MaterialTheme.spacings.small))
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small)) {
                FilledTonalButton(onClick = onShare, enabled = !isExporting) {
                    Text(stringResource(CoreR.string.share))
                }
                OutlinedButton(onClick = onCopy, enabled = !isExporting) {
                    Text(stringResource(SettingsR.string.export_device_profile_copy))
                }
            }
        }
    }
}

private fun shareJson(context: Context, json: String) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_SUBJECT, "Ephyra device profile")
        putExtra(Intent.EXTRA_TEXT, json)
    }
    try {
        context.startActivity(Intent.createChooser(sendIntent, null))
    } catch (e: Exception) {
        Timber.e(e, "Failed to launch share intent for device profile")
    }
}

private fun copyToClipboard(context: Context, json: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clip = ClipData.newPlainText("Ephyra device profile", json)
    clipboard?.setPrimaryClip(clip)
}

@Composable
@PreviewScreenSizes
private fun AboutScreenLayoutPreview() {
    FindroidTheme {
        AboutScreenLayout(
            state =
                AboutState(
                    info =
                        AboutInfo(
                            versionName = "2.3.0",
                            versionCode = 2030000,
                            channel = ReleaseChannel.STABLE,
                            installSource = InstallSource.GooglePlay,
                            abi = "arm64-v8a",
                            is64Bit = true,
                            manufacturer = "Google",
                            model = "Pixel 9",
                            androidVersion = "16",
                            screen =
                                ScreenSummary(
                                    widthPx = 1080,
                                    heightPx = 2424,
                                    refreshRateHz = 120f,
                                    supportedRefreshRatesHz = listOf(60f, 120f),
                                    hdrTypes = listOf("HDR10", "HLG"),
                                ),
                            videoDecoders = listOf("av1", "h264", "hevc", "vp9"),
                            dolbyVisionProfiles = emptyList(),
                        ),
                    server =
                        AboutServer(
                            name = "Home",
                            address = "https://jellyfin.example.com",
                            userName = "demo",
                            jellyfinVersion = "10.11.2",
                        ),
                ),
            navigateBack = {},
            onLicensesClick = {},
            onShareDeviceProfile = {},
            onCopyDeviceProfile = {},
        )
    }
}
