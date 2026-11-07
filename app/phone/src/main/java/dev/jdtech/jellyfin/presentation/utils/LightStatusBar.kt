package dev.jdtech.jellyfin.presentation.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import java.util.WeakHashMap

/**
 * Asks for light status-bar icons while [condition] holds, whatever the theme (4-design.md §3,
 * T12): over a backdrop the theme's dark icons in light mode disappear into dark artwork.
 *
 * Requests are counted per window, so two item pages that overlap during a screen transition
 * can't undo each other: the icons go back to the theme's default once no screen asks any more.
 */
@Composable
fun LightStatusBarWhile(condition: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = remember(view) { view.context.findWindow() } ?: return
    // enableEdgeToEdge() picks dark icons for a light configuration and light ones for a dark one.
    val themeDefaultLightAppearance = !isSystemInDarkTheme()

    DisposableEffect(window, condition, themeDefaultLightAppearance) {
        if (!condition) return@DisposableEffect onDispose {}
        val controller = WindowCompat.getInsetsController(window, view)
        requests[window] = (requests[window] ?: 0) + 1
        controller.isAppearanceLightStatusBars = false
        onDispose {
            val remaining = (requests[window] ?: 1) - 1
            if (remaining > 0) {
                requests[window] = remaining
            } else {
                requests.remove(window)
                controller.isAppearanceLightStatusBars = themeDefaultLightAppearance
            }
        }
    }
}

/**
 * Light status-bar icons while an item page's backdrop is behind the status bar ([hasArtwork] is
 * false while the page is loading or shows an error): until the top bar
 * has faded to the solid background (`rememberTopBarBackgroundAlpha`). The header is always taller
 * than that fade distance, so this is also "while the header is under the bar".
 */
@Composable
fun LightStatusBarOverArtwork(hasArtwork: Boolean, topBarBackgroundAlpha: () -> Float) {
    val overArtwork by
        remember(topBarBackgroundAlpha) { derivedStateOf { topBarBackgroundAlpha() < 0.5f } }
    LightStatusBarWhile(hasArtwork && overArtwork)
}

private val requests = WeakHashMap<Window, Int>()

private tailrec fun Context.findWindow(): Window? =
    when (this) {
        is Activity -> window
        is ContextWrapper -> baseContext.findWindow()
        else -> null
    }
