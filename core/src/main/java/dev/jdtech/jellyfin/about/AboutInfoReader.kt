package dev.jdtech.jellyfin.about

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Process
import android.view.Display
import dev.jdtech.jellyfin.player.DeviceProfileBuilder
import timber.log.Timber

/** CE (#211): reads [AboutInfo] from the package manager, the display and the codec probe. */
object AboutInfoReader {

    fun read(context: Context, deviceProfileBuilder: DeviceProfileBuilder): AboutInfo {
        val packageManager = context.packageManager
        val packageInfo = packageManager.getPackageInfo(context.packageName, 0)
        val is64Bit = Process.is64Bit()
        val probed = deviceProfileBuilder.probedCodecsSnapshot()
        return AboutInfo(
            versionName = packageInfo.versionName.orEmpty(),
            versionCode = packageInfo.longVersionCode,
            channel = ReleaseChannel.fromPackageName(context.packageName),
            installSource = InstallSource.fromInstaller(installerPackageName(context)),
            abi =
                (if (is64Bit) Build.SUPPORTED_64_BIT_ABIS else Build.SUPPORTED_32_BIT_ABIS)
                    .firstOrNull()
                    .orEmpty(),
            is64Bit = is64Bit,
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
            androidVersion = Build.VERSION.RELEASE.orEmpty(),
            screen = screen(context),
            videoDecoders = probed.videoCodecProfiles.keys.sorted(),
            dolbyVisionProfiles = probed.dolbyVisionProfiles.keys.sorted(),
        )
    }

    private fun installerPackageName(context: Context): String? =
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(context.packageName)
            }
        } catch (e: Exception) {
            Timber.w(e, "Could not read the installer package")
            null
        }

    /**
     * The default display through [DisplayManager], which works from the application context
     * (`Context.display` doesn't). The mode gives the panel's resolution: a 4K box often draws the
     * app at 1080p.
     */
    private fun screen(context: Context): ScreenSummary {
        val display =
            context
                .getSystemService(DisplayManager::class.java)
                ?.getDisplay(Display.DEFAULT_DISPLAY)
                ?: return ScreenSummary(0, 0, 0f, emptyList(), emptyList())
        val mode = display.mode
        val hdrTypes =
            try {
                @Suppress("DEPRECATION")
                display.hdrCapabilities?.supportedHdrTypes?.map(::hdrTypeName).orEmpty()
            } catch (_: Exception) {
                emptyList()
            }
        return ScreenSummary(
            widthPx = mode.physicalWidth,
            heightPx = mode.physicalHeight,
            refreshRateHz = mode.refreshRate,
            supportedRefreshRatesHz =
                display.supportedModes.map { it.refreshRate }.distinct().sorted(),
            hdrTypes = hdrTypes.distinct(),
        )
    }

    private fun hdrTypeName(type: Int): String =
        when (type) {
            Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "DOLBY_VISION"
            Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"
            Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> "HDR10_PLUS"
            Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"
            else -> "UNKNOWN_$type"
        }
}
