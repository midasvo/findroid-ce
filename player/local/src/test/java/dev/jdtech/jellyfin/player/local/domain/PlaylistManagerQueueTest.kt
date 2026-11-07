package dev.jdtech.jellyfin.player.local.domain

import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidImages
import dev.jdtech.jellyfin.models.FindroidSource
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.lang.reflect.Proxy
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** The episode list for the queue is fetched after the first item, not before it. */
class PlaylistManagerQueueTest {
    private val seriesId = UUID.randomUUID()
    private val seasonId = UUID.randomUUID()
    private val first = episode(1)
    private val second = episode(2)
    private val calls = mutableListOf<String>()

    @Test
    fun `the first episode is returned without fetching the season`() {
        val manager = PlaylistManager(fakeRepository())

        val item = runBlocking { manager.getInitialItem(first.id, BaseItemKind.EPISODE) }

        assertNotNull(item)
        assertEquals(first.id, item!!.itemId)
        assertEquals(false, "getEpisodes" in calls)
    }

    @Test
    fun `loading the queue fills in the upcoming episodes once`() {
        val manager = PlaylistManager(fakeRepository())
        runBlocking {
            manager.getInitialItem(first.id, BaseItemKind.EPISODE)
            manager.loadQueue()
            manager.loadQueue()
        }

        assertEquals(listOf(second.id), manager.upcomingItems().map { it.id })
        assertEquals(1, calls.count { it == "getEpisodes" })
    }

    @Test
    fun `the next item waits for the queue`() {
        val manager = PlaylistManager(fakeRepository())
        val next = runBlocking {
            manager.getInitialItem(first.id, BaseItemKind.EPISODE)
            manager.getNextPlayerItem()
        }

        assertEquals(second.id, next?.itemId)
    }

    private fun fakeRepository() =
        Proxy.newProxyInstance(
            JellyfinRepository::class.java.classLoader,
            arrayOf(JellyfinRepository::class.java),
        ) { _, method, _ ->
            calls.add(method.name)
            when (method.name) {
                "getEpisode" -> first
                "getEpisodes" -> listOf(first, second)
                "getSeasons" -> emptyList<Any>()
                "getPlaybackSources" -> listOf(source())
                else -> error("Unexpected call to ${method.name}")
            }
        } as JellyfinRepository

    private fun episode(number: Int) =
        FindroidEpisode(
            id = UUID.randomUUID(),
            name = "Episode $number",
            originalTitle = null,
            overview = "",
            indexNumber = number,
            indexNumberEnd = null,
            parentIndexNumber = 1,
            sources = emptyList(),
            played = false,
            favorite = false,
            canPlay = true,
            canDownload = false,
            runtimeTicks = 0L,
            playbackPositionTicks = 0L,
            premiereDate = null,
            seriesId = seriesId,
            seriesName = "Show",
            seasonId = seasonId,
            seasonName = "Season 1",
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
