package dev.jdtech.jellyfin.presentation.utils

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass

/**
 * Window width class, the only thing layout decisions key on. See docs/tablet/4-design.md §1:
 * never device type, never `smallestScreenWidthDp`.
 *
 * Compact is below 600 dp (phones in portrait), Medium is 600–839 dp (tablets in portrait, phones
 * in landscape, split-screen halves), Expanded is 840 dp and up (tablets in landscape, desktop).
 */
enum class WidthClass {
    Compact,
    Medium,
    Expanded,
}

@Composable
fun currentWidthClass(): WidthClass {
    val sizeClass = currentWindowAdaptiveInfo().windowSizeClass
    return when {
        sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) ->
            WidthClass.Expanded
        sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) ->
            WidthClass.Medium
        else -> WidthClass.Compact
    }
}

/**
 * True below 480 dp of height: a phone in landscape. Such a window is Medium wide, but a tall hero
 * or a two-pane layout would eat the whole screen, so screens fall back to the Compact layout.
 */
@Composable
fun isCompactHeight(): Boolean =
    !currentWindowAdaptiveInfo()
        .windowSizeClass
        .isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)

/**
 * Layout tokens per width class (docs/tablet/4-design.md §2). Values that don't depend on the
 * class are plain properties; the rest take the class so a caller can't forget it.
 */
object Layout {
    /** Any column of running text: about 75 characters of bodyMedium. */
    val textMaxWidth: Dp = 720.dp

    /** Setup and login forms. */
    val formMaxWidth: Dp = 480.dp

    /** Settings lists. */
    val settingsMaxWidth: Dp = 640.dp

    /** Outer margin of a screen's content, outside the navigation rail. */
    fun margin(width: WidthClass): Dp =
        when (width) {
            WidthClass.Compact -> 16.dp
            WidthClass.Medium -> 24.dp
            WidthClass.Expanded -> 24.dp
        }

    /** Space between grid cells and between cards in a row. */
    fun gutter(width: WidthClass): Dp =
        when (width) {
            WidthClass.Compact -> 12.dp
            WidthClass.Medium -> 16.dp
            WidthClass.Expanded -> 16.dp
        }

    /** Width of a 16:9 card in a horizontal row. */
    fun cardHorizontal(width: WidthClass): Dp =
        when (width) {
            WidthClass.Compact -> 260.dp
            WidthClass.Medium -> 300.dp
            WidthClass.Expanded -> 320.dp
        }

    /** Width of a 2:3 poster card in a horizontal row. */
    fun cardVertical(width: WidthClass): Dp =
        when (width) {
            WidthClass.Compact -> 150.dp
            WidthClass.Medium -> 170.dp
            WidthClass.Expanded -> 180.dp
        }

    /** Minimum cell size of a poster grid; the grid keeps at least two columns. */
    fun gridMinCell(width: WidthClass): Dp =
        when (width) {
            WidthClass.Compact -> 160.dp
            WidthClass.Medium -> 170.dp
            WidthClass.Expanded -> 180.dp
        }

    /** Width of the poster beside the text in a detail page hero; 0 means no poster (Compact). */
    fun posterInHeader(width: WidthClass): Dp =
        when (width) {
            WidthClass.Compact -> 0.dp
            WidthClass.Medium -> 160.dp
            WidthClass.Expanded -> 200.dp
        }

    /**
     * Height of the backdrop hero on item pages at Medium and Expanded width (4-design.md §6): as
     * close to 16:9 as the window allows, never more than 55 % of its height, never below 320 dp.
     */
    fun heroHeight(width: Dp, windowHeight: Dp): Dp =
        maxOf(320.dp, minOf(width * 9f / 16f, windowHeight * 0.55f))
}

/**
 * True when an item page (Movie, Show, Episode) should use its wide hero layout: Medium or
 * Expanded width with room in height. A phone in landscape keeps the compact layout.
 */
@Composable
fun useWideItemLayout(): Boolean = currentWidthClass() != WidthClass.Compact && !isCompactHeight()
