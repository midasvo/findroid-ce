package dev.jdtech.jellyfin

import androidx.navigation3.runtime.NavKey
import dev.jdtech.jellyfin.models.CollectionType
import kotlinx.serialization.Serializable

@Serializable data object WelcomeRoute : NavKey

@Serializable data object PermissionRoute : NavKey

@Serializable data object ServersRoute : NavKey

@Serializable data object AddServerRoute : NavKey

@Serializable data object UsersRoute : NavKey

@Serializable data class LoginRoute(val username: String? = null) : NavKey

@Serializable data object MainRoute : NavKey

@Serializable
data class LibraryRoute(
    val libraryId: String,
    val libraryName: String,
    val libraryType: CollectionType,
) : NavKey

@Serializable
data class CollectionRoute(
    val collectionId: String,
    val collectionName: String,
) : NavKey

@Serializable data class MovieRoute(val itemId: String) : NavKey

@Serializable data class ShowRoute(val itemId: String) : NavKey

@Serializable data class SeasonRoute(val seasonId: String) : NavKey

/** CE (#130): an episode opens on the season page of its season, with that episode focused. */
@Serializable data class EpisodeRoute(val episodeId: String) : NavKey

/** CE (#136): person page showing bio and movies/shows. */
@Serializable data class PersonRoute(val personId: String) : NavKey

@Serializable
data class PlayerRoute(
    val itemId: String,
    val itemKind: String,
    val mediaSourceId: String? = null,
    val startFromBeginning: Boolean = false,
    val shuffle: Boolean = false,
) : NavKey

@Serializable data object SettingsRoute : NavKey

@Serializable data class SettingsSubRoute(val indexes: List<Int>) : NavKey
