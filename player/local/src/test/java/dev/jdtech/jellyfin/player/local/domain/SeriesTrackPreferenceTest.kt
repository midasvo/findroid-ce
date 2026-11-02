package dev.jdtech.jellyfin.player.local.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Unit tests for matching a remembered series audio or subtitle choice to another file's tracks. */
class SeriesTrackPreferenceTest {

    private fun track(language: String?, title: String? = null) = TrackCandidate(language, title)

    @Test
    fun `language and title beat position`() {
        val candidates = listOf(track("eng", "Commentary"), track("eng", "Full"), track("eng"))
        val stored = StoredChoice("eng", "Full", 0)
        assertEquals(1, matchSeriesTrack(stored, candidates))
    }

    @Test
    fun `position beats plain language`() {
        val candidates = listOf(track("jpn"), track("eng", "A"), track("eng", "B"))
        val stored = StoredChoice("eng", "Gone", 1)
        assertEquals(2, matchSeriesTrack(stored, candidates))
    }

    @Test
    fun `falls back to first track of the language`() {
        val candidates = listOf(track("jpn"), track("eng", "A"), track("eng", "B"))
        assertEquals(1, matchSeriesTrack(StoredChoice("eng", "Gone", 5), candidates))
        assertEquals(1, matchSeriesTrack(StoredChoice("eng", null, null), candidates))
    }

    @Test
    fun `title only match without a language`() {
        val candidates = listOf(track("eng"), track(null, " forced "))
        assertEquals(1, matchSeriesTrack(StoredChoice(null, "Forced", null), candidates))
    }

    @Test
    fun `two letter and three letter codes match`() {
        val candidates = listOf(track("fra"), track("eng"))
        assertEquals(1, matchSeriesTrack(StoredChoice("en", null, 0), candidates))
        assertEquals(0, matchSeriesTrack(StoredChoice("fr-CA", null, 0), candidates))
    }

    @Test
    fun `no match gives null`() {
        val candidates = listOf(track("jpn"), track("eng"))
        assertNull(matchSeriesTrack(StoredChoice("deu", "Full", 0), candidates))
        assertNull(matchSeriesTrack(StoredChoice(null, "Full", null), candidates))
        assertNull(matchSeriesTrack(StoredChoice("eng", null, 0), emptyList()))
    }

    @Test
    fun `choice records the position within the language`() {
        val candidates = listOf(track("jpn"), track("en", "A"), track("jpn"), track("eng", "B"))
        assertEquals(StoredChoice("eng", "B", 1), seriesTrackChoice(candidates, 3))
        assertEquals(StoredChoice("jpn", null, 1), seriesTrackChoice(candidates, 2))
    }

    @Test
    fun `untagged and untitled tracks give no choice`() {
        val candidates = listOf(track(null), track("und", " "))
        assertNull(seriesTrackChoice(candidates, 0))
        assertNull(seriesTrackChoice(candidates, 1))
        assertNull(seriesTrackChoice(candidates, 2))
    }

    @Test
    fun `title only choice has no position`() {
        val candidates = listOf(track(null, "Forced"))
        assertEquals(StoredChoice(null, "Forced", null), seriesTrackChoice(candidates, 0))
    }
}
