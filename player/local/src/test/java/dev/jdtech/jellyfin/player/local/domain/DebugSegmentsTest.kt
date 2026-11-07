package dev.jdtech.jellyfin.player.local.domain

import dev.jdtech.jellyfin.models.FindroidSegment
import dev.jdtech.jellyfin.models.FindroidSegmentType
import org.junit.Assert.assertEquals
import org.junit.Test

class DebugSegmentsTest {
    @Test
    fun `parses one segment per line in seconds`() {
        assertEquals(
            listOf(
                FindroidSegment(FindroidSegmentType.INTRO, 10_000L, 40_000L),
                FindroidSegment(FindroidSegmentType.OUTRO, 60_000L, 9_999_000L),
            ),
            DebugSegments.parse("# test\nintro, 10, 40\n\nOUTRO,60,9999\n"),
        )
    }
}
