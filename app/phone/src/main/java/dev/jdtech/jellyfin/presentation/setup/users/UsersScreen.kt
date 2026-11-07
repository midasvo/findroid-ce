package dev.jdtech.jellyfin.presentation.setup.users

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jdtech.jellyfin.api.JellyfinApi
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.dummy.dummyUser
import dev.jdtech.jellyfin.models.User
import dev.jdtech.jellyfin.presentation.setup.components.RootLayout
import dev.jdtech.jellyfin.presentation.setup.components.UserItem
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.setup.R as SetupR
import dev.jdtech.jellyfin.setup.presentation.users.UsersAction
import dev.jdtech.jellyfin.setup.presentation.users.UsersEvent
import dev.jdtech.jellyfin.setup.presentation.users.UsersState
import dev.jdtech.jellyfin.setup.presentation.users.UsersViewModel
import dev.jdtech.jellyfin.utils.ObserveAsEvents
import java.util.UUID

@Composable
fun UsersScreen(
    navigateToHome: () -> Unit,
    onChangeServerClick: () -> Unit,
    onAddClick: () -> Unit,
    onBackClick: () -> Unit,
    onPublicUserClick: (String) -> Unit,
    showBack: Boolean = true,
    viewModel: UsersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(true) { viewModel.loadUsers() }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is UsersEvent.NavigateToHome -> navigateToHome()
        }
    }

    UsersScreenLayout(
        state = state,
        baseUrl = JellyfinApi.getInstance(LocalContext.current).api.baseUrl,
        showBack = showBack,
        onAction = { action ->
            when (action) {
                is UsersAction.OnChangeServerClick -> onChangeServerClick()
                is UsersAction.OnAddClick -> onAddClick()
                is UsersAction.OnBackClick -> onBackClick()
                is UsersAction.OnPublicUserClick -> onPublicUserClick(action.username)
                else -> Unit
            }
            viewModel.onAction(action)
        },
    )
}

@Composable
private fun UsersScreenLayout(
    state: UsersState,
    baseUrl: String? = null,
    showBack: Boolean = true,
    onAction: (UsersAction) -> Unit,
) {
    var openDeleteDialog by remember { mutableStateOf(false) }
    var selectedUser by remember { mutableStateOf<User?>(null) }

    RootLayout {
        Column(
            modifier =
                Modifier.padding(horizontal = 24.dp)
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .align(Alignment.Center)
        ) {
            if (showBack) {
                Spacer(modifier = Modifier.height(64.dp))
            } else {
                Spacer(modifier = Modifier.weight(0.2f))
                Image(
                    painter = painterResource(id = CoreR.drawable.ic_banner),
                    contentDescription = null,
                    modifier = Modifier.width(250.dp).align(Alignment.CenterHorizontally),
                )
                Spacer(modifier = Modifier.height(32.dp))
            }
            Text(
                text = stringResource(SetupR.string.users),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = state.serverName ?: "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))
            if (state.users.isEmpty() && state.publicUsers.isEmpty()) {
                Text(
                    text = stringResource(SetupR.string.users_no_users),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.weight(1f))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 112.dp),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
                    contentPadding = PaddingValues(bottom = 96.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    items(state.users, key = { "u-" + it.id }) { user ->
                        UserItem(
                            user = user,
                            baseUrl = baseUrl,
                            current = user.id == state.currentUserId,
                            onClick = { onAction(UsersAction.OnUserClick(userId = user.id)) },
                            onLongClick = {
                                selectedUser = user
                                openDeleteDialog = true
                            },
                            onRemoveClick = {
                                selectedUser = user
                                openDeleteDialog = true
                            },
                        )
                    }
                    if (state.publicUsers.isNotEmpty()) {
                        item(key = "public-header", span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text =
                                    stringResource(
                                        SetupR.string.ce_users_other_on_server,
                                        state.serverName ?: "",
                                    ),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(top = MaterialTheme.spacings.small),
                            )
                        }
                        items(state.publicUsers, key = { "p-" + it.id }) { user ->
                            UserItem(
                                user = user,
                                baseUrl = baseUrl,
                                subtitle = stringResource(SetupR.string.ce_users_sign_in),
                                onClick = {
                                    onAction(UsersAction.OnPublicUserClick(username = user.name))
                                },
                            )
                        }
                    }
                }
            }
        }
        if (showBack) {
            IconButton(
                onClick = { onAction(UsersAction.OnBackClick) },
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Icon(
                    painter = painterResource(CoreR.drawable.ic_arrow_left),
                    contentDescription = null,
                )
            }
        }
        IconButton(
            onClick = { onAction(UsersAction.OnChangeServerClick) },
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 8.dp),
        ) {
            Icon(painter = painterResource(CoreR.drawable.ic_server), contentDescription = null)
        }
        ExtendedFloatingActionButton(
            onClick = { onAction(UsersAction.OnAddClick) },
            icon = { Icon(painterResource(CoreR.drawable.ic_plus), contentDescription = null) },
            text = { Text(text = stringResource(SetupR.string.users_btn_add_user)) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
        )

        if (openDeleteDialog && selectedUser != null) {
            AlertDialog(
                title = { Text(text = stringResource(SetupR.string.remove_user_dialog)) },
                text = {
                    Text(
                        text =
                            stringResource(
                                SetupR.string.remove_user_dialog_text,
                                selectedUser!!.name,
                            )
                    )
                },
                onDismissRequest = { openDeleteDialog = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            openDeleteDialog = false
                            onAction(UsersAction.OnDeleteUser(selectedUser!!.id))
                        }
                    ) {
                        Text(text = stringResource(SetupR.string.confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { openDeleteDialog = false }) {
                        Text(text = stringResource(SetupR.string.cancel))
                    }
                },
            )
        }
    }
}

@PreviewScreenSizes
@Composable
private fun UsersScreenLayoutPreview() {
    FindroidTheme {
        UsersScreenLayout(
            state =
                UsersState(
                    users = listOf(dummyUser, User(id = UUID.randomUUID(), name = "Bob", serverId = "")),
                    publicUsers =
                        listOf(User(id = UUID.randomUUID(), name = "Alice", serverId = "")),
                    serverName = "Jellyfin",
                    currentUserId = dummyUser.id,
                ),
            onAction = {},
        )
    }
}
