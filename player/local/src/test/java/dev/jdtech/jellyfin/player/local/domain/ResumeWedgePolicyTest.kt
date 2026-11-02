// Adapted from Moonfin (https://github.com/Moonfin-Client/Moonfin-Core) at 9352e95d. See
// ResumeWedgePolicy.kt. Moonfin is licensed under GPL-2.0-or-later.
package dev.jdtech.jellyfin.player.local.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumeWedgePolicyTest {

    private fun decide(
        stillBuffering: Boolean = true,
        playWhenReady: Boolean = true,
        bufferedAheadMs: Long = 18_000L,
        isLiveSource: Boolean = false,
    ) =
        ResumeWedgePolicy.shouldReprepare(
            stillBuffering = stillBuffering,
            playWhenReady = playWhenReady,
            bufferedAheadMs = bufferedAheadMs,
            isLiveSource = isLiveSource,
        )

    @Test
    fun `a resume still buffering with the film loaded is prepared again`() {
        assertTrue(decide())
    }

    @Test
    fun `a resume that started playing is left alone`() {
        assertFalse(decide(stillBuffering = false))
    }

    @Test
    fun `a network that ran dry is left to the loader`() {
        assertFalse(decide(bufferedAheadMs = 2_000L))
    }

    @Test
    fun `the runway floor itself counts as loaded`() {
        assertTrue(decide(bufferedAheadMs = ResumeWedgePolicy.RUNWAY_FLOOR_MS))
    }

    @Test
    fun `a viewer who paused again keeps their pause`() {
        assertFalse(decide(playWhenReady = false))
    }

    @Test
    fun `live sources are never prepared`() {
        assertFalse(decide(isLiveSource = true))
    }
}
