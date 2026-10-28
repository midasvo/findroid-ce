package dev.jdtech.jellyfin.player.local.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for [resolveAutoPlayNext] — the "Play next episode automatically" resolution. */
class AutoPlayNextTest {

    @Test
    fun `always overrides the server`() {
        assertTrue(resolveAutoPlayNext(pref = "on", server = true))
        assertTrue(resolveAutoPlayNext(pref = "on", server = false))
        assertTrue(resolveAutoPlayNext(pref = "on", server = null))
    }

    @Test
    fun `never overrides the server`() {
        assertFalse(resolveAutoPlayNext(pref = "off", server = true))
        assertFalse(resolveAutoPlayNext(pref = "off", server = false))
        // Offline (no server value): an explicit "Never" is still honoured.
        assertFalse(resolveAutoPlayNext(pref = "off", server = null))
    }

    @Test
    fun `use server setting follows the server`() {
        assertTrue(resolveAutoPlayNext(pref = "server", server = true))
        assertFalse(resolveAutoPlayNext(pref = "server", server = false))
    }

    @Test
    fun `use server setting falls back to autoplay when the server value is unknown`() {
        // Offline, or getUserConfiguration failed.
        assertTrue(resolveAutoPlayNext(pref = "server", server = null))
    }

    @Test
    fun `an unset preference behaves like use server setting`() {
        assertTrue(resolveAutoPlayNext(pref = null, server = true))
        assertFalse(resolveAutoPlayNext(pref = null, server = false))
        assertTrue(resolveAutoPlayNext(pref = null, server = null))
    }

    @Test
    fun `an unknown preference value behaves like use server setting`() {
        assertTrue(resolveAutoPlayNext(pref = "garbage", server = true))
        assertFalse(resolveAutoPlayNext(pref = "garbage", server = false))
        assertTrue(resolveAutoPlayNext(pref = "garbage", server = null))
    }

    @Test
    fun `the server is only consulted when the preference is not an override`() {
        assertFalse(autoPlayNextFollowsServer("on"))
        assertFalse(autoPlayNextFollowsServer("off"))
        assertTrue(autoPlayNextFollowsServer("server"))
        assertTrue(autoPlayNextFollowsServer(null))
    }
}
