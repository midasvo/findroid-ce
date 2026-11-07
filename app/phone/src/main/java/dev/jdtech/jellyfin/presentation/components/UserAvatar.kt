package dev.jdtech.jellyfin.presentation.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.presentation.dummy.dummyUser
import dev.jdtech.jellyfin.core.presentation.ephyra.AvatarColors
import dev.jdtech.jellyfin.core.presentation.ephyra.EphyraAvatar
import dev.jdtech.jellyfin.models.User
import dev.jdtech.jellyfin.models.primaryImageUrl
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme

/** A user's avatar in the phone theme. [baseUrl] is the server the user belongs to. */
@Composable
fun UserAvatar(
    user: User,
    baseUrl: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    current: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val colors =
        remember(scheme) {
            AvatarColors(
                containers =
                    listOf(
                        scheme.secondaryContainer to scheme.onSecondaryContainer,
                        scheme.tertiaryContainer to scheme.onTertiaryContainer,
                        scheme.primaryContainer to scheme.onPrimaryContainer,
                        scheme.surfaceContainerHighest to scheme.onSurfaceVariant,
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

@Preview
@Composable
private fun UserAvatarInitialsPreview() {
    FindroidTheme { UserAvatar(user = dummyUser, baseUrl = null, size = 56.dp) }
}

@Preview
@Composable
private fun UserAvatarCurrentPreview() {
    FindroidTheme { UserAvatar(user = dummyUser, baseUrl = null, size = 56.dp, current = true) }
}

@Preview
@Composable
private fun UserAvatarPicturePreview() {
    FindroidTheme {
        UserAvatar(
            user = dummyUser.copy(primaryImageTag = "tag"),
            baseUrl = "https://demo.example",
            size = 56.dp,
            current = true,
        )
    }
}
