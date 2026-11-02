package dev.jdtech.jellyfin.film.presentation.library

/**
 * CE (#135): where each letter of the A–Z jump bar starts in a library sorted by name.
 *
 * Built from server counts instead of item names, because a paged grid only has the names it has
 * loaded. The server also decides what a sort name is ("The Office" sorts as "office"), so the bar
 * always agrees with the order of the grid. Everything before "a" (digits, most symbols) is [OTHER].
 */
class LetterIndex private constructor(val letters: List<Letter>, val total: Int) {
    /** A letter of the bar: its items are positions `start until start + count`. */
    data class Letter(val char: Char, val start: Int, val count: Int) {
        val isEmpty: Boolean
            get() = count == 0
    }

    /** The letter of the item at [position], or null when it's outside the list. */
    fun letterAt(position: Int): Char? {
        if (position !in 0 until total) return null
        return letters.firstOrNull { position >= it.start && position < it.start + it.count }?.char
    }

    /** The position the bar jumps to for [char], or null when the library has nothing there. */
    fun positionOf(char: Char): Int? = letters.firstOrNull { it.char == char && !it.isEmpty }?.start

    companion object {
        const val OTHER = '#'

        /** The letters of the bar, in ascending order. */
        val CHARS: List<Char> = listOf(OTHER) + ('A'..'Z')

        /** What to ask the server for: the number of items whose sort name comes before each. */
        val BOUNDARIES: List<String> = ('a'..'z').map { it.toString() }

        /**
         * @param countsBefore for each of [BOUNDARIES], the number of items whose sort name comes
         *   before it.
         * @param total the number of items in the library. Items after "z" (for example accents)
         *   count as Z, which is where the server puts them too.
         * @param ascending the sort order of the grid. When descending, Z comes first.
         * @return null when the counts don't fit together (the library changed between the
         *   requests), so the screen hides the bar instead of jumping to the wrong place.
         */
        fun build(countsBefore: List<Int>, total: Int, ascending: Boolean): LetterIndex? {
            if (countsBefore.size != BOUNDARIES.size) return null
            // Where each letter starts in ascending order, plus the end of the list.
            val bounds = listOf(0) + countsBefore + total
            if (bounds.zipWithNext().any { (a, b) -> b < a } || total < 0) return null
            val letters =
                CHARS.mapIndexed { i, char ->
                    val start = bounds[i]
                    val count = bounds[i + 1] - start
                    Letter(char, if (ascending) start else total - start - count, count)
                }
            return LetterIndex(if (ascending) letters else letters.reversed(), total)
        }
    }
}
