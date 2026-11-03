package dev.jdtech.jellyfin.ce.watchnext

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchNextPlannerTest {
    private val now = 1_000_000L

    private fun entry(
        id: String,
        kind: WatchNextKind = WatchNextKind.CONTINUE,
        positionMs: Long = 60_000,
        engagement: Long? = 500_000,
    ) =
        WatchNextEntry(
            itemId = id,
            kind = kind,
            itemType = WatchNextItemType.EPISODE,
            title = "Show $id",
            durationMs = 1_200_000,
            positionMs = positionMs,
            lastEngagementMs = engagement,
        )

    private fun program(id: Long, entry: WatchNextEntry, browsable: Boolean = true) =
        PublishedProgram(programId = id, itemId = entry.itemId, browsable = browsable, entry = entry)

    @Test
    fun `new items are inserted`() {
        val plan = planWatchNext(listOf(entry("a"), entry("b")), emptyList(), emptyMap(), now)

        assertEquals(listOf("a", "b"), plan.inserts.map { it.itemId })
        assertTrue(plan.updates.isEmpty())
        assertTrue(plan.deletes.isEmpty())
    }

    @Test
    fun `an unchanged program is left alone`() {
        val a = entry("a")
        val plan = planWatchNext(listOf(a), listOf(program(1, a)), emptyMap(), now)

        assertTrue(plan.inserts.isEmpty())
        assertTrue(plan.updates.isEmpty())
        assertTrue(plan.deletes.isEmpty())
    }

    @Test
    fun `a new position updates the existing program instead of adding one`() {
        val before = entry("a", positionMs = 60_000)
        val after = entry("a", positionMs = 600_000, engagement = 900_000)
        val plan = planWatchNext(listOf(after), listOf(program(7, before)), emptyMap(), now)

        assertTrue(plan.inserts.isEmpty())
        assertEquals(listOf(7L to after), plan.updates)
        assertTrue(plan.deletes.isEmpty())
    }

    @Test
    fun `an item that is no longer a candidate is removed`() {
        // Finished: gone from Continue Watching, and its next episode is now Next Up.
        val finished = entry("a")
        val next = entry("b", kind = WatchNextKind.NEXT, positionMs = 0, engagement = null)
        val plan = planWatchNext(listOf(next), listOf(program(1, finished)), emptyMap(), now)

        assertEquals(listOf(1L), plan.deletes)
        assertEquals(listOf("b"), plan.inserts.map { it.itemId })
    }

    @Test
    fun `duplicate programs of one item are reduced to one`() {
        val a = entry("a")
        val plan = planWatchNext(listOf(a), listOf(program(1, a), program(2, a)), emptyMap(), now)

        assertEquals(listOf(2L), plan.deletes)
        assertTrue(plan.inserts.isEmpty())
    }

    @Test
    fun `a program without an item id is removed`() {
        val stray = PublishedProgram(programId = 3, itemId = null, browsable = true, entry = null)
        val plan = planWatchNext(emptyList(), listOf(stray), emptyMap(), now)

        assertEquals(listOf(3L), plan.deletes)
    }

    @Test
    fun `an item the user removed from the row is not added back`() {
        val a = entry("a")
        val plan = planWatchNext(listOf(a), listOf(program(1, a, browsable = false)), emptyMap(), now)

        assertTrue(plan.inserts.isEmpty())
        assertEquals(listOf(1L), plan.deletes)
        assertEquals(mapOf("a" to 60_000L), plan.dismissed)
    }

    @Test
    fun `a dismissed item stays away while its position is the same`() {
        val plan = planWatchNext(listOf(entry("a")), emptyList(), mapOf("a" to 60_000L), now)

        assertTrue(plan.inserts.isEmpty())
        assertEquals(mapOf("a" to 60_000L), plan.dismissed)
    }

    @Test
    fun `a dismissed item comes back once it is watched again`() {
        val plan =
            planWatchNext(listOf(entry("a", positionMs = 90_000)), emptyList(), mapOf("a" to 60_000L), now)

        assertEquals(listOf("a"), plan.inserts.map { it.itemId })
        assertTrue(plan.dismissed.isEmpty())
    }

    @Test
    fun `a dismissal is forgotten once the item is no longer a candidate`() {
        val plan = planWatchNext(emptyList(), emptyList(), mapOf("a" to 60_000L), now)

        assertTrue(plan.dismissed.isEmpty())
    }

    @Test
    fun `a dismissal without a known position takes the current one`() {
        val plan = planWatchNext(listOf(entry("a")), emptyList(), mapOf("a" to UNKNOWN_POSITION), now)

        assertTrue(plan.inserts.isEmpty())
        assertEquals(mapOf("a" to 60_000L), plan.dismissed)
    }

    @Test
    fun `next up keeps the engagement time it was published with`() {
        val published = entry("b", kind = WatchNextKind.NEXT, positionMs = 0, engagement = 400_000)
        val fresh = published.copy(lastEngagementMs = null)
        val plan = planWatchNext(listOf(fresh), listOf(program(5, published)), emptyMap(), now)

        assertTrue(plan.inserts.isEmpty())
        assertTrue(plan.updates.isEmpty())
    }

    @Test
    fun `new next up items keep the server's order`() {
        val plan =
            planWatchNext(
                listOf(
                    entry("b", kind = WatchNextKind.NEXT, positionMs = 0, engagement = null),
                    entry("c", kind = WatchNextKind.NEXT, positionMs = 0, engagement = null),
                ),
                emptyList(),
                emptyMap(),
                now,
            )

        assertEquals(listOf(now, now - 1), plan.inserts.map { it.lastEngagementMs })
    }

    @Test
    fun `an item in both lists gets one continue program`() {
        val plan =
            planWatchNext(
                listOf(entry("a"), entry("a", kind = WatchNextKind.NEXT, positionMs = 0)),
                emptyList(),
                emptyMap(),
                now,
            )

        assertEquals(listOf(WatchNextKind.CONTINUE), plan.inserts.map { it.kind })
    }

    @Test
    fun `only the most recently watched items are kept`() {
        val entries = (1..5).map { entry("$it", engagement = it * 1000L) }
        val plan = planWatchNext(entries, emptyList(), emptyMap(), now, maxPrograms = 2)

        assertEquals(listOf("5", "4"), plan.inserts.map { it.itemId })
    }
}
