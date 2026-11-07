package dev.jdtech.jellyfin.presentation.film.components

import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.ephyra.backdropCrossfadeMillis
import dev.jdtech.jellyfin.core.presentation.theme.EphyraColor
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.presentation.theme.darkScheme
import dev.jdtech.jellyfin.presentation.theme.lightScheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.presentation.utils.Layout
import dev.jdtech.jellyfin.presentation.utils.WidthClass
import dev.jdtech.jellyfin.presentation.utils.drawTopScrimOverArt
import dev.jdtech.jellyfin.presentation.utils.parallaxLayoutModifier
import dev.jdtech.jellyfin.presentation.utils.rememberSafePadding
import dev.jdtech.jellyfin.utils.copyOnLongClick

/** Text on artwork (VISION §3.4): white, and white at 70 % for secondary lines. */
private val OnArt = Color.White
private val OnArtSecondary = Color.White.copy(alpha = 0.7f)

/** Room left at the top of the hero for the status bar and the back/home buttons. */
private val TopBarClearance = 72.dp

/**
 * The backdrop hero of an item page at Medium and Expanded width (docs/tablet/4-design.md §6):
 * a full-width backdrop, cropped from the top centre, with the poster (or an episode's still) and
 * the item's text column in its bottom part.
 *
 * The hero is artwork, so its contents are drawn in the dark scheme whatever the app theme, with
 * white text over `scrimOverArt` (VISION §3.4). In the light theme the hero is a dark band above
 * the light page; in the dark theme its bottom fades into the page.
 *
 * The box grows past [Layout.heroHeight] when the column needs more room, so nothing is cut off.
 */
@Composable
fun ItemHeroHeader(
    item: FindroidItem,
    scrollState: ScrollState,
    widthClass: WidthClass,
    title: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val safePadding = rememberSafePadding()
    val margin = Layout.margin(widthClass)
    val heroScheme = heroColorScheme()

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val windowHeight =
            with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
        val heroHeight = Layout.heroHeight(maxWidth, windowHeight)
        val pageBackground = heroScheme.background

        Box(modifier = Modifier.fillMaxWidth().heightIn(min = heroHeight).clipToBounds()) {
            HeroBackdrop(
                item = item,
                scrollState = scrollState,
                modifier = Modifier.matchParentSize(),
            )
            Canvas(modifier = Modifier.matchParentSize()) {
                // Left edge: 60 % → 0 over the left 60 % of the width, behind the poster and text.
                drawRect(
                    brush =
                        Brush.horizontalGradient(
                            colors = listOf(EphyraColor.scrimOverArtBottom, Color.Transparent),
                            startX = 0f,
                            endX = size.width * 0.6f,
                        )
                )
                // Bottom: scrimOverArt 10 % → 60 %, then into the hero's own background.
                drawRect(
                    brush =
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.3f to EphyraColor.scrimOverArtTop,
                            0.8f to EphyraColor.scrimOverArtBottom,
                            1f to pageBackground,
                        )
                )
                drawTopScrimOverArt()
            }
            MaterialTheme(colorScheme = heroScheme) {
                Row(
                    modifier =
                        Modifier.align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(
                                start = safePadding.start + margin,
                                top = safePadding.top + TopBarClearance,
                                end = safePadding.end + margin,
                                bottom = margin,
                            ),
                    horizontalArrangement = Arrangement.spacedBy(margin),
                ) {
                    HeroPoster(item = item, widthClass = widthClass)
                    Column(modifier = Modifier.weight(1f, fill = false).widthIn(max = Layout.textMaxWidth)) {
                        CompositionLocalProvider(LocalContentColor provides OnArt) {
                            title()
                            content()
                        }
                    }
                }
            }
        }
    }
}

/** Title of the hero: `headlineLarge` at Expanded, `headlineMedium` at Medium. */
@Composable
fun HeroTitle(text: String, widthClass: WidthClass) {
    Text(
        text = text,
        modifier = Modifier.copyOnLongClick(text),
        color = OnArt,
        overflow = TextOverflow.Ellipsis,
        maxLines = 3,
        style = heroTitleStyle(widthClass),
    )
}

