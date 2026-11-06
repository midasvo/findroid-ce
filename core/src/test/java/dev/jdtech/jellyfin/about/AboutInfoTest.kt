package dev.jdtech.jellyfin.about

import org.junit.Assert.assertEquals
import org.junit.Test

class AboutInfoTest {

    @Test
    fun `known installers map to their store`() {
        assertEquals(InstallSource.GooglePlay, InstallSource.fromInstaller("com.android.vending"))
        assertEquals(InstallSource.FDroid, InstallSource.fromInstaller("org.fdroid.fdroid"))
        assertEquals(InstallSource.FDroid, InstallSource.fromInstaller("org.fdroid.basic"))
        assertEquals(InstallSource.Obtainium, InstallSource.fromInstaller("dev.imranr.obtainium"))
    }

    @Test
    fun `adb and the package installer count as sideloaded`() {
        assertEquals(InstallSource.Sideloaded, InstallSource.fromInstaller(null))
        assertEquals(InstallSource.Sideloaded, InstallSource.fromInstaller("com.android.shell"))
        assertEquals(
            InstallSource.Sideloaded,
            InstallSource.fromInstaller("com.google.android.packageinstaller"),
        )
    }

    @Test
    fun `an unknown installer keeps its package name`() {
        assertEquals(
            InstallSource.Other("com.example.store"),
            InstallSource.fromInstaller("com.example.store"),
        )
    }

    @Test
    fun `the channel follows the application ID suffix`() {
        assertEquals(ReleaseChannel.STABLE, ReleaseChannel.fromPackageName("nl.midasvo.findroid.ce"))
        assertEquals(
            ReleaseChannel.BETA,
            ReleaseChannel.fromPackageName("nl.midasvo.findroid.ce.staging"),
        )
        assertEquals(
            ReleaseChannel.DEBUG,
            ReleaseChannel.fromPackageName("nl.midasvo.findroid.ce.debug"),
        )
    }

    @Test
    fun `the device name doesn't repeat the manufacturer`() {
        fun info(manufacturer: String, model: String) =
            AboutInfo(
                versionName = "2.0.0",
                versionCode = 1,
                channel = ReleaseChannel.STABLE,
                installSource = InstallSource.Sideloaded,
                abi = "arm64-v8a",
                is64Bit = true,
                manufacturer = manufacturer,
                model = model,
                androidVersion = "14",
                screen = ScreenSummary(1920, 1080, 60f, listOf(60f), emptyList()),
                videoDecoders = emptyList(),
                dolbyVisionProfiles = emptyList(),
            )
        assertEquals("Xiaomi MIBOX4", info("Xiaomi", "MIBOX4").device)
        assertEquals("Google Pixel 9", info("Google", "Google Pixel 9").device)
    }
}
