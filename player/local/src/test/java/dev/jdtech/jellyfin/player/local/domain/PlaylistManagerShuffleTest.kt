package dev.jdtech.jellyfin.player.local.domain

import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidImages
import dev.jdtech.jellyfin.models.FindroidSeason
import dev.jdtech.jellyfin.models.FindroidSource
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.lang.reflect.Proxy
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistManagerShuffleTest {
    private val seriesId = UUID.randomUUID()

    @Test
    fun `shuffling a series queues episodes across regular seasons and starts from 0`() {
        val season0 = season(0)
        val season1 = season(1)
        val season2 = season(2)

        val epSpecial = episode(season0, 1)
        val ep1 = episode(season1, 1).copy(playbackPositionTicks = 50000000L)
        val ep2 = episode(season1, 2)
        val ep3 = episode(season2, 1)

        val showMap =
            mapOf(
                season0 to listOf(epSpecial),
                season1 to listOf(ep1, ep2),
                season2 to listOf(ep3),
            )

        val manager = PlaylistManager(fakeRepository(showMap))
        val initialItem =
            runBlocking {
                manager.getInitialItem(
                    itemId = seriesId,
                    itemKind = BaseItemKind.SERIES,
                    shuffle = true,
                )
            }

        assertNotNull(initialItem)
        assertEquals(0L, initialItem!!.playbackPosition)
        assertTrue(initialItem.itemId in setOf(ep1.id, ep2.id, ep3.id))

        // Total queue count should be 3 (excluding season 0 specials when regular seasons exist)
        val upcoming = manager.upcomingItems()
        assertEquals(2, upcoming.size)
        val allQueuedIds = upcoming.map { it.id }.toSet() + initialItem.itemId
        assertEquals(setOf(ep1.id, ep2.id, ep3.id), allQueuedIds)
    }

    private fun fakeRepository(show: Map<FindroidSeason, List<FindroidEpisode>>) =
        Proxy.newProxyInstance(
            JellyfinRepository::class.java.classLoader,
            arrayOf(JellyfinRepository::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getSeasons" -> show.keys.toList()
                "getEpisodes" -> show.entries.first { it.key.id == args[1] }.value
                "getPlaybackSources" -> listOf(source())
                else -> error("Unexpected call to ${method.name}")
            }
        } as JellyfinRepository

    private fun season(number: Int) =
        FindroidSeason(
            id = UUID.randomUUID(),
            name = "Season $number",
            seriesId = seriesId,
            seriesName = "Show",
            originalTitle = null,
            overview = "",
            sources = emptyList(),
            indexNumber = number,
            episodes = emptyList(),
            played = false,
            favorite = false,
            canPlay = true,
            canDownload = false,
            unplayedItemCount = null,
            images = FindroidImages(),
        )

    private fun episode(season: FindroidSeason, number: Int) =
        FindroidEpisode(
            id = UUID.randomUUID(),
            name = "Episode $number",
            originalTitle = null,
            overview = "",
            indexNumber = number,
            indexNumberEnd = null,
            parentIndexNumber = season.indexNumber,
            sources = emptyList(),
            played = false,
            favorite = false,
            canPlay = true,
            canDownload = false,
            runtimeTicks = 100000000L,
            playbackPositionTicks = 0L,
            premiereDate = null,
            seriesId = seriesId,
            seriesName = "Show",
            seasonId = season.id,
            seasonName = season.name,
            communityRating = null,
            people = emptyList(),
            missing = false,
            images = FindroidImages(),
            chapters = emptyList(),
            trickplayInfo = null,
        )

    private fun source() =
        FindroidSource(
            id = "source",
            name = "Source",
            type = FindroidSourceType.REMOTE,
            path = "http://server/video",
            size = 0L,
            mediaStreams = emptyList(),
        )
}
