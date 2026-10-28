package dev.jdtech.jellyfin.setup.presentation.addresses

import dev.jdtech.jellyfin.models.ServerAddress
import dev.jdtech.jellyfin.models.UiText

data class ServerAddressesState(
    val addresses: List<ServerAddress> = emptyList(),
    val error: Collection<UiText>? = null,
)
