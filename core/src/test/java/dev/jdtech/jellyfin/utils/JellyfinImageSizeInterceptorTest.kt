package dev.jdtech.jellyfin.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class JellyfinImageSizeInterceptorTest {
    private val poster = "https://demo.example/items/abc/Images/Primary?tag=t1"

    @Test
    fun `adds the size and quality to an item image`() {
        assertEquals("$poster&fillWidth=320&fillHeight=480&quality=90", poster.withImageSize(300, 450))
    }

    @Test
    fun `rounds up to a fixed size so one image has few cache entries`() {
        assertEquals(poster.withImageSize(641, 961), poster.withImageSize(900, 1200))
    }

    @Test
    fun `an exact step stays that step`() {
        assertEquals("$poster&fillWidth=640&quality=90", poster.withImageSize(640, null))
    }

    @Test
    fun `sizes above the largest step use the largest step`() {
        assertEquals("$poster&fillWidth=3840&quality=90", poster.withImageSize(7680, null))
    }

    @Test
    fun `a request with one known side only sends that side`() {
        assertEquals("$poster&fillHeight=240&quality=90", poster.withImageSize(null, 200))
    }

    @Test
    fun `a request without a size keeps the original`() {
        assertEquals(poster, poster.withImageSize(null, null))
    }

    @Test
    fun `covers backdrops, logos and chapter images and servers on a subpath`() {
        listOf(
                "https://demo.example/items/abc/Images/Backdrop/0?tag=t1",
                "https://demo.example/items/abc/Images/Logo?tag=t1",
                "http://demo.example:8096/jellyfin/Items/abc/Images/Chapter/3?tag=t1",
            )
            .forEach { assertEquals("$it&fillWidth=320&quality=90", it.withImageSize(300, null)) }
    }

    @Test
    fun `a url without a query gets one`() {
        val url = "https://demo.example/items/abc/Images/Primary"
        assertEquals("$url?fillWidth=320&quality=90", url.withImageSize(300, null))
    }

    @Test
    fun `downloaded images pass through`() {
        listOf(
                "images/abc/primary",
                "/data/user/0/nl.midasvo.findroid.ce/files/images/abc/primary",
                "file:///data/user/0/nl.midasvo.findroid.ce/files/items/abc/Images/Primary",
            )
            .forEach { assertEquals(it, it.withImageSize(300, 450)) }
    }

    @Test
    fun `adds the size to a user image`() {
        val url = "https://demo.example/Users/abc/Images/Primary?tag=t1"
        assertEquals("$url&fillWidth=160&fillHeight=160&quality=90", url.withImageSize(100, 100))
    }

    @Test
    fun `other urls pass through`() {
        listOf(
                "https://example.com/poster.jpg",
                "https://demo.example/videos/abc/Trickplay/320/0.jpg",
            )
            .forEach { assertEquals(it, it.withImageSize(300, 450)) }
    }

    @Test
    fun `a url that already has a size passes through`() {
        val url = "$poster&maxWidth=100"
        assertEquals(url, url.withImageSize(300, 450))
    }
}
