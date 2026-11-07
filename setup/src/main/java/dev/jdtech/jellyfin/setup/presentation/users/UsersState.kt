package dev.jdtech.jellyfin.setup.presentation.users

import dev.jdtech.jellyfin.models.User
import java.util.UUID

data class UsersState(
    val users: List<User> = emptyList(),
    val publicUsers: List<User> = emptyList(),
    val serverName: String? = null,
    val currentUserId: UUID? = null,
)
