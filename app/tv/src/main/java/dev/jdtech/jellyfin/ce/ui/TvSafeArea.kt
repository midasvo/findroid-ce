package dev.jdtech.jellyfin.ce.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * CE (G10, Rule 3): Android TV 10-foot UI safe-area spacing tokens.
 *
 * Android TV specifies a 5% overscan safety margin, which translates to 48 dp left and right
 * and 27 dp top and bottom on 1080p displays.
 */
object TvSafeArea {
    val horizontal = 48.dp
    val vertical = 27.dp

    val paddingValues = PaddingValues(horizontal = horizontal, vertical = vertical)
}

/** Applies TV safe area padding (48 dp horizontal, 27 dp vertical) to the composable. */
fun Modifier.tvSafeAreaPadding(): Modifier =
    this.padding(horizontal = TvSafeArea.horizontal, vertical = TvSafeArea.vertical)
