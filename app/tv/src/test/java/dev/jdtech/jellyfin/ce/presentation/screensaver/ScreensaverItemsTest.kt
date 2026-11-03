package dev.jdtech.jellyfin.ce.presentation.screensaver

import android.app.Application
import android.net.Uri
import dev.jdtech.jellyfin.models.FindroidImages
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.SortBy
import dev.jdtech.jellyfin.models.SortOrder
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.lang.reflect.Proxy
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric for android.net.Uri in FindroidImages.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ScreensaverItemsTest {
    private val withBackdrop = movie("With backdrop", Uri.parse("https://jellyfin.test/b.jpg"))
    private val withoutBackdrop = movie("Without backdrop", null)

    private val calls = mutableListOf<Pair<String, Array<Any?>>>()

    @Test
    fun `random asks the server for random items with a backdrop`() = runBlocking {
        val items = loadScreensaverItems(repository { listOf(withBackdrop) }, "random")

        assertEquals(listOf(withBackdrop), items)
        val (name, args) = calls.single()
        assertEquals("getRandomItemsWithBackdrop", name)
        assertEquals(listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES), args[0])
    }

    @Test
    fun `an unknown source falls back to random`() = runBlocking {
        loadScreensaverItems(repository { emptyList() }, "something else")

        assertEquals("getRandomItemsWithBackdrop", calls.single().first)
    }

    @Test
    fun `recently added asks for the newest first and drops items without a backdrop`() =
        runBlocking {
            val items =
                loadScreensaverItems(
                    repository { listOf(withoutBackdrop, withBackdrop) },
                    SCREENSAVER_SOURCE_RECENTLY_ADDED,
                )

            assertEquals(listOf(withBackdrop), items)
            val (name, args) = calls.single()
            assertEquals("getItems", name)
            assertTrue(args.contains(SortBy.DATE_ADDED))
            assertTrue(args.contains(SortOrder.DESCENDING))
        }

    @Test
    fun `a failing server gives an empty list`() = runBlocking {
        val items = loadScreensaverItems(repository { throw IllegalStateException("offline") }, "random")

        assertTrue(items.isEmpty())
    }

    @Test(expected = CancellationException::class)
    fun `cancellation is not swallowed`(): Unit = runBlocking {
        loadScreensaverItems(repository { throw CancellationException("stopped") }, "random")
    }

    /** A repository whose item calls are recorded and answered by [answer]. */
    private fun repository(answer: () -> List<FindroidItem>): JellyfinRepository =
        Proxy.newProxyInstance(
            JellyfinRepository::class.java.classLoader,
            arrayOf(JellyfinRepository::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getItems",
                "getRandomItemsWithBackdrop" -> {
                    calls += method.name to (args ?: emptyArray())
                    answer()
                }
                else -> throw NotImplementedError("JellyfinRepository.${method.name}")
            }
        } as JellyfinRepository

    private fun movie(name: String, backdrop: Uri?) =
        FindroidMovie(
            id = UUID.nameUUIDFromBytes(name.toByteArray()),
            name = name,
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
            people = emptyList(),
            genres = emptyList(),
            communityRating = null,
            officialRating = null,
            status = "",
            productionYear = null,
            endDate = null,
            trailer = null,
            images = FindroidImages(backdrop = backdrop),
            chapters = emptyList(),
            trickplayInfo = null,
        )
}
