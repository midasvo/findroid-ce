package dev.jdtech.jellyfin.core.presentation.theme

import androidx.compose.ui.graphics.Color

/** Brand values outside the Material scheme. See docs/identity/VISION.md §3.3. */
data object EphyraColor {
    /** Halo colour on phone: primary at 45 % alpha. */
    val glow = Color(0x734FD8F0)
    /** Halo colour on TV: primary at 55 % alpha, a little stronger for 3 m. */
    val glowTv = Color(0x8C4FD8F0)
    /** onSurface in dark and the icon's shine. Never a surface. */
    val pale = Color(0xFFE4FBFF)
    /** Top and bottom stops of the scrim drawn over artwork before text. */
    val scrimOverArtTop = Color(0x1A000000)
    val scrimOverArtBottom = Color(0x99000000)
}
