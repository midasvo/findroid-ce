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
import org.junit.Assert.assertNull
import org.junit.Test

/** Unit tests for how [PlaylistManager] continues into the next season. */
class PlaylistManagerNextSeasonTest {
    private val seriesId = UUID.randomUUID()

    @Test
    fun `the last episode of a season is followed by the next season`() {
        val season1 = season(1)
        val season2 = season(2)
        val first = episode(season1, 1)
        val last = episode(season1, 2)
        val next = episode(season2, 1)
        val manager = playing(first, mapOf(season1 to listOf(first, last), season2 to listOf(next)))

        assertEquals(last.id, manager.nextAfter(first))
        assertEquals(next.id, manager.nextAfter(last))
    }

    @Test
    fun `playback keeps rolling over through later seasons`() {
        val season1 = season(1)
        val season2 = season(2)
        val season3 = season(3)
        val episode1 = episode(season1, 1)
        val episode2 = episode(season2, 1)
        val episode3 = episode(season3, 1)
        val manager =
            playing(
                episode1,
                mapOf(
                    season1 to listOf(episode1),
                    season2 to listOf(episode2),
                    season3 to listOf(episode3),
                ),
            )

        assertEquals(episode2.id, manager.nextAfter(episode1))
        assertEquals(episode3.id, manager.nextAfter(episode2))
    }

    @Test
    fun `seasons with only missing episodes are skipped`() {
        val season1 = season(1)
        val season2 = season(2)
        val season4 = season(4)
        val last = episode(season1, 1)
        val next = episode(season4, 1)
        val manager =
            playing(
                last,
                mapOf(
                    season1 to listOf(last),
                    season2 to listOf(episode(season2, 1, missing = true)),
                    season4 to listOf(next),
                ),
            )

        assertEquals(next.id, manager.nextAfter(last))
    }

    @Test
    fun `a special listed at the end of a season does not stop the rollover`() {
        val specials = season(0)
        val season1 = season(1)
        val season2 = season(2)
        val first = episode(season1, 1)
        val special = episode(specials, 1)
        val next = episode(season2, 1)
        // The server lists a special that airs after a season inside that season's episodes.
        val manager =
            playing(
                first,
                mapOf(
                    specials to listOf(special),
                    season1 to listOf(first, special),
                    season2 to listOf(next),
                ),
            )

        assertEquals(special.id, manager.nextAfter(first))
        assertEquals(next.id, manager.nextAfter(special))
    }

    @Test
    fun `specials do not roll over into season 1`() {
        val specials = season(0)
        val season1 = season(1)
        val special = episode(specials, 1)
        val manager =
            playing(
                special,
                mapOf(specials to listOf(special), season1 to listOf(episode(season1, 1))),
            )

        assertNull(manager.nextAfter(special))
    }

    @Test
    fun `the last episode of the last season ends playback`() {
        val season1 = season(1)
        val last = episode(season1, 1)
        val manager = playing(last, mapOf(season1 to listOf(last)))

        assertNull(manager.nextAfter(last))
    }

    /** A manager playing [start], from a server that lists [show] for each season. */
    private fun playing(
        start: FindroidEpisode,
        show: Map<FindroidSeason, List<FindroidEpisode>>,
    ): PlaylistManager {
        val manager = PlaylistManager(fakeRepository(show))
        runBlocking { manager.getInitialItem(start.id, BaseItemKind.EPISODE) }
        return manager
    }

    private fun PlaylistManager.nextAfter(episode: FindroidEpisode): UUID? {
        setCurrentMediaItemIndex(episode.id)
        return runBlocking { getNextPlayerItem() }?.itemId
    }

    private fun fakeRepository(show: Map<FindroidSeason, List<FindroidEpisode>>) =
        Proxy.newProxyInstance(
            JellyfinRepository::class.java.classLoader,
            arrayOf(JellyfinRepository::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getEpisode" -> show.values.flatten().first { it.id == args[0] }
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

    private fun episode(season: FindroidSeason, number: Int, missing: Boolean = false) =
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
            runtimeTicks = 0L,
            playbackPositionTicks = 0L,
            premiereDate = null,
            seriesId = seriesId,
            seriesName = "Show",
            seasonId = season.id,
            seasonName = season.name,
            communityRating = null,
            people = emptyList(),
            missing = missing,
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
