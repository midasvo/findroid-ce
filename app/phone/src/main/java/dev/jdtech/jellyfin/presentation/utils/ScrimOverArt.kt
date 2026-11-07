package dev.jdtech.jellyfin.presentation.utils

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.presentation.theme.EphyraColor

/** How far down the status-bar scrim reaches over a backdrop (4-design.md §3). */
val TopScrimOverArtHeight = 96.dp

/**
 * The top `scrimOverArt` gradient (VISION §3.3): black at 10 % at the top edge, fading to nothing
 * over [TopScrimOverArtHeight], so the status-bar icons stay readable over bright artwork.
 */
fun DrawScope.drawTopScrimOverArt() {
    val height = TopScrimOverArtHeight.toPx()
    drawRect(
        brush =
            Brush.verticalGradient(
                colors = listOf(EphyraColor.scrimOverArtTop, Color.Transparent),
                startY = 0f,
                endY = height,
            ),
        size = Size(size.width, minOf(height, size.height)),
    )
}
