package dev.jdtech.jellyfin.player.local.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for what [AudioDecoderFallback] remembers about broken decoders. */
class AudioDecoderFallbackTest {

    private val decoder = "c2.dolby.eac3.decoder"

    @Test
    fun `a decoder works until it is marked broken`() {
        val fallback = AudioDecoderFallback("build-1", emptySet()) {}

        assertFalse(fallback.isBroken(decoder))
        assertTrue(fallback.markBroken(decoder))
        assertTrue(fallback.isBroken(decoder))
    }

    @Test
    fun `other decoders stay available`() {
        val fallback = AudioDecoderFallback("build-1", emptySet()) {}

        fallback.markBroken(decoder)

        assertFalse(fallback.isBroken("c2.android.aac.decoder"))
    }

    @Test
    fun `marking a decoder twice gives no second retry`() {
        var stored = 0
        val fallback = AudioDecoderFallback("build-1", emptySet()) { stored++ }

        assertTrue(fallback.markBroken(decoder))
        assertFalse(fallback.markBroken(decoder))
        assertEquals(1, stored)
    }

    @Test
    fun `a broken decoder is remembered in the next session`() {
        var stored = emptySet<String>()
        AudioDecoderFallback("build-1", stored) { stored = it }.markBroken(decoder)

        assertTrue(AudioDecoderFallback("build-1", stored) {}.isBroken(decoder))
    }

    @Test
    fun `an OS update gets to try the decoder again`() {
        var stored = emptySet<String>()
        AudioDecoderFallback("build-1", stored) { stored = it }.markBroken(decoder)

        assertFalse(AudioDecoderFallback("build-2", stored) {}.isBroken(decoder))
    }

    @Test
    fun `an entry from another OS build is kept when a new one is added`() {
        var stored = emptySet<String>()
        AudioDecoderFallback("build-1", stored) { stored = it }.markBroken(decoder)
        AudioDecoderFallback("build-2", stored) { stored = it }.markBroken(decoder)

        assertEquals(2, stored.size)
    }
}
