package dev.jdtech.jellyfin.utils

import android.net.Uri
import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.size.Dimension
import coil3.size.pxOrElse

private const val IMAGE_QUALITY = 90

/**
 * Sizes that requests are rounded up to. The server caches every size it renders, and so does Coil,
 * so a fixed set keeps one image from filling both caches with near-identical copies.
 */
private val IMAGE_SIZE_STEPS = listOf(160, 240, 320, 480, 640, 960, 1280, 1920, 2560, 3840)

private val JELLYFIN_IMAGE_URL =
    Regex("""^https?://.+/items/[^/?]+/images/[^?]+(\?.*)?$""", RegexOption.IGNORE_CASE)

private val SIZED_IMAGE_URL =
    Regex("""[?&](fillWidth|fillHeight|maxWidth|maxHeight|width|height)=""", RegexOption.IGNORE_CASE)

/**
 * Asks the server for artwork at the size it will be shown, instead of the full-size original.
 *
 * Lives in the image loader so nothing else sees the sized URLs: downloads fetch the URLs in
 * `FindroidImages` directly and keep storing the original.
 */
class JellyfinImageSizeInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val data = chain.request.data
        if (data !is String && data !is Uri && data !is coil3.Uri) return chain.proceed()

        val url = data.toString()
        val sizedUrl = url.withImageSize(chain.size.width.pxOrNull(), chain.size.height.pxOrNull())
        if (sizedUrl == url) return chain.proceed()

        return chain.withRequest(chain.request.newBuilder().data(sizedUrl).build()).proceed()
    }

    private fun Dimension.pxOrNull(): Int? = pxOrElse { -1 }.takeIf { it > 0 }
}

/**
 * Adds a display size to a Jellyfin item image URL. Anything else, and a URL that already has a
 * size, comes back unchanged. So does a request without a size: it wants the original.
 *
 * Uses `fillWidth` and `fillHeight`, which return an image that covers the box, as the web client
 * does. With `maxWidth`, a wide backdrop cropped into a tall poster would come back too low to fill
 * it.
 */
internal fun String.withImageSize(width: Int?, height: Int?): String {
    if (width == null && height == null) return this
    if (!JELLYFIN_IMAGE_URL.matches(this) || SIZED_IMAGE_URL.containsMatchIn(this)) return this

    val params =
        listOfNotNull(
            width?.let { "fillWidth=${it.roundedToImageSizeStep()}" },
            height?.let { "fillHeight=${it.roundedToImageSizeStep()}" },
            "quality=$IMAGE_QUALITY",
        )
    return this + (if ('?' in this) "&" else "?") + params.joinToString("&")
}

private fun Int.roundedToImageSizeStep(): Int =
    IMAGE_SIZE_STEPS.firstOrNull { it >= this } ?: IMAGE_SIZE_STEPS.last()
