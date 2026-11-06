package dev.jdtech.jellyfin.about

/**
 * CE (#211): what the About screen tells you about this install, the server and the device. The
 * same facts go into a bug report, so they're kept as data and formatted by the screen.
 */
data class AboutInfo(
    val versionName: String,
    val versionCode: Long,
    val channel: ReleaseChannel,
    val installSource: InstallSource,
    /** The ABI the app runs as, e.g. `armeabi-v7a` on a 32-bit box. */
    val abi: String,
    val is64Bit: Boolean,
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val screen: ScreenSummary,
    /** Video decoders, as ffmpeg codec names (hevc, av1, ...), sorted. */
    val videoDecoders: List<String>,
    /** Dolby Vision profiles a decoder advertises, sorted. */
    val dolbyVisionProfiles: List<Int>,
) {
    val device: String
        get() =
            if (model.startsWith(manufacturer, ignoreCase = true)) model
            else "$manufacturer $model".trim()
}

/** The display mode the screen runs at, not the app's (often 1080p) window. */
data class ScreenSummary(
    val widthPx: Int,
    val heightPx: Int,
    val refreshRateHz: Float,
    /** Every refresh rate the display can switch to, sorted. */
    val supportedRefreshRatesHz: List<Float>,
    /** DOLBY_VISION, HDR10, HDR10_PLUS, HLG. */
    val hdrTypes: List<String>,
)

data class AboutServer(
    val name: String,
    val address: String?,
    val userName: String?,
    /** Null while loading, when offline, or when the server can't be reached. */
    val jellyfinVersion: String?,
)

enum class ReleaseChannel {
    STABLE,
    BETA,
    DEBUG;

    companion object {
        /** Debug and beta builds have their own application ID suffix (`.debug`, `.staging`). */
        fun fromPackageName(packageName: String): ReleaseChannel =
            when {
                packageName.endsWith(".debug") -> DEBUG
                packageName.endsWith(".staging") -> BETA
                else -> STABLE
            }
    }
}

sealed interface InstallSource {
    data object GooglePlay : InstallSource

    data object FDroid : InstallSource

    data object Obtainium : InstallSource

    /** adb, a file manager or the system package installer. */
    data object Sideloaded : InstallSource

    data class Other(val packageName: String) : InstallSource

    companion object {
        fun fromInstaller(installerPackageName: String?): InstallSource =
            when (installerPackageName) {
                null,
                "com.android.shell",
                "com.android.packageinstaller",
                "com.google.android.packageinstaller" -> Sideloaded
                "com.android.vending" -> GooglePlay
                "org.fdroid.fdroid",
                "org.fdroid.basic" -> FDroid
                "dev.imranr.obtainium",
                "dev.imranr.obtainium.fdroid" -> Obtainium
                else -> Other(installerPackageName)
            }
    }
}