/** A secondary line next to the title: an original title or an episode's season and number. */
@Composable
fun HeroSubtitle(text: String, style: TextStyle = MaterialTheme.typography.bodyMedium) {
    Text(
        text = text,
        modifier = Modifier.copyOnLongClick(text),
        color = OnArtSecondary,
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
        style = style,
    )
}

/**
 * The metadata line: plain texts (year, runtime, rating…) followed by the community rating star,
 * built the same way as on the compact screens.
 */
@Composable
fun HeroMetadataLine(texts: List<String>, communityRating: Float?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
        verticalAlignment = Alignment.Bottom,
    ) {
        texts.forEach { text ->
            Text(
                text = text,
                modifier = Modifier.copyOnLongClick(text),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        communityRating?.let { rating ->
            val ratingText = "%.1f".format(rating)
            Row(
                modifier = Modifier.copyOnLongClick(ratingText),
                verticalAlignment = Alignment.Bottom,
            ) {
                Icon(
                    painter = painterResource(CoreR.drawable.ic_star),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                )
                Spacer(Modifier.width(MaterialTheme.spacings.extraSmall))
                Text(text = ratingText, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Chips in the hero keep their own on-container colour instead of the white text on art. */
@Composable
fun HeroChips(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant,
        content = content,
    )
}

@Composable
private fun heroTitleStyle(widthClass: WidthClass): TextStyle =
    when (widthClass) {
        WidthClass.Expanded -> MaterialTheme.typography.headlineLarge
        else -> MaterialTheme.typography.headlineMedium
    }

@Composable
private fun HeroPoster(item: FindroidItem, widthClass: WidthClass) {
    val posterWidth = Layout.posterInHeader(widthClass)
    if (item is FindroidEpisode) {
        ItemPoster(
            item = item,
            direction = Direction.HORIZONTAL,
            modifier = Modifier.width(posterWidth * 1.6f).clip(MaterialTheme.shapes.small),
        )
    } else {
        ItemPoster(
            item = item,
            direction = Direction.VERTICAL,
            modifier = Modifier.width(posterWidth).clip(MaterialTheme.shapes.small),
        )
    }
}

/**
 * The backdrop. An episode shows its still beside the text, so the hero behind it uses the show's
 * backdrop and only falls back to the still.
 */
@Composable
private fun HeroBackdrop(item: FindroidItem, scrollState: ScrollState, modifier: Modifier) {
    val context = LocalContext.current
    val sources =
        remember(item.id, item.images) {
            when (item) {
                    is FindroidEpisode -> listOf(item.images.showBackdrop, item.images.primary)
                    else -> listOf(item.images.backdrop)
                }
                .filterNotNull()
                .distinct()
                .map { uri ->
                    // Downloaded images are stored as paths relative to the files directory.
                    if (uri.scheme == null) {
                        Uri.Builder()
                            .appendEncodedPath("${context.filesDir}")
                            .appendEncodedPath(uri.path)
                            .build()
                    } else uri
                }
        }
    var index by remember(sources) { mutableIntStateOf(0) }

    AsyncImage(
        model =
            ImageRequest.Builder(context)
                .data(sources.getOrNull(index))
                .crossfade(backdropCrossfadeMillis())
                .build(),
        contentDescription = null,
        modifier = modifier.parallaxLayoutModifier(scrollState = scrollState, rate = 2),
        placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceContainer),
        onError = { if (index < sources.lastIndex) index++ },
        contentScale = ContentScale.Crop,
        alignment = Alignment.TopCenter,
    )
}

/** The dark counterpart of the current scheme: dynamic when the app uses dynamic colour. */
@Composable
private fun heroColorScheme(): ColorScheme {
    val current = MaterialTheme.colorScheme
    return when {
        current.background.luminance() < 0.5f -> current
        current === lightScheme -> darkScheme
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            dynamicDarkColorScheme(LocalContext.current)
        else -> darkScheme
    }
}
