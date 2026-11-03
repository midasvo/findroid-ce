package dev.jdtech.jellyfin.ce.presentation.screensaver

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.jdtech.jellyfin.settings.domain.AppPreferences
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
}
