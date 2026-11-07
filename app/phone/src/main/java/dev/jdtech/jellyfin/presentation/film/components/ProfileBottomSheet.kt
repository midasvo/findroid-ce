package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.dummy.dummyUser
import dev.jdtech.jellyfin.models.User
import dev.jdtech.jellyfin.presentation.components.UserAvatar
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.setup.R as SetupR
import dev.jdtech.jellyfin.setup.presentation.users.UsersAction
import dev.jdtech.jellyfin.setup.presentation.users.UsersEvent
import dev.jdtech.jellyfin.setup.presentation.users.UsersState
import dev.jdtech.jellyfin.setup.presentation.users.UsersViewModel
import dev.jdtech.jellyfin.utils.ObserveAsEvents
import java.util.UUID

/** The profile sheet (#245): who is signed in, one tap to another user, add user, settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileBottomSheet(
    baseUrl: String?,
    onSwitchServer: () -> Unit,
    onPublicUserClick: (username: String) -> Unit,
    onAddUser: () -> Unit,
    onSettings: () -> Unit,
    onUserSwitched: () -> Unit,
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(),
    viewModel: UsersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(true) { viewModel.loadUsers() }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is UsersEvent.NavigateToHome -> onUserSwitched()
        }
    }

    ProfileBottomSheetLayout(
        state = state,
        baseUrl = baseUrl,
        onSwitchServer = onSwitchServer,
        onPublicUserClick = onPublicUserClick,
        onAddUser = onAddUser,
        onSettings = onSettings,
        onUserClick = { userId -> viewModel.onAction(UsersAction.OnUserClick(userId)) },
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileBottomSheetLayout(
    state: UsersState,
    baseUrl: String?,
    onSwitchServer: () -> Unit,
    onPublicUserClick: (String) -> Unit,
    onAddUser: () -> Unit,
    onSettings: () -> Unit,
    onUserClick: (UUID) -> Unit,
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(),
) {
    val currentUser = state.users.firstOrNull { it.id == state.currentUserId }
    val otherUsers = state.users.filter { it.id != state.currentUserId }

    ModalBottomSheet(onDismissRequest = onDismissRequest, sheetState = sheetState) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(
                        start = MaterialTheme.spacings.medium,
                        end = MaterialTheme.spacings.medium,
                        bottom = MaterialTheme.spacings.default,
                    ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
        ) {
            if (currentUser != null) {
                UserAvatar(user = currentUser, baseUrl = baseUrl, size = 96.dp, current = true)
                Text(
                    text = currentUser.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = state.serverName ?: "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier.clip(MaterialTheme.shapes.small)
                            .clickable(onClick = onSwitchServer)
                            .padding(
                                horizontal = MaterialTheme.spacings.small,
                                vertical = MaterialTheme.spacings.extraSmall,
                            ),
                )
            }

            if (otherUsers.isNotEmpty() || state.publicUsers.isNotEmpty()) {
                Text(
                    text = stringResource(SetupR.string.ce_users_switch_user),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth().padding(top = MaterialTheme.spacings.small),
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = MaterialTheme.spacings.small),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
                ) {
                    items(items = otherUsers, key = { "u-" + it.id }) { user ->
                        ProfileSheetUser(
                            user = user,
                            baseUrl = baseUrl,
                            onClick = { onUserClick(user.id) },
                        )
                    }
                    if (otherUsers.isNotEmpty() && state.publicUsers.isNotEmpty()) {
                        item(key = "divider") { VerticalDivider(modifier = Modifier.height(48.dp)) }
                    }
                    items(items = state.publicUsers, key = { "p-" + it.id }) { user ->
                        ProfileSheetUser(
                            user = user,
                            baseUrl = baseUrl,
                            subtitle = stringResource(SetupR.string.ce_users_sign_in),
                            onClick = { onPublicUserClick(user.name) },
                        )
                    }
                }
            }

            OutlinedButton(onClick = onAddUser, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(SetupR.string.users_btn_add_user))
            }
            OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(CoreR.string.title_settings))
            }
        }
    }
}

@Composable
private fun ProfileSheetUser(
    user: User,
    baseUrl: String?,
    onClick: () -> Unit,
    subtitle: String? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier.width(72.dp)
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onClick)
                .padding(vertical = MaterialTheme.spacings.extraSmall),
    ) {
        UserAvatar(user = user, baseUrl = baseUrl, size = 48.dp)
        Text(
            text = user.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = MaterialTheme.spacings.extraSmall),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
private fun ProfileBottomSheetPreview() {
    val other = User(id = UUID.randomUUID(), name = "Alice", serverId = "")
    FindroidTheme {
        ProfileBottomSheetLayout(
            state =
                UsersState(
                    users = listOf(dummyUser, other),
                    publicUsers = listOf(User(id = UUID.randomUUID(), name = "Bob", serverId = "")),
                    serverName = "Jellyfin",
                    currentUserId = dummyUser.id,
                ),
            baseUrl = null,
            onSwitchServer = {},
            onPublicUserClick = {},
            onAddUser = {},
            onSettings = {},
            onUserClick = {},
            onDismissRequest = {},
            sheetState = rememberStandardBottomSheetState(initialValue = SheetValue.Expanded),
        )
    }
}
