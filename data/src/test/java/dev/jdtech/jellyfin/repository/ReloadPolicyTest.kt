package dev.jdtech.jellyfin.repository

import java.util.UUID
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReloadPolicyTest {
    private var now = 1_000L
    private val events = DataChangeEvents()
    private val policy = ReloadPolicy(events, maxAgeMs = 60_000, clock = { now })

    private fun loaded() = policy.complete(policy.begin())

    @Test
    fun `loads when nothing has loaded yet`() {
        assertTrue(policy.shouldReload())
    }

    @Test
    fun `a plain back right after a load does not reload`() {
        loaded()
        now += 5_000
        assertFalse(policy.shouldReload())
    }

    @Test
    fun `data older than the limit reloads`() {
        loaded()
        now += 61_000
        assertTrue(policy.shouldReload())
    }

    @Test
    fun `a change somewhere else makes it dirty`() {
        loaded()
        events.onChanged()
        assertTrue(policy.shouldReload())
    }

    @Test
    fun `a change made on the screen itself does not`() {
        loaded()
        policy.changedHere()
        assertFalse(policy.shouldReload())
    }

    @Test
    fun `a change during a load is still dirty afterwards`() {
        val token = policy.begin()
        events.onChanged()
        policy.complete(token)
        assertTrue(policy.shouldReload())
    }

    @Test
    fun `a failed load leaves it due`() {
        policy.begin()
        assertTrue(policy.shouldReload())
    }

    @Test
    fun `invalidate makes it due`() {
        loaded()
        policy.invalidate()
        assertTrue(policy.shouldReload())
    }

    @Test
    fun `playback stop reloads once, through the stop event`() {
        val playback = PlaybackEvents(events)
        loaded()
        playback.onPlaying(UUID.randomUUID())
        now += 90_000
        // The player has closed but its stop is not reported yet: the stop event reloads.
        assertFalse(policy.shouldReload(stopEventFollows = true))
        // A screen without the stop event reloads on resume.
        assertTrue(policy.shouldReload(stopEventFollows = false))
        playback.onStopped(UUID.randomUUID())
        // A screen that missed the event still finds out afterwards.
        assertTrue(policy.shouldReload(stopEventFollows = true))
    }
}
