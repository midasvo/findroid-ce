package dev.jdtech.jellyfin.ce.ui.focus

import org.junit.Assert.assertEquals
import org.junit.Test

class FocusPositionTest {
    private val keys = listOf("a", "b", "c", "d")

    @Test
    fun resolveFollowsTheKeyWhenTheItemMoved() {
        val position = FocusPosition(row = 1, column = 0, key = null)
        position.save(row = 1, column = 0, key = "c")

        position.resolve(row = 1, count = keys.size) { keys[it] }

        assertEquals(2, position.column)
    }

    @Test
    fun resolveClampsTheIndexWhenTheKeyIsGone() {
        val position = FocusPosition(row = 1, column = 0, key = null)
        position.save(row = 1, column = 9, key = "gone")

        position.resolve(row = 1, count = keys.size) { keys[it] }

        assertEquals(3, position.column)
    }

    @Test
    fun resolveLeavesOtherRowsAlone() {
        val position = FocusPosition(row = 0, column = 0, key = null)
        position.save(row = 0, column = 1, key = "c")

        position.resolve(row = 1, count = keys.size) { keys[it] }

        assertEquals(0, position.row)
        assertEquals(1, position.column)
    }
}
