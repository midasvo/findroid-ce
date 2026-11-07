package dev.jdtech.jellyfin.core.presentation.ephyra

import java.text.BreakIterator
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.BasicText
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import dev.jdtech.jellyfin.core.R
import dev.jdtech.jellyfin.core.presentation.theme.OutfitFamily
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

/**
 * Colours of an [EphyraAvatar]. Core has no Material 3, so the screens build this from their theme.
 *
 * @param containers (container, onContainer) pairs; a user's fallback picks one by id.
 */
@Immutable
data class AvatarColors(
    val containers: List<Pair<Color, Color>>,
    val badgeContainer: Color,
    val badgeContent: Color,
    val badgeRing: Color,
)

/** Which of [count] colours a user gets. Deterministic, so a user keeps their colour. */
fun avatarColorIndex(seed: UUID, count: Int): Int = abs(seed.hashCode().toLong()).toInt() % count

/**
 * The initials shown when a user has no picture: the first letter of the first and the last word of
 * [name], or "?" when there is none. Letters are whole graphemes, so an emoji is not cut in half.
 */
fun avatarInitials(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return "?"
    val picked = if (words.size == 1) listOf(words.first()) else listOf(words.first(), words.last())
    return picked.joinToString("") { it.firstGrapheme() }.uppercase(Locale.getDefault())
}

private fun String.firstGrapheme(): String {
    val iterator = BreakIterator.getCharacterInstance()
    iterator.setText(this)
    val end = iterator.next()
    return if (end == BreakIterator.DONE) this else substring(0, end)
}

/**
 * A user's round avatar: their profile picture, or their initials on a colour from [colors] until
 * it loads and when it fails. [current] adds a check badge for the signed-in user.
 */
@Composable
fun EphyraAvatar(
    name: String,
    imageUrl: String?,
    seed: UUID,
    size: Dp,
    colors: AvatarColors,
    modifier: Modifier = Modifier,
    current: Boolean = false,
    contentDescription: String? = null,
) {
    val (container, onContainer) = colors.containers[avatarColorIndex(seed, colors.containers.size)]
    val semanticsModifier =
        if (contentDescription != null) {
            Modifier.semantics { this.contentDescription = contentDescription }
        } else {
            Modifier
        }

    Box(modifier = modifier.size(size).then(semanticsModifier)) {
        Box(
            modifier = Modifier.size(size).clip(CircleShape).background(container),
            contentAlignment = Alignment.Center,
        ) {
            val fontSize = with(LocalDensity.current) { (size * 0.42f).toSp() }
            // Once the picture is in, the initials go: a picture with transparent areas would
            // otherwise show them through.
            var pictureShown by remember(imageUrl) { mutableStateOf(false) }
            if (!pictureShown) {
                BasicText(
                    text = avatarInitials(name),
                    style =
                        TextStyle(
                            color = onContainer,
                            fontFamily = OutfitFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = fontSize,
                        ),
                )
            }
            if (imageUrl != null) {
                val crossfadeMillis = if (reducedMotion()) 0 else EphyraMotion.DURATION_MEDIUM
                val request =
                    ImageRequest.Builder(LocalContext.current)
                        .data(imageUrl)
                        .crossfade(crossfadeMillis)
                        .build()
                // The initials stay underneath: a failed or slow picture shows them, a loaded one
                // covers them.
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onSuccess = { pictureShown = true },
                    modifier = Modifier.size(size),
                )
            }
        }
        if (current) {
            val badgeSize = maxOf(16.dp, size * 0.28f)
            val ring = 2.dp
            Box(
                modifier =
                    Modifier.align(Alignment.BottomEnd)
                        .size(badgeSize + ring * 2)
                        .background(colors.badgeRing, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier.size(badgeSize).background(colors.badgeContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colors.badgeContent),
                        modifier = Modifier.size(badgeSize * 0.6f),
                    )
                }
            }
        }
    }
}
