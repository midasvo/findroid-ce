package dev.jdtech.jellyfin.di

import dev.jdtech.jellyfin.api.JellyfinApi
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.ServerWithAddressAndUser
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * What the API client needs to act for the current server and user: the server's current address,
 * and the current user's token and id. All null when the database has no address for the server.
 */
internal data class ApiSession(
    val serverId: String,
    val baseUrl: String?,
    val accessToken: String?,
    val userId: UUID?,
)

/** The session the database describes for [serverId], the way the client was always set up. */
internal fun ServerWithAddressAndUser?.toApiSession(serverId: String): ApiSession {
    val address = this?.address ?: return ApiSession(serverId, null, null, null)
    return ApiSession(serverId, address.address, user?.accessToken, user?.id)
}

internal fun JellyfinApi.applySession(session: ApiSession) {
    api.update(baseUrl = session.baseUrl, accessToken = session.accessToken)
    userId = session.userId
}

internal fun JellyfinApi.holds(session: ApiSession): Boolean =
    api.baseUrl == session.baseUrl &&
        api.accessToken == session.accessToken &&
        userId == session.userId

/** The copy of the session kept in preferences, read without touching Room. */
internal fun AppPreferences.savedApiSession(): ApiSession? {
    val serverId = getValue(sessionServerId) ?: return null
    val userId =
        getValue(sessionUserId)?.let { id -> runCatching { UUID.fromString(id) }.getOrNull() }
    return ApiSession(serverId, getValue(sessionBaseUrl), getValue(sessionAccessToken), userId)
}

internal fun AppPreferences.saveApiSession(session: ApiSession?) {
    if (savedApiSession() == session) return
    sharedPreferences
        .edit()
        .putString(sessionServerId.backendName, session?.serverId)
        .putString(sessionBaseUrl.backendName, session?.baseUrl)
        .putString(sessionAccessToken.backendName, session?.accessToken)
        .putString(sessionUserId.backendName, session?.userId?.toString())
        .apply()
}

/**
 * Keeps the copy in preferences equal to what the database says for the current server, whatever
 * changes it: a login, a server, user or address switch, a deleted user or server. Every one of
 * those writes Room, so watching Room covers them all from one place.
 *
 * The client itself is only corrected once, at the first reading: the copy can be behind when the
 * app was stopped right after a change. Later changes are applied to the client by the code that
 * makes them (SetupRepositoryImpl), as before. The correction is skipped when that code has already
 * changed the client since [initial] was applied.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun mirrorApiSession(
    jellyfinApi: JellyfinApi,
    appPreferences: AppPreferences,
    database: ServerDatabaseDao,
    initial: ApiSession?,
) {
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        var first = true
        appPreferences
            .observe(appPreferences.currentServer)
            .flatMapLatest { serverId ->
                if (serverId == null) {
                    flowOf(null)
                } else {
                    database.observeServerWithAddressAndUser(serverId).map {
                        it.toApiSession(serverId)
                    }
                }
            }
            .distinctUntilChanged()
            .catch { e -> Timber.e(e, "Failed to watch the current session") }
            .collect { session ->
                if (first) {
                    first = false
                    if (
                        initial != null &&
                            session != null &&
                            session != initial &&
                            jellyfinApi.holds(initial)
                    ) {
                        jellyfinApi.applySession(session)
                    }
                }
                appPreferences.saveApiSession(session)
            }
    }
}
