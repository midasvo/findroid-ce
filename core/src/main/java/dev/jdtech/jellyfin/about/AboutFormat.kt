package dev.jdtech.jellyfin.about

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.jdtech.jellyfin.settings.R as SettingsR
import java.util.Locale

/* CE (#211): the About values as people read them, shared by the phone and TV About screens. */

@Composable
fun channelText(channel: ReleaseChannel): String =
    stringResource(
        when (channel) {
            ReleaseChannel.STABLE -> SettingsR.string.about_channel_stable
            ReleaseChannel.BETA -> SettingsR.string.about_channel_beta
            ReleaseChannel.DEBUG -> SettingsR.string.about_channel_debug
        }
    )

@Composable
fun installSourceText(source: InstallSource): String =
    when (source) {
        InstallSource.Sideloaded -> stringResource(SettingsR.string.about_install_sideloaded)
        else -> BugReportLink.installSourceName(source)
    }

@Composable
fun buildText(info: AboutInfo, isTv: Boolean): String =
    stringResource(
        if (isTv) SettingsR.string.about_build_tv else SettingsR.string.about_build_phone,
        if (info.is64Bit) 64 else 32,
        info.abi,
    )

fun resolutionText(screen: ScreenSummary): String =
    "${screen.widthPx}×${screen.heightPx}, ${hzText(screen.refreshRateHz)}"

/** `60 Hz`, `59.94 Hz`. */
fun hzText(hz: Float): String =
    "%.2f".format(Locale.ROOT, hz).trimEnd('0').trimEnd('.') + " Hz"

@Composable
fun hdrText(hdrTypes: List<String>): String =
    if (hdrTypes.isEmpty()) stringResource(SettingsR.string.about_none)
    else hdrTypes.joinToString(", ", transform = ::hdrName)

private fun hdrName(type: String): String =
    when (type) {
        "DOLBY_VISION" -> "Dolby Vision"
        "HDR10_PLUS" -> "HDR10+"
        else -> type
    }

/** ffmpeg's codec names as people know them. */
fun codecName(codec: String): String =
    when (codec) {
        "h263" -> "H.263"
        "h264" -> "H.264"
        "hevc" -> "HEVC"
        "mpeg2video" -> "MPEG-2"
        "mpeg4" -> "MPEG-4"
        "vc1" -> "VC-1"
        "eac3" -> "E-AC-3"
        "ac3" -> "AC-3"
        "truehd" -> "TrueHD"
        "dts" -> "DTS"
        "opus" -> "Opus"
        "vorbis" -> "Vorbis"
        "flac" -> "FLAC"
        else -> codec.uppercase()
    }

/** `H.264, HEVC, AV1, Dolby Vision 5/8`. */
@Composable
fun decodersText(info: AboutInfo): String =
    buildList {
            addAll(info.videoDecoders.map(::codecName))
            if (info.dolbyVisionProfiles.isNotEmpty()) {
                add("Dolby Vision ${info.dolbyVisionProfiles.joinToString("/")}")
            }
        }
        .joinToString(", ")
        .ifEmpty { stringResource(SettingsR.string.about_none) }
