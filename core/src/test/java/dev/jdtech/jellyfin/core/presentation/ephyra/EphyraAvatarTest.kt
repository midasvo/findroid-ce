package dev.jdtech.jellyfin.core.presentation.ephyra

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EphyraAvatarTest {
    @Test
    fun `one word gives one letter`() {
        assertEquals("M", avatarInitials("midas"))
    }

    @Test
    fun `first and last word give two letters`() {
        assertEquals("MO", avatarInitials("Midas van Oene"))
    }

    @Test
    fun `a blank name gives a question mark`() {
        assertEquals("?", avatarInitials("  "))
        assertEquals("?", avatarInitials(""))
    }

    @Test
    fun `an emoji or combining mark is kept whole`() {
        assertEquals("😀", avatarInitials("😀"))
        assertEquals("😀S", avatarInitials("😀 smile"))
        assertEquals("É", avatarInitials("é"))
    }

    @Test
    fun `the colour index is stable and in range`() {
        val seed = UUID.fromString("31e47044-9b79-4bb0-99d0-0e477ed65420")
        val index = avatarColorIndex(seed, 4)
        assertEquals(index, avatarColorIndex(seed, 4))
        assertTrue(index in 0..3)
        repeat(200) { assertTrue(avatarColorIndex(UUID.randomUUID(), 4) in 0..3) }
    }
}
