package dev.jdtech.jellyfin.core.presentation.downloader

import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidImages
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for what Smart Downloads queues and removes. */
class SmartDownloadPlannerTest {
    private val seriesId = UUID.randomUUID()
    private val since = 1_000L

    @Test
    fun `tops up to the chosen number across a season boundary`() {
        val s1 = (1..4).map { episode(1, it, played = it == 1) }
        val s2 = (1..3).map { episode(2, it) }

        val plan = plan(s1 + s2, keepReady = 3, finishedRecently = true)
        assertEquals(listOf(s1[1], s1[2], s1[3]), plan.toQueue)

        val finishedSeason = s1.map { it.copy(played = true) }
        val nextPlan = plan(finishedSeason + s2, keepReady = 3, finishedRecently = true)
        assertEquals(s2, nextPlan.toQueue)
    }

    @Test
    fun `episodes are queued in aired order whatever order they come in`() {
        val e1 = episode(1, 1, played = true)
        val e2 = episode(1, 2)
        val e3 = episode(2, 1)

        val plan = plan(listOf(e3, e2, e1), keepReady = 2, finishedRecently = true)

        assertEquals(listOf(e2, e3), plan.toQueue)
    }

    @Test
    fun `specials are skipped`() {
        val special = episode(0, 1)
        val e1 = episode(1, 1, played = true)
        val e2 = episode(1, 2)

        val plan = plan(listOf(special, e1, e2), keepReady = 3, finishedRecently = true)

        assertEquals(listOf(e2), plan.toQueue)
    }

    @Test
    fun `a played special does not count as watching the series`() {
        val special = episode(0, 1, played = true)
        val e1 = episode(1, 1)

        val plan = plan(listOf(special, e1), keepReady = 3, finishedRecently = true)

        assertTrue(plan.toQueue.isEmpty())
    }

    @Test
    fun `nothing is queued when no episode has been played`() {
        val plan = plan((1..5).map { episode(1, it) }, keepReady = 3, finishedRecently = true)

        assertTrue(plan.toQueue.isEmpty())
    }

    @Test
    fun `nothing is queued when no episode was finished and nothing was watched`() {
        val episodes = listOf(episode(1, 1, played = true)) + (2..5).map { episode(1, it) }

        val plan = plan(episodes, keepReady = 3, finishedRecently = false)

        assertTrue(plan.toQueue.isEmpty())
        assertTrue(plan.toDelete.isEmpty())
    }

    @Test
    fun `held episodes count whether they are downloaded or in flight`() {
        val e1 = episode(1, 1, played = true)
        val episodes = listOf(e1) + (2..6).map { episode(1, it) }

        val plan =
            plan(
                episodes,
                downloads = mapOf(episodes[1].id to download(downloadedAt = 10, smart = false)),
                inFlightIds = setOf(episodes[2].id),
                keepReady = 3,
                finishedRecently = true,
            )

        assertEquals(listOf(episodes[3]), plan.toQueue)
    }

    @Test
    fun `nothing is queued when enough episodes are already held`() {
        val e1 = episode(1, 1, played = true)
        val episodes = listOf(e1) + (2..4).map { episode(1, it) }

        val plan =
            plan(
                episodes,
                downloads = episodes.drop(1).associate { it.id to download(10, smart = true) },
                keepReady = 3,
                finishedRecently = true,
            )

        assertTrue(plan.toQueue.isEmpty())
    }

    @Test
    fun `episodes that cannot be downloaded are not queued`() {
        val e1 = episode(1, 1, played = true)
        val missing = episode(1, 2, missing = true)
        val blocked = episode(1, 3, canDownload = false)
        val e4 = episode(1, 4)

        val plan = plan(listOf(e1, missing, blocked, e4), keepReady = 3, finishedRecently = true)

        assertEquals(listOf(e4), plan.toQueue)
    }

    @Test
    fun `episodes played out of order are not queued again`() {
        val e1 = episode(1, 1, played = true)
        val e2 = episode(1, 2)
        val e3 = episode(1, 3, played = true)
        val e4 = episode(1, 4)
        val e5 = episode(1, 5)

        val plan = plan(listOf(e1, e2, e3, e4, e5), keepReady = 2, finishedRecently = true)

        assertEquals(listOf(e4, e5), plan.toQueue)
    }

    @Test
    fun `a watched episode that Smart Downloads queued is deleted`() {
        val e1 = watchedEpisode(1, 1, playedAt = 5_000)
        val e2 = episode(1, 2)

        val plan =
            plan(
                listOf(e1, e2),
                downloads = mapOf(e1.id to download(downloadedAt = 2_000, smart = true)),
                keepReady = 1,
                finishedRecently = false,
            )

        assertEquals(listOf(e1), plan.toDelete)
    }

    @Test
    fun `a deleted download is replaced even when nothing was finished just now`() {
        val e1 = watchedEpisode(1, 1, playedAt = 5_000)
        val e2 = episode(1, 2)
        val e3 = episode(1, 3)

        val plan =
            plan(
                listOf(e1, e2, e3),
                downloads = mapOf(e1.id to download(downloadedAt = 2_000, smart = true)),
                keepReady = 2,
                finishedRecently = false,
            )

        assertEquals(listOf(e1), plan.toDelete)
        assertEquals(listOf(e2, e3), plan.toQueue)
    }

