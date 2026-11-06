package dev.jdtech.jellyfin.ce.ui.focus

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ButtonBorder
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import dev.jdtech.jellyfin.core.presentation.ephyra.reducedMotion

/**
 * The TV focus ring (docs/identity/VISION.md §5, §6): 3 dp `primary`, drawn 2 dp outside the
 * element (tv-material's [Border.inset] grows the outline outwards), following its [shape].
 *
 * [inside] draws the ring just inside the edge instead, for cards whose parent clips them to its
 * own bounds (the settings groups), where a ring outside would be cut off.
 */
@Composable
fun ephyraFocusedBorder(
    shape: Shape = MaterialTheme.shapes.small,
    inside: Boolean = false,
): Border =
    Border(
        BorderStroke(3.dp, MaterialTheme.colorScheme.primary),
        inset = if (inside) (-1.5).dp else 2.dp,
        shape = shape,
    )

/**
 * The focus ring on a pill `Button` or a round `IconButton`: ring only. Buttons keep tv-material's
 * focused fill and get no halo and no scale.
 */
@Composable
fun ephyraButtonBorder(): ButtonBorder =
    ButtonDefaults.border(focusedBorder = ephyraFocusedBorder(CircleShape))

/** Alpha of the focus and seek-thumb halo on TV (`EphyraColor.glowTv`). */
const val EphyraFocusGlowAlpha = 0.55f

/**
 * The halo behind the focus ring: `primary` at [EphyraFocusGlowAlpha], 20 dp. Read from the
 * scheme, so it follows the ring's colour.
 */
@Composable
fun ephyraFocusedGlow(): Glow =
    Glow(
        elevationColor = MaterialTheme.colorScheme.primary.copy(alpha = EphyraFocusGlowAlpha),
        elevation = 20.dp,
    )

/**
 * Focused cards grow to this. Buttons and the seek bar keep 1. Under [reducedMotion] the card keeps
 * its size and the ring alone marks focus (VISION §6).
 *
 * tv-material animates the scale with its own spec (300 ms in 1.1.0); its `scale()` takes no
 * animation spec, so the timing isn't Ephyra's token.
 */
val EphyraFocusScale: Float
    @Composable get() = if (reducedMotion()) 1f else 1.06f
