package dev.jdtech.jellyfin.film.domain.homerows

import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.CollectionType
import dev.jdtech.jellyfin.models.Server
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.settings.domain.models.Preference
import java.util.UUID
import javax.inject.Inject
import org.jellyfin.sdk.model.api.BaseItemDto

/** CE (#142): whose Home layout it is. Each user on each server has their own. */
data class HomeRowsProfile(val serverId: String, val userId: UUID) {
    val preference: Preference<String?>
        get() = Preference("pref_ce_home_rows_${serverId}_$userId", null)

    companion object {
        fun of(server: Server?): HomeRowsProfile? {
            val userId = server?.currentUserId ?: return null
            return HomeRowsProfile(server.id, userId)
        }
    }
}

/** CE (#142): reads and writes the Home layout of a [HomeRowsProfile]. */
class HomeRowsStore
@Inject
constructor(
    private val appPreferences: AppPreferences,
    private val database: ServerDatabaseDao,
) {
    suspend fun currentProfile(): HomeRowsProfile? =
        appPreferences.getValue(appPreferences.currentServer)?.let {
            HomeRowsProfile.of(database.getServer(it))
        }

    /** Null when nothing is stored, or there's no profile to store it for. */
    fun read(profile: HomeRowsProfile?): List<HomeRowConfig>? =
        profile?.let { HomeRows.decode(appPreferences.getValue(it.preference)) }

    fun write(profile: HomeRowsProfile, rows: List<HomeRowConfig>) {
        appPreferences.setValue(profile.preference, HomeRows.encode(rows))
    }

    fun legacy(): LegacyHomeSwitches =
        LegacyHomeSwitches(
            suggestions = appPreferences.getValue(appPreferences.homeSuggestions),
            continueWatching = appPreferences.getValue(appPreferences.homeContinueWatching),
            nextUp = appPreferences.getValue(appPreferences.homeNextUp),
            latest = appPreferences.getValue(appPreferences.homeLatest),
        )
}

/** The libraries that can have a Latest row on Home. */
suspend fun JellyfinRepository.homeLibraries(): List<BaseItemDto> =
    getUserViews().filter { view ->
        CollectionType.fromString(view.collectionType?.serialName) in CollectionType.supported
    }
