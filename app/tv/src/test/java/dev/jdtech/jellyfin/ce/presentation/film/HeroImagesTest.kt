package dev.jdtech.jellyfin.ce.presentation.film

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.size.Scale
import coil3.size.Size
import dev.jdtech.jellyfin.core.presentation.dummy.dummyEpisode
import dev.jdtech.jellyfin.core.presentation.dummy.dummyMovie
import dev.jdtech.jellyfin.imageMemoryCacheBytes
import dev.jdtech.jellyfin.models.FindroidImages
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The prefetch around the focused card and the hero's requests (#155). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HeroImagesTest {
    private val base = "https://demo.example/items/abc/Images"
    private val movie =
        dummyMovie.copy(
            images =
                FindroidImages(
                    backdrop = Uri.parse("$base/Backdrop/0?tag=b"),
                    logo = Uri.parse("$base/Logo?tag=l"),
                )
        )
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val images = HeroImages(context, ImageLoader(context), 1344, 480, 120)

    @Test
    fun `neighbours are the nearest cards on both sides, next first`() {
        assertEquals(listOf(6, 4, 7, 3, 8, 2), neighbourIndices(5, 20, 3))
    }

    @Test
    fun `the first card only has neighbours after it`() {
        assertEquals(listOf(1, 2, 3), neighbourIndices(0, 20, 3))
    }

    @Test
    fun `the last card only has neighbours before it`() {
        assertEquals(listOf(18, 17, 16), neighbourIndices(19, 20, 3))
    }

    @Test
    fun `a short row has as many neighbours as there are cards`() {
        assertEquals(listOf(2, 0), neighbourIndices(1, 3, 3))
        assertEquals(emptyList<Int>(), neighbourIndices(0, 1, 3))
    }

    @Test
    fun `the backdrop is cached under its sized url`() {
        val request = images.backdrop(movie)!!
        val url = "$base/Backdrop/0?tag=b&fillWidth=1920&quality=90"
        assertEquals(url, request.data)
        assertEquals(url, request.memoryCacheKey)
        assertEquals(emptyMap<String, String>(), request.memoryCacheKeyExtras)
    }

    @Test
    fun `hero and prefetch ask for the same backdrop and logo`() {
        listOf(HeroImages::backdrop, HeroImages::logo).forEach { request ->
            val first = request(images, movie)!!
            // The hero adds its placeholder, which must not change what is cached.
            val hero = first.newBuilder().placeholderMemoryCacheKey("previous").build()
            val prefetch = request(images, movie)!!
            assertEquals(prefetch.memoryCacheKey, hero.memoryCacheKey)
            assertEquals(prefetch.data, hero.data)
            assertEquals(prefetch.size(), hero.size())
        }
    }

    @Test
    fun `a detail page's placeholder is the hero's cached backdrop`() {
        val hero = images.backdrop(movie)!!
        val key = images.backdropCacheKey(movie.images.backdrop)!!
        assertEquals(hero.memoryCacheKey, key.key)
        assertEquals(hero.memoryCacheKeyExtras, key.extras)
        assertNull(images.backdropCacheKey(null))
    }

    @Test
    fun `the backdrop is decoded at the size it is shown`() {
        val request = images.backdrop(movie)!!
        assertEquals(Size(1344, 756), request.size())
        assertEquals(Scale.FILL, request.scale)
        assertEquals(Precision.INEXACT, request.precision)
    }

    @Test
    fun `the logo fits the logo box`() {
        val request = images.logo(movie)!!
        assertEquals("$base/Logo?tag=l&fillWidth=480&quality=90", request.memoryCacheKey)
        assertEquals(Size(480, 120), request.size())
        assertEquals(Scale.FIT, request.scale)
    }

    @Test
    fun `an episode uses its show's backdrop and logo`() {
        val episode =
            dummyEpisode.copy(
                images =
                    FindroidImages(
                        backdrop = Uri.parse("$base/Backdrop/0?tag=episode"),
                        showBackdrop = Uri.parse("$base/Backdrop/0?tag=show"),
                        showLogo = Uri.parse("$base/Logo?tag=show"),
                    )
            )
        assertEquals("$base/Backdrop/0?tag=show&fillWidth=1920&quality=90", heroBackdropUrl(episode, 1344))
        assertEquals("$base/Logo?tag=show&fillWidth=480&quality=90", heroLogoUrl(episode, 480))
    }

    @Test
    fun `an item without artwork has no requests`() {
        val bare = dummyMovie.copy(images = FindroidImages())
        assertNull(images.backdrop(bare))
        assertNull(images.logo(bare))
    }

    @Test
    fun `the memory cache is a share of the app memory, with a cap`() {
        assertEquals(57L * 1024 * 1024, imageMemoryCacheBytes(192))
        assertEquals(96L * 1024 * 1024, imageMemoryCacheBytes(512))
    }

    private fun ImageRequest.size(): Size = runBlocking { sizeResolver.size() }
}
