package dev.jdtech.jellyfin.models

import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

interface FindroidItem {
    val id: UUID
    val name: String
    val originalTitle: String?
    val overview: String
    val played: Boolean
    val favorite: Boolean
    val canPlay: Boolean
    val canDownload: Boolean
    val sources: List<FindroidSource>
    val runtimeTicks: Long
    val playbackPositionTicks: Long
    val unplayedItemCount: Int?
    val images: FindroidImages
    val chapters: List<FindroidChapter>
}

suspend fun BaseItemDto.toFindroidItem(
    jellyfinRepository: JellyfinRepository,
    serverDatabase: ServerDatabaseDao? = null,
): FindroidItem? =
    toFindroidItem(
        jellyfinRepository,
        if (serverDatabase != null && type in LOCAL_SOURCE_KINDS)
            serverDatabase.getLocalSourcesForItems(listOf(id))
        else emptyMap(),
    )

private val LOCAL_SOURCE_KINDS = setOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE)

/** For lists: [localSources] comes from one [getLocalSourcesForItems] call for all items. */
suspend fun BaseItemDto.toFindroidItem(
    jellyfinRepository: JellyfinRepository,
    localSources: Map<UUID, List<FindroidSource>>,
): FindroidItem? {
    return when (type) {
        BaseItemKind.MOVIE -> toFindroidMovie(jellyfinRepository, localSources)
        BaseItemKind.EPISODE -> toFindroidEpisode(jellyfinRepository, localSources)
        BaseItemKind.SEASON -> toFindroidSeason(jellyfinRepository)
        BaseItemKind.SERIES -> toFindroidShow(jellyfinRepository)
        BaseItemKind.BOX_SET -> toFindroidBoxSet(jellyfinRepository)
        BaseItemKind.FOLDER -> toFindroidFolder(jellyfinRepository)
        else -> null
    }
}

fun FindroidItem.isDownloading(): Boolean {
    return sources
        .filter { it.type == FindroidSourceType.LOCAL }
        .any { it.path.endsWith(".download") }
}

fun FindroidItem.isDownloaded(): Boolean {
    return sources
        .filter { it.type == FindroidSourceType.LOCAL }
        .any { !it.path.endsWith(".download") }
}

/** The versions a user can pick from. A downloaded copy keeps the id of its server version. */
fun FindroidItem.getVersions(): List<FindroidSource> {
    return sources.distinctBy { it.id }
}
