package dev.jdtech.jellyfin.ce.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.MaterialTheme
import dev.jdtech.jellyfin.core.presentation.ephyra.AvatarColors
import dev.jdtech.jellyfin.core.presentation.ephyra.EphyraAvatar
import dev.jdtech.jellyfin.models.User
import dev.jdtech.jellyfin.models.primaryImageUrl

/** A user's avatar in the TV theme. [baseUrl] is the server the user belongs to. */
@Composable
fun UserAvatar(
    user: User,
    baseUrl: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    current: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val highest = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest
    val onHighest = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
    val colors =
        remember(scheme, highest, onHighest) {
            AvatarColors(
                containers =
                    listOf(
                        scheme.secondaryContainer to scheme.onSecondaryContainer,
                        scheme.tertiaryContainer to scheme.onTertiaryContainer,
                        scheme.primaryContainer to scheme.onPrimaryContainer,
                        highest to onHighest,
                    ),
                badgeContainer = scheme.primary,
                badgeContent = scheme.onPrimary,
                badgeRing = scheme.surface,
            )
        }
    EphyraAvatar(
        name = user.name,
        imageUrl = user.primaryImageUrl(baseUrl),
        seed = user.id,
        size = size,
        colors = colors,
        modifier = modifier,
        current = current,
    )
}
