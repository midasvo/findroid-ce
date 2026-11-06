package dev.jdtech.jellyfin.core.presentation.ephyra

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlin.random.Random

/** One loop of the screensaver's drift, ms. The specks rise 4 dp/s, so [SpeckRise] per loop. */
const val SPECK_LOOP_MILLIS = 20_000

private val SpeckRise = 80.dp

/** Part of its loop over which a drifting speck fades in, and again out, so it never jumps. */
private const val SPECK_FADE = 0.15f

/**
 * The bioluminescent specks (docs/identity/VISION.md §9), allowed only on empty states and the TV
 * screensaver: [count] circles over the whole draw area, radii 1–3 dp, alpha 15–55 % times
 * [alphaScale], alternately [primary] and [tertiary]. `Random(seed)` places them, so the field is
 * the same every time.
 *
 * [drift] is how far through its [SPECK_LOOP_MILLIS] loop the field is, 0 to 1, or null for a still
 * field. Each speck starts its loop at its own point, rises [SpeckRise] through its still position
 * and fades in and out at the ends.
 */
fun DrawScope.drawSpecks(
    count: Int,
    seed: Int,
    primary: Color,
    tertiary: Color,
    alphaScale: Float = 1f,
    drift: Float? = null,
) {
    val random = Random(seed)
    val rise = SpeckRise.toPx()
    repeat(count) { i ->
        val x = random.nextFloat() * size.width
        val y = random.nextFloat() * size.height
        val radius = (1f + random.nextFloat() * 2f).dp.toPx()
        val alpha = 0.15f + random.nextFloat() * 0.40f
        val phase = random.nextFloat()
        var offset = 0f
        var fade = 1f
        if (drift != null) {
            val t = (drift + phase) % 1f
            offset = rise * (0.5f - t)
            fade = (min(t, 1f - t) / SPECK_FADE).coerceAtMost(1f)
        }
        drawCircle(
            color = (if (i % 2 == 0) primary else tertiary).copy(alpha = alpha * alphaScale * fade),
            radius = radius,
            center = Offset(x, y + offset),
        )
    }
}
