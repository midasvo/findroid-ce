package dev.jdtech.jellyfin.film.presentation.show

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import dev.jdtech.jellyfin.core.presentation.downloader.DownloadQueue
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.lang.reflect.Proxy
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** CE (#141): the show ViewModel only asks the server for a theme song when the setting is on. */
@OptIn(ExperimentalCoroutinesApi::class)
class ThemeSongPreferenceTest {
    private val showId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val url = "https://jellyfin.test/Audio/2/stream?static=true"
    private var lookups = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `off by default, so no lookup`() {
        val viewModel = viewModel(appPreferences(stored = null))
        viewModel.loadThemeSong(showId)

        assertEquals(0, lookups)
        assertNull(viewModel.state.value.themeSongUrl)
    }

    @Test
    fun `turned on, the song lands in the state`() {
        val viewModel = viewModel(appPreferences(stored = true))
        viewModel.loadThemeSong(showId)

        assertEquals(1, lookups)
        assertEquals(url, viewModel.state.value.themeSongUrl)
    }

    private fun viewModel(preferences: AppPreferences): ShowViewModel {
        val repository = repository()
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
        )
    }

    private fun repository(): JellyfinRepository =
        Proxy.newProxyInstance(
            JellyfinRepository::class.java.classLoader,
            arrayOf(JellyfinRepository::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getThemeSongUrl" -> url.also { lookups++ }
                else -> throw NotImplementedError("JellyfinRepository.${method.name}")
            }
        } as JellyfinRepository

    /** Preferences where only the theme music switch may be stored; null means never touched. */
    private fun appPreferences(stored: Boolean?): AppPreferences {
        val preferences =
            Proxy.newProxyInstance(
                SharedPreferences::class.java.classLoader,
                arrayOf(SharedPreferences::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "getBoolean" ->
                        if (args!![0] == "pref_tv_theme_music" && stored != null) stored
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

    private inline fun <reified T : Any> unused(): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
            throw NotImplementedError("${T::class.java.simpleName}.${method.name}")
        } as T
}
