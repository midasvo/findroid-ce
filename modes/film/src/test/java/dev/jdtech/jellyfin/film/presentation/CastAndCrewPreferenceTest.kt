package dev.jdtech.jellyfin.film.presentation

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import dev.jdtech.jellyfin.core.presentation.downloader.DownloadQueue
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.film.domain.VideoMetadataParser
import dev.jdtech.jellyfin.film.presentation.episode.EpisodeViewModel
import dev.jdtech.jellyfin.film.presentation.movie.MovieViewModel
import dev.jdtech.jellyfin.film.presentation.show.ShowViewModel
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidImages
import dev.jdtech.jellyfin.models.FindroidItemPerson
import dev.jdtech.jellyfin.models.FindroidItemPersonImage
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.utils.Downloader
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
import org.jellyfin.sdk.model.api.PersonKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Drives the real detail ViewModels with the "Display cast and crew" setting on and off. The phone
 * and TV screens both hide a section when its state is empty, so the state is what decides.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CastAndCrewPreferenceTest {
    private val itemId = UUID.fromString("11111111-1111-1111-1111-111111111111")

    private val actor = person("Actor", PersonKind.ACTOR)
    private val director = person("Director", PersonKind.DIRECTOR)
    private val writer = person("Writer", PersonKind.WRITER)
    private val people = listOf(actor, director, writer)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `movie shows cast and crew by default`() {
        val state = loadMovie(appPreferences(stored = null))

        assertEquals(listOf(actor), state.actors)
        assertEquals(director, state.director)
        assertEquals(listOf(writer), state.writers)
    }

    @Test
    fun `movie hides cast and crew when turned off`() {
        val state = loadMovie(appPreferences(stored = false))

        assertTrue(state.actors.isEmpty())
        assertNull(state.director)
        assertTrue(state.writers.isEmpty())
    }

    @Test
    fun `show shows cast and crew by default`() {
        val state = loadShow(appPreferences(stored = null))

        assertEquals(listOf(actor), state.actors)
        assertEquals(director, state.director)
        assertEquals(listOf(writer), state.writers)
    }

    @Test
    fun `show hides cast and crew when turned off`() {
        val state = loadShow(appPreferences(stored = false))

        assertTrue(state.actors.isEmpty())
        assertNull(state.director)
        assertTrue(state.writers.isEmpty())
    }

    @Test
    fun `episode shows actors by default`() {
        assertEquals(listOf(actor), loadEpisode(appPreferences(stored = null)).actors)
    }

    @Test
    fun `episode hides actors when turned off`() {
        assertTrue(loadEpisode(appPreferences(stored = false)).actors.isEmpty())
    }

    private fun loadMovie(preferences: AppPreferences) = runBlocking {
        val viewModel = MovieViewModel(repository(), preferences, VideoMetadataParser)
        viewModel.loadMovie(itemId)
        withTimeout(5_000) { viewModel.state.first { it.movie != null } }
    }

    private fun loadShow(preferences: AppPreferences) = runBlocking {
        val repository = repository()
        val viewModel =
            ShowViewModel(
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
                database = noDownloads(),
            )
        viewModel.loadShow(itemId)
        withTimeout(5_000) { viewModel.state.first { it.show != null } }
    }

    private fun loadEpisode(preferences: AppPreferences) = runBlocking {
        val viewModel = EpisodeViewModel(repository(), preferences, VideoMetadataParser)
        viewModel.loadEpisode(itemId)
        withTimeout(5_000) { viewModel.state.first { it.episode != null } }
    }

    private fun person(name: String, type: PersonKind) =
        FindroidItemPerson(
            id = UUID.nameUUIDFromBytes(name.toByteArray()),
            name = name,
            type = type,
            role = "",
            image = FindroidItemPersonImage(uri = null, blurHash = null),
        )

    private fun repository(): JellyfinRepository =
        Proxy.newProxyInstance(
            JellyfinRepository::class.java.classLoader,
            arrayOf(JellyfinRepository::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getMovie" -> movie()
                "getShow" -> show()
                "getEpisode" -> episode()
                "getNextUp",
                "getSeasons",
                "getEpisodes" -> emptyList<Any>()
                else -> throw NotImplementedError("JellyfinRepository.${method.name}")
            }
        } as JellyfinRepository

    private fun movie() =
        FindroidMovie(
            id = itemId,
            name = "Movie",
            originalTitle = null,
            overview = "",
            sources = emptyList(),
            played = false,
            favorite = false,
            canPlay = true,
            canDownload = false,
            runtimeTicks = 0L,
            playbackPositionTicks = 0L,
            premiereDate = null,
            people = people,
            genres = emptyList(),
            communityRating = null,
            officialRating = null,
            status = "",
            productionYear = null,
            endDate = null,
            trailer = null,
            images = FindroidImages(),
            chapters = emptyList(),
            trickplayInfo = null,
        )

    private fun show() =
        FindroidShow(
            id = itemId,
            name = "Show",
            originalTitle = null,
            overview = "",
            sources = emptyList(),
            seasons = emptyList(),
            played = false,
            favorite = false,
            canPlay = true,
            canDownload = false,
            unplayedItemCount = null,
            genres = emptyList(),
            people = people,
            runtimeTicks = 0L,
            communityRating = null,
            officialRating = null,
            status = "",
            productionYear = null,
            endDate = null,
            trailer = null,
            images = FindroidImages(),
        )

    private fun episode() =
        FindroidEpisode(
            id = itemId,
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
            canDownload = false,
            runtimeTicks = 0L,
            playbackPositionTicks = 0L,
            premiereDate = null,
            seriesId = itemId,
            seriesName = "Show",
            seasonId = itemId,
            seasonName = null,
            communityRating = null,
            people = people,
            missing = false,
            images = FindroidImages(),
            chapters = emptyList(),
            trickplayInfo = null,
        )

    /** Preferences where only the cast and crew switch may be stored; null means never touched. */
    private fun appPreferences(stored: Boolean?): AppPreferences {
        val preferences =
            Proxy.newProxyInstance(
                SharedPreferences::class.java.classLoader,
                arrayOf(SharedPreferences::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "getBoolean" ->
                        if (args!![0] == "pref_display_cast_and_crew" && stored != null) stored
                        else args[1]
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

    /** A database with nothing downloaded, for the show page's download counts. */
    private fun noDownloads(): ServerDatabaseDao =
        Proxy.newProxyInstance(
            ServerDatabaseDao::class.java.classLoader,
            arrayOf(ServerDatabaseDao::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getSeasonEpisodeCounts" -> emptyList<Any>()
                else -> throw NotImplementedError("ServerDatabaseDao.${method.name}")
            }
        } as ServerDatabaseDao

    private inline fun <reified T : Any> unused(): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
            throw NotImplementedError("${T::class.java.simpleName}.${method.name}")
        } as T
}
