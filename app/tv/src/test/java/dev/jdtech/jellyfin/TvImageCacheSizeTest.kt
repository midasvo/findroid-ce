package dev.jdtech.jellyfin

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The TV default for the image disk cache (#155). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TvImageCacheSizeTest {
    private val preferences =
        AppPreferences(
            ApplicationProvider.getApplicationContext<Application>()
                .getSharedPreferences("test", Context.MODE_PRIVATE)
        )

    @Test
    fun `without a size of its own, TV uses the TV default`() {
        preferences.useTvImageCacheSize()
        assertEquals(TV_IMAGE_CACHE_SIZE_MB, preferences.getValue(preferences.imageCacheSize))
    }

    @Test
    fun `a size the user picked stays, even the phone default`() {
        preferences.setValue(preferences.imageCacheSize, 20)
        preferences.useTvImageCacheSize()
        assertEquals(20, preferences.getValue(preferences.imageCacheSize))
    }
}
