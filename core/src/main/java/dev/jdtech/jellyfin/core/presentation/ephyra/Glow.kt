package dev.jdtech.jellyfin.core.presentation.ephyra

import android.graphics.BlurMaskFilter
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp

/**
 * The bioluminescent halo (docs/identity/VISION.md §6). Draws a blurred copy of [shape] in
 * [color] behind the content. [enabled] false draws nothing, for light theme and reduced motion.
 *
 * The halo extends outside the element's bounds, so nothing between it and the screen may clip.
 * Pass `MaterialTheme.colorScheme.primary.copy(alpha = …)` as [color], so dynamic colour is
 * followed; `EphyraColor.glow` and `glowTv` document the default values.
 */
fun Modifier.ephyraGlow(shape: Shape, color: Color, blur: Dp, enabled: Boolean = true): Modifier =
    if (!enabled) this
    else
        drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val paint =
                Paint().apply {
                    this.color = color
                    asFrameworkPaint().maskFilter =
                        BlurMaskFilter(blur.toPx(), BlurMaskFilter.Blur.NORMAL)
                }
            onDrawBehind { drawIntoCanvas { it.drawOutline(outline, paint) } }
        }

/**
 * False in light theme, where the halo has nothing to glow against. Pass
 * `MaterialTheme.colorScheme.surface` (core has no Material 3 dependency to read it here).
 */
fun glowEnabled(surface: Color): Boolean = surface.luminance() <= 0.5f
