package dev.jdtech.jellyfin.models

import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.io.File
import java.util.UUID
import org.jellyfin.sdk.model.api.MediaProtocol
import org.jellyfin.sdk.model.api.MediaSourceInfo

data class FindroidSource(
    val id: String,
    val name: String,
    val type: FindroidSourceType,
    val path: String,
    val size: Long,
    val mediaStreams: List<FindroidMediaStream>,
    val downloadId: Long? = null,
    /**
     * True when [path] points at a server-side transcode rather than the original
     * file: an HLS manifest on the playback path, or a progressive stream on the
     * download path (Dolby Vision conversion or a user-picked quality cap).
     */
    val transcoded: Boolean = false,
)

suspend fun MediaSourceInfo.toFindroidSource(
    jellyfinRepository: JellyfinRepository,
    itemId: UUID,
    includePath: Boolean = false,
): FindroidSource {
    // When the server decided this source needs transcoding it returns a (relative)
    // transcodingUrl — an HLS manifest on the playback path, a progressive stream
    // on the download path. Honor it over the original file.
    val transcodeUrl = transcodingUrl
    val transcoded = !transcodeUrl.isNullOrBlank()
    val path =
        when {
            transcoded -> jellyfinRepository.getBaseUrl() + transcodeUrl
            protocol == MediaProtocol.FILE -> {
                try {
                    if (includePath) jellyfinRepository.getStreamUrl(itemId, id.orEmpty()) else ""
                } catch (_: Exception) {
                    ""
                }
            }
            protocol == MediaProtocol.HTTP -> this.path.orEmpty()
            else -> ""
        }
    return FindroidSource(
        id = id.orEmpty(),
        name = name.orEmpty(),
        type = FindroidSourceType.REMOTE,
        path = path,
        size = size ?: 0,
        mediaStreams =
            mediaStreams?.map { it.toFindroidMediaStream(jellyfinRepository) } ?: emptyList(),
        transcoded = transcoded,
    )
}

suspend fun FindroidSourceDto.toFindroidSource(
    serverDatabaseDao: ServerDatabaseDao
): FindroidSource = toFindroidSource(serverDatabaseDao.getMediaStreamsBySourceId(id))

/** Maps with already-fetched [streams], so a list of sources costs no query per source. */
fun FindroidSourceDto.toFindroidSource(streams: List<FindroidMediaStreamDto>): FindroidSource {
    return FindroidSource(
        id = id,
        name = name,
        type = type,
        path = path,
        size = File(path).length(),
        mediaStreams = streams.map { it.toFindroidMediaStream() },
        downloadId = downloadId,
    )
}

/**
 * The downloaded (local) sources of every item in [itemIds], keyed by item id, in two queries
 * however long the list is (none when it is empty). Items without a download are absent.
 */
suspend fun ServerDatabaseDao.getLocalSourcesForItems(
    itemIds: Collection<UUID>
): Map<UUID, List<FindroidSource>> {
    if (itemIds.isEmpty()) return emptyMap()
    val dtos = itemIds.distinct().chunked(SQL_CHUNK).flatMap { getSourcesForItems(it) }
    if (dtos.isEmpty()) return emptyMap()
    val streams =
        dtos
            .map { it.id }
            .chunked(SQL_CHUNK)
            .flatMap { getMediaStreamsForSources(it) }
            .groupBy { it.sourceId }
    return dtos
        .groupBy { it.itemId }
        .mapValues { (_, sources) -> sources.map { it.toFindroidSource(streams[it.id].orEmpty()) } }
}

// Stays under SQLite's historical limit of 999 bound variables per statement.
private const val SQL_CHUNK = 500

enum class FindroidSourceType {
    REMOTE,
    LOCAL,
}
