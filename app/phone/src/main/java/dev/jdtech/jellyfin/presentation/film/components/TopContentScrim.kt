package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/**
 * Fades scrolling content out behind the status bar and a floating header, so it can't run
 * through them. Blends into the background, so it stays invisible until content scrolls under it.
 */
@Composable
fun TopContentScrim(height: Dp, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.background
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(height)
                .background(Brush.verticalGradient(0.8f to color, 1f to Color.Transparent))
    )
}
