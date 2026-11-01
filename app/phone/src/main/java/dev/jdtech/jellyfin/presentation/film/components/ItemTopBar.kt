package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.presentation.utils.rememberSafePadding

/** Scroll distance over which the top bar goes from see-through to solid. */
private val BackgroundFadeStart = 120.dp
private val BackgroundFadeEnd = 180.dp

/**
 * [backgroundAlpha] fades in a solid background behind the buttons, so scrolled content doesn't
 * run through them. See [rememberTopBarBackgroundAlpha].
 */
@Composable
fun ItemTopBar(
    hasBackButton: Boolean,
    hasHomeButton: Boolean,
    onBackClick: () -> Unit = {},
    onHomeClick: () -> Unit = {},
    backgroundAlpha: () -> Float = { 0f },
    content: @Composable (RowScope.() -> Unit) = {},
) {
    val safePadding = rememberSafePadding()
    val backgroundColor = MaterialTheme.colorScheme.background

    Box(modifier = Modifier.fillMaxWidth()) {
        // Keeps the status bar readable on top of artwork.
        Box(
            modifier =
                Modifier.matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent)
                        )
                    )
        )
        Box(
            modifier =
                Modifier.matchParentSize()
                    .graphicsLayer { alpha = backgroundAlpha() }
                    .background(
                        Brush.verticalGradient(
                            0.85f to backgroundColor,
                            1f to Color.Transparent,
                        )
                    )
        )
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(
                        start = safePadding.start + MaterialTheme.spacings.small,
                        top = safePadding.top + MaterialTheme.spacings.small,
                        end = safePadding.end + MaterialTheme.spacings.small,
                        bottom = MaterialTheme.spacings.small,
                    )
        ) {
            if (hasBackButton) {
                IconButton(
                    onClick = onBackClick,
                    colors =
                        IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.5f),
                            contentColor = Color.White,
                        ),
                ) {
                    Icon(
                        painter = painterResource(CoreR.drawable.ic_arrow_left),
                        contentDescription = stringResource(CoreR.string.navigate_back),
                    )
                }
            }
            if (hasHomeButton) {
                IconButton(
                    onClick = onHomeClick,
                    colors =
                        IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.5f),
                            contentColor = Color.White,
                        ),
                ) {
                    Icon(
                        painter = painterResource(CoreR.drawable.ic_home),
                        contentDescription = stringResource(CoreR.string.title_home),
                    )
                }
            }
            content()
        }
    }
}

@Composable
fun rememberTopBarBackgroundAlpha(scrollState: ScrollState): () -> Float {
    val fade = rememberBackgroundFade()
    return remember(scrollState, fade) { { fade(scrollState.value) } }
}

@Composable
fun rememberTopBarBackgroundAlpha(lazyListState: LazyListState): () -> Float {
    val fade = rememberBackgroundFade()
    return remember(lazyListState, fade) {
        {
            if (lazyListState.firstVisibleItemIndex > 0) 1f
            else fade(lazyListState.firstVisibleItemScrollOffset)
        }
    }
}

@Composable
private fun rememberBackgroundFade(): (Int) -> Float {
    val density = LocalDensity.current
    return remember(density) {
        val start = with(density) { BackgroundFadeStart.toPx() }
        val end = with(density) { BackgroundFadeEnd.toPx() }
        val fade: (Int) -> Float = { scrolled ->
            ((scrolled - start) / (end - start)).coerceIn(0f, 1f)
        }
        fade
    }
}

@Composable
@Preview(showBackground = true)
private fun ItemTopBarPreview() {
    FindroidTheme { ItemTopBar(hasBackButton = true, hasHomeButton = true) }
}
