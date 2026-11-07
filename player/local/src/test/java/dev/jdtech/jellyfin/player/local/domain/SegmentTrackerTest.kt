package dev.jdtech.jellyfin.player.local.domain

import dev.jdtech.jellyfin.models.FindroidSegment
import dev.jdtech.jellyfin.models.FindroidSegmentType
import dev.jdtech.jellyfin.models.FindroidSegmentType.COMMERCIAL
import dev.jdtech.jellyfin.models.FindroidSegmentType.INTRO
import dev.jdtech.jellyfin.models.FindroidSegmentType.OUTRO
import dev.jdtech.jellyfin.models.FindroidSegmentType.PREVIEW
import dev.jdtech.jellyfin.models.FindroidSegmentType.RECAP
import dev.jdtech.jellyfin.models.FindroidSegmentType.UNKNOWN
import dev.jdtech.jellyfin.models.MediaSegmentAction
import dev.jdtech.jellyfin.models.MediaSegmentAction.ASK
import dev.jdtech.jellyfin.models.MediaSegmentAction.IGNORE
import dev.jdtech.jellyfin.models.MediaSegmentAction.SKIP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentTrackerTest {
    private val duration = 1_400_000L // 23:20
    private val recap = FindroidSegment(RECAP, startTicks = 0L, endTicks = 30_000L)
    private val intro = FindroidSegment(INTRO, startTicks = 60_000L, endTicks = 150_000L)
    private val outro = FindroidSegment(OUTRO, startTicks = 1_310_000L, endTicks = 1_398_000L)

    private fun tracker(
        actions: Map<FindroidSegmentType, MediaSegmentAction> =
            mapOf(INTRO to ASK, OUTRO to ASK, RECAP to IGNORE, PREVIEW to IGNORE, COMMERCIAL to IGNORE),
        buttonDurationMs: Long = 5_000L,
        thresholdMs: Long = 5_000L,
        onlyInPip: Boolean = false,
        segments: List<FindroidSegment> = listOf(recap, intro, outro),
    ) =
        SegmentTracker(actions, buttonDurationMs, thresholdMs, onlyInPip).apply {
            setSegments(segments)
        }

    private fun SegmentTracker.at(
        positionMs: Long,
        hasNext: Boolean = true,
        inPip: Boolean = false,
    ) = update(positionMs, duration, hasNext, inPip)

    // --- active segment ---------------------------------------------------------------------

    @Test
    fun `active segment covers start to just before the end`() {
        val t = tracker()
        assertNull(t.activeSegment(59_999L))
        assertEquals(intro, t.activeSegment(60_000L))
        assertEquals(intro, t.activeSegment(149_899L))
        assertNull(t.activeSegment(149_900L))
        assertNull(t.activeSegment(150_000L))
    }

    @Test
    fun `no segments means nothing is ever active`() {
        val t = tracker(segments = emptyList())
        assertNull(t.activeSegment(70_000L))
        assertEquals(SegmentDecision.None, t.at(70_000L))
    }

    @Test
    fun `back-to-back segments hand over at the boundary`() {
        val first = FindroidSegment(RECAP, 0L, 20_000L)
        val second = FindroidSegment(INTRO, 20_000L, 50_000L)
        val t = tracker(actions = mapOf(RECAP to ASK, INTRO to ASK), segments = listOf(first, second))
        assertEquals(first, (t.at(19_000L) as SegmentDecision.Ask).segment)
        val next = t.at(20_000L) as SegmentDecision.Ask
        assertEquals(second, next.segment)
        assertTrue("entering the second segment opens a new window", next.inButtonWindow)
    }

    // --- actions ----------------------------------------------------------------------------

    @Test
    fun `ignored types and types without a setting give nothing`() {
        val t = tracker(segments = listOf(recap, FindroidSegment(UNKNOWN, 200_000L, 210_000L)))
        assertEquals(SegmentDecision.None, t.at(10_000L))
        assertEquals(SegmentDecision.None, t.at(205_000L))
    }

    @Test
    fun `isActive is false only when every type is ignored`() {
        assertTrue(tracker().isActive)
        assertFalse(tracker(actions = FindroidSegmentType.entries.associateWith { IGNORE }).isActive)
        assertFalse(tracker(actions = emptyMap()).isActive)
    }

    // --- button window ----------------------------------------------------------------------

    @Test
    fun `button shows for the duration after entering, then hides`() {
        val t = tracker()
        assertTrue((t.at(60_000L) as SegmentDecision.Ask).inButtonWindow)
        assertTrue((t.at(64_999L) as SegmentDecision.Ask).inButtonWindow)
        val late = t.at(65_000L) as SegmentDecision.Ask
        assertFalse(late.inButtonWindow)
        assertEquals("still in the segment, so the controls can offer it", intro, late.segment)
    }

    @Test
    fun `seeking into the middle of a segment starts the window there`() {
        val t = tracker()
        assertTrue((t.at(100_000L) as SegmentDecision.Ask).inButtonWindow)
        assertTrue((t.at(104_000L) as SegmentDecision.Ask).inButtonWindow)
        assertFalse((t.at(105_000L) as SegmentDecision.Ask).inButtonWindow)
    }

    @Test
    fun `seeking back inside the segment opens the window again`() {
        val t = tracker()
        t.at(60_000L)
        assertFalse((t.at(80_000L) as SegmentDecision.Ask).inButtonWindow)
        assertTrue((t.at(70_000L) as SegmentDecision.Ask).inButtonWindow)
    }

    @Test
    fun `leaving the segment early hides it and re-entering starts a new window`() {
        val t = tracker()
        t.at(60_000L)
        t.at(80_000L)
        assertEquals(SegmentDecision.None, t.at(200_000L))
        assertTrue((t.at(90_000L) as SegmentDecision.Ask).inButtonWindow)
    }

    @Test
    fun `the button hides when the segment ends`() {
        val t = tracker()
        t.at(146_000L)
        assertEquals(SegmentDecision.None, t.at(149_950L))
    }

    @Test
    fun `a new item forgets the old one's window`() {
        val t = tracker()
        t.at(60_000L)
        t.at(80_000L)
        t.setSegments(listOf(intro))
        assertTrue((t.at(80_000L) as SegmentDecision.Ask).inButtonWindow)
    }

    // --- auto-skip --------------------------------------------------------------------------

    @Test
    fun `auto-skip fires once per entry`() {
        val t = tracker(actions = mapOf(INTRO to SKIP))
        assertEquals(
            SegmentDecision.AutoSkip(intro, SkipTarget.SeekTo(150_000L)),
            t.at(60_500L),
        )
        assertEquals("the seek has not landed yet", SegmentDecision.None, t.at(61_500L))
        t.at(200_000L)
        assertTrue("seeking back in skips again", t.at(70_000L) is SegmentDecision.AutoSkip)
    }

    @Test
    fun `auto-skip only in picture-in-picture asks outside it`() {
        val t = tracker(actions = mapOf(INTRO to SKIP), onlyInPip = true)
        assertTrue(t.at(60_000L, inPip = false) is SegmentDecision.Ask)
        assertTrue(t.at(61_000L, inPip = true) is SegmentDecision.AutoSkip)
    }

    // --- skip target ------------------------------------------------------------------------

    @Test
    fun `skipping an intro lands at its end`() {
        val decision = tracker().at(70_000L) as SegmentDecision.Ask
        assertEquals(SkipTarget.SeekTo(150_000L), decision.target)
    }

    @Test
    fun `an outro that runs to the end goes to the next item`() {
        val decision = tracker().at(1_320_000L) as SegmentDecision.Ask
        assertEquals(SkipTarget.NextItem, decision.target)
    }

    @Test
    fun `an outro on the last item lands at its end`() {
        val decision = tracker().at(1_320_000L, hasNext = false) as SegmentDecision.Ask
        assertEquals(SkipTarget.SeekTo(1_398_000L), decision.target)
    }

    @Test
    fun `an outro followed by more than the threshold lands at its end`() {
        val t = tracker()
        val early = FindroidSegment(OUTRO, 1_300_000L, 1_390_000L) // 10 s before the end
        assertEquals(SkipTarget.SeekTo(1_390_000L), t.skipTarget(early, duration, hasNextItem = true))
        val closing = FindroidSegment(OUTRO, 1_300_000L, 1_395_001L)
        assertEquals(SkipTarget.NextItem, t.skipTarget(closing, duration, hasNextItem = true))
    }

    @Test
    fun `an unknown runtime never goes to the next item`() {
        val t = tracker()
        assertEquals(SkipTarget.SeekTo(1_398_000L), t.skipTarget(outro, 0L, hasNextItem = true))
        assertEquals(
            SkipTarget.SeekTo(1_398_000L),
            t.skipTarget(outro, Long.MIN_VALUE + 1, hasNextItem = true),
        )
    }

    @Test
    fun `only outros go to the next item`() {
        val t = tracker()
        val lateIntro = FindroidSegment(INTRO, 1_310_000L, 1_398_000L)
        assertEquals(SkipTarget.SeekTo(1_398_000L), t.skipTarget(lateIntro, duration, true))
    }

    @Test
    fun `automatic outro skip with autoplay off ends the item`() {
        assertEquals(
            SkipTarget.EndItem,
            SegmentTracker.autoSkipTarget(SkipTarget.NextItem, autoPlayNext = false),
        )
        assertEquals(
            SkipTarget.NextItem,
            SegmentTracker.autoSkipTarget(SkipTarget.NextItem, autoPlayNext = true),
        )
        assertEquals(
            SkipTarget.SeekTo(150_000L),
            SegmentTracker.autoSkipTarget(SkipTarget.SeekTo(150_000L), autoPlayNext = false),
        )
    }
}
