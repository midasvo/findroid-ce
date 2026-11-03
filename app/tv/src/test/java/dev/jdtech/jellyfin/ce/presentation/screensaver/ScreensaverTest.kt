package dev.jdtech.jellyfin.ce.presentation.screensaver

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.jdtech.jellyfin.models.FindroidImages
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.net.URI
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ScreensaverTest {

    private lateinit var context: Context
    private lateinit var appPreferences: AppPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val sp = context.getSharedPreferences("test_screensaver_prefs", Context.MODE_PRIVATE)
        appPreferences = AppPreferences(sp)
    }

    @Test
    fun `screensaver preferences have expected defaults`() {
        assertEquals("random", appPreferences.getValue(appPreferences.tvScreensaverSource))
        assertEquals("20", appPreferences.getValue(appPreferences.tvScreensaverInterval))
        assertTrue(appPreferences.getValue(appPreferences.tvScreensaverShowClock))
    }

    @Test
    fun `screensaver preferences update correctly`() {
        appPreferences.setValue(appPreferences.tvScreensaverSource, "recently_added")
        assertEquals("recently_added", appPreferences.getValue(appPreferences.tvScreensaverSource))

        appPreferences.setValue(appPreferences.tvScreensaverInterval, "30")
        assertEquals("30", appPreferences.getValue(appPreferences.tvScreensaverInterval))

        appPreferences.setValue(appPreferences.tvScreensaverShowClock, false)
        assertFalse(appPreferences.getValue(appPreferences.tvScreensaverShowClock))
    }

    @Test
    fun `screensaver filters candidate items without backdrops`() {
        val withBackdrop =
            createDummyMovie(
                name = "Movie With Backdrop",
                backdropUri = URI.create("https://example.com/backdrop.jpg"),
            )
        val withoutBackdrop = createDummyMovie(name = "Movie Without Backdrop", backdropUri = null)

        val candidates = listOf(withBackdrop, withoutBackdrop)
        val filtered = candidates.filter { it.images.backdrop != null }

        assertEquals(1, filtered.size)
        assertEquals("Movie With Backdrop", filtered.first().name)
    }

    private fun createDummyMovie(name: String, backdropUri: URI?): FindroidMovie {
        return FindroidMovie(
            id = UUID.randomUUID(),
            name = name,
            originalTitle = null,
            overview = "",
            played = false,
            favorite = false,
            canPlay = true,
            canDownload = false,
            runtimeTicks = 0L,
            playbackPositionTicks = 0L,
            unplayedItemCount = null,
            sources = emptyList(),
            images =
                FindroidImages(
                    primary = null,
                    backdrop = backdropUri,
                    logo = null,
                    thumb = null,
                    banner = null,
                    disc = null,
                    showBackdrop = null,
                    showLogo = null,
                ),
            chapters = emptyList(),
        )
    }
}
