package dev.jdtech.jellyfin.about

import android.net.Uri

/**
 * CE (#211): a link to a new bug report on GitHub with the device info filled in. The issue form
 * (`.github/ISSUE_TEMPLATE/bug-report.yml`) takes a query parameter per field id.
 *
 * It's in English because the issue is, and it leaves out the server address and the user name: the
 * issue is public.
 */
object BugReportLink {
    private const val NEW_ISSUE = "https://github.com/midasvo/ephyra/issues/new"

    fun build(info: AboutInfo, jellyfinVersion: String?, isTv: Boolean): String {
        val variant = if (isTv) "TV" else "phone"
        val context = buildList {
            add("Installed from: ${installSourceName(info.installSource)}")
            add("Build: $variant, ${if (info.is64Bit) 64 else 32}-bit (${info.abi})")
            add("Screen: ${screenText(info.screen)}")
            add("Video decoders: ${decodersText(info)}")
        }
        return Uri.parse(NEW_ISSUE)
            .buildUpon()
            .appendQueryParameter("template", "bug-report.yml")
            .appendQueryParameter("title", if (isTv) "TV: " else "")
            .appendQueryParameter("device", info.device)
            .appendQueryParameter("android", info.androidVersion)
            .appendQueryParameter(
                "app",
                "${info.versionName} (${info.versionCode}, ${info.channel.name.lowercase()})",
            )
            .appendQueryParameter("jellyfin", jellyfinVersion.orEmpty())
            .appendQueryParameter("context", context.joinToString("\n"))
            .build()
            .toString()
    }

    fun installSourceName(source: InstallSource): String =
        when (source) {
            InstallSource.GooglePlay -> "Google Play"
            InstallSource.FDroid -> "F-Droid"
            InstallSource.Obtainium -> "Obtainium"
            InstallSource.Sideloaded -> "sideloaded"
            is InstallSource.Other -> source.packageName
        }

    /** `3840×2160, 60 Hz, DOLBY_VISION/HDR10`. */
    fun screenText(screen: ScreenSummary): String = buildList {
            add("${screen.widthPx}×${screen.heightPx}")
            add("${screen.refreshRateHz.toInt()} Hz")
            if (screen.hdrTypes.isNotEmpty()) add(screen.hdrTypes.joinToString("/"))
        }
        .joinToString(", ")

    private fun decodersText(info: AboutInfo): String = buildList {
            addAll(info.videoDecoders)
            if (info.dolbyVisionProfiles.isNotEmpty()) {
                add("DV ${info.dolbyVisionProfiles.joinToString("/")}")
            }
        }
        .joinToString(", ")
}