    @Test
    fun `a download the user started is never deleted but still counts as watching`() {
        val e1 = watchedEpisode(1, 1, playedAt = 5_000)
        val e2 = episode(1, 2)

        val plan =
            plan(
                listOf(e1, e2),
                downloads = mapOf(e1.id to download(downloadedAt = 2_000, smart = false)),
                keepReady = 1,
                finishedRecently = false,
            )

        assertTrue(plan.toDelete.isEmpty())
        assertEquals(listOf(e2), plan.toQueue)
    }

    @Test
    fun `an episode watched before Smart Downloads was turned on is not deleted`() {
        val e1 = watchedEpisode(1, 1, playedAt = 500)

        val plan =
            plan(
                listOf(e1, episode(1, 2)),
                downloads = mapOf(e1.id to download(downloadedAt = 100, smart = true)),
                keepReady = 1,
                finishedRecently = false,
            )

        assertTrue(plan.toDelete.isEmpty())
    }

    @Test
    fun `an episode played before it was downloaded is kept for a rewatch`() {
        val e1 = watchedEpisode(1, 1, playedAt = 3_000)

        val plan =
            plan(
                listOf(e1, episode(1, 2)),
                downloads = mapOf(e1.id to download(downloadedAt = 4_000, smart = true)),
                keepReady = 1,
                finishedRecently = false,
            )

        assertTrue(plan.toDelete.isEmpty())
    }

    @Test
    fun `episodes with unknown dates are not deleted`() {
        val noPlayedDate = episode(1, 1, played = true)
        val noDownloadDate = watchedEpisode(1, 2, playedAt = 5_000)

        val plan =
            plan(
                listOf(noPlayedDate, noDownloadDate, episode(1, 3)),
                downloads =
                    mapOf(
                        noPlayedDate.id to download(downloadedAt = 2_000, smart = true),
                        noDownloadDate.id to download(downloadedAt = null, smart = true),
                    ),
                keepReady = 1,
                finishedRecently = false,
            )

        assertTrue(plan.toDelete.isEmpty())
    }

    @Test
    fun `an unplayed episode is never deleted`() {
        val e1 = episode(1, 1, lastPlayedAt = 5_000)

        val plan =
            plan(
                listOf(e1, episode(1, 2)),
                downloads = mapOf(e1.id to download(downloadedAt = 2_000, smart = true)),
                keepReady = 1,
                finishedRecently = true,
            )

        assertTrue(plan.toDelete.isEmpty())
    }

    @Test
    fun `the playing episode is neither deleted nor counted as played`() {
        val e1 = watchedEpisode(1, 1, playedAt = 5_000)
        val e2 = episode(1, 2)

        val plan =
            plan(
                listOf(e1, e2),
                downloads = mapOf(e1.id to download(downloadedAt = 2_000, smart = true)),
                keepReady = 1,
                finishedRecently = true,
                playingItemId = e1.id,
            )

        assertTrue(plan.toDelete.isEmpty())
        assertTrue(plan.toQueue.isEmpty())
    }

    @Test
    fun `only the playing episode is spared when an earlier one was watched too`() {
        val e1 = watchedEpisode(1, 1, playedAt = 5_000)
        val e2 = watchedEpisode(1, 2, playedAt = 6_000)
        val e3 = episode(1, 3)

        val plan =
            plan(
                listOf(e1, e2, e3),
                downloads =
                    mapOf(
                        e1.id to download(downloadedAt = 2_000, smart = true),
                        e2.id to download(downloadedAt = 2_000, smart = true),
                    ),
                keepReady = 2,
                finishedRecently = false,
                playingItemId = e2.id,
            )

        assertEquals(listOf(e1), plan.toDelete)
    }

    private fun plan(
        episodes: List<FindroidEpisode>,
        downloads: Map<UUID, SmartDownload> = emptyMap(),
        inFlightIds: Set<UUID> = emptySet(),
        keepReady: Int,
        finishedRecently: Boolean,
        playingItemId: UUID? = null,
    ) =
        planSmartDownloads(
            episodes = episodes,
            downloads = downloads,
            inFlightIds = inFlightIds,
            keepReady = keepReady,
            since = since,
            finishedRecently = finishedRecently,
            playingItemId = playingItemId,
        )

    private fun download(downloadedAt: Long?, smart: Boolean) =
        SmartDownload(downloadedAt = downloadedAt, smartQueued = smart)

    private fun watchedEpisode(season: Int, number: Int, playedAt: Long) =
        episode(season, number, played = true, lastPlayedAt = playedAt)

    private fun episode(
        season: Int,
        number: Int,
        played: Boolean = false,
        missing: Boolean = false,
        canDownload: Boolean = true,
        lastPlayedAt: Long? = null,
    ) =
        FindroidEpisode(
            id = UUID.randomUUID(),
            name = "Episode $number",
            originalTitle = null,
            overview = "",
            indexNumber = number,
            indexNumberEnd = null,
            parentIndexNumber = season,
            sources = emptyList(),
            played = played,
            favorite = false,
            canPlay = true,
            canDownload = canDownload,
            runtimeTicks = 0L,
            playbackPositionTicks = 0L,
            premiereDate = null,
            seriesId = seriesId,
            seriesName = "Show",
            seasonId = UUID.nameUUIDFromBytes("season $season".toByteArray()),
            seasonName = null,
            communityRating = null,
            people = emptyList(),
            missing = missing,
            lastPlayedDate =
                lastPlayedAt?.let {
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault())
                },
            images = FindroidImages(),
            chapters = emptyList(),
            trickplayInfo = null,
        )
}
