package dev.jdtech.jellyfin.film.presentation.show

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import dev.jdtech.jellyfin.core.presentation.downloader.DownloadQueue
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidImages
import dev.jdtech.jellyfin.models.FindroidSeason
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.models.SeasonEpisodeCounts
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.lang.reflect.Proxy
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * CE (#252): the show page asks for the series' episodes once, not once per season, and takes the
 * downloaded counts from Room. A favourite toggle doesn't load the page again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShowLoadingTest {
    private val showId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val season1 = UUID.fromString("22222222-2222-2222-2222-222222222221")
    private val season2 = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val calls = mutableListOf<String>()
    private val episodeSeasonIds = mutableListOf<UUID?>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `download counts come from one series request and Room`() = runBlocking {
        val viewModel = viewModel(SeasonEpisodeCounts(season1, total = 1, downloaded = 1))
        viewModel.loadShow(showId)
        val state = withTimeout(5_000) { viewModel.state.first { it.seasonDownloadInfo.isNotEmpty() } }

        assertEquals(listOf<UUID?>(null), episodeSeasonIds)
        assertEquals(SeasonDownloadInfo(downloadedCount = 1, totalCount = 3), state.seasonDownloadInfo[season1])
        assertEquals(SeasonDownloadInfo(downloadedCount = 0, totalCount = 2), state.seasonDownloadInfo[season2])
        assertTrue(state.hasDownloads)
        assertNull(state.error)
    }

    @Test
    fun `nothing downloaded`() = runBlocking {
        val viewModel = viewModel()
        viewModel.loadShow(showId)
        val state = withTimeout(5_000) { viewModel.state.first { it.seasonDownloadInfo.isNotEmpty() } }

        assertEquals(false, state.hasDownloads)
        assertEquals(0, state.seasonDownloadInfo[season1]?.downloadedCount)
    }

    @Test
    fun `favourite toggles in place`() = runBlocking {
        val viewModel = viewModel()
        viewModel.loadShow(showId)
        withTimeout(5_000) { viewModel.state.first { it.seasonDownloadInfo.isNotEmpty() } }
        calls.clear()

        viewModel.onAction(ShowAction.MarkAsFavorite)
        val state = withTimeout(5_000) { viewModel.state.first { it.show?.favorite == true } }

        assertEquals(listOf("markAsFavorite"), calls)
        assertEquals(2, state.seasons.size)
    }

    private fun viewModel(vararg counts: SeasonEpisodeCounts): ShowViewModel {
        val repository = repository()
        val preferences = appPreferences()
        return ShowViewModel(
            repository = repository,
            appPreferences = preferences,
            downloader = unused(),
            downloadQueue =
                DownloadQueue(
                    downloader = unused(),
                    appPreferences = preferences,
                    repositoryProvider = { repository },
                    database = unused<ServerDatabaseDao>(),
                    context = ContextWrapper(null) as Context,
                ),
            database = database(counts.toList()),
        )
    }

    private fun repository(): JellyfinRepository =
        Proxy.newProxyInstance(
            JellyfinRepository::class.java.classLoader,
            arrayOf(JellyfinRepository::class.java),
        ) { _, method, args ->
            synchronized(calls) { calls.add(method.name) }
            when (method.name) {
                "getShow" -> show()
                "getNextUp" -> emptyList<Any>()
                "getSeasons" -> listOf(season(season1, 1), season(season2, 2))
                "getEpisodes" -> {
                    synchronized(calls) { episodeSeasonIds.add(args!![1] as UUID?) }
                    List(3) { episode(season1) } + List(2) { episode(season2) }
                }
                "markAsFavorite" -> Unit
                else -> throw NotImplementedError("JellyfinRepository.${method.name}")
            }
        } as JellyfinRepository

    private fun database(counts: List<SeasonEpisodeCounts>): ServerDatabaseDao =
        Proxy.newProxyInstance(
            ServerDatabaseDao::class.java.classLoader,
            arrayOf(ServerDatabaseDao::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getSeasonEpisodeCounts" -> counts
                else -> throw NotImplementedError("ServerDatabaseDao.${method.name}")
            }
        } as ServerDatabaseDao

    private fun show() =
        FindroidShow(
            id = showId,
            name = "Show",
            originalTitle = null,
            overview = "",
            sources = emptyList(),
            seasons = emptyList(),
            played = false,
            favorite = false,
            canPlay = true,
            canDownload = true,
            unplayedItemCount = null,
            genres = emptyList(),
            people = emptyList(),
            runtimeTicks = 0L,
            communityRating = null,
            officialRating = null,
            status = "",
            productionYear = null,
            endDate = null,
            trailer = null,
            images = FindroidImages(),
        )

    private fun season(id: UUID, index: Int) =
        FindroidSeason(
            id = id,
            name = "Season $index",
            seriesId = showId,
            seriesName = "Show",
            originalTitle = null,
            overview = "",
            sources = emptyList(),
            indexNumber = index,
            episodes = emptyList(),
            played = false,
            favorite = false,
            canPlay = true,
            canDownload = true,
            unplayedItemCount = null,
            images = FindroidImages(),
        )

    private fun episode(seasonId: UUID) =
        FindroidEpisode(
            id = UUID.randomUUID(),
            name = "Episode",
            originalTitle = null,
            overview = "",
            indexNumber = 1,
            indexNumberEnd = null,
            parentIndexNumber = 1,
            sources = emptyList(),
            played = false,
            favorite = false,
            canPlay = true,
            canDownload = true,
            runtimeTicks = 0L,
            playbackPositionTicks = 0L,
            premiereDate = null,
            seriesId = showId,
            seriesName = "Show",
            seasonId = seasonId,
            seasonName = null,
            communityRating = null,
            people = emptyList(),
            missing = false,
            images = FindroidImages(),
            chapters = emptyList(),
            trickplayInfo = null,
        )

    private fun appPreferences(): AppPreferences {
        val preferences =
            Proxy.newProxyInstance(
                SharedPreferences::class.java.classLoader,
                arrayOf(SharedPreferences::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "getBoolean",
                    "getString",
                    "getInt",
                    "getLong",
                    "getFloat",
                    "getStringSet" -> args!![1]
                    else -> throw UnsupportedOperationException("SharedPreferences.${method.name}")
                }
            } as SharedPreferences
        return AppPreferences(preferences)
    }

    private inline fun <reified T : Any> unused(): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
            throw NotImplementedError("${T::class.java.simpleName}.${method.name}")
        } as T
}
