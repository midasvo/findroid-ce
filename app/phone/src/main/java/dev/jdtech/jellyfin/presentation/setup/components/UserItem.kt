package dev.jdtech.jellyfin.presentation.setup.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.dummy.dummyUser
import dev.jdtech.jellyfin.models.User
import dev.jdtech.jellyfin.presentation.components.UserAvatar
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings

/**
 * A user in the Users grid: a 96 dp avatar with the name under it. [onRemoveClick], when set, adds
 * a small button at the top-right so removing a saved user is discoverable next to the long-press.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UserItem(
    user: User,
    baseUrl: String?,
    modifier: Modifier = Modifier,
    current: Boolean = false,
    subtitle: String? = null,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onRemoveClick: (() -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current

    Box(modifier = modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier.align(Alignment.Center)
                    .clip(MaterialTheme.shapes.medium)
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongClick()
                        },
                    )
                    .padding(MaterialTheme.spacings.small),
        ) {
            Box {
                UserAvatar(user = user, baseUrl = baseUrl, size = 96.dp, current = current)
                if (onRemoveClick != null) {
                    IconButton(
                        onClick = onRemoveClick,
                        modifier =
                            Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-4).dp).size(32.dp),
                    ) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_trash),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Text(
                text = user.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = MaterialTheme.spacings.small),
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
@Preview(showBackground = true)
private fun UserItemPreview() {
    FindroidTheme {
        UserItem(
            user = dummyUser,
            baseUrl = null,
            current = true,
            modifier = Modifier.width(128.dp),
            onRemoveClick = {},
        )
    }
}
