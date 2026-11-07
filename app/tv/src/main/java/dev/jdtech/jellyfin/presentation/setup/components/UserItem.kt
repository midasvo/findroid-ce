package dev.jdtech.jellyfin.presentation.setup.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.jdtech.jellyfin.ce.ui.components.UserAvatar
import dev.jdtech.jellyfin.ce.ui.focus.EphyraFocusScale
import dev.jdtech.jellyfin.ce.ui.focus.ephyraFocusedBorder
import dev.jdtech.jellyfin.ce.ui.focus.ephyraFocusedGlow
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.dummy.dummyUser
import dev.jdtech.jellyfin.core.presentation.ephyra.EphyraMotion
import dev.jdtech.jellyfin.core.presentation.ephyra.reducedMotion
import dev.jdtech.jellyfin.models.User
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings

val UserItemAvatarSize = 144.dp

/**
 * One face on the Users screen (docs/identity/VISION.md §6): the focused avatar takes the ring, the
 * halo and the 1.06 scale. [current] marks the signed-in user; [caption] sits under the name (the
 * "Sign in" hint of a user who is not saved yet). [onFocused] reports when the avatar gets focus.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UserItem(
    user: User,
    modifier: Modifier = Modifier,
    baseUrl: String? = null,
    current: Boolean = false,
    caption: String? = null,
    onClick: (User) -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    onFocused: (User) -> Unit = {},
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val focusedScale = EphyraFocusScale
    // The badge sits on the avatar's corner, which the circle-clipped Surface would cut off, so it
    // is drawn outside the Surface and scaled with the same factor.
    val scale by
        animateFloatAsState(
            targetValue = if (focused) focusedScale else 1f,
            animationSpec = tween(if (reducedMotion()) 0 else EphyraMotion.DURATION_SHORT),
            label = "user_scale",
        )

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.width(160.dp)) {
        Box(
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
        ) {
            Surface(
                onClick = { onClick(user) },
                onLongClick = onLongClick,
                shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
                border = ClickableSurfaceDefaults.border(focusedBorder = ephyraFocusedBorder(CircleShape)),
                glow = ClickableSurfaceDefaults.glow(focusedGlow = ephyraFocusedGlow()),
                scale = ClickableSurfaceScale.None,
                colors =
                    ClickableSurfaceDefaults.colors(
                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    ),
                modifier =
                    Modifier.size(UserItemAvatarSize)
                        .then(
                            if (focusRequester != null) Modifier.focusRequester(focusRequester)
                            else Modifier
                        )
                        .onFocusChanged {
                        focused = it.hasFocus || it.isFocused
                        if (focused) onFocused(user)
                    },
            ) {
                UserAvatar(user = user, baseUrl = baseUrl, size = UserItemAvatarSize)
            }
            if (current) {
                val badgeSize = UserItemAvatarSize * 0.28f
                Box(
                    modifier =
                        Modifier.align(Alignment.BottomEnd)
                            .zIndex(1f)
                            .size(badgeSize + 4.dp)
                            .background(MaterialTheme.colorScheme.surface, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier =
                            Modifier.size(badgeSize)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(CoreR.drawable.ic_check),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimary),
                            modifier = Modifier.size(badgeSize * 0.6f),
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(MaterialTheme.spacings.medium))
        Text(
            text = user.name,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Preview
@Composable
private fun UserComponentPreview() {
    FindroidTheme { UserItem(user = dummyUser, current = true, caption = "Sign in") }
}
