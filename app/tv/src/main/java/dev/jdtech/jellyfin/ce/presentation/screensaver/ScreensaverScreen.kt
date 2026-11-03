package dev.jdtech.jellyfin.ce.presentation.screensaver

import android.text.format.DateFormat
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.size.Scale
import dev.jdtech.jellyfin.api.JellyfinApi
import dev.jdtech.jellyfin.ce.presentation.film.heroBackdropUrl
import dev.jdtech.jellyfin.ce.presentation.film.heroLogoUrl
import dev.jdtech.jellyfin.ce.ui.tvSafeAreaPadding
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.SortBy
import dev.jdtech.jellyfin.models.SortOrder
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber

@Composable
fun ScreensaverScreen(
    jellyfinApi: JellyfinApi,
    repository: JellyfinRepository,
    appPreferences: AppPreferences,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.roundToPx() }

    val source by
        appPreferences
            .observe(appPreferences.tvScreensaverSource)
            .collectAsStateWithLifecycle(
                initialValue = appPreferences.getValue(appPreferences.tvScreensaverSource)
            )
    val intervalStr by
        appPreferences
            .observe(appPreferences.tvScreensaverInterval)
            .collectAsStateWithLifecycle(
                initialValue = appPreferences.getValue(appPreferences.tvScreensaverInterval)
            )
    val showClock by
        appPreferences
            .observe(appPreferences.tvScreensaverShowClock)
            .collectAsStateWithLifecycle(
                initialValue = appPreferences.getValue(appPreferences.tvScreensaverShowClock)
            )

    val intervalSeconds = remember(intervalStr) { intervalStr.toIntOrNull() ?: 20 }

    var items by remember { mutableStateOf<List<FindroidItem>>(emptyList()) }
    var currentIndex by remember { mutableIntStateOf(0) }

    val userId = jellyfinApi.userId

    // Load items when screensaver starts or source changes
    LaunchedEffect(userId, source) {
        if (userId == null) {
            items = emptyList()
            return@LaunchedEffect
        }
        items =
            withContext(Dispatchers.IO) {
                try {
                    val isRecent = source == "recently_added"
                    val fetched =
                        repository
                            .getItems(
                                parentId = null,
                                includeTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                                recursive = true,
                                sortBy = if (isRecent) SortBy.DATE_ADDED else SortBy.NAME,
                                sortOrder =
                                    if (isRecent) SortOrder.DESCENDING else SortOrder.ASCENDING,
                                limit = if (isRecent) 100 else 200,
                            )
                            .filter { it.images.backdrop != null }

                    val candidates =
                        if (fetched.isNotEmpty()) {
                            fetched
                        } else {
                            // Fallback to any items if user has no movies/series
                            repository
                                .getItems(
                                    parentId = null,
                                    recursive = true,
                                    limit = 100,
                                )
                                .filter { it.images.backdrop != null }
                        }

                    if (isRecent) candidates else candidates.shuffled()
                } catch (e: Exception) {
                    Timber.w(e, "Screensaver: failed to load library artwork")
                    emptyList()
                }
            }
        currentIndex = 0
    }

    // Interval ticker to cycle items
    LaunchedEffect(items, intervalSeconds) {
        if (items.isEmpty()) return@LaunchedEffect
        while (isActive) {
            delay(intervalSeconds * 1000L)
            currentIndex = (currentIndex + 1) % items.size
        }
    }

    val currentItem = items.getOrNull(currentIndex)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Crossfade(
            targetState = currentItem,
            animationSpec = tween(durationMillis = 1000),
            label = "ScreensaverItemCrossfade",
            modifier = Modifier.fillMaxSize(),
        ) { item ->
            if (item != null) {
                ScreensaverArtwork(
                    item = item,
                    screenWidthPx = screenWidthPx,
                    screenHeightPx = screenHeightPx,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                )
            }
        }

        if (showClock) {
            ScreensaverClock(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .tvSafeAreaPadding()
            )
        }
    }
}

@Composable
private fun ScreensaverArtwork(
    item: FindroidItem,
    screenWidthPx: Int,
    screenHeightPx: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val backdropUrl = remember(item.id, screenWidthPx) { heroBackdropUrl(item, screenWidthPx) }
    val logoWidthPx = remember(screenWidthPx) { screenWidthPx / 4 }
    val logoUrl = remember(item.id, logoWidthPx) { heroLogoUrl(item, logoWidthPx) }

    Box(modifier = modifier.fillMaxSize()) {
        // One image decoded at screen size
        AsyncImage(
            model =
                ImageRequest.Builder(context)
                    .data(backdropUrl)
                    .size(screenWidthPx, screenHeightPx)
                    .scale(Scale.FILL)
                    .precision(Precision.INEXACT)
                    .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // Gradient scrim at the bottom for title/logo legibility
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .height(200.dp)
                    .align(Alignment.BottomStart)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                        )
                    )
        )

        // Bottom-start title or logo within TV safe area
        Box(
            modifier =
                Modifier.align(Alignment.BottomStart)
                    .tvSafeAreaPadding()
        ) {
            var logoFailed by remember(item.id) { mutableStateOf(false) }

            if (logoUrl != null && !logoFailed) {
                AsyncImage(
                    model =
                        ImageRequest.Builder(context)
                            .data(logoUrl)
                            .size(logoWidthPx, logoWidthPx / 2)
                            .scale(Scale.FIT)
                            .precision(Precision.INEXACT)
                            .build(),
                    contentDescription = item.name,
                    contentScale = ContentScale.Fit,
                    onError = { logoFailed = true },
                    modifier = Modifier.width(320.dp).heightIn(max = 100.dp),
                )
            } else {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ScreensaverClock(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var timeString by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val format = DateFormat.getTimeFormat(context)
        while (isActive) {
            timeString = format.format(Date())
            delay(1000L)
        }
    }

    Text(
        text = timeString,
        style = MaterialTheme.typography.headlineMedium,
        color = Color.White.copy(alpha = 0.9f),
        modifier = modifier,
    )
}
