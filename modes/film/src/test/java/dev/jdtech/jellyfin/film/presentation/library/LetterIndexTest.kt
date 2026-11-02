package dev.jdtech.jellyfin.film.presentation.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LetterIndexTest {
    /**
     * A stand-in for the server: Jellyfin sorts on a lower-case sort name without a leading "the",
     * "a" or "an", and answers `nameLessThan` with an ordinal compare on it.
     */
    private fun sortName(name: String): String =
        name.lowercase().replaceFirst(Regex("^(the|a|an) "), "")

    private fun indexOf(names: List<String>, ascending: Boolean = true): LetterIndex {
        val sortNames = names.map(::sortName)
        val counts = LetterIndex.BOUNDARIES.map { b -> sortNames.count { it < b } }
        return LetterIndex.build(counts, names.size, ascending)!!
    }

    /** The grid order the server returns. */
    private fun sorted(names: List<String>, ascending: Boolean = true): List<String> =
        names.sortedBy(::sortName).let { if (ascending) it else it.reversed() }

    private val library =
        listOf(
            "Bluey",
            "The Office",
            "Paw Patrol",
            "24",
            "!Women Art Revolution",
            "A Bug's Life",
            "Peppa Pig",
            "Other Show",
            "Zootopia",
            "Arthur",
        )

    @Test
    fun `every item is under the letter of its own sort name`() {
        val index = indexOf(library)
        val grid = sorted(library)
        grid.forEachIndexed { position, name ->
            val first = sortName(name).first().uppercaseChar()
            val expected = if (first in 'A'..'Z') first else LetterIndex.OTHER
            assertEquals(name, expected, index.letterAt(position))
        }
    }

    @Test
    fun `digits and symbols go under the other letter, before A`() {
        val index = indexOf(library)
        val grid = sorted(library)
        assertEquals(0, index.positionOf(LetterIndex.OTHER))
        assertEquals(LetterIndex.OTHER, index.letterAt(grid.indexOf("24")))
        assertEquals(LetterIndex.OTHER, index.letterAt(grid.indexOf("!Women Art Revolution")))
        assertEquals(2, index.positionOf('A'))
    }

    @Test
    fun `articles are skipped the way the server sorts`() {
        val index = indexOf(library)
        val grid = sorted(library)
        assertEquals('O', index.letterAt(grid.indexOf("The Office")))
        assertEquals('B', index.letterAt(grid.indexOf("A Bug's Life")))
        // "Arthur" starts with "A", not with the article "a ".
        assertEquals('A', index.letterAt(grid.indexOf("Arthur")))
        assertEquals(grid.indexOf("Bluey"), index.positionOf('B'))
    }

    @Test
    fun `a jump lands on the first item of the letter`() {
        val index = indexOf(library)
        val grid = sorted(library)
        assertEquals(grid.indexOfFirst { sortName(it).startsWith("p") }, index.positionOf('P'))
        assertEquals(grid.indexOf("Zootopia"), index.positionOf('Z'))
    }

    @Test
    fun `empty letters have no position and take no room`() {
        val index = indexOf(library)
        assertNull(index.positionOf('C'))
        assertNull(index.positionOf('Q'))
        assertTrue(index.letters.first { it.char == 'C' }.isEmpty)
        assertEquals(library.size, index.letters.sumOf { it.count })
    }

    @Test
    fun `descending order puts Z first`() {
        val index = indexOf(library, ascending = false)
        val grid = sorted(library, ascending = false)
        assertEquals('Z', index.letters.first().char)
        assertEquals(0, index.positionOf('Z'))
        // Descending, "Other Show" comes before "The Office".
        assertEquals(grid.indexOf("Other Show"), index.positionOf('O'))
        assertEquals('O', index.letterAt(grid.indexOf("The Office")))
        assertEquals(LetterIndex.OTHER, index.letterAt(grid.lastIndex))
        assertEquals('P', index.letterAt(grid.indexOf("Paw Patrol")))
    }

    @Test
    fun `items after z count as Z`() {
        val names = listOf("Amélie", "Zorro", "Ébène")
        val index = indexOf(names)
        assertEquals('Z', index.letterAt(sorted(names).indexOf("Ébène")))
    }

    @Test
    fun `an empty library has no letters to jump to`() {
        val index = indexOf(emptyList())
        assertTrue(index.letters.all { it.isEmpty })
        assertNull(index.letterAt(0))
        assertNull(index.positionOf('A'))
    }

    @Test
    fun `counts that don't fit together give no index`() {
        val counts = LetterIndex.BOUNDARIES.map { 5 }.toMutableList().also { it[3] = 2 }
        assertNull(LetterIndex.build(counts, 10, ascending = true))
        assertNull(LetterIndex.build(LetterIndex.BOUNDARIES.map { 20 }, 10, ascending = true))
        assertNull(LetterIndex.build(listOf(1, 2), 10, ascending = true))
    }

    @Test
    fun `positions outside the list have no letter`() {
        val index = indexOf(library)
        assertNull(index.letterAt(-1))
        assertNull(index.letterAt(library.size))
    }
}
