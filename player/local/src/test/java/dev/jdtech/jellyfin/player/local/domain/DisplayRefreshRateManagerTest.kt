package dev.jdtech.jellyfin.player.local.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DisplayRefreshRateManagerTest {

    private val mode1080p60 = DisplayModeCandidate(modeId = 1, physicalWidth = 1920, physicalHeight = 1080, refreshRate = 60.0f)
    private val mode1080p59 = DisplayModeCandidate(modeId = 2, physicalWidth = 1920, physicalHeight = 1080, refreshRate = 59.94006f)
    private val mode1080p50 = DisplayModeCandidate(modeId = 3, physicalWidth = 1920, physicalHeight = 1080, refreshRate = 50.0f)
    private val mode1080p30 = DisplayModeCandidate(modeId = 4, physicalWidth = 1920, physicalHeight = 1080, refreshRate = 30.0f)
    private val mode1080p25 = DisplayModeCandidate(modeId = 5, physicalWidth = 1920, physicalHeight = 1080, refreshRate = 25.0f)
    private val mode1080p24 = DisplayModeCandidate(modeId = 6, physicalWidth = 1920, physicalHeight = 1080, refreshRate = 24.0f)
    private val mode1080p23 = DisplayModeCandidate(modeId = 7, physicalWidth = 1920, physicalHeight = 1080, refreshRate = 23.976025f)

    private val mode4k60 = DisplayModeCandidate(modeId = 10, physicalWidth = 3840, physicalHeight = 2160, refreshRate = 60.0f)
    private val mode4k24 = DisplayModeCandidate(modeId = 11, physicalWidth = 3840, physicalHeight = 2160, refreshRate = 24.0f)

    private val allModes = listOf(
        mode1080p60,
        mode1080p59,
        mode1080p50,
        mode1080p30,
        mode1080p25,
        mode1080p24,
        mode1080p23,
    )

    @Test
    fun `23_976 fps film matches 23_976 Hz if available`() {
        val result = DisplayRefreshRateManager.findBestDisplayMode(
            modes = allModes,
            currentMode = mode1080p60,
            targetFrameRate = 23.976f,
        )
        assertEquals(mode1080p23.modeId, result?.modeId)
    }

    @Test
    fun `23_976 fps film switches to 24 Hz if 23_976 is not available`() {
        val modes = listOf(mode1080p60, mode1080p50, mode1080p24)
        val result = DisplayRefreshRateManager.findBestDisplayMode(
            modes = modes,
            currentMode = mode1080p60,
            targetFrameRate = 23.976f,
        )
        assertEquals(mode1080p24.modeId, result?.modeId)
    }

    @Test
    fun `24 fps film matches 24 Hz`() {
        val result = DisplayRefreshRateManager.findBestDisplayMode(
            modes = allModes,
            currentMode = mode1080p60,
            targetFrameRate = 24.0f,
        )
        assertEquals(mode1080p24.modeId, result?.modeId)
    }

    @Test
    fun `50 fps material switches to 50 Hz`() {
        val result = DisplayRefreshRateManager.findBestDisplayMode(
            modes = allModes,
            currentMode = mode1080p60,
            targetFrameRate = 50.0f,
        )
        assertEquals(mode1080p50.modeId, result?.modeId)
    }

    @Test
    fun `25 fps material matches 50 Hz on TV lacking 25 Hz`() {
        val modes = listOf(mode1080p60, mode1080p50, mode1080p24)
        val result = DisplayRefreshRateManager.findBestDisplayMode(
            modes = modes,
            currentMode = mode1080p60,
            targetFrameRate = 25.0f,
        )
        assertEquals(mode1080p50.modeId, result?.modeId)
    }

    @Test
    fun `25 fps material matches 25 Hz when available`() {
        val result = DisplayRefreshRateManager.findBestDisplayMode(
            modes = allModes,
            currentMode = mode1080p60,
            targetFrameRate = 25.0f,
        )
        assertEquals(mode1080p25.modeId, result?.modeId)
    }

    @Test
    fun `59_94 fps matches 59_94 Hz when available`() {
        val result = DisplayRefreshRateManager.findBestDisplayMode(
            modes = allModes,
            currentMode = mode1080p60,
            targetFrameRate = 59.94f,
        )
        assertEquals(mode1080p59.modeId, result?.modeId)
    }

    @Test
    fun `60 fps matches 60 Hz`() {
        val result = DisplayRefreshRateManager.findBestDisplayMode(
            modes = allModes,
            currentMode = mode1080p60,
            targetFrameRate = 60.0f,
        )
        assertEquals(mode1080p60.modeId, result?.modeId)
    }

    @Test
    fun `preserves current resolution and does not switch resolution`() {
        val mixedModes = listOf(mode1080p60, mode1080p24, mode4k60, mode4k24)
        val result = DisplayRefreshRateManager.findBestDisplayMode(
            modes = mixedModes,
            currentMode = mode4k60,
            targetFrameRate = 23.976f,
        )
        assertEquals(mode4k24.modeId, result?.modeId)
    }

    @Test
    fun `returns null when no compatible mode exists`() {
        val modes = listOf(mode1080p60)
        val result = DisplayRefreshRateManager.findBestDisplayMode(
            modes = modes,
            currentMode = mode1080p60,
            targetFrameRate = 23.976f,
        )
        assertNull(result)
    }

    @Test
    fun `returns null for invalid frame rates or empty modes`() {
        assertNull(DisplayRefreshRateManager.findBestDisplayMode(allModes, mode1080p60, 0f))
        assertNull(DisplayRefreshRateManager.findBestDisplayMode(allModes, mode1080p60, -24f))
        assertNull(DisplayRefreshRateManager.findBestDisplayMode(allModes, mode1080p60, Float.NaN))
        assertNull(DisplayRefreshRateManager.findBestDisplayMode(emptyList(), mode1080p60, 24f))
    }
}
